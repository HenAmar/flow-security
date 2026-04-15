# Flow Security — Data Flow Monitoring System

A system that monitors data flows between services, detects new sensitive data flows, and fires alerts with appropriate severity based on service exposure.

## Language Choice

Go would arguably be a better fit for this type of high-throughput event processing system — lightweight goroutines, built-in concurrency primitives, and lower memory footprint. However, I chose **Java with Spring Boot** deliberately to leverage its mature ecosystem for handling edge cases: reliable queue patterns, crash recovery, graceful shutdown hooks, and clear separation of concerns through dependency injection and interfaces. The focus here was on correctness and production-readiness over raw performance.

## System Design

```
                           ┌──────────────┐
                           │    Sensor    │
                           │ (millions/s) │
                           └──────┬───────┘
                                  │
                           POST /events (JSON array)
                                  │
                                  ▼
╔═══════════════════════════════════════════════════════════════╗
║                     SPRING BOOT API                          ║
║                                                              ║
║   POST /events              PUT /services/{name}/public      ║
║   → LPUSH Redis queue       → Write to PostgreSQL            ║
║   → return 202 immediately  → Update Redis cache             ║
║                              → return 200                    ║
╚════════════╤═══════════════════════════════╤══════════════════╝
             │                               │
             ▼                               ▼
╔════════════════════════╗     ╔══════════════════════════╗
║        REDIS           ║     ║      POSTGRESQL          ║
║     (runtime)          ║     ║   (source of truth)      ║
║                        ║     ║                          ║
║  events:queue   LIST   ║     ║  services table          ║
║  ┌──────────────────┐  ║     ║  ┌────────┬──────────┐  ║
║  │ event3           │  ║     ║  │ name   │ is_public│  ║
║  │ event2           │  ║     ║  ├────────┼──────────┤  ║
║  │ event1           │  ║     ║  │stripe  │ true     │  ║
║  └──────────────────┘  ║     ║  │payment │ false    │  ║
║                        ║     ║  └────────┴──────────┘  ║
║  flow:{src}:{dst} SET  ║     ╚══════════════════════════╝
║  ┌──────────────────┐  ║              │
║  │ FIRST_NAME       │  ║              │ sync on startup
║  │ CREDIT_CARD_NUM  │  ║              │ + on config change
║  └──────────────────┘  ║              │
║                        ║◄─────────────┘
║  public_services  SET  ║
║  ┌──────────────────┐  ║
║  │ stripe.com       │  ║
║  └──────────────────┘  ║
║                        ║
║  Config:               ║
║  appendonly yes         ║
║  appendfsync everysec   ║
╚═══════════╤════════════╝
            │
            │ BRPOPLPUSH (reliable queue)
            │
            ▼
╔═══════════════════════════════════════════════════════╗
║                  BACKGROUND WORKER                    ║
║                                                       ║
║  ① Pop event from queue                               ║
║  ② Filter sensitive types only                        ║
║     (FIRST_NAME, LAST_NAME, CREDIT_CARD, SSN)        ║
║  ③ SISMEMBER — already seen? → skip (99% of events)  ║
║  ④ If new → Log alert FIRST (at-least-once)           ║
║  ⑤ SADD — mark as seen                                ║
║  ⑥ ACK — remove from processing list                  ║
╚═══════════════════════╤═══════════════════════════════╝
                        │
                        ▼
              ╔════════════════════╗
              ║  AlertDispatcher   ║  (interface)
              ║                    ║
              ║  → LogDispatcher   ║  (our impl: stdout)
              ║  → WebhookDisp.   ║  (production: HTTP)
              ╚════════════════════╝
                        │
                        ▼
    [ALERT][HIGH] CREDIT_CARD_NUMBER: users → stripe.com
    [ALERT][MEDIUM] FIRST_NAME: users → payment
```

## Redis Data Model

Redis serves two purposes: **event queue** and **dedup memory**.

```
LPUSH → events:queue → BRPOPLPUSH → events:processing → LREM → gone
 (in)                   (pop+move)                       (ack)
```

Events are removed from the queue on pop. The `events:processing` list acts as a safety net — if the worker crashes before acking, unprocessed events are recovered on startup.

### Keys at runtime:

| Key | Type | Purpose | Example |
|---|---|---|---|
| `events:queue` | LIST | Inbox — events waiting to be processed | `[event3, event2, event1]` |
| `events:processing` | LIST | In-flight — crash recovery backup | `[event1]` (or empty) |
| `flow:{src}:{dst}` | SET | Dedup — sensitive data types seen per flow | `{FIRST_NAME, CREDIT_CARD_NUMBER}` |
| `public_services` | SET | Cache of public services (synced from PG) | `{stripe.com}` |

### Dedup key structure:

```
Key   = flow:{source}:{destination}     → WHO talks to WHO
Value = SET of sensitive data types     → WHAT flowed between them

Example:
  flow:users:payment        → { FIRST_NAME, LAST_NAME, CREDIT_CARD_NUMBER }
  flow:payment:stripe.com   → { CREDIT_CARD_NUMBER }
```

Storage is bounded by `services² × sensitive_types` (~40K entries max, ~4MB). Billions of events do NOT mean billions of storage — we only store unique flow combinations.

## Key Design Decisions

1. **Non-blocking ingestion** — POST → Redis queue → 202. Sensor never waits.
2. **Reliable queue** — `BRPOPLPUSH` pattern: pop from queue, push to processing list, ack after done. Crash recovery reprocesses unacked events on startup.
3. **At-least-once alerts** — Log alert first, persist to Redis second. May duplicate on crash, but never lose an alert.
4. **Separation of concerns** — Redis for hot path (queue, dedup, cache). PostgreSQL for config source of truth (service public/private).
5. **Alert interface** — `AlertDispatcher` is pluggable. Current: console logger. Production: swap to webhook/HTTP dispatcher with no code change.

## Crash Recovery

| Scenario | What happens | Data loss |
|---|---|---|
| App crash | Unacked events in `events:processing`, reprocessed on restart | None |
| Redis restart | AOF replays writes, queue + dedup sets restored | ~1 sec |
| Redis dead | Dedup lost → may re-fire some alerts (minor alert fatigue) | Acceptable |
| PG dead | Redis cache still serves, no impact on hot path | Config stale |

## Known Trade-off: Race Condition in Dedup

The current dedup logic uses two separate Redis calls — `SISMEMBER` (check) then `SADD` (mark):

```java
if (!redis.opsForSet().isMember(flowKey, dataType)) {   // check
    alertDispatcher.dispatch(alert);                      // alert first
    redis.opsForSet().add(flowKey, dataType);             // mark seen
}
```

With a **single worker thread** (current design), this is safe — no concurrent access. However, with multiple workers, two threads could both read "not seen" before either writes, causing a **duplicate alert**.

The atomic alternative — `SADD` first, alert if return value is 1 — eliminates the race but introduces a different risk: if the app crashes after `SADD` but before alerting, the alert is **lost forever** (marked as seen but never dispatched).

I chose **correctness over dedup** — it's better to occasionally duplicate an alert than to silently lose one. For multiple workers, this can be solved with a Redis Lua script that atomically checks, marks, and returns the result in a single operation.

## Quick Start

```bash
# Start all services (Redis + PostgreSQL + App)
docker compose up --build -d

# Watch alerts in real-time
docker compose logs -f app | grep "ALERT"

# Run demo with auto-verification
bash demo.sh

# Cleanup
docker compose down -v
```

## API Endpoints

### 1. Ingest Events
```bash
POST /events
```
Receives sensor data, queues for processing, returns 202 immediately.

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '[
    {
      "date": "1610293274000",
      "source": "users",
      "destination": "payment",
      "values": {
        "firstName": "FIRST_NAME",
        "lastName": "LAST_NAME",
        "cc": "CREDIT_CARD_NUMBER",
        "price": "NUMBER"
      }
    }
  ]'
```

### 2. Set Service as Public
```bash
PUT /services/{name}/public
```

```bash
curl -X PUT http://localhost:8080/services/stripe.com/public
```

### 3. Set Service as Private
```bash
DELETE /services/{name}/public
```

```bash
curl -X DELETE http://localhost:8080/services/stripe.com/public
```

## Scale-Up Path

| Current | Production |
|---|---|
| 1 API instance | N instances + load balancer |
| Redis LIST queue | Kafka (partitioned by src:dst) |
| 1 worker thread | N workers per partition |
| 1 Redis instance | Redis Cluster (sharded) |
| 1 PostgreSQL | PG with read replicas |
