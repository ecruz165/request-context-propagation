package com.example.demo.observability;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for RequestContext observability
 */
@Configuration
@Slf4j
public class RequestContextObservabilityConfig {

    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        return new ObservedAspect(observationRegistry);
    }

    // Disabled for Spring Boot 3 compatibility
    /*
    @Bean
    public ServerHttpObservationFilter serverHttpObservationFilter(
            ObservationRegistry observationRegistry,
            RequestContextObservationConvention convention) {

        ServerHttpObservationFilter filter = new ServerHttpObservationFilter(observationRegistry);
        filter.setObservationConvention(convention);
        return filter;
    }
    */

    // Note: WebMvcTagsProvider is not available in Spring Boot 3
    // Custom metrics integration would need to be implemented differently
}
