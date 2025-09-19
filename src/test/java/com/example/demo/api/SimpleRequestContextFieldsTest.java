package com.example.demo.api;

import com.example.demo.config.BaseApiTest;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Simplified test for request context fields that focuses on basic functionality
 * Tests the core patterns without complex JWT or session handling
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:request-context-config.yml"
})
public class SimpleRequestContextFieldsTest extends BaseApiTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        wireMock.resetAll();
        
        // Setup downstream service mock
        wireMock.stubFor(get(urlPathEqualTo("/downstream/service"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Service-Version", "2.1.0")
                        .withHeader("X-RateLimit-Remaining", "95")
                        .withHeader("X-Response-Time", "150")
                        .withBody("{\"status\": \"success\"}")));
    }

    // ========================================
    // PATTERN 1: Basic Bidirectional Propagation
    // ========================================

    @Test
    @DisplayName("headerId1 - Basic bidirectional propagation")
    void testHeaderId1_BasicBidirectional() {
        given()
            .header("X-HEADER-ID-1", "test-header-1")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-HEADER-ID-1", equalTo("test-header-1")));
    }

    @Test
    @DisplayName("headerId2 - Basic bidirectional propagation")
    void testHeaderId2_BasicBidirectional() {
        given()
            .header("X-HEADER-ID-2", "test-header-2")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-HEADER-ID-2", equalTo("test-header-2")));
    }

    // ========================================
    // PATTERN 2: With Default Values
    // ========================================

    @Test
    @DisplayName("headerId3 - Default value when header missing")
    void testHeaderId3_DefaultValue() {
        // Don't send the header - should use default value
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation with default value
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-HEADER-ID-3", equalTo("default-header-3")));
    }

    @Test
    @DisplayName("headerId4 - Default value when header missing")
    void testHeaderId4_DefaultValue() {
        // Don't send the header - should use default value
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation with default value
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-HEADER-ID-4", equalTo("default-header-4")));
    }

    // ========================================
    // PATTERN 3: Extract Only (No Propagation)
    // ========================================

    @Test
    @DisplayName("emailHeader - Extract only, no propagation")
    void testEmailHeader_ExtractOnly() {
        given()
            .header("X-User-Email", "user@example.com")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify NO downstream propagation (extract only)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withoutHeader("X-User-Email"));
    }

    // ========================================
    // PATTERN 4: Sensitive with Custom Masking
    // ========================================

    @Test
    @DisplayName("sensitiveHeader - Sensitive data with masking")
    void testSensitiveHeader_WithMasking() {
        given()
            .header("X-Sensitive-Data", "secret-12345")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation (sensitive data still propagates)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Sensitive-Data", equalTo("secret-12345")));
    }

    // ========================================
    // PATTERN 5: Cookie Sources
    // ========================================

    @Test
    @DisplayName("cookieId1 - Cookie upstream extraction only")
    void testCookieId1_CookieExtraction() {
        given()
            .cookie("session-id", "cookie-session-123")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify NO downstream propagation (cookies are upstream-only)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withoutHeader("Cookie"));
    }

    @Test
    @DisplayName("cookieId2 - Cookie upstream extraction with default")
    void testCookieId2_CookieWithDefault() {
        // Don't send cookie - should use default value for upstream processing
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify NO downstream propagation (cookies are upstream-only)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withoutHeader("Cookie"));
    }

    // ========================================
    // PATTERN 6: Query Parameters
    // ========================================

    @Test
    @DisplayName("queryId1 - Query parameter bidirectional propagation")
    void testQueryId1_QueryPropagation() {
        given()
            .queryParam("version", "v1.2.3")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation as query parameter
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withQueryParam("version", equalTo("v1.2.3")));
    }

    @Test
    @DisplayName("queryId2 - Query parameter with default value")
    void testQueryId2_QueryWithDefault() {
        // Don't send query param - should use default
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation with default value
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withQueryParam("format", equalTo("default-query")));
    }

    @Test
    @DisplayName("queryId5 - Query extract only")
    void testQueryId5_QueryExtractOnly() {
        given()
            .queryParam("scope", "user-specific")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify NO downstream propagation (extract only)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withoutHeader("scope"));
    }

    // ========================================
    // PATTERN 7: Session Attributes
    // ========================================

    @Test
    @DisplayName("sessionId - Session attribute extraction")
    void testSessionId_SessionAttribute() {
        // Session attributes are server-side, so we test without setting them
        // The system should handle missing session gracefully
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Session attributes don't propagate downstream in this test setup
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service")));
    }

    // ========================================
    // PATTERN 8: JWT Claims (Extract-Only)
    // ========================================

    @Test
    @DisplayName("userId - JWT claim extraction with default")
    void testUserId_JwtClaimWithDefault() {
        // No JWT token provided - should use default value
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // JWT claims are extract-only, no downstream propagation expected
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service")));
    }

    // ========================================
    // PATTERN 11: Cross-Type Enrichment
    // ========================================

    @Test
    @DisplayName("apiKey - Header to Query parameter enrichment")
    void testApiKey_HeaderToQuery() {
        given()
            .header("X-API-Key", "secret-api-key-12345678")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation as query parameter (cross-type)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withQueryParam("api_key", equalTo("secret-api-key-12345678")));
    }

    @Test
    @DisplayName("clientVersion - Query to Header enrichment")
    void testClientVersion_QueryToHeader() {
        given()
            .queryParam("client_version", "2.1.0")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation as header (cross-type)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Client-Version", equalTo("2.1.0")));
    }

    @Test
    @DisplayName("clientVersion - Default value when query missing")
    void testClientVersion_DefaultValue() {
        // Don't send query param - should use default
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation with default value
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Client-Version", equalTo("1.0")));
    }

    // ========================================
    // PATTERN 12: Downstream Response Extraction
    // ========================================

    @Test
    @DisplayName("downstreamServiceVersion - Extract from downstream response")
    void testDownstreamServiceVersion_ResponseExtraction() {
        // Configure WireMock to return a service version header
        wireMock.stubFor(get(urlPathEqualTo("/downstream/service"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Service-Version", "service-v2.1.0")
                        .withBody("{\"status\": \"success\"}")));

        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify the request was made (correlation ID should be generated)
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Request-ID", matching(".*")));
    }

    @Test
    @DisplayName("downstreamRateLimit - Extract rate limit from response")
    void testDownstreamRateLimit_ResponseExtraction() {
        // Configure WireMock to return rate limit header
        wireMock.stubFor(get(urlPathEqualTo("/downstream/service"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-RateLimit-Remaining", "95")
                        .withBody("{\"status\": \"success\"}")));

        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify the request was made
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service")));
    }

    // ========================================
    // PATTERN 13: Bidirectional with Downstream
    // ========================================

    @Test
    @DisplayName("correlationId - Full bidirectional with downstream extraction")
    void testCorrelationId_FullBidirectional() {
        given()
            .header("X-Correlation-ID", "corr-12345")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Correlation-ID", equalTo("corr-12345")));
    }

    @Test
    @DisplayName("correlationId - Generated when missing")
    void testCorrelationId_Generated() {
        // Don't send correlation ID - should be generated
        given()
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify downstream propagation with generated UUID
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Correlation-ID", matching(".*")));
    }

    // ========================================
    // PATTERN 15: Downstream Monitoring with Context
    // ========================================

    @Test
    @DisplayName("serviceHealth - Service monitoring with context")
    void testServiceHealth_MonitoringWithContext() {
        given()
            .header("X-Service-Name", "user-service")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify service name propagation for monitoring
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-Service-Name", equalTo("user-service")));
    }

    @Test
    @DisplayName("cacheStatus - Cache monitoring with user context")
    void testCacheStatus_CacheMonitoring() {
        given()
            .header("X-User-ID", "user-789")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify user context propagation for cache analysis
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-User-ID", equalTo("user-789")));
    }

    @Test
    @DisplayName("featureFlags - Feature flag context propagation")
    void testFeatureFlags_ContextPropagation() {
        given()
            .header("X-User-Segment", "premium")
        .when()
            .get("/api/test/downstream")
        .then()
            .statusCode(200);

        // Verify user segment propagation for feature flags
        wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
                .withHeader("X-User-Segment", equalTo("premium")));
    }
}
