package com.jefelabs.modules.requestcontext.demo.controller;

import com.jefelabs.modules.requestcontext.config.RequestContextWebClientBuilder;
import com.jefelabs.modules.requestcontext.service.ReactiveTestService;
import com.jefelabs.modules.requestcontext.service.RequestContext;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Test controller for request context field testing
 * Provides endpoints that trigger downstream calls and return context data
 */
@RestController
@RequestMapping("/api/test")
@Slf4j
public class TestController {

    @Autowired
    private RequestContextService requestContextService;

    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;

    @Autowired
    private ReactiveTestService reactiveTestService;


    @Autowired(required = false)
    private WebClient webClient;

    /**
     * Endpoint that makes downstream calls and returns all context fields
     * Used for testing upstream extraction and downstream propagation
     */
    @GetMapping("/downstream")
    public ResponseEntity<Map<String, Object>> testDownstreamCall() {
        // Make downstream call to trigger propagation
        String downstreamResponse = webClient.get()
                .uri("http://localhost:8089/downstream/service")
                .retrieve()
                .bodyToMono(String.class)
                .block();

        // Get all context fields
        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", getAllContextFields());
        response.put("downstreamFields", getDownstreamFields());
        response.put("downstreamResponse", downstreamResponse);

        return ResponseEntity.ok(response);
    }

    /**
     * DeferredResult endpoint that demonstrates real microservice patterns:
     * - Uses ReactiveTestService to make concurrent WebClient calls
     * - Blocks reactive streams to get final result
     * - Propagates upstream context values through reactive chains
     * - Captures downstream values and propagates them back upstream
     */
    @GetMapping("/deferred-reactive-aggregation")
    public DeferredResult<ResponseEntity<Map<String, Object>>> testDeferredReactiveAggregation(
            @RequestParam(defaultValue = "test-user") String userId) {

        DeferredResult<ResponseEntity<Map<String, Object>>> deferredResult = new DeferredResult<>(30000L);

        // Process with reactive service (already handles async with block())
        // Use the reactive test service to aggregate data
        Map<String, Object> aggregatedData = reactiveTestService.aggregateDataFromMultipleServices(userId);

        // Add current context fields to response
        Map<String, Object> response = new HashMap<>();
        response.put("aggregatedData", aggregatedData);
        response.put("contextFields", getAllContextFields());
        response.put("downstreamFields", getDownstreamFields());
        response.put("processingType", "deferred-reactive");
        response.put("userId", userId);

        deferredResult.setResult(ResponseEntity.ok(response));


        return deferredResult;
    }

    /**
     * DeferredResult endpoint that tests upstream context usage in reactive streams
     */
    @GetMapping("/deferred-upstream-context-test")
    public DeferredResult<ResponseEntity<Map<String, Object>>> testDeferredUpstreamContext(
            @RequestParam(defaultValue = "test-value") String testValue) {

        DeferredResult<ResponseEntity<Map<String, Object>>> deferredResult = new DeferredResult<>(15000L);

        // Process asynchronously
        CompletableFuture.supplyAsync(() -> {
            try {
                // Use the reactive test service to test upstream context usage
                Map<String, Object> testResult = reactiveTestService.testUpstreamContextInReactiveStream(testValue);

                // Add current context fields to response
                Map<String, Object> response = new HashMap<>();
                response.put("testResult", testResult);
                response.put("contextFields", getAllContextFields());
                response.put("processingType", "deferred-upstream-test");
                response.put("inputTestValue", testValue);

                return ResponseEntity.ok(response);

            } catch (Exception e) {
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("status", "error");
                errorResponse.put("message", e.getMessage());
                errorResponse.put("testValue", testValue);
                return ResponseEntity.status(500).body(errorResponse);
            }
        }).thenAccept(result -> deferredResult.setResult(result));

        return deferredResult;
    }

    /**
     * Simple endpoint without downstream calls
     * Used for testing extract-only fields
     */
    @GetMapping("/simple")
    public ResponseEntity<Map<String, Object>> testSimple() {
        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", getAllContextFields());
        return ResponseEntity.ok(response);
    }

    /**
     * Get all upstream context fields
     */
    private Map<String, Object> getAllContextFields() {
        Map<String, Object> fields = new HashMap<>();

        // Get current context
        RequestContext context = requestContextService.getCurrentContext().orElse(null);
        if (context == null) {
            return fields; // Return empty map if no context
        }

        // Pattern 1: Basic bidirectional
        fields.put("headerId1", context.getMaskedOrOriginal("headerId1"));
        fields.put("headerId2", context.getMaskedOrOriginal("headerId2"));

        // Pattern 2: With defaults
        fields.put("headerId3", context.getMaskedOrOriginal("headerId3"));
        fields.put("headerId7", context.getMaskedOrOriginal("headerId7"));

        // Pattern 3: Extract-only
        fields.put("emailHeader", context.getMaskedOrOriginal("emailHeader"));

        // Pattern 4: Sensitive
        fields.put("sensitiveHeader", context.getMaskedOrOriginal("sensitiveHeader"));

        // Pattern 5: Cookies
        fields.put("cookieId1", context.getMaskedOrOriginal("cookieId1"));
        fields.put("cookieId2", context.getMaskedOrOriginal("cookieId2"));

        // Pattern 6: Query parameters
        fields.put("queryId1", context.getMaskedOrOriginal("queryId1"));
        fields.put("queryId2", context.getMaskedOrOriginal("queryId2"));
        fields.put("queryId5", context.getMaskedOrOriginal("queryId5"));

        // Pattern 7: Session
        fields.put("sessionId", context.getMaskedOrOriginal("sessionId"));

        // Pattern 8: JWT Claims (only available in protected endpoints)
        fields.put("userId", context.getMaskedOrOriginal("userId"));
        fields.put("userEmail", context.getMaskedOrOriginal("userEmail"));
        fields.put("userRole", context.getMaskedOrOriginal("userRole"));

        // Pattern 9: Generated values
        fields.put("requestId", context.getMaskedOrOriginal("requestId"));

        // Pattern 10: Fallback chains
        fields.put("tenantId", context.getMaskedOrOriginal("tenantId"));

        // Pattern 11: Cross-type enrichment
        fields.put("apiKey", context.getMaskedOrOriginal("apiKey"));
        fields.put("clientVersion", context.getMaskedOrOriginal("clientVersion"));

        // Pattern 13: Bidirectional with downstream
        fields.put("correlationId", context.getMaskedOrOriginal("correlationId"));

        // Pattern 14: Observability
        fields.put("applicationId", context.getMaskedOrOriginal("applicationId"));
        fields.put("clientId", context.getMaskedOrOriginal("clientId"));
        fields.put("traceId", context.getMaskedOrOriginal("traceId"));

        // Pattern 15: Downstream monitoring context
        fields.put("serviceHealth", context.getMaskedOrOriginal("serviceHealth"));
        fields.put("cacheStatus", context.getMaskedOrOriginal("cacheStatus"));
        fields.put("dbQueryTime", context.getMaskedOrOriginal("dbQueryTime"));
        fields.put("featureFlags", context.getMaskedOrOriginal("featureFlags"));

        return fields;
    }

    /**
     * Get downstream response fields
     * These are extracted from downstream service responses
     */
    private Map<String, Object> getDownstreamFields() {
        Map<String, Object> fields = new HashMap<>();

        // Get current context
        RequestContext context = requestContextService.getCurrentContext().orElse(null);
        if (context == null) {
            return fields; // Return empty map if no context
        }

        // Pattern 12: Downstream response extraction
        fields.put("downstreamServiceVersion", context.getMaskedOrOriginal("downstreamServiceVersion"));
        fields.put("downstreamResponseStatus", context.getMaskedOrOriginal("downstreamResponseStatus"));
        fields.put("rateLimitRemaining", context.getMaskedOrOriginal("rateLimitRemaining"));
        fields.put("downstreamResponseTime", context.getMaskedOrOriginal("downstreamResponseTime"));
        fields.put("downstreamErrorCode", context.getMaskedOrOriginal("downstreamErrorCode"));

        // Pattern 13: Bidirectional with downstream
        fields.put("correlationId", context.getMaskedOrOriginal("correlationId"));

        // Pattern 15: Downstream monitoring
        fields.put("serviceHealth", context.getMaskedOrOriginal("serviceHealth"));
        fields.put("cacheStatus", context.getMaskedOrOriginal("cacheStatus"));
        fields.put("dbQueryTime", context.getMaskedOrOriginal("dbQueryTime"));
        fields.put("featureFlags", context.getMaskedOrOriginal("featureFlags"));
        fields.put("securityWarnings", context.getMaskedOrOriginal("securityWarnings"));

        return fields;
    }

    /**
     * Test endpoint demonstrating concurrent zip and block with upstream propagation
     * This endpoint makes requests to two distinct systems, uses Mono.zip, blocks, and propagates captured values upstream
     */
    @GetMapping("/concurrent-zip-block-test")
    public ResponseEntity<Map<String, Object>> testConcurrentZipBlockUpstream() {
        log.info("Starting concurrent zip and block test");

        try {
            // Create WebClients for two distinct systems
            WebClient userServiceClient = webClientBuilder.createForSystem("user-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient profileServiceClient = webClientBuilder.createForSystem("profile-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            // Get current context for propagation
            Optional<RequestContext> currentContext = requestContextService.getCurrentContext();

            // Make concurrent calls using Mono.zip with context propagation
            Mono<Map> userCall = userServiceClient.get()
                    .uri("/user-service/users/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            Mono<Map> profileCall = profileServiceClient.get()
                    .uri("/profile-service/profiles/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            // Add context to both calls if available
            if (currentContext.isPresent()) {
                RequestContext context = currentContext.get();
                userCall = userCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
                profileCall = profileCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
            }

            // Zip the results and add processing metadata
            Mono<Map<String, Object>> combinedMono = Mono.zip(userCall, profileCall)
                    .map(tuple -> {
                        Map<String, Object> combined = new HashMap<>();
                        combined.put("user", tuple.getT1());
                        combined.put("profile", tuple.getT2());
                        combined.put("timestamp", System.currentTimeMillis());

                        // Add combined processing time to context for upstream propagation
                        String combinedTime = System.currentTimeMillis() + "ms";
                        requestContextService.setField("combinedProcessingTime", combinedTime);

                        return combined;
                    })
                    // Ensure upstream values are propagated
                    .transform(mono -> RequestContextWebClientBuilder.propagateUpstreamValues(mono, requestContextService));

            // Block to get the result (this should trigger upstream propagation)
            Map<String, Object> result = RequestContextWebClientBuilder.blockWithUpstreamPropagation(
                    combinedMono, requestContextService);

            // Build response with captured values
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("message", "Concurrent zip and block test completed");
            responseData.put("userServiceData", result.get("user"));
            responseData.put("profileServiceData", result.get("profile"));
            responseData.put("timestamp", result.get("timestamp"));

            // Add captured values from context
            Map<String, Object> capturedValues = new HashMap<>();
            capturedValues.put("downstreamUserVersion", requestContextService.getField("downstreamUserServiceVersion"));
            capturedValues.put("downstreamProfileVersion", requestContextService.getField("downstreamProfileServiceVersion"));
            capturedValues.put("combinedProcessingTime", requestContextService.getField("combinedProcessingTime"));
            responseData.put("capturedValues", capturedValues);

            log.info("Concurrent zip and block test completed with captured values: {}", capturedValues);

            return ResponseEntity.ok(responseData);

        } catch (Exception e) {
            log.error("Error in concurrent zip and block test", e);
            return ResponseEntity.status(500)
                    .body(Map.of("error", "Concurrent zip and block test failed: " + e.getMessage()));
        }
    }

    /**
     * DeferredResult endpoint that demonstrates:
     * 1. Upstream values available in reactive streams
     * 2. Concurrent WebClient calls with zip and block
     * 3. Captured downstream values propagated back as upstream headers
     * <p>
     * This simulates real microservice scenarios with async processing
     */
    @GetMapping("/deferred-reactive-context")
    public DeferredResult<ResponseEntity<Map<String, Object>>> testDeferredReactiveContext() {
        DeferredResult<ResponseEntity<Map<String, Object>>> deferredResult = new DeferredResult<>(30000L);

        // Capture current context for async processing
        Optional<RequestContext> currentContext = requestContextService.getCurrentContext();
        String initialRequestId = requestContextService.getField("requestId");
        String initialCorrelationId = requestContextService.getField("correlationId");

        log.info("Starting DeferredResult with context - RequestId: {}, CorrelationId: {}",
                initialRequestId, initialCorrelationId);

        // Async processing with context propagation
        CompletableFuture.supplyAsync(() -> {
                    try {
                        // Create WebClients for concurrent calls
                        WebClient userServiceClient = webClientBuilder.createForSystem("user-service")
                                .baseUrl("http://localhost:8089")
                                .build();

                        WebClient profileServiceClient = webClientBuilder.createForSystem("profile-service")
                                .baseUrl("http://localhost:8089")
                                .build();

                        WebClient paymentServiceClient = webClientBuilder.createForSystem("payment-service")
                                .baseUrl("http://localhost:8089")
                                .build();

                        // Make concurrent calls using reactive streams with context
                        Mono<Map> userCall = userServiceClient.get()
                                .uri("/user-service/users/deferred-user")
                                .retrieve()
                                .bodyToMono(Map.class);

                        Mono<Map> profileCall = profileServiceClient.get()
                                .uri("/profile-service/profiles/deferred-user")
                                .retrieve()
                                .bodyToMono(Map.class);

                        Mono<Map> paymentCall = paymentServiceClient.get()
                                .uri("/payment-service/accounts/deferred-user")
                                .retrieve()
                                .bodyToMono(Map.class);

                        // Zip all calls together and process results
                        Mono<Map<String, Object>> combinedMono = Mono.zip(userCall, profileCall, paymentCall)
                                .map(tuple -> {
                                    Map<String, Object> combined = new HashMap<>();
                                    combined.put("user", tuple.getT1());
                                    combined.put("profile", tuple.getT2());
                                    combined.put("payment", tuple.getT3());
                                    combined.put("processingTimestamp", System.currentTimeMillis());

                                    // Add computed values to context for upstream propagation
                                    String processingTime = String.valueOf(System.currentTimeMillis() - Long.parseLong(initialRequestId.split("-")[0]));
                                    requestContextService.setField("deferredProcessingTime", processingTime + "ms");
                                    requestContextService.setField("serviceCallCount", "3");

                                    return combined;
                                })
                                // Ensure context is propagated through reactive stream
                                .transform(mono -> RequestContextWebClientBuilder.propagateUpstreamValues(mono, requestContextService));

                        // Block to get results (triggers upstream propagation)
                        Map<String, Object> result = RequestContextWebClientBuilder.blockWithUpstreamPropagation(
                                combinedMono, requestContextService);

                        // Build response with all captured values
                        Map<String, Object> responseData = new HashMap<>();
                        responseData.put("message", "DeferredResult reactive context test completed");
                        responseData.put("initialContext", Map.of(
                                "requestId", initialRequestId,
                                "correlationId", initialCorrelationId
                        ));
                        responseData.put("serviceResults", result);

                        // Capture all downstream values that were extracted
                        Map<String, Object> capturedValues = new HashMap<>();
                        capturedValues.put("downstreamUserVersion", requestContextService.getField("downstreamUserServiceVersion"));
                        capturedValues.put("downstreamProfileVersion", requestContextService.getField("downstreamProfileServiceVersion"));
                        capturedValues.put("downstreamPaymentVersion", requestContextService.getField("downstreamPaymentServiceVersion"));
                        capturedValues.put("deferredProcessingTime", requestContextService.getField("deferredProcessingTime"));
                        capturedValues.put("serviceCallCount", requestContextService.getField("serviceCallCount"));
                        responseData.put("capturedValues", capturedValues);

                        // Get all context fields for verification
                        responseData.put("allContextFields", requestContextService.getAllFields());

                        log.info("DeferredResult completed with captured values: {}", capturedValues);

                        return ResponseEntity.ok(responseData);

                    } catch (Exception e) {
                        log.error("Error in DeferredResult processing", e);
                        return ResponseEntity.status(500)
                                .body(Map.of("error", "DeferredResult processing failed: " + e.getMessage()));
                    }
                }).thenAccept(result -> deferredResult.setResult((ResponseEntity<Map<String, Object>>) result))
                .exceptionally(throwable -> {
                    log.error("DeferredResult failed", throwable);
                    deferredResult.setErrorResult(ResponseEntity.status(500)
                            .body(Map.of("error", "Async processing failed: " + throwable.getMessage())));
                    return null;
                });

        return deferredResult;
    }

    /**
     * Test endpoint for system-specific field propagation
     * Demonstrates the new extSysIds feature where fields can be configured
     * to only propagate to specific external systems
     */
    @GetMapping("/system-specific-propagation")
    public ResponseEntity<Map<String, Object>> testSystemSpecificPropagation() {
        log.info("Testing system-specific field propagation");

        try {
            // Create WebClients for different systems with specific extSysIds
            WebClient userServiceClient = webClientBuilder.createForSystem("user-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient profileServiceClient = webClientBuilder.createForSystem("profile-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient paymentServiceClient = webClientBuilder.createForSystem("payment-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            WebClient notificationServiceClient = webClientBuilder.createForSystem("notification-service")
                    .baseUrl("http://localhost:8089")
                    .build();

            // Get current context for propagation
            Optional<RequestContext> currentContext = requestContextService.getCurrentContext();

            // Make calls to different systems
            Mono<Map> userCall = userServiceClient.get()
                    .uri("/user-service/users/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            Mono<Map> profileCall = profileServiceClient.get()
                    .uri("/profile-service/profiles/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            Mono<Map> paymentCall = paymentServiceClient.get()
                    .uri("/payment-service/accounts/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            Mono<Map> notificationCall = notificationServiceClient.get()
                    .uri("/notification-service/preferences/test-user")
                    .retrieve()
                    .bodyToMono(Map.class);

            // Add context to all calls if available
            if (currentContext.isPresent()) {
                RequestContext context = currentContext.get();
                userCall = userCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
                profileCall = profileCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
                paymentCall = paymentCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
                notificationCall = notificationCall.contextWrite(reactor.util.context.Context.of("REQUEST_CONTEXT", context));
            }

            // Execute all calls concurrently
            Mono<Map<String, Object>> allResults = Mono.zip(userCall, profileCall, paymentCall, notificationCall)
                    .map(tuple -> {
                        Map<String, Object> results = new HashMap<>();
                        results.put("userService", tuple.getT1());
                        results.put("profileService", tuple.getT2());
                        results.put("paymentService", tuple.getT3());
                        results.put("notificationService", tuple.getT4());
                        return results;
                    });

            // Block and get results
            Map<String, Object> propagationResults = allResults.block();

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("message", "System-specific propagation test completed");
            response.put("propagationResults", propagationResults);
            response.put("contextFields", getAllContextFields());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error in system-specific propagation test", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Internal server error",
                    "message", e.getMessage()
            ));
        }
    }

}
