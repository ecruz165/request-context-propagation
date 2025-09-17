package com.example.demo.controller;

import com.example.demo.config.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Controller demonstrating DeferredResult variations with RequestContext
 * DeferredResult enables asynchronous request processing with timeout handling
 */
@Slf4j
@RestController
@RequestMapping("/api/deferred")
@RequiredArgsConstructor
public class DeferredResultController {

    // ============================================
    // 1. Basic Async Processing
    // ============================================

    @GetMapping("/basic/{id}")
    public DeferredResult<Map<String, Object>> basicAsync(@PathVariable String id,
                                                          HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Starting basic async processing for id: {}", id);

        DeferredResult<Map<String, Object>> deferredResult = new DeferredResult<>(5000L);

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            log.warn("Request timeout for id: {}", id);
            Map<String, Object> timeoutResponse = new HashMap<>();
            timeoutResponse.put("error", "Request timeout");
            timeoutResponse.put("id", id);
            timeoutResponse.put("requestId", context.get("requestId"));
            timeoutResponse.put("timeoutAt", LocalDateTime.now());
            deferredResult.setResult(timeoutResponse);
        });

        // Set completion handler
        deferredResult.onCompletion(() -> 
            log.info("Async processing completed for id: {}", id));

        // Process asynchronously
        CompletableFuture.runAsync(() -> {
            try {
                // Simulate processing time
                Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 3000));

                Map<String, Object> result = new HashMap<>();
                result.put("id", id);
                result.put("status", "completed");
                result.put("requestId", context.get("requestId"));
                result.put("processedBy", context.get("userId"));
                result.put("completedAt", LocalDateTime.now());
                result.put("processingTime", "1-3 seconds");

                deferredResult.setResult(result);

            } catch (InterruptedException e) {
                log.error("Processing interrupted for id: {}", id);
                deferredResult.setErrorResult(
                    Map.of("error", "Processing interrupted", "id", id)
                );
            }
        }, ForkJoinPool.commonPool());

        return deferredResult;
    }

    // ============================================
    // 2. Batch Processing with Progress
    // ============================================

    @PostMapping("/batch-processing")
    public DeferredResult<BatchProcessingResponse> processBatch(
            @RequestBody BatchRequest batchRequest,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        String batchId = UUID.randomUUID().toString();
        log.info("Starting batch processing: {} with {} items", batchId, batchRequest.getItems().size());

        DeferredResult<BatchProcessingResponse> deferredResult = new DeferredResult<>(30000L);

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            BatchProcessingResponse timeoutResponse = new BatchProcessingResponse();
            timeoutResponse.setBatchId(batchId);
            timeoutResponse.setStatus("TIMEOUT");
            timeoutResponse.setError("Batch processing timeout");
            timeoutResponse.setRequestId(context.get("requestId"));
            deferredResult.setResult(timeoutResponse);
        });

        // Process batch asynchronously
        CompletableFuture.supplyAsync(() -> {
            BatchProcessingResponse response = new BatchProcessingResponse();
            response.setBatchId(batchId);
            response.setRequestId(context.get("requestId"));
            response.setStartedAt(LocalDateTime.now());
            response.setProcessedBy(context.get("userId"));

            try {
                // Simulate batch processing
                int totalItems = batchRequest.getItems().size();
                int processed = 0;

                for (String item : batchRequest.getItems()) {
                    // Simulate item processing
                    Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
                    processed++;
                    
                    log.debug("Processed item {} ({}/{})", item, processed, totalItems);
                }

                response.setStatus("COMPLETED");
                response.setTotalItems(totalItems);
                response.setProcessedItems(processed);
                response.setCompletedAt(LocalDateTime.now());

            } catch (InterruptedException e) {
                response.setStatus("INTERRUPTED");
                response.setError("Batch processing was interrupted");
            } catch (Exception e) {
                response.setStatus("FAILED");
                response.setError("Batch processing failed: " + e.getMessage());
            }

            return response;
        }, ForkJoinPool.commonPool())
        .thenAccept(deferredResult::setResult)
        .exceptionally(ex -> {
            log.error("Batch processing error", ex);
            BatchProcessingResponse errorResponse = new BatchProcessingResponse();
            errorResponse.setBatchId(batchId);
            errorResponse.setStatus("ERROR");
            errorResponse.setError(ex.getMessage());
            deferredResult.setResult(errorResponse);
            return null;
        });

        return deferredResult;
    }

    // ============================================
    // 3. Long-Running Task with Cancellation
    // ============================================

    @PostMapping("/long-task")
    public DeferredResult<TaskResponse> startLongTask(
            @RequestBody TaskRequest taskRequest,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        String taskId = UUID.randomUUID().toString();
        log.info("Starting long task: {} of type: {}", taskId, taskRequest.getTaskType());

        DeferredResult<TaskResponse> deferredResult = new DeferredResult<>(60000L);

        // Create cancellable future
        CompletableFuture<TaskResponse> taskFuture = CompletableFuture.supplyAsync(() -> {
            TaskResponse response = new TaskResponse();
            response.setTaskId(taskId);
            response.setTaskType(taskRequest.getTaskType());
            response.setRequestId(context.get("requestId"));
            response.setStartedAt(LocalDateTime.now());
            response.setStartedBy(context.get("userId"));

            try {
                // Simulate long-running task with checkpoints
                for (int i = 0; i < 10; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        response.setStatus("CANCELLED");
                        response.setProgress(i * 10);
                        return response;
                    }

                    Thread.sleep(2000); // 2 seconds per step
                    response.setProgress((i + 1) * 10);
                    log.debug("Task {} progress: {}%", taskId, response.getProgress());
                }

                response.setStatus("COMPLETED");
                response.setCompletedAt(LocalDateTime.now());

            } catch (InterruptedException e) {
                response.setStatus("INTERRUPTED");
                Thread.currentThread().interrupt();
            }

            return response;
        }, ForkJoinPool.commonPool());

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            taskFuture.cancel(true);
            TaskResponse timeoutResponse = new TaskResponse();
            timeoutResponse.setTaskId(taskId);
            timeoutResponse.setStatus("TIMEOUT");
            timeoutResponse.setError("Task execution timeout");
            deferredResult.setResult(timeoutResponse);
        });

        // Set completion handler
        taskFuture.thenAccept(deferredResult::setResult)
                  .exceptionally(ex -> {
                      TaskResponse errorResponse = new TaskResponse();
                      errorResponse.setTaskId(taskId);
                      errorResponse.setStatus("ERROR");
                      errorResponse.setError(ex.getMessage());
                      deferredResult.setResult(errorResponse);
                      return null;
                  });

        return deferredResult;
    }

    // ============================================
    // 4. External API Simulation
    // ============================================

    @GetMapping("/external/{service}")
    public DeferredResult<ExternalApiResponse> callExternalService(
            @PathVariable String service,
            @RequestParam(defaultValue = "false") boolean simulateFailure,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        log.info("Calling external service: {}", service);

        DeferredResult<ExternalApiResponse> deferredResult = new DeferredResult<>(10000L);

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            ExternalApiResponse timeoutResponse = new ExternalApiResponse();
            timeoutResponse.setService(service);
            timeoutResponse.setStatus("TIMEOUT");
            timeoutResponse.setError("External service call timeout");
            timeoutResponse.setRequestId(context.get("requestId"));
            deferredResult.setResult(timeoutResponse);
        });

        // Simulate external API call
        CompletableFuture.supplyAsync(() -> {
            ExternalApiResponse response = new ExternalApiResponse();
            response.setService(service);
            response.setRequestId(context.get("requestId"));
            response.setCalledAt(LocalDateTime.now());
            response.setCalledBy(context.get("userId"));

            try {
                // Simulate network delay
                Thread.sleep(ThreadLocalRandom.current().nextInt(1000, 5000));

                if (simulateFailure) {
                    response.setStatus("FAILED");
                    response.setError("Simulated external service failure");
                    response.setHttpStatus(500);
                } else {
                    response.setStatus("SUCCESS");
                    response.setHttpStatus(200);
                    response.setData(Map.of(
                        "serviceResponse", "Mock data from " + service,
                        "timestamp", LocalDateTime.now(),
                        "version", "1.0.0"
                    ));
                }

                response.setCompletedAt(LocalDateTime.now());

            } catch (InterruptedException e) {
                response.setStatus("INTERRUPTED");
                Thread.currentThread().interrupt();
            }

            return response;
        }, ForkJoinPool.commonPool())
        .thenAccept(deferredResult::setResult)
        .exceptionally(ex -> {
            ExternalApiResponse errorResponse = new ExternalApiResponse();
            errorResponse.setService(service);
            errorResponse.setStatus("ERROR");
            errorResponse.setError(ex.getMessage());
            deferredResult.setResult(errorResponse);
            return null;
        });

        return deferredResult;
    }

    // ============================================
    // 5. Conditional Processing
    // ============================================

    @PostMapping("/conditional")
    public DeferredResult<ResponseEntity<Map<String, Object>>> conditionalProcessing(
            @RequestBody ConditionalRequest conditionalRequest,
            HttpServletRequest request) {

        RequestContext context = (RequestContext) request.getAttribute(
                RequestContext.REQUEST_CONTEXT_ATTRIBUTE);

        DeferredResult<ResponseEntity<Map<String, Object>>> deferredResult = 
            new DeferredResult<>(15000L);

        // Set timeout handler
        deferredResult.onTimeout(() -> {
            Map<String, Object> timeoutResponse = Map.of(
                "error", "Conditional processing timeout",
                "condition", conditionalRequest.getCondition(),
                "requestId", context.get("requestId")
            );
            deferredResult.setResult(ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT).body(timeoutResponse));
        });

        // Process conditionally
        CompletableFuture.supplyAsync(() -> {
            Map<String, Object> response = new HashMap<>();
            response.put("condition", conditionalRequest.getCondition());
            response.put("requestId", context.get("requestId"));
            response.put("processedAt", LocalDateTime.now());

            try {
                // Simulate conditional logic
                switch (conditionalRequest.getCondition().toLowerCase()) {
                    case "fast" -> {
                        Thread.sleep(500);
                        response.put("result", "Fast processing completed");
                        return ResponseEntity.ok(response);
                    }
                    case "slow" -> {
                        Thread.sleep(5000);
                        response.put("result", "Slow processing completed");
                        return ResponseEntity.ok(response);
                    }
                    case "error" -> {
                        response.put("error", "Simulated error condition");
                        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
                    }
                    default -> {
                        response.put("result", "Default processing completed");
                        return ResponseEntity.ok(response);
                    }
                }
            } catch (InterruptedException e) {
                response.put("error", "Processing interrupted");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
            }
        }, ForkJoinPool.commonPool())
        .thenAccept(deferredResult::setResult)
        .exceptionally(ex -> {
            Map<String, Object> errorResponse = Map.of(
                "error", ex.getMessage(),
                "condition", conditionalRequest.getCondition()
            );
            deferredResult.setResult(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse));
            return null;
        });

        return deferredResult;
    }

    // ============================================
    // DTOs
    // ============================================

    @Data
    public static class BatchRequest {
        private List<String> items;
        private String operation;
        private Map<String, Object> parameters;
    }

    @Data
    public static class BatchProcessingResponse {
        private String batchId;
        private String requestId;
        private String status;
        private int totalItems;
        private int processedItems;
        private String processedBy;
        private String error;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class TaskRequest {
        private String taskType;
        private Map<String, Object> parameters;
        private int priority;
    }

    @Data
    public static class TaskResponse {
        private String taskId;
        private String taskType;
        private String requestId;
        private String status;
        private int progress;
        private String startedBy;
        private String error;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class ExternalApiResponse {
        private String service;
        private String requestId;
        private String status;
        private int httpStatus;
        private String calledBy;
        private String error;
        private Object data;
        private LocalDateTime calledAt;
        private LocalDateTime completedAt;
    }

    @Data
    public static class ConditionalRequest {
        private String condition;
        private Map<String, Object> parameters;
    }
}
