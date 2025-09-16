package com.example.demo.config;

import com.example.demo.filter.RequestContextCaptureWebClientFilter;
import com.example.demo.filter.RequestContextLoggingWebClientFilter;
import com.example.demo.filter.RequestContextPropagationWebClientFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration with RequestContext propagation and capture
 */
@Configuration
@Slf4j
public class DemoWebClientConfig {

    private final RequestContextPropagationWebClientFilter propagationFilter;
    private final RequestContextCaptureWebClientFilter captureFilter;
    private final RequestContextLoggingWebClientFilter loggingFilter;

    public DemoWebClientConfig(RequestContextPropagationWebClientFilter propagationFilter,
                               RequestContextCaptureWebClientFilter captureFilter,
                               RequestContextLoggingWebClientFilter loggingFilter) {
        this.propagationFilter = propagationFilter;
        this.captureFilter = captureFilter;
        this.loggingFilter = loggingFilter;
    }

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
                // Add request context propagation (outbound headers)
                .filter(propagationFilter.createFilter())

                // Add response context capture (inbound headers)
                .filter(captureFilter.createFilter())

                // Add logging filter (separate concern)
                .filter(loggingFilter.createFilter())

                // Default timeout
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                .build();
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}