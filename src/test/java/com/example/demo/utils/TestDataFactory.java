package com.example.demo.utils;

import java.time.Instant;
import java.util.*;

public class TestDataFactory {

    private static final Random random = new Random();

    // User data generation
    public static Map<String, Object> createTestUser() {
        return createTestUser("test-user-" + System.currentTimeMillis());
    }

    public static Map<String, Object> createTestUser(String userId) {
        Map<String, Object> user = new HashMap<>();
        user.put("id", userId);
        user.put("username", "user_" + userId.substring(userId.lastIndexOf('-') + 1));
        user.put("email", user.get("username") + "@example.com");
        user.put("firstName", "Test");
        user.put("lastName", "User");
        user.put("role", "USER");
        user.put("department", "Engineering");
        user.put("active", true);
        user.put("createdAt", Instant.now().toString());
        user.put("permissions", Arrays.asList("READ", "WRITE"));
        return user;
    }

    // Request context data generation
    public static Map<String, Object> createRequestContext() {
        Map<String, Object> context = new HashMap<>();
        context.put("requestId", UUID.randomUUID().toString());
        context.put("correlationId", UUID.randomUUID().toString());
        context.put("sessionId", "session-" + System.currentTimeMillis());
        context.put("tenantId", "test-tenant");
        context.put("userId", "user-123");
        context.put("timestamp", Instant.now().toString());
        context.put("userAgent", "Test-Client/1.0");
        context.put("ipAddress", "192.168.1." + (random.nextInt(254) + 1));
        return context;
    }

    // Response data generation
    public static Map<String, Object> createApiResponse(String message) {
        return createApiResponse(message, null);
    }

    public static Map<String, Object> createApiResponse(String message, Object data) {
        Map<String, Object> response = new HashMap<>();
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        response.put("success", true);
        
        if (data != null) {
            response.put("data", data);
        }
        
        response.put("context", createRequestContext());
        return response;
    }

    public static Map<String, Object> createErrorResponse(String error, int code) {
        Map<String, Object> response = new HashMap<>();
        response.put("error", error);
        response.put("code", code);
        response.put("timestamp", Instant.now().toString());
        response.put("success", false);
        response.put("requestId", UUID.randomUUID().toString());
        return response;
    }

    // Downstream service responses
    public static String createDownstreamResponse() {
        return createDownstreamResponse("Downstream service response");
    }

    public static String createDownstreamResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("service", "downstream");
        response.put("message", message);
        response.put("timestamp", Instant.now().toString());
        response.put("processingTime", random.nextInt(100) + 10);
        
        // Add context that should be captured
        Map<String, Object> context = new HashMap<>();
        context.put("serviceVersion", "1.0.0");
        context.put("instanceId", "instance-" + random.nextInt(10));
        context.put("region", "us-east-1");
        response.put("context", context);
        
        return toJson(response);
    }

    // Test scenarios data
    public static Map<String, String> createSecurityHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("X-API-Key", "test-api-key-" + UUID.randomUUID());
        headers.put("X-Client-ID", "test-client-123");
        headers.put("X-Request-Signature", generateSignature());
        headers.put("X-Timestamp", String.valueOf(System.currentTimeMillis()));
        return headers;
    }

    public static Map<String, String> createMaliciousPayloads() {
        Map<String, String> payloads = new HashMap<>();
        payloads.put("xss", "<script>alert('xss')</script>");
        payloads.put("sql", "'; DROP TABLE users; --");
        payloads.put("command", "$(rm -rf /)");
        payloads.put("path", "../../../etc/passwd");
        payloads.put("unicode", "𝒽𝑒𝓁𝓁𝑜 🌍");
        return payloads;
    }

    // Performance test data
    public static List<Map<String, Object>> createBulkTestData(int count) {
        List<Map<String, Object>> data = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i);
            item.put("name", "Item " + i);
            item.put("value", random.nextDouble() * 1000);
            item.put("active", random.nextBoolean());
            item.put("tags", generateTags());
            data.add(item);
        }
        return data;
    }

    public static byte[] createLargePayload(int sizeInKB) {
        int sizeInBytes = sizeInKB * 1024;
        byte[] payload = new byte[sizeInBytes];
        random.nextBytes(payload);
        // Make it somewhat JSON-like
        String jsonPrefix = "{\"data\":\"";
        String jsonSuffix = "\"}";
        System.arraycopy(jsonPrefix.getBytes(), 0, payload, 0, jsonPrefix.length());
        System.arraycopy(jsonSuffix.getBytes(), 0, payload, 
            sizeInBytes - jsonSuffix.length(), jsonSuffix.length());
        return payload;
    }

    // Transformation test data
    public static Map<String, String> createTransformationTestData() {
        Map<String, String> data = new HashMap<>();
        data.put("lowercase", "UPPERCASE_TEXT");
        data.put("uppercase", "lowercase_text");
        data.put("base64", "SGVsbG8gV29ybGQ=");
        data.put("url", "hello%20world%20%26%20more");
        data.put("json", "{\"nested\":{\"value\":\"test\"}}");
        data.put("expression", "Bearer #{token}");
        return data;
    }

    // Utility methods
    private static String generateSignature() {
        return "sha256=" + UUID.randomUUID().toString().replace("-", "");
    }

    private static List<String> generateTags() {
        String[] tagPool = {"important", "test", "demo", "active", "pending", "processed"};
        List<String> tags = new ArrayList<>();
        int tagCount = random.nextInt(3) + 1;
        for (int i = 0; i < tagCount; i++) {
            tags.add(tagPool[random.nextInt(tagPool.length)]);
        }
        return tags;
    }

    private static String toJson(Map<String, Object> map) {
        // Simple JSON conversion - in production you'd use Jackson
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                json.append(",");
            }
            json.append("\"").append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value instanceof String) {
                json.append("\"").append(value).append("\"");
            } else if (value instanceof Number || value instanceof Boolean) {
                json.append(value);
            } else {
                json.append("\"").append(value.toString()).append("\"");
            }
            first = false;
        }
        json.append("}");
        return json.toString();
    }
}