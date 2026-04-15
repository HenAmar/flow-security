#!/bin/bash
# Demo & verification script for Flow Security System
# Usage: docker compose up --build -d && sleep 10 && bash demo.sh

BASE_URL="http://localhost:8080"
BOLD="\033[1m"
GREEN="\033[32m"
RED="\033[31m"
YELLOW="\033[33m"
RESET="\033[0m"
PASS=0
FAIL=0

check_alert() {
  local description="$1"
  local pattern="$2"
  local expected="$3"
  local since="${4:-10s}"
  sleep 2
  local count
  count=$(docker compose logs app --since "$since" 2>&1 | grep -c "$pattern")
  if [ "$count" = "$expected" ]; then
    echo -e "  ${GREEN}✅ PASS${RESET} — $description"
    PASS=$((PASS+1))
  else
    echo -e "  ${RED}❌ FAIL${RESET} — $description (expected $expected, got $count)"
    FAIL=$((FAIL+1))
  fi
}

echo -e "${BOLD}╔══════════════════════════════════════════════════════════╗${RESET}"
echo -e "${BOLD}║           FLOW SECURITY — DEMO & VERIFICATION           ║${RESET}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════╝${RESET}"
echo ""
echo -e "${BOLD}Services used in this test:${RESET}"
echo "  • users        — internal service (private)"
echo "  • payment      — internal service (private)"
echo "  • stripe.com   — external service (toggled public/private)"
echo "  • logs         — internal service (private)"
echo "  • metrics      — internal service (private)"
echo ""
echo -e "${BOLD}Sensitive data types:${RESET} FIRST_NAME, LAST_NAME, CREDIT_CARD_NUMBER, SOCIAL_SECURITY_NUMBER"
echo -e "${BOLD}Non-sensitive types:${RESET}  NUMBER, DATE"
echo ""
echo -e "${BOLD}Test plan:${RESET}"
echo "  1. Send events with mixed data types     → expect 4 MEDIUM alerts (sensitive only)"
echo "  2. Resend same events                     → expect 0 alerts (dedup)"
echo "  3. Mark stripe.com as public              → config change"
echo "  4. Send SSN to public stripe.com          → expect 1 HIGH alert"
echo "  5. Send FIRST_NAME in reverse direction   → expect 1 MEDIUM alert (new flow)"
echo "  6. Remove stripe.com public, send SSN     → expect 1 MEDIUM alert (no longer public)"
echo "  7. Re-mark stripe.com public, send data   → expect 1 HIGH alert (public again)"
echo "  8. Send only non-sensitive data            → expect 0 alerts"
echo ""
echo -e "${BOLD}──────────────────────────────────────────────────────────${RESET}"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 1: Send initial events (users → payment, payment → stripe)${RESET}"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293274000","source":"users","destination":"payment","values":{"firstName":"FIRST_NAME","lastName":"LAST_NAME","cc":"CREDIT_CARD_NUMBER","price":"NUMBER","txDate":"DATE"}},
    {"date":"1610293275000","source":"payment","destination":"stripe.com","values":{"credit_card":"CREDIT_CARD_NUMBER","amount":"NUMBER"}}
  ]' > /dev/null
sleep 3
check_alert "MEDIUM alert: FIRST_NAME users → payment" "MEDIUM.*FIRST_NAME.*users.*payment" "1" "15s"
check_alert "MEDIUM alert: LAST_NAME users → payment" "MEDIUM.*LAST_NAME.*users.*payment" "1" "15s"
check_alert "MEDIUM alert: CREDIT_CARD_NUMBER users → payment" "MEDIUM.*CREDIT_CARD_NUMBER.*users.*payment" "1" "15s"
check_alert "MEDIUM alert: CREDIT_CARD_NUMBER payment → stripe" "MEDIUM.*CREDIT_CARD_NUMBER.*payment.*stripe" "1" "15s"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 2: Send SAME events again (dedup — should NOT alert)${RESET}"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293276000","source":"users","destination":"payment","values":{"firstName":"FIRST_NAME","cc":"CREDIT_CARD_NUMBER"}}
  ]' > /dev/null
sleep 2
ALERTS=$(docker compose logs app --since 3s 2>&1 | grep -c "ALERT.*users.*payment")
if [ "$ALERTS" = "0" ]; then
  echo -e "  ${GREEN}✅ PASS${RESET} — no duplicate alerts"
  PASS=$((PASS+1))
else
  echo -e "  ${RED}❌ FAIL${RESET} — got $ALERTS duplicate alerts"
  FAIL=$((FAIL+1))
fi
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 3: Mark stripe.com as PUBLIC${RESET}"
RESP=$(curl -s -X PUT "$BASE_URL/services/stripe.com/public")
if echo "$RESP" | grep -q '"status":"public"'; then
  echo -e "  ${GREEN}✅ PASS${RESET} — stripe.com marked public"
  PASS=$((PASS+1))
else
  echo -e "  ${RED}❌ FAIL${RESET} — $RESP"
  FAIL=$((FAIL+1))
fi
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 4: Send SSN to public service (should be HIGH alert)${RESET}"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293277000","source":"payment","destination":"stripe.com","values":{"ssn":"SOCIAL_SECURITY_NUMBER","amount":"NUMBER"}}
  ]' > /dev/null
check_alert "HIGH alert: SSN payment → stripe.com" "HIGH.*SOCIAL_SECURITY_NUMBER.*payment.*stripe" "1"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 5: Reverse direction (payment → users, should alert)${RESET}"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293278000","source":"payment","destination":"users","values":{"name":"FIRST_NAME"}}
  ]' > /dev/null
check_alert "MEDIUM alert: FIRST_NAME payment → users" "MEDIUM.*FIRST_NAME.*payment.*users" "1"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 6: Remove public status — new flows should be MEDIUM${RESET}"
curl -s -X DELETE "$BASE_URL/services/stripe.com/public" > /dev/null
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293280000","source":"users","destination":"stripe.com","values":{"ssn":"SOCIAL_SECURITY_NUMBER"}}
  ]' > /dev/null
check_alert "MEDIUM alert after removing public" "MEDIUM.*SOCIAL_SECURITY_NUMBER.*users.*stripe" "1"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 7: Re-mark public — new flows should be HIGH again${RESET}"
curl -s -X PUT "$BASE_URL/services/stripe.com/public" > /dev/null
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293281000","source":"users","destination":"stripe.com","values":{"ln":"LAST_NAME"}}
  ]' > /dev/null
check_alert "HIGH alert after re-marking public" "HIGH.*LAST_NAME.*users.*stripe" "1"
echo ""

# ─────────────────────────────────────────────
echo -e "${YELLOW}▶ TEST 8: Non-sensitive data only (should NOT alert)${RESET}"
curl -s -X POST "$BASE_URL/events" \
  -H "Content-Type: application/json" \
  -d '[
    {"date":"1610293279000","source":"logs","destination":"metrics","values":{"count":"NUMBER","date":"DATE"}}
  ]' > /dev/null
sleep 2
ALERTS=$(docker compose logs app --since 3s 2>&1 | grep -c "ALERT.*logs.*metrics")
if [ "$ALERTS" = "0" ]; then
  echo -e "  ${GREEN}✅ PASS${RESET} — no alert for non-sensitive data"
  PASS=$((PASS+1))
else
  echo -e "  ${RED}❌ FAIL${RESET} — got $ALERTS alerts for non-sensitive data"
  FAIL=$((FAIL+1))
fi
echo ""

# ─────────────────────────────────────────────
echo -e "${BOLD}═══════════════════════════════════════════════════════════${RESET}"
echo -e "${BOLD}  Results: ${GREEN}$PASS passed${RESET}, ${RED}$FAIL failed${RESET} (of $((PASS+FAIL)))"
echo -e "${BOLD}═══════════════════════════════════════════════════════════${RESET}"
