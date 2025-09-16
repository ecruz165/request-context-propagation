package com.example.demo.controller;

import com.example.demo.config.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Test controller demonstrating various return types with RequestContext
 */
@Slf4j
@RestController
@RequestMapping("/api/test")
@RequiredArgsConstructor
public class DemoTestController {

    private final DemoTestService userService;

    // ============================================
    // 1. DeferredResult - Async Servlet Processing
    // ============================================

    @GetMapping("/deferred/{id}")
    public DeferredResult<Map<String, Object>> getDeferredResult(@PathVariable String id,
                                                                 HttpServletRequest request) {

        // Get context from request
        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("DeferredResult endpoint - Handler: {}, RequestId: {}, Principal: {}",
                context.get("handler"),
                context.get("requestId"),
                context.get("principal"));

        // Create DeferredResult with 5 second timeout
        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(5000L);

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            log.warn("Request timeout for id: {}", id);
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                            .body(Map.of("error", "Request timeout"))
            );
        });

        // Process asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate async processing
                Thread.sleep(ThreadLocalRandom.current().nextInt(100, 1000));

                Map<String, Object> result = new HashMap<>();
                result.put("id", id);
                result.put("processedAt", LocalDateTime.now());
                result.put("requestId", context.get("requestId"));
                result.put("handler", context.get("handler"));
                result.put("processedBy", context.get("principal"));
                result.put("type", "DeferredResult");

                deferredResult.setResult(result);

            } catch (InterruptedException e) {
                deferredResult.setErrorResult(
                        Map.of("error", "Processing interrupted")
                );
            }
        }, ForkJoinPool.commonPool());

        return deferredResult;
    }

    @PostMapping("/deferred/batch")
    public DeferredResult<BatchResponse> processBatch(@RequestBody BatchRequest request,
                                                      HttpServletRequest httpRequest) {

        RequestContext context = (RequestContext) httpRequest.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        DeferredResult<BatchResponse> deferredResult = new DeferredResult<>(10000L);

        // Process batch asynchronously
        CompletableFuture.supplyAsync(() -> {
                    BatchResponse response = new BatchResponse();
                    response.setBatchId(UUID.randomUUID().toString());
                    response.setRequestId(context.get("requestId"));
                    response.setItemsProcessed(request.getItems().size());
                    response.setProcessedBy(context.get("principal"));
                    response.setStatus("COMPLETED");

                    // Simulate processing
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        response.setStatus("FAILED");
                    }

                    return response;
                }).thenAccept(deferredResult::setResult)
                .exceptionally(ex -> {
                    deferredResult.setErrorResult(
                            Map.of("error", ex.getMessage())
                    );
                    return null;
                });

        return deferredResult;
    }

    // ============================================
    // 2. Mono - Reactive Return Type
    // ============================================

    @GetMapping("/mono/{userId}")
    public Mono<Map<String, Object>> getMonoResponse(@PathVariable String userId,
                                                     HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Mono endpoint - Handler: {}, RequestId: {}",
                context.get("handler"), context.get("requestId"));

        return userService.getUser(userId)
                .map(user -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("user", user);
                    response.put("requestId", context.get("requestId"));
                    response.put("handler", context.get("handler"));
                    response.put("fetchedBy", context.get("principal"));
                    response.put("type", "Mono");
                    return response;
                })
                .defaultIfEmpty(Map.of(
                        "error", "User not found",
                        "userId", userId,
                        "requestId", context.get("requestId")
                ))
                .doOnSuccess(result ->
                        log.info("Mono completed for user: {}", userId)
                )
                .doOnError(error ->
                        log.error("Error in Mono processing: {}", error.getMessage())
                );
    }

    @GetMapping(value = "/flux/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent> getFluxStream(HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        return Flux.interval(Duration.ofSeconds(1))
                .take(10)
                .map(sequence -> {
                    ServerSentEvent event = new ServerSentEvent();
                    event.setId(String.valueOf(sequence));
                    event.setEvent("update");
                    event.setData(Map.of(
                            "sequence", sequence,
                            "timestamp", LocalDateTime.now(),
                            "requestId", context.get("requestId"),
                            "streamedBy", context.get("principal")
                    ));
                    return event;
                })
                .doOnComplete(() ->
                        log.info("Flux stream completed for request: {}", context.get("requestId"))
                );
    }

    // ============================================
    // 3. ResponseEntity - Standard Spring Response
    // ============================================

    @GetMapping("/entity/{id}")
    public ResponseEntity<TestResponse> getResponseEntity(@PathVariable String id,
                                                          @RequestParam(required = false) String type,
                                                          HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("ResponseEntity endpoint - Handler: {}, Type: {}",
                context.get("handler"), type);

        // Build response
        TestResponse response = new TestResponse();
        response.setId(id);
        response.setType(type != null ? type : "default");
        response.setProcessedAt(LocalDateTime.now());
        response.setRequestId(context.get("requestId"));
        response.setHandler(context.get("handler"));
        response.setProcessedBy(context.get("principal"));
        response.setApplicationId(context.get("applicationId"));

        // Add custom headers from context
        return ResponseEntity.ok()
                .header("X-Request-Id", context.get("requestId"))
                .header("X-Handler", context.get("handler"))
                .header("X-Processed-By", context.get("principal"))
                .body(response);
    }

    @PostMapping("/entity")
    public ResponseEntity<Map<String, Object>> createEntity(@RequestBody TestRequest request,
                                                            HttpServletRequest httpRequest) {

        RequestContext context = (RequestContext) httpRequest.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        // Validation
        if (request.getName() == null || request.getName().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of(
                            "error", "Name is required",
                            "requestId", context.get("requestId")
                    ));
        }

        // Process request
        String generatedId = UUID.randomUUID().toString();

        Map<String, Object> response = new HashMap<>();
        response.put("id", generatedId);
        response.put("name", request.getName());
        response.put("createdAt", LocalDateTime.now());
        response.put("createdBy", context.get("principal"));
        response.put("requestId", context.get("requestId"));
        response.put("handler", context.get("handler"));

        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Location", "/api/test/entity/" + generatedId)
                .body(response);
    }

    @DeleteMapping("/entity/{id}")
    public ResponseEntity<Void> deleteEntity(@PathVariable String id,
                                             HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Deleting entity: {} by user: {}", id, context.get("principal"));

        // Simulate deletion
        boolean deleted = ThreadLocalRandom.current().nextBoolean();

        if (deleted) {
            return ResponseEntity.noContent()
                    .header("X-Deleted-By", context.get("principal"))
                    .header("X-Request-Id", context.get("requestId"))
                    .build();
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    // ============================================
    // 4. Void - Fire and Forget
    // ============================================

    @PostMapping("/void/log")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void logEvent(@RequestBody Map<String, Object> event,
                         HttpServletRequest request,
                         HttpServletResponse response) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        // Log the event with context
        log.info("Event logged - RequestId: {}, Principal: {}, Handler: {}, Event: {}",
                context.get("requestId"),
                context.get("principal"),
                context.get("handler"),
                event);

        // Add response headers even for void
        response.setHeader("X-Request-Id", context.get("requestId"));
        response.setHeader("X-Event-Logged", "true");
        response.setHeader("X-Logged-At", LocalDateTime.now().toString());

        // Async processing without waiting
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate async processing
                Thread.sleep(1000);
                log.info("Async processing completed for event: {}", event.get("type"));
            } catch (InterruptedException e) {
                log.error("Async processing interrupted", e);
            }
        });
    }

    @PutMapping("/void/update/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateResource(@PathVariable String id,
                               @RequestBody Map<String, Object> updates,
                               HttpServletRequest request) throws IOException {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Updating resource: {} with {} fields by user: {}",
                id,
                updates.size(),
                context.get("principal"));

        // Validate
        if (updates.isEmpty()) {
            throw new IllegalArgumentException("No updates provided");
        }

        // Fire and forget update
        CompletableFuture.runAsync(() -> {
            // Simulate update
            updates.forEach((key, value) -> {
                log.debug("Updating field {} = {} for resource {}", key, value, id);
            });
        });
    }

    // ============================================
    // 5. SSE - Server Sent Events
    // ============================================

    @GetMapping("/sse/events")
    public SseEmitter getServerSentEvents(HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        SseEmitter emitter = new SseEmitter(30000L); // 30 second timeout

        CompletableFuture.runAsync(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .id(String.valueOf(i))
                            .name("update")
                            .data(Map.of(
                                    "index", i,
                                    "timestamp", LocalDateTime.now(),
                                    "requestId", context.get("requestId"),
                                    "sentBy", context.get("principal")
                            ));

                    emitter.send(event);
                    Thread.sleep(1000);
                }
                emitter.complete();

            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    // ============================================
    // DTOs
    // ============================================

    @Data
    public static class TestRequest {
        private String name;
        private String type;
        private Map<String, Object> metadata;
    }

    @Data
    public static class TestResponse {
        private String id;
        private String type;
        private LocalDateTime processedAt;
        private String requestId;
        private String handler;
        private String processedBy;
        private String applicationId;
    }

    @Data
    public static class BatchRequest {
        private List<String> items;
        private String operation;
    }

    @Data
    public static class BatchResponse {
        private String batchId;
        private String requestId;
        private int itemsProcessed;
        private String processedBy;
        private String status;
    }

    @Data
    public static class ServerSentEvent {
        private String id;
        private String event;
        private Object data;
    }

    @Data
    public static class User {
        private String id;
        private String name;
        private String email;
    }
}