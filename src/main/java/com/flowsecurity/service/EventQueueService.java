package com.flowsecurity.service;

import com.flowsecurity.config.FlowProperties;
import com.flowsecurity.model.FlowEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EventQueueService {

    private static final Logger log = LoggerFactory.getLogger(EventQueueService.class);

    private final StringRedisTemplate redis;
    private final FlowProperties properties;
    private final ObjectMapper objectMapper;

    public EventQueueService(StringRedisTemplate redis, FlowProperties properties, ObjectMapper objectMapper) {
        this.redis = redis;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public void enqueue(List<FlowEvent> events) {
        if (events.isEmpty()) return;

        String[] serialized = events.stream()
                .map(this::serialize)
                .toArray(String[]::new);

        redis.opsForList().leftPushAll(properties.getQueueKey(), serialized);
        log.debug("Enqueued {} events", events.size());
    }

    private String serialize(FlowEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize event", e);
        }
    }
}
