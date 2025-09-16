package com.example.demo.controller;

import com.example.demo.config.RequestContext;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Simple reactive service demonstrating WebClient with RequestContext
 */
@Service
@Slf4j
public class DemoTestService {

    private final WebClient webClient;
    private static final String BASE_URL = "http://localhost:8081";

    public DemoTestService(WebClient webClient) {
        this.webClient = webClient;
    }

    // ============================================
    // 1. Simple GET
    // ============================================

    public Mono<UserDto> getUser(String userId) {
        // Get context and log with requestId
        RequestContext.getCurrentContext().ifPresent(context ->
                log.info("Fetching user {} - RequestId: {}", userId, context.get("requestId"))
        );

        return webClient.get()
                .uri(BASE_URL + "/api/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserDto.class)
                .timeout(Duration.ofSeconds(5))
                .doOnError(error -> log.error("Error fetching user {}", userId, error));
    }

    // ============================================
    // 2. Simple POST
    // ============================================

    public Mono<OrderDto> createOrder(OrderRequest request) {
        // Add context info to request
        RequestContext.getCurrentContext().ifPresent(context -> {
            request.setRequestId(context.get("requestId"));
            request.setCreatedBy(context.get("principal"));
            log.info("Creating order - RequestId: {}", context.get("requestId"));
        });

        return webClient.post()
                .uri(BASE_URL + "/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OrderDto.class)
                .timeout(Duration.ofSeconds(5));
    }

    // ============================================
    // 3. GET List
    // ============================================

    public Flux<UserDto> searchUsers(String query) {
        RequestContext.getCurrentContext().ifPresent(context ->
                log.info("Searching users: {} - RequestId: {}", query, context.get("requestId"))
        );

        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(BASE_URL + "/api/users/search")
                        .queryParam("q", query)
                        .build())
                .retrieve()
                .bodyToFlux(UserDto.class)
                .timeout(Duration.ofSeconds(10));
    }

    // ============================================
    // 4. Parallel Calls
    // ============================================

    public Mono<Map<String, Object>> getUserWithOrders(String userId) {
        RequestContext.getCurrentContext().ifPresent(context ->
                log.info("Fetching user with orders - RequestId: {}", context.get("requestId"))
        );

        // Call two endpoints in parallel
        Mono<UserDto> userMono = getUser(userId);
        Mono<List<OrderDto>> ordersMono = getUserOrders(userId);

        return Mono.zip(userMono, ordersMono)
                .map(tuple -> Map.of(
                        "user", tuple.getT1(),
                        "orders", tuple.getT2()
                ));
    }

    // ============================================
    // 5. Sequential Calls
    // ============================================

    public Mono<OrderDto> createOrderAndNotify(OrderRequest request) {
        RequestContext.getCurrentContext().ifPresent(context ->
                log.info("Create order with notification - RequestId: {}", context.get("requestId"))
        );

        return createOrder(request)
                .flatMap(order ->
                        sendNotification(order.getUserId(), "Order created: " + order.getId())
                                .thenReturn(order)
                );
    }

    // ============================================
    // Helper Methods
    // ============================================

    private Mono<List<OrderDto>> getUserOrders(String userId) {
        return webClient.get()
                .uri(BASE_URL + "/api/users/{id}/orders", userId)
                .retrieve()
                .bodyToFlux(OrderDto.class)
                .collectList();
    }

    private Mono<Void> sendNotification(String userId, String message) {
        return webClient.post()
                .uri(BASE_URL + "/api/notifications")
                .bodyValue(Map.of(
                        "userId", userId,
                        "message", message
                ))
                .retrieve()
                .bodyToMono(Void.class);
    }

    // ============================================
    // Simple DTOs
    // ============================================

    @Data
    public static class UserDto {
        private String id;
        private String name;
        private String email;
    }

    @Data
    public static class OrderDto {
        private String id;
        private String userId;
        private Double amount;
        private String status;
    }

    @Data
    public static class OrderRequest {
        private String userId;
        private Double amount;
        private List<String> items;
        private String requestId;
        private String createdBy;
    }
}