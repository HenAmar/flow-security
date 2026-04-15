package com.flowsecurity.controller;

import com.flowsecurity.model.FlowEvent;
import com.flowsecurity.service.EventQueueService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
public class EventController {

    private final EventQueueService eventQueueService;

    public EventController(EventQueueService eventQueueService) {
        this.eventQueueService = eventQueueService;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> receiveEvents(@RequestBody List<FlowEvent> events) {
        eventQueueService.enqueue(events);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("status", "accepted", "count", String.valueOf(events.size())));
    }
}
