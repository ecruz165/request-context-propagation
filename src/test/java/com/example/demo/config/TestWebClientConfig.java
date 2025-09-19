package com.example.demo.config;

import com.example.demo.filter.RequestContextWebClientPropagationFilter;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test configuration for WebClient with propagation filter
 */
@TestConfiguration
public class TestWebClientConfig {

    @Bean
    public WebClient webClient(RequestContextWebClientPropagationFilter propagationFilter) {
        return WebClient.builder()
                .baseUrl("http://localhost:8089")
                .filter(propagationFilter.createFilter())
                .build();
    }
}
