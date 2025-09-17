package com.example.demo.api.source;

import com.example.demo.config.BaseApiTest;
import com.example.demo.utils.JwtTestHelper;
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
 * Comprehensive test suite for TOKEN source extraction functionality.
 * 
 * Tests the framework's ability to extract values from JWT tokens during the
 * post-authentication phase and make them available in the RequestContext.
 * 
 * TOKEN sources are extracted after Spring Security processing, allowing access
 * to authenticated JWT claims and token-based context information.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("Token Source Extraction Tests")
class TokenSourceExtractionTest extends BaseApiTest {

    private RequestSpecification requestSpec;

    @BeforeEach
    void setUp() {
        requestSpec = given()
                .port(port)
                .contentType("application/json")
                .accept("application/json");
    }

    @Test
    @DisplayName("Should extract single token claim")
    void shouldExtractSingleTokenClaim() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "claim-value-123",
                "jwtClaimId2", "claim-value-456"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/single")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("claim-value-123"))
            .body("extractedJwtClaimId2", equalTo("claim-value-456"))
            .body("contextFields.jwtClaimId1", equalTo("claim-value-123"))
            .body("contextFields.jwtClaimId2", equalTo("claim-value-456"));
    }

    @Test
    @DisplayName("Should extract multiple token claims")
    void shouldExtractMultipleTokenClaims() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "multi-claim-1",
                "jwtClaimId2", "multi-claim-2",
                "jwtClaimId3", "multi-claim-3"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/multiple")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("multi-claim-1"))
            .body("extractedJwtClaimId2", equalTo("multi-claim-2"))
            .body("extractedJwtClaimId3", equalTo("multi-claim-3"))
            .body("contextFields.jwtClaimId1", equalTo("multi-claim-1"))
            .body("contextFields.jwtClaimId2", equalTo("multi-claim-2"))
            .body("contextFields.jwtClaimId3", equalTo("multi-claim-3"));
    }

    @Test
    @DisplayName("Should handle POST requests with token and body")
    void shouldHandlePostRequestWithTokenAndBody() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "post-claim-1",
                "jwtClaimId2", "post-claim-2"
        ));
        
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("name", "Test User");
        requestBody.put("action", "token-test");
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .body(requestBody)
            .when()
            .post("/request-entity/token/with-body")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("post-claim-1"))
            .body("extractedJwtClaimId2", equalTo("post-claim-2"))
            .body("requestBody.name", equalTo("Test User"))
            .body("requestBody.action", equalTo("token-test"))
            .body("contextFields.jwtClaimId1", equalTo("post-claim-1"))
            .body("contextFields.jwtClaimId2", equalTo("post-claim-2"));
    }

    @Test
    @DisplayName("Should extract standard JWT claims")
    void shouldExtractStandardJwtClaims() {
        String jwt = JwtTestHelper.generateJwt("test-user", "ADMIN", Map.of(
                "jwtClaimId1", "standard-claim-1",
                "custom_field", "custom-value"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/claims")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("standard-claim-1"))
            .body("contextFields.jwtClaimId1", equalTo("standard-claim-1"))
            // Verify that standard JWT claims are accessible in context
            .body("contextFields", hasKey("jwtClaimId1"));
    }

    @Test
    @DisplayName("Should handle tokens with various claim types")
    void shouldHandleTokensWithVariousClaimTypes() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "string-claim",
                "jwtClaimId2", 12345,  // Number claim
                "jwtClaimId3", true    // Boolean claim
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/multiple")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("string-claim"))
            .body("extractedJwtClaimId2", equalTo("12345"))  // Should be converted to string
            .body("extractedJwtClaimId3", equalTo("true"))   // Should be converted to string
            .body("contextFields.jwtClaimId1", equalTo("string-claim"))
            .body("contextFields.jwtClaimId2", equalTo("12345"))
            .body("contextFields.jwtClaimId3", equalTo("true"));
    }

    @Test
    @DisplayName("Should handle tokens with complex claim values")
    void shouldHandleTokensWithComplexClaimValues() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "complex-claim-with-special-chars-@#$%",
                "jwtClaimId2", "very-long-claim-value-that-contains-many-characters-and-should-be-handled-properly-123456789",
                "jwtClaimId3", "claim.with.dots.and-dashes_and_underscores"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/multiple")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("complex-claim-with-special-chars-@#$%"))
            .body("extractedJwtClaimId2", equalTo("very-long-claim-value-that-contains-many-characters-and-should-be-handled-properly-123456789"))
            .body("extractedJwtClaimId3", equalTo("claim.with.dots.and-dashes_and_underscores"))
            .body("contextFields.jwtClaimId1", equalTo("complex-claim-with-special-chars-@#$%"))
            .body("contextFields.jwtClaimId2", equalTo("very-long-claim-value-that-contains-many-characters-and-should-be-handled-properly-123456789"))
            .body("contextFields.jwtClaimId3", equalTo("claim.with.dots.and-dashes_and_underscores"));
    }

    @Test
    @DisplayName("Should use default values when token claims are missing")
    void shouldUseDefaultValuesWhenTokenClaimsMissing() {
        // Generate JWT without jwtClaimId2 (which has default value "anonymous")
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "provided-claim",
                "jwtClaimId3", "another-provided-claim"
                // jwtClaimId2 is missing, should use default
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/defaults")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("provided-claim"))
            .body("extractedJwtClaimId2", equalTo("anonymous")) // Default value from config
            .body("extractedJwtClaimId3", equalTo("another-provided-claim"))
            .body("contextFields.jwtClaimId1", equalTo("provided-claim"))
            .body("contextFields.jwtClaimId2", equalTo("anonymous"))
            .body("contextFields.jwtClaimId3", equalTo("another-provided-claim"));
    }

    @Test
    @DisplayName("Should override default values when token claims are provided")
    void shouldOverrideDefaultValuesWhenTokenClaimsProvided() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "override-claim-1",
                "jwtClaimId2", "override-default-value", // Should override default "anonymous"
                "jwtClaimId3", "override-claim-3"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/defaults")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("override-claim-1"))
            .body("extractedJwtClaimId2", equalTo("override-default-value")) // Should override default
            .body("extractedJwtClaimId3", equalTo("override-claim-3"))
            .body("contextFields.jwtClaimId1", equalTo("override-claim-1"))
            .body("contextFields.jwtClaimId2", equalTo("override-default-value"))
            .body("contextFields.jwtClaimId3", equalTo("override-claim-3"));
    }

    @Test
    @DisplayName("Should handle requests without Authorization header")
    void shouldHandleRequestsWithoutAuthorizationHeader() {
        // Don't provide Authorization header
        given(requestSpec)
            .when()
            .get("/request-entity/token/defaults")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", nullValue()) // No token, no claims
            .body("extractedJwtClaimId2", equalTo("anonymous")) // Default value should still be used
            .body("extractedJwtClaimId3", nullValue()) // No token, no claims
            .body("contextFields.jwtClaimId2", equalTo("anonymous"));
    }

    @Test
    @DisplayName("Should handle malformed Authorization header")
    void shouldHandleMalformedAuthorizationHeader() {
        given(requestSpec)
            .header("Authorization", "InvalidFormat token-without-bearer")
            .when()
            .get("/request-entity/token/defaults")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", nullValue()) // Malformed header, no claims
            .body("extractedJwtClaimId2", equalTo("anonymous")) // Default value should still be used
            .body("extractedJwtClaimId3", nullValue()) // Malformed header, no claims
            .body("contextFields.jwtClaimId2", equalTo("anonymous"));
    }

    @Test
    @DisplayName("Should verify post-authentication phase extraction")
    void shouldVerifyPostAuthenticationPhaseExtraction() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "post-auth-test-123"
        ));

        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .header("X-HEADER-ID-1", "test-header-value") // Add a header to verify pre-auth extraction
            .when()
            .get("/request-entity/token/single")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo("post-auth-test-123"))
            .body("contextFields.jwtClaimId1", equalTo("post-auth-test-123"))
            // Verify that both pre-auth and post-auth fields are present
            .body("contextFields", hasKey("headerId1")) // Pre-auth HEADER field
            .body("contextFields", hasKey("requestId")) // Pre-auth generated field
            .body("contextFields", hasKey("jwtClaimId1")) // Post-auth TOKEN field
            .body("contextFields.headerId1", equalTo("test-header-value")) // Verify header was extracted
            .body("timestamp", notNullValue());
    }

    @Test
    @DisplayName("Should handle empty token claims")
    void shouldHandleEmptyTokenClaims() {
        String jwt = JwtTestHelper.generateJwt(Map.of(
                "jwtClaimId1", "",
                "jwtClaimId2", "   ", // Whitespace only
                "jwtClaimId3", "valid-claim"
        ));
        
        given(requestSpec)
            .header("Authorization", "Bearer " + jwt)
            .when()
            .get("/request-entity/token/multiple")
            .then()
            .statusCode(200)
            .body("extractedJwtClaimId1", equalTo(""))
            .body("extractedJwtClaimId2", equalTo("   "))
            .body("extractedJwtClaimId3", equalTo("valid-claim"))
            .body("contextFields.jwtClaimId1", equalTo(""))
            .body("contextFields.jwtClaimId2", equalTo("   "))
            .body("contextFields.jwtClaimId3", equalTo("valid-claim"));
    }
}
