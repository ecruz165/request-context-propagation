package com.example.demo.controller;

import com.example.demo.config.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Controller demonstrating Mono and Flux variations with RequestContext
 * Reactive programming with Project Reactor for non-blocking operations
 */
@Slf4j
@RestController
@RequestMapping("/api/reactive")
@RequiredArgsConstructor
public class ReactiveController {

    // ============================================
    // 1. Basic Mono Operations
    // ============================================

    @GetMapping("/mono/basic/{id}")
    public Mono<Map<String, Object>> basicMono(@PathVariable String id,
                                               HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Processing basic Mono for id: {}", id);

        return Mono.fromCallable(() -> {
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", id);
                    result.put("requestId", context.get("requestId"));
                    result.put("processedBy", context.get("userId"));
                    result.put("timestamp", LocalDateTime.now());
                    result.put("type", "basic-mono");
                    return result;
                })
                .delayElement(Duration.ofMillis(ThreadLocalRandom.current().nextInt(100, 1000)))
                .doOnSuccess(result -> log.info("Basic Mono completed for id: {}", id))
                .doOnError(error -> log.error("Basic Mono error for id: {}", id, error));
    }

    @PostMapping("/mono/transform")
    public Mono<TransformResponse> transformData(@RequestBody TransformRequest transformRequest,
                                                HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Mono.just(transformRequest)
                .map(req -> {
                    TransformResponse response = new TransformResponse();
                    response.setRequestId(context.get("requestId"));
                    response.setOriginalData(req.getData());
                    response.setTransformationType(req.getTransformationType());
                    response.setProcessedBy(context.get("userId"));
                    response.setProcessedAt(LocalDateTime.now());
                    return response;
                })
                .map(this::applyTransformation)
                .doOnSuccess(result -> log.info("Data transformation completed: {}", 
                        result.getTransformationType()));
    }

    // ============================================
    // 2. Error Handling and Fallbacks
    // ============================================

    @GetMapping("/mono/error-handling/{id}")
    public Mono<ResponseEntity<Map<String, Object>>> monoWithErrorHandling(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean simulateError,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Mono.fromCallable(() -> {
                    if (simulateError) {
                        throw new RuntimeException("Simulated error for id: " + id);
                    }
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", id);
                    result.put("requestId", context.get("requestId"));
                    result.put("status", "success");
                    result.put("processedAt", LocalDateTime.now());
                    return ResponseEntity.ok(result);
                })
                .onErrorResume(error -> {
                    log.error("Error processing id: {}", id, error);
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("id", id);
                    errorResponse.put("requestId", context.get("requestId"));
                    errorResponse.put("error", error.getMessage());
                    errorResponse.put("fallback", true);
                    errorResponse.put("timestamp", LocalDateTime.now());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                })
                .doOnError(error -> log.error("Unhandled error for id: {}", id, error));
    }

    @GetMapping("/mono/retry/{id}")
    public Mono<Map<String, Object>> monoWithRetry(@PathVariable String id,
                                                   HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Mono.fromCallable(() -> {
                    // Simulate intermittent failures
                    if (ThreadLocalRandom.current().nextDouble() < 0.7) {
                        throw new RuntimeException("Temporary failure for id: " + id);
                    }
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("id", id);
                    result.put("requestId", context.get("requestId"));
                    result.put("status", "success-after-retry");
                    result.put("processedAt", LocalDateTime.now());
                    return result;
                })
                .retry(3)
                .doOnError(error -> log.error("Failed after retries for id: {}", id, error))
                .onErrorReturn(Map.of(
                        "id", id,
                        "requestId", context.get("requestId"),
                        "error", "Failed after 3 retries",
                        "timestamp", LocalDateTime.now()
                ));
    }

    // ============================================
    // 3. Flux Streaming Operations
    // ============================================

    @GetMapping(value = "/flux/stream/{count}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<StreamEvent> streamEvents(@PathVariable int count,
                                         HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Starting event stream with {} events", count);

        return Flux.range(1, count)
                .delayElements(Duration.ofSeconds(1))
                .map(sequence -> {
                    StreamEvent event = new StreamEvent();
                    event.setSequence(sequence);
                    event.setRequestId(context.get("requestId"));
                    event.setStreamedBy(context.get("userId"));
                    event.setTimestamp(LocalDateTime.now());
                    event.setData(Map.of(
                            "message", "Event " + sequence + " of " + count,
                            "progress", (sequence * 100) / count
                    ));
                    return event;
                })
                .doOnComplete(() -> log.info("Event stream completed for request: {}", 
                        context.get("requestId")))
                .doOnCancel(() -> log.info("Event stream cancelled for request: {}", 
                        context.get("requestId")));
    }

    @GetMapping("/flux/batch/{batchSize}")
    public Flux<BatchEvent> batchProcessing(@PathVariable int batchSize,
                                           HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Flux.range(1, 100) // Process 100 items
                .buffer(batchSize) // Group into batches
                .delayElements(Duration.ofMillis(500))
                .map(batch -> {
                    BatchEvent event = new BatchEvent();
                    event.setBatchNumber(batch.get(0) / batchSize + 1);
                    event.setBatchSize(batch.size());
                    event.setItems(batch);
                    event.setRequestId(context.get("requestId"));
                    event.setProcessedBy(context.get("userId"));
                    event.setProcessedAt(LocalDateTime.now());
                    return event;
                })
                .doOnComplete(() -> log.info("Batch processing completed"));
    }

    // ============================================
    // 4. Combining Multiple Monos
    // ============================================

    @GetMapping("/mono/combine/{id}")
    public Mono<CombinedResponse> combineMultipleSources(@PathVariable String id,
                                                        HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        Mono<String> userInfo = Mono.fromCallable(() -> "User-" + id)
                .delayElement(Duration.ofMillis(200));

        Mono<String> profileInfo = Mono.fromCallable(() -> "Profile-" + id)
                .delayElement(Duration.ofMillis(300));

        Mono<String> settingsInfo = Mono.fromCallable(() -> "Settings-" + id)
                .delayElement(Duration.ofMillis(150));

        return Mono.zip(userInfo, profileInfo, settingsInfo)
                .map(tuple -> {
                    CombinedResponse response = new CombinedResponse();
                    response.setId(id);
                    response.setRequestId(context.get("requestId"));
                    response.setUserInfo(tuple.getT1());
                    response.setProfileInfo(tuple.getT2());
                    response.setSettingsInfo(tuple.getT3());
                    response.setCombinedAt(LocalDateTime.now());
                    response.setCombinedBy(context.get("userId"));
                    return response;
                })
                .doOnSuccess(result -> log.info("Combined data for id: {}", id));
    }

    // ============================================
    // 5. Conditional Reactive Processing
    // ============================================

    @PostMapping("/mono/conditional")
    public Mono<ResponseEntity<Map<String, Object>>> conditionalMono(
            @RequestBody ConditionalRequest conditionalRequest,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Mono.just(conditionalRequest)
                .flatMap(req -> {
                    switch (req.getCondition().toLowerCase()) {
                        case "fast":
                            return Mono.just(createResponse("Fast processing", context))
                                    .delayElement(Duration.ofMillis(100));
                        case "slow":
                            return Mono.just(createResponse("Slow processing", context))
                                    .delayElement(Duration.ofSeconds(2));
                        case "error":
                            return Mono.error(new RuntimeException("Conditional error"));
                        default:
                            return Mono.just(createResponse("Default processing", context));
                    }
                })
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    Map<String, Object> errorResponse = new HashMap<>();
                    errorResponse.put("error", error.getMessage());
                    errorResponse.put("requestId", context.get("requestId"));
                    errorResponse.put("timestamp", LocalDateTime.now());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(errorResponse));
                });
    }

    // ============================================
    // Helper Methods
    // ============================================

    private TransformResponse applyTransformation(TransformResponse response) {
        switch (response.getTransformationType().toLowerCase()) {
            case "uppercase":
                response.setTransformedData(response.getOriginalData().toString().toUpperCase());
                break;
            case "lowercase":
                response.setTransformedData(response.getOriginalData().toString().toLowerCase());
                break;
            case "reverse":
                response.setTransformedData(new StringBuilder(response.getOriginalData().toString())
                        .reverse().toString());
                break;
            default:
                response.setTransformedData(response.getOriginalData());
        }
        return response;
    }

    private Map<String, Object> createResponse(String message, RequestContext context) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("requestId", context.get("requestId"));
        response.put("processedBy", context.get("userId"));
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    // ============================================
    // DTOs
    // ============================================

    @Data
    public static class TransformRequest {
        private Object data;
        private String transformationType;
    }

    @Data
    public static class TransformResponse {
        private String requestId;
        private Object originalData;
        private Object transformedData;
        private String transformationType;
        private String processedBy;
        private LocalDateTime processedAt;
    }

    @Data
    public static class StreamEvent {
        private int sequence;
        private String requestId;
        private String streamedBy;
        private LocalDateTime timestamp;
        private Map<String, Object> data;
    }

    @Data
    public static class BatchEvent {
        private int batchNumber;
        private int batchSize;
        private List<Integer> items;
        private String requestId;
        private String processedBy;
        private LocalDateTime processedAt;
    }

    @Data
    public static class CombinedResponse {
        private String id;
        private String requestId;
        private String userInfo;
        private String profileInfo;
        private String settingsInfo;
        private String combinedBy;
        private LocalDateTime combinedAt;
    }

    @Data
    public static class ConditionalRequest {
        private String condition;
        private Map<String, Object> parameters;
    }
}
