package com.flowsecurity.service;

import com.flowsecurity.config.FlowProperties;
import com.flowsecurity.model.ServiceEntity;
import com.flowsecurity.repository.ServiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ServiceConfigService {

    private static final Logger log = LoggerFactory.getLogger(ServiceConfigService.class);

    private final ServiceRepository serviceRepository;
    private final StringRedisTemplate redis;
    private final FlowProperties properties;

    public ServiceConfigService(ServiceRepository serviceRepository,
                                StringRedisTemplate redis,
                                FlowProperties properties) {
        this.serviceRepository = serviceRepository;
        this.redis = redis;
        this.properties = properties;
    }

    public void setPublic(String serviceName) {
        ServiceEntity entity = serviceRepository.findById(serviceName)
                .orElse(new ServiceEntity(serviceName, false));
        entity.setPublic(true);
        serviceRepository.save(entity);

        redis.opsForSet().add(properties.getPublicServicesKey(), serviceName);
        log.info("Service '{}' marked as PUBLIC", serviceName);
    }

    public void setPrivate(String serviceName) {
        serviceRepository.findById(serviceName).ifPresent(entity -> {
            entity.setPublic(false);
            serviceRepository.save(entity);
        });

        redis.opsForSet().remove(properties.getPublicServicesKey(), serviceName);
        log.info("Service '{}' marked as PRIVATE", serviceName);
    }

    public boolean isPublic(String serviceName) {
        return Boolean.TRUE.equals(
                redis.opsForSet().isMember(properties.getPublicServicesKey(), serviceName));
    }
}
