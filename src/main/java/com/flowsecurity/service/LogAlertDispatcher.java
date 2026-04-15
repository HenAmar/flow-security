package com.flowsecurity.service;

import com.flowsecurity.model.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LogAlertDispatcher implements AlertDispatcher {

    private static final Logger log = LoggerFactory.getLogger(LogAlertDispatcher.class);

    @Override
    public void dispatch(Alert alert) {
        log.warn("🚨 {} ", alert);
    }
}
