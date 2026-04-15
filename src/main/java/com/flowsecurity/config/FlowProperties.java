package com.flowsecurity.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@ConfigurationProperties(prefix = "flow")
public class FlowProperties {

    private List<String> sensitiveTypes;
    private String queueKey;
    private String processingKey;
    private String flowKeyPrefix;
    private String publicServicesKey;

    public List<String> getSensitiveTypes() { return sensitiveTypes; }
    public void setSensitiveTypes(List<String> sensitiveTypes) { this.sensitiveTypes = sensitiveTypes; }

    public String getQueueKey() { return queueKey; }
    public void setQueueKey(String queueKey) { this.queueKey = queueKey; }

    public String getProcessingKey() { return processingKey; }
    public void setProcessingKey(String processingKey) { this.processingKey = processingKey; }

    public String getFlowKeyPrefix() { return flowKeyPrefix; }
    public void setFlowKeyPrefix(String flowKeyPrefix) { this.flowKeyPrefix = flowKeyPrefix; }

    public String getPublicServicesKey() { return publicServicesKey; }
    public void setPublicServicesKey(String publicServicesKey) { this.publicServicesKey = publicServicesKey; }
}
