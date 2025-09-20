package com.example.demo.service;

import com.example.demo.config.RequestContextWebClientBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

/**
 * Test service that demonstrates real-world microservice patterns:
 * - Multiple concurrent WebClient calls
 * - Reactive stream processing with zip and block
 * - Context propagation throughout reactive chains
 * - Upstream value propagation after blocking
 */
@Service
@Slf4j
public class ReactiveTestService {

    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;

    @Autowired
    private RequestContextService requestContextService;

    /**
     * Simulates a real microservice scenario:
     * 1. Makes concurrent calls to multiple downstream services
     * 2. Uses reactive streams (Mono.zip) to combine results
     * 3. Blocks to get final result (typical in DeferredResult scenarios)
     * 4. Propagates captured downstream values back to upstream context
     */
    public Map<String, Object> aggregateDataFromMultipleServices(String userId) {
        log.info("Starting data aggregation for user: {}", userId);

        try {
            // Create WebClients for different downstream services
            WebClient userServiceClient = webClientBuilder.createForSystem("user-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient profileServiceClient = webClientBuilder.createForSystem("profile-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient paymentServiceClient = webClientBuilder.createForSystem("payment-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            // Make concurrent reactive calls - these will propagate upstream context
            Mono<Map> userCall = userServiceClient.get()
                    .uri("/user-service/users/{userId}", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnNext(response -> log.debug("Received user service response for: {}", userId));

            Mono<Map> profileCall = profileServiceClient.get()
                    .uri("/profile-service/profiles/{userId}", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnNext(response -> log.debug("Received profile service response for: {}", userId));

            Mono<Map> paymentCall = paymentServiceClient.get()
                    .uri("/payment-service/accounts/{userId}", userId)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .doOnNext(response -> log.debug("Received payment service response for: {}", userId));

            // Combine all results using reactive zip - maintains context propagation
            Mono<Map<String, Object>> combinedMono = Mono.zip(userCall, profileCall, paymentCall)
                    .map(tuple -> {
                        Map<String, Object> aggregatedData = new HashMap<>();
                        
                        // Combine data from all services
                        aggregatedData.put("userData", tuple.getT1());
                        aggregatedData.put("profileData", tuple.getT2());
                        aggregatedData.put("paymentData", tuple.getT3());
                        aggregatedData.put("aggregationTimestamp", System.currentTimeMillis());
                        
                        // Add computed values to context for upstream propagation
                        String processingTime = String.valueOf(System.currentTimeMillis() - getStartTime());
                        requestContextService.setField("aggregationProcessingTime", processingTime);
                        
                        // Add service count for metrics
                        requestContextService.setField("servicesAggregated", "3");
                        
                        log.debug("Aggregated data from {} services in {}ms", 3, processingTime);
                        return aggregatedData;
                    });

            // Block to get final result - this is where DeferredResult would typically block
            Map<String, Object> result = combinedMono.block();

            // Add metadata about captured downstream values
            Map<String, Object> capturedValues = new HashMap<>();
            capturedValues.put("userServiceVersion", requestContextService.getField("downstreamUserServiceVersion"));
            capturedValues.put("profileServiceVersion", requestContextService.getField("downstreamProfileServiceVersion"));
            capturedValues.put("paymentServiceVersion", requestContextService.getField("downstreamPaymentServiceVersion"));
            capturedValues.put("aggregationTime", requestContextService.getField("aggregationProcessingTime"));
            capturedValues.put("servicesCount", requestContextService.getField("servicesAggregated"));

            result.put("capturedDownstreamValues", capturedValues);
            result.put("aggregationStatus", "success");

            log.info("Data aggregation completed for user: {} with captured values: {}", userId, capturedValues);
            return result;

        } catch (Exception e) {
            log.error("Error during data aggregation for user: {}", userId, e);
            
            // Return error response with context
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("aggregationStatus", "error");
            errorResponse.put("errorMessage", e.getMessage());
            errorResponse.put("userId", userId);
            
            return errorResponse;
        }
    }

    /**
     * Test method that demonstrates context usage within reactive streams
     */
    public Map<String, Object> testUpstreamContextInReactiveStream(String testValue) {
        log.info("Testing upstream context usage in reactive stream with value: {}", testValue);

        // Set a test value in upstream context
        requestContextService.setField("testUpstreamValue", testValue);

        WebClient webClient = webClientBuilder.create()
                .baseUrl("http://localhost:8089")
                .build();

        Mono<Map<String, Object>> testMono = webClient.get()
                .uri("http://localhost:8089/test-endpoint")
                .retrieve()
                .bodyToMono(Map.class)
                .map(downstreamResponse -> {
                    Map<String, Object> result = new HashMap<>();
                    
                    // Use upstream value within reactive stream
                    String upstreamValue = requestContextService.getField("testUpstreamValue");
                    result.put("usedUpstreamValue", upstreamValue);
                    result.put("downstreamResponse", downstreamResponse);
                    
                    // Add new value for upstream propagation
                    String computedValue = "computed-from-" + upstreamValue;
                    requestContextService.setField("computedInReactiveStream", computedValue);
                    result.put("computedValue", computedValue);
                    
                    return result;
                });

        // Block and get result
        Map<String, Object> result = testMono.block();
        
        // Verify context values are available after blocking
        result.put("finalUpstreamValue", requestContextService.getField("testUpstreamValue"));
        result.put("finalComputedValue", requestContextService.getField("computedInReactiveStream"));
        
        return result;
    }

    private long getStartTime() {
        // In real implementation, this would be stored in context or as instance variable
        return System.currentTimeMillis() - 100; // Simulate some processing time
    }
}
