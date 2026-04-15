package com.flowsecurity.service;

import com.flowsecurity.config.FlowProperties;
import com.flowsecurity.model.Alert;
import com.flowsecurity.model.FlowEvent;
import com.flowsecurity.repository.ServiceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EventProcessor {

    private static final Logger log = LoggerFactory.getLogger(EventProcessor.class);

    private final StringRedisTemplate redis;
    private final FlowProperties properties;
    private final ObjectMapper objectMapper;
    private final AlertDispatcher alertDispatcher;
    private final ServiceConfigService serviceConfigService;
    private final ServiceRepository serviceRepository;
    private final Set<String> sensitiveTypes;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public EventProcessor(StringRedisTemplate redis,
                          FlowProperties properties,
                          ObjectMapper objectMapper,
                          AlertDispatcher alertDispatcher,
                          ServiceConfigService serviceConfigService,
                          ServiceRepository serviceRepository) {
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.alertDispatcher = alertDispatcher;
        this.serviceConfigService = serviceConfigService;
        this.serviceRepository = serviceRepository;
        this.sensitiveTypes = Set.copyOf(properties.getSensitiveTypes());
    }

    @PostConstruct
    public void start() {
        rebuildCacheFromPostgres();
        recoverUnackedEvents();
        executor.submit(this::processLoop);
        log.info("Event processor started");
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdown();
        try {
            executor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("Event processor stopped");
    }

    private void rebuildCacheFromPostgres() {
        // Rebuild public_services cache from PG source of truth
        redis.delete(properties.getPublicServicesKey());
        serviceRepository.findByIsPublicTrue().forEach(entity ->
                redis.opsForSet().add(properties.getPublicServicesKey(), entity.getName()));
        log.info("Rebuilt public_services cache from PostgreSQL");
    }

    private void recoverUnackedEvents() {
        // Reprocess any events that were in-flight during a previous crash
        Long count = redis.opsForList().size(properties.getProcessingKey());
        if (count != null && count > 0) {
            log.warn("Found {} unacked events, reprocessing...", count);
            String event;
            while ((event = redis.opsForList().rightPop(properties.getProcessingKey())) != null) {
                processEvent(event);
            }
            log.info("Recovered all unacked events");
        }
    }

    private void processLoop() {
        log.info("Worker loop started, waiting for events...");
        while (running.get()) {
            try {
                // BRPOPLPUSH: pop from queue, push to processing (reliable queue)
                String event = redis.opsForList().rightPopAndLeftPush(
                        properties.getQueueKey(),
                        properties.getProcessingKey());

                if (event == null) {
                    // No event available, short sleep to avoid busy-waiting
                    Thread.sleep(100);
                    continue;
                }

                processEvent(event);

                // ACK: remove from processing list
                redis.opsForList().remove(properties.getProcessingKey(), 1, event);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Error processing event", e);
            }
        }
    }

    private void processEvent(String eventJson) {
        try {
            FlowEvent event = objectMapper.readValue(eventJson, FlowEvent.class);
            String src = event.getSource();
            String dst = event.getDestination();
            Map<String, String> values = event.getValues();

            if (values == null || values.isEmpty()) return;

            String flowKey = properties.getFlowKeyPrefix() + src + ":" + dst;

            for (String dataType : values.values()) {
                if (!sensitiveTypes.contains(dataType)) continue;

                // Check without writing first (fast path for seen data)
                Boolean alreadySeen = redis.opsForSet().isMember(flowKey, dataType);
                if (Boolean.TRUE.equals(alreadySeen)) continue;

                // New sensitive flow detected — alert FIRST, persist SECOND
                boolean isPublic = serviceConfigService.isPublic(src)
                        || serviceConfigService.isPublic(dst);

                Alert.Severity severity = isPublic ? Alert.Severity.HIGH : Alert.Severity.MEDIUM;
                Alert alert = new Alert(severity, src, dst, dataType);

                // Log first (at-least-once: may duplicate, never lose)
                alertDispatcher.dispatch(alert);

                // Then mark as seen
                redis.opsForSet().add(flowKey, dataType);
            }
        } catch (Exception e) {
            log.error("Failed to process event: {}", eventJson, e);
        }
    }
}
