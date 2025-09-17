package com.example.demo.utils;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.springframework.http.HttpHeaders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;

public class ApiTestUtils {

    // Header construction utilities
    public static Map<String, String> createContextHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-Request-ID", UUID.randomUUID().toString());
        headers.put("X-Correlation-ID", UUID.randomUUID().toString());
        headers.put("X-User-ID", "test-user-123");
        headers.put("X-Tenant-ID", "test-tenant");
        headers.put("X-Session-ID", "test-session-" + System.currentTimeMillis());
        return headers;
    }

    public static Map<String, String> createTracingHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-B3-TraceId", generateTraceId());
        headers.put("X-B3-SpanId", generateSpanId());
        headers.put("X-B3-ParentSpanId", generateSpanId());
        headers.put("X-B3-Sampled", "1");
        return headers;
    }

    public static String createBearerToken(String token) {
        return "Bearer " + token;
    }

    // Request builders
    public static RequestSpecification withAuthentication(RequestSpecification spec, String jwt) {
        return spec.header(HttpHeaders.AUTHORIZATION, createBearerToken(jwt));
    }

    public static RequestSpecification withContextHeaders(RequestSpecification spec) {
        Map<String, String> headers = createContextHeaders();
        headers.forEach(spec::header);
        return spec;
    }

    // Response validation utilities
    public static void validateSuccessResponse(Response response) {
        response.then()
            .statusCode(200)
            .contentType("application/json")
            .body("$", notNullValue());
    }

    public static void validateErrorResponse(Response response, int expectedStatus) {
        response.then()
            .statusCode(expectedStatus)
            .contentType("application/json")
            .body("error", notNullValue())
            .body("message", notNullValue())
            .body("timestamp", notNullValue());
    }

    public static void validateContextInResponse(Response response, String... expectedFields) {
        for (String field : expectedFields) {
            response.then().body("context." + field, notNullValue());
        }
    }

    public static void validateHeadersInResponse(Response response, String... expectedHeaders) {
        for (String header : expectedHeaders) {
            response.then().header(header, notNullValue());
        }
    }

    // Context extraction utilities
    public static Map<String, Object> extractContext(Response response) {
        return response.jsonPath().getMap("context");
    }

    public static String extractCorrelationId(Response response) {
        return response.getHeader("X-Correlation-ID");
    }

    public static String extractRequestId(Response response) {
        return response.getHeader("X-Request-ID");
    }

    // Test data generators
    public static Map<String, String> generateQueryParams() {
        Map<String, String> params = new HashMap<>();
        params.put("page", "1");
        params.put("size", "10");
        params.put("sort", "name,asc");
        params.put("filter", "active:true");
        return params;
    }

    public static Map<String, String> generateFormData() {
        Map<String, String> formData = new HashMap<>();
        formData.put("username", "testuser");
        formData.put("email", "test@example.com");
        formData.put("role", "USER");
        return formData;
    }

    public static Map<String, Object> generateRequestBody() {
        Map<String, Object> body = new HashMap<>();
        body.put("name", "Test Entity");
        body.put("description", "Test description");
        body.put("active", true);
        body.put("metadata", Map.of(
            "created", System.currentTimeMillis(),
            "version", "1.0.0"
        ));
        return body;
    }

    // Cookie utilities
    public static String createCookie(String name, String value) {
        return name + "=" + value;
    }

    public static String createMultipleCookies(Map<String, String> cookies) {
        return cookies.entrySet().stream()
            .map(e -> e.getKey() + "=" + e.getValue())
            .reduce((a, b) -> a + "; " + b)
            .orElse("");
    }

    // Timing utilities
    public static long measureResponseTime(Response response) {
        return response.getTime();
    }

    public static void assertResponseTimeWithin(Response response, long maxMillis) {
        long responseTime = response.getTime();
        if (responseTime > maxMillis) {
            throw new AssertionError("Response time " + responseTime + "ms exceeds maximum " + maxMillis + "ms");
        }
    }

    // Private helper methods
    private static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String generateSpanId() {
        return Long.toHexString(System.nanoTime());
    }
}