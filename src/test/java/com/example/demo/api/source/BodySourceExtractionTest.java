package com.example.demo.api.source;

import com.example.demo.config.BaseApiTest;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.HashMap;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Comprehensive test suite for BODY source extraction functionality.
 * 
 * Tests the framework's ability to extract values from JSON request bodies during the
 * post-authentication phase using JSONPath expressions and make them available in the RequestContext.
 * 
 * BODY sources are extracted after Spring Security processing, allowing access to
 * request body content and nested JSON structures.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Body Source Extraction Tests")
class BodySourceExtractionTest extends BaseApiTest {

    private RequestSpecification requestSpec;

    @BeforeEach
    void setUp() {
        requestSpec = given()
                .port(port)
                .contentType("application/json")
                .accept("application/json");
    }

    @Test
    @DisplayName("Should extract single body field using JSONPath")
    void shouldExtractSingleBodyField() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "body-id-123");
        value.put("email", "test@example.com");
        requestBody.put("value", value);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/single")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("body-id-123"))
            .body("extractedBodyId2", equalTo("test@example.com"))
            .body("requestBody.value.id", equalTo("body-id-123"))
            .body("requestBody.value.email", equalTo("test@example.com"))
            .body("contextFields.bodyId1", equalTo("body-id-123"))
            .body("contextFields.bodyId2", equalTo("test@example.com"));
    }

    @Test
    @DisplayName("Should extract multiple body fields using different JSONPath expressions")
    void shouldExtractMultipleBodyFields() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "multi-body-id-456");
        value.put("email", "multi@example.com");
        requestBody.put("value", value);
        requestBody.put("timestamp", System.currentTimeMillis());
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/multiple")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("multi-body-id-456"))
            .body("extractedBodyId2", equalTo("multi@example.com"))
            .body("requestBody.value.id", equalTo("multi-body-id-456"))
            .body("requestBody.value.email", equalTo("multi@example.com"))
            .body("contextFields.bodyId1", equalTo("multi-body-id-456"))
            .body("contextFields.bodyId2", equalTo("multi@example.com"));
    }

    @Test
    @DisplayName("Should extract from nested JSON structures")
    void shouldExtractFromNestedJsonStructures() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "nested-id-789");
        value.put("email", "nested@example.com");
        
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("source", "api-test");
        metadata.put("version", "1.0");
        
        requestBody.put("value", value);
        requestBody.put("metadata", metadata);
        requestBody.put("action", "create");
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/nested")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("nested-id-789"))
            .body("extractedBodyId2", equalTo("nested@example.com"))
            .body("requestBody.value.id", equalTo("nested-id-789"))
            .body("requestBody.value.email", equalTo("nested@example.com"))
            .body("requestBody.metadata.source", equalTo("api-test"))
            .body("requestBody.action", equalTo("create"))
            .body("contextFields.bodyId1", equalTo("nested-id-789"))
            .body("contextFields.bodyId2", equalTo("nested@example.com"));
    }

    @Test
    @DisplayName("Should handle complex nested JSON with arrays and objects")
    void shouldHandleComplexNestedJson() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "complex-id-999");
        value.put("email", "complex@example.com");
        
        Map<String, Object> user = new HashMap<>();
        user.put("name", "Test User");
        user.put("roles", new String[]{"USER", "ADMIN"});
        
        Map<String, Object> preferences = new HashMap<>();
        preferences.put("theme", "dark");
        preferences.put("notifications", true);
        
        requestBody.put("value", value);
        requestBody.put("user", user);
        requestBody.put("preferences", preferences);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/nested")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("complex-id-999"))
            .body("extractedBodyId2", equalTo("complex@example.com"))
            .body("requestBody.value.id", equalTo("complex-id-999"))
            .body("requestBody.value.email", equalTo("complex@example.com"))
            .body("requestBody.user.name", equalTo("Test User"))
            .body("requestBody.preferences.theme", equalTo("dark"))
            .body("contextFields.bodyId1", equalTo("complex-id-999"))
            .body("contextFields.bodyId2", equalTo("complex@example.com"));
    }

    @Test
    @DisplayName("Should use default values when JSONPath fields are missing")
    void shouldUseDefaultValuesWhenFieldsMissing() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        // Only provide email, missing id (which has default value "unknown-body-id")
        value.put("email", "default-test@example.com");
        requestBody.put("value", value);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/defaults")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("unknown-body-id")) // Default value from config
            .body("extractedBodyId2", equalTo("default-test@example.com"))
            .body("requestBody.value.email", equalTo("default-test@example.com"))
            .body("contextFields.bodyId1", equalTo("unknown-body-id"))
            .body("contextFields.bodyId2", equalTo("default-test@example.com"));
    }

    @Test
    @DisplayName("Should override default values when JSONPath fields are provided")
    void shouldOverrideDefaultValuesWhenFieldsProvided() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "override-body-id");
        value.put("email", "override@example.com");
        requestBody.put("value", value);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/defaults")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("override-body-id")) // Should override default
            .body("extractedBodyId2", equalTo("override@example.com"))
            .body("requestBody.value.id", equalTo("override-body-id"))
            .body("requestBody.value.email", equalTo("override@example.com"))
            .body("contextFields.bodyId1", equalTo("override-body-id"))
            .body("contextFields.bodyId2", equalTo("override@example.com"));
    }

    @Test
    @DisplayName("Should handle sensitive data with masking")
    void shouldHandleSensitiveDataWithMasking() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "sensitive-id-123");
        value.put("email", "sensitive@example.com");
        requestBody.put("value", value);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/sensitive")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("sensitive-id-123"))
            .body("extractedBodyId2", equalTo("sensitive@example.com")) // Should be extracted (masking is for logging)
            .body("requestBody.value.id", equalTo("sensitive-id-123"))
            .body("requestBody.value.email", equalTo("sensitive@example.com"))
            .body("contextFields.bodyId1", equalTo("sensitive-id-123"))
            .body("contextFields.bodyId2", equalTo("sensitive@example.com"));
    }

    @Test
    @DisplayName("Should handle empty request body gracefully")
    void shouldHandleEmptyRequestBodyGracefully() {
        Map<String, Object> requestBody = new HashMap<>();
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/defaults")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("unknown-body-id")) // Default value
            .body("extractedBodyId2", nullValue()) // No default, should be null
            .body("requestBody", equalTo(requestBody))
            .body("contextFields.bodyId1", equalTo("unknown-body-id"));
    }

    @Test
    @DisplayName("Should handle malformed JSON structure")
    void shouldHandleMalformedJsonStructure() {
        Map<String, Object> requestBody = new HashMap<>();
        // Missing the expected "value" wrapper
        requestBody.put("id", "direct-id-123");
        requestBody.put("email", "direct@example.com");
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/defaults")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("unknown-body-id")) // Default value (JSONPath not found)
            .body("extractedBodyId2", nullValue()) // JSONPath not found, no default
            .body("requestBody.id", equalTo("direct-id-123"))
            .body("requestBody.email", equalTo("direct@example.com"))
            .body("contextFields.bodyId1", equalTo("unknown-body-id"));
    }

    @Test
    @DisplayName("Should verify post-authentication phase extraction")
    void shouldVerifyPostAuthenticationPhaseExtraction() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", "post-auth-body-test");
        value.put("email", "postauth@example.com");
        requestBody.put("value", value);
        
        given(requestSpec)
            .header("X-HEADER-ID-1", "test-header-value") // Add a header to verify pre-auth extraction
            .body(requestBody)
            .when()
            .post("/request-entity/body/single")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("post-auth-body-test"))
            .body("extractedBodyId2", equalTo("postauth@example.com"))
            // Verify that both pre-auth and post-auth fields are present
            .body("contextFields", hasKey("headerId1")) // Pre-auth HEADER field
            .body("contextFields", hasKey("requestId")) // Pre-auth generated field
            .body("contextFields", hasKey("bodyId1")) // Post-auth BODY field
            .body("contextFields", hasKey("bodyId2")) // Post-auth BODY field
            .body("contextFields.headerId1", equalTo("test-header-value")) // Verify header was extracted
            .body("contextFields.bodyId1", equalTo("post-auth-body-test")) // Verify body was extracted
            .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("Should handle null and empty values in JSON")
    void shouldHandleNullAndEmptyValuesInJson() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", ""); // Empty string
        value.put("email", null); // Null value
        requestBody.put("value", value);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/multiple")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo(""))
            .body("extractedBodyId2", nullValue())
            .body("requestBody.value.id", equalTo(""))
            .body("contextFields.bodyId1", equalTo(""));
    }

    @Test
    @DisplayName("Should handle various data types in JSON body")
    void shouldHandleVariousDataTypesInJsonBody() {
        Map<String, Object> requestBody = new HashMap<>();
        Map<String, Object> value = new HashMap<>();
        value.put("id", 12345); // Number as ID
        value.put("email", "types@example.com");
        requestBody.put("value", value);
        requestBody.put("active", true);
        requestBody.put("score", 98.5);
        
        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/body/multiple")
            .then()
            .statusCode(200)
            .body("extractedBodyId1", equalTo("12345")) // Should be converted to string
            .body("extractedBodyId2", equalTo("types@example.com"))
            .body("requestBody.value.id", equalTo(12345)) // Original type preserved in response
            .body("requestBody.active", equalTo(true))
            .body("requestBody.score", equalTo(98.5f))
            .body("contextFields.bodyId1", equalTo("12345")) // Context stores as string
            .body("contextFields.bodyId2", equalTo("types@example.com"));
    }
}
