package com.flowsecurity.controller;

import com.flowsecurity.service.ServiceConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/services")
public class ServiceController {

    private final ServiceConfigService serviceConfigService;

    public ServiceController(ServiceConfigService serviceConfigService) {
        this.serviceConfigService = serviceConfigService;
    }

    @PutMapping("/{name}/public")
    public ResponseEntity<Map<String, String>> setPublic(@PathVariable String name) {
        serviceConfigService.setPublic(name);
        return ResponseEntity.ok(Map.of("service", name, "status", "public"));
    }

    @DeleteMapping("/{name}/public")
    public ResponseEntity<Map<String, String>> setPrivate(@PathVariable String name) {
        serviceConfigService.setPrivate(name);
        return ResponseEntity.ok(Map.of("service", name, "status", "private"));
    }
}
