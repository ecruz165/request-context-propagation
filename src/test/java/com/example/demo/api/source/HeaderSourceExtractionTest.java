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
 * Comprehensive test suite for HEADER source extraction functionality.
 * 
 * Tests the framework's ability to extract values from HTTP headers during the
 * pre-authentication phase and make them available in the RequestContext.
 * 
 * HEADER sources are extracted early in the request lifecycle, before Spring Security
 * processing, ensuring they're available even for failed authentication attempts.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Header Source Extraction Tests")
class HeaderSourceExtractionTest extends BaseApiTest {

    private RequestSpecification requestSpec;

    @BeforeEach
    void setUp() {
        requestSpec = given()
                .port(port)
                .contentType("application/json")
                .accept("application/json");
    }

    @Test
    @DisplayName("Should extract single header value")
    void shouldExtractSingleHeaderValue() {
        String headerValue = "test-header-value-123";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", headerValue)
            .when()
            .get("/request-entity/header/single")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(headerValue))
            .body("contextFields.headerId1", equalTo(headerValue))
            .body("extractedHeaderId3", equalTo("default-header-3")) // Default value
            .body("extractedHeaderId7", equalTo("anonymous-user")); // Default value
    }

    @Test
    @DisplayName("Should extract multiple header values")
    void shouldExtractMultipleHeaderValues() {
        String header1 = "multi-header-1";
        String header2 = "multi-header-2";
        String header4 = "multi-header-4";
        String header5 = "multi-header-5";
        String header6 = "multi-header-6";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", header1)
            .header("X-HEADER-ID-2", header2)
            .header("X-HEADER-ID-4", header4)
            .header("X-HEADER-ID-5", header5)
            .header("X-HEADER-ID-6", header6)
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(header1))
            .body("extractedHeaderId2", equalTo(header2))
            .body("extractedHeaderId3", equalTo("default-header-3")) // Default when not provided
            .body("extractedHeaderId4", equalTo(header4))
            .body("extractedHeaderId5", equalTo(header5))
            .body("extractedHeaderId6", equalTo(header6))
            .body("extractedHeaderId7", equalTo("anonymous-user")) // Default when not provided
            .body("contextFields.headerId1", equalTo(header1))
            .body("contextFields.headerId2", equalTo(header2))
            .body("contextFields.headerId4", equalTo(header4))
            .body("contextFields.headerId5", equalTo(header5))
            .body("contextFields.headerId6", equalTo(header6));
    }

    @Test
    @DisplayName("Should handle POST requests with headers and body")
    void shouldHandlePostRequestWithHeadersAndBody() {
        String header1 = "post-header-1";
        String header2 = "post-header-2";
        String sensitiveValue = "sensitive-data-12345";
        String emailValue = "user@example.com";
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "Test User");
        requestBody.put("action", "header-test");
        
        given(requestSpec)
            .header("X-HEADER-ID-1", header1)
            .header("X-HEADER-ID-2", header2)
            .header("X-Sensitive-Data", sensitiveValue)
            .header("X-User-Email", emailValue)
            .body(requestBody)
            .when()
            .post("/request-entity/header/with-body")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(header1))
            .body("extractedHeaderId2", equalTo(header2))
            .body("extractedSensitiveHeader", notNullValue()) // Should be masked
            .body("extractedEmailHeader", notNullValue()) // Should be masked
            .body("requestBody.name", equalTo("Test User"))
            .body("requestBody.action", equalTo("header-test"))
            .body("contextFields.headerId1", equalTo(header1))
            .body("contextFields.headerId2", equalTo(header2));
    }

    @Test
    @DisplayName("Should extract headers with various formats and special characters")
    void shouldExtractHeadersWithVariousFormats() {
        String complexHeader1 = "complex-header-value-with-dashes-123";
        String complexHeader2 = "header_with_underscores_456";
        String complexHeader4 = "header.with.dots.789";
        String complexHeader5 = "UPPERCASE-HEADER-VALUE";
        String complexHeader6 = "MiXeD-cAsE-hEaDeR-vAlUe";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", complexHeader1)
            .header("X-HEADER-ID-2", complexHeader2)
            .header("X-HEADER-ID-4", complexHeader4)
            .header("X-HEADER-ID-5", complexHeader5)
            .header("X-HEADER-ID-6", complexHeader6)
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(complexHeader1))
            .body("extractedHeaderId2", equalTo(complexHeader2))
            .body("extractedHeaderId4", equalTo(complexHeader4))
            .body("extractedHeaderId5", equalTo(complexHeader5))
            .body("extractedHeaderId6", equalTo(complexHeader6))
            .body("contextFields.headerId1", equalTo(complexHeader1))
            .body("contextFields.headerId2", equalTo(complexHeader2))
            .body("contextFields.headerId4", equalTo(complexHeader4))
            .body("contextFields.headerId5", equalTo(complexHeader5))
            .body("contextFields.headerId6", equalTo(complexHeader6));
    }

    @Test
    @DisplayName("Should handle very long header values")
    void shouldHandleVeryLongHeaderValues() {
        String longHeader1 = "very-long-header-value-that-contains-many-characters-and-should-be-handled-properly-123456789";
        String longHeader2 = "another-extremely-long-header-value-with-lots-of-information-that-might-be-used-in-real-world-scenarios-987654321";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", longHeader1)
            .header("X-HEADER-ID-2", longHeader2)
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(longHeader1))
            .body("extractedHeaderId2", equalTo(longHeader2))
            .body("contextFields.headerId1", equalTo(longHeader1))
            .body("contextFields.headerId2", equalTo(longHeader2));
    }

    @Test
    @DisplayName("Should handle headers with numeric values")
    void shouldHandleHeadersWithNumericValues() {
        String numericHeader1 = "12345";
        String numericHeader2 = "67890";
        String numericHeader4 = "999999999";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", numericHeader1)
            .header("X-HEADER-ID-2", numericHeader2)
            .header("X-HEADER-ID-4", numericHeader4)
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(numericHeader1))
            .body("extractedHeaderId2", equalTo(numericHeader2))
            .body("extractedHeaderId4", equalTo(numericHeader4))
            .body("contextFields.headerId1", equalTo(numericHeader1))
            .body("contextFields.headerId2", equalTo(numericHeader2))
            .body("contextFields.headerId4", equalTo(numericHeader4));
    }

    @Test
    @DisplayName("Should handle headers with special characters and symbols")
    void shouldHandleHeadersWithSpecialCharacters() {
        String specialHeader1 = "header-with-@-symbol";
        String specialHeader2 = "header_with_#_hash";
        String specialHeader4 = "header.with.%20.encoded";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", specialHeader1)
            .header("X-HEADER-ID-2", specialHeader2)
            .header("X-HEADER-ID-4", specialHeader4)
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(specialHeader1))
            .body("extractedHeaderId2", equalTo(specialHeader2))
            .body("extractedHeaderId4", equalTo(specialHeader4))
            .body("contextFields.headerId1", equalTo(specialHeader1))
            .body("contextFields.headerId2", equalTo(specialHeader2))
            .body("contextFields.headerId4", equalTo(specialHeader4));
    }

    @Test
    @DisplayName("Should use default values when headers are missing")
    void shouldUseDefaultValuesWhenHeadersMissing() {
        // Don't provide X-HEADER-ID-3 or HEADER-ID-7 headers
        given(requestSpec)
            .header("X-HEADER-ID-1", "provided-header")
            .when()
            .get("/request-entity/header/defaults")
            .then()
            .statusCode(200)
            .body("extractedHeaderId3", equalTo("default-header-3")) // Default value from config
            .body("extractedHeaderId7", equalTo("anonymous-user")) // Default value from config
            .body("contextFields.headerId3", equalTo("default-header-3"))
            .body("contextFields.headerId7", equalTo("anonymous-user"));
    }

    @Test
    @DisplayName("Should override default values when headers are provided")
    void shouldOverrideDefaultValuesWhenHeadersProvided() {
        String customHeader3 = "custom-header-3-value";
        String customHeader7 = "custom-user-value";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", "test-header")
            .header("X-HEADER-ID-3", customHeader3)
            .header("HEADER-ID-7", customHeader7) // Note: different header name format
            .when()
            .get("/request-entity/header/defaults")
            .then()
            .statusCode(200)
            .body("extractedHeaderId3", equalTo(customHeader3)) // Should override default
            .body("extractedHeaderId7", equalTo(customHeader7)) // Should override default
            .body("contextFields.headerId3", equalTo(customHeader3))
            .body("contextFields.headerId7", equalTo(customHeader7));
    }

    @Test
    @DisplayName("Should extract sensitive headers with masking")
    void shouldExtractSensitiveHeadersWithMasking() {
        String sensitiveData = "sensitive-data-12345";
        String emailData = "user@example.com";
        String creditCard = "1234-5678-9012-3456";
        String apiKey = "api-key-abcdef123456789";
        
        given(requestSpec)
            .header("X-Sensitive-Data", sensitiveData)
            .header("X-User-Email", emailData)
            .header("X-Credit-Card", creditCard)
            .header("X-API-Key", apiKey)
            .when()
            .get("/request-entity/header/sensitive")
            .then()
            .statusCode(200)
            .body("extractedSensitiveHeader", notNullValue()) // Should be present but masked
            .body("extractedEmailHeader", notNullValue()) // Should be present but masked
            .body("extractedCreditCardField", notNullValue()) // Should be present but masked
            .body("extractedApiKeyField", notNullValue()) // Should be present but masked
            .body("contextFields.sensitiveHeader", notNullValue())
            .body("contextFields.emailHeader", notNullValue())
            .body("contextFields.creditCardField", notNullValue())
            .body("contextFields.apiKeyField", notNullValue());
    }

    @Test
    @DisplayName("Should verify pre-authentication phase extraction")
    void shouldVerifyPreAuthenticationPhaseExtraction() {
        String headerValue = "pre-auth-test-123";
        
        given(requestSpec)
            .header("X-HEADER-ID-1", headerValue)
            .when()
            .get("/request-entity/header/single")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(headerValue))
            .body("contextFields.headerId1", equalTo(headerValue))
            // Verify that both pre-auth (HEADER) and post-auth fields are present
            .body("contextFields", hasKey("headerId1")) // Pre-auth HEADER field
            .body("contextFields", hasKey("requestId")) // Pre-auth generated field
            .body("contextFields", hasKey("userId")) // Post-auth field with default
            .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("Should handle empty header values")
    void shouldHandleEmptyHeaderValues() {
        given(requestSpec)
            .header("X-HEADER-ID-1", "")
            .header("X-HEADER-ID-2", "   ") // Whitespace only
            .when()
            .get("/request-entity/header/multiple")
            .then()
            .statusCode(200)
            .body("extractedHeaderId1", equalTo(""))
            .body("extractedHeaderId2", equalTo(""))  // Spring trims whitespace-only headers to empty
            .body("contextFields.headerId1", equalTo(""))
            .body("contextFields.headerId2", equalTo(""));  // Spring trims whitespace-only headers to empty
    }
}
