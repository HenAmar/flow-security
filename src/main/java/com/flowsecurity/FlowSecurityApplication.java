package com.flowsecurity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class FlowSecurityApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowSecurityApplication.class, args);
    }
}
