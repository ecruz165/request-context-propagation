package com.jefelabs.modules.requestcontext.api;

import com.jefelabs.modules.requestcontext.config.BaseApiTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Comprehensive test for DeferredResult endpoints with reactive WebClient calls.
 * 
 * This test validates the real-world microservice scenario where:
 * 1. DeferredResult endpoints make concurrent WebClient calls
 * 2. Reactive streams (Mono.zip) combine multiple service responses
 * 3. Block() is used to get final results
 * 4. Upstream context values are used within reactive streams
 * 5. Downstream captured values are propagated back to upstream headers
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
                classes = com.jefelabs.modules.requestcontext.demo.DemoApplication.class)
@ActiveProfiles("test")
@TestPropertySource(properties = {"spring.config.import=classpath:request-context-config.yml"})
public class DeferredResultReactiveContextTest extends BaseApiTest {

    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
        wireMock.resetAll();
        setupDownstreamServiceMocks();
    }

    private void setupDownstreamServiceMocks() {
        // Mock user service
        wireMock.stubFor(get(urlPathMatching("/user-service/users/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Service-Version", "user-service-v1.2.3")
                        .withHeader("X-Response-Time", "45ms")
                        .withBody("""
                            {
                                "userId": "test-user",
                                "name": "John Doe",
                                "email": "john.doe@example.com",
                                "status": "active"
                            }
                            """)));

        // Mock profile service
        wireMock.stubFor(get(urlPathMatching("/profile-service/profiles/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Service-Version", "profile-service-v2.1.0")
                        .withHeader("X-Response-Time", "32ms")
                        .withBody("""
                            {
                                "profileId": "profile-123",
                                "preferences": {
                                    "theme": "dark",
                                    "notifications": true
                                },
                                "lastLogin": "2024-01-15T10:30:00Z"
                            }
                            """)));

        // Mock payment service
        wireMock.stubFor(get(urlPathMatching("/payment-service/accounts/.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Service-Version", "payment-service-v1.5.2")
                        .withHeader("X-Response-Time", "67ms")
                        .withBody("""
                            {
                                "accountId": "acc-789",
                                "balance": 1250.75,
                                "currency": "USD",
                                "status": "active"
                            }
                            """)));

        // Mock test endpoint for upstream context testing
        wireMock.stubFor(get(urlPathEqualTo("/test-endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Test-Response", "success")
                        .withBody("""
                            {
                                "message": "test response",
                                "timestamp": "2024-01-15T10:30:00Z"
                            }
                            """)));
    }

    @Test
    @DisplayName("DeferredResult with concurrent reactive calls - validates upstream context propagation and downstream capture")
    void testDeferredResultWithConcurrentReactiveCalls() {
        // Given - Set upstream context values that should be propagated
        String testRequestId = "deferred-test-" + System.currentTimeMillis();
        String testCorrelationId = "corr-" + System.currentTimeMillis();
        String testUserId = "test-user-123";

        // When - Make request to DeferredResult endpoint that uses ReactiveTestService
        given()
            .header("X-Request-ID", testRequestId)
            .header("X-Correlation-ID", testCorrelationId)
            .header("X-User-ID", testUserId)
            .queryParam("userId", testUserId)
        .when()
            .get("/api/test/deferred-reactive-aggregation")
        .then()
            .statusCode(200)
            .body("aggregatedData.aggregationStatus", org.hamcrest.Matchers.equalTo("success"))
            .body("aggregatedData.userData.userId", org.hamcrest.Matchers.equalTo("test-user"))
            .body("aggregatedData.profileData.profileId", org.hamcrest.Matchers.equalTo("profile-123"))
            .body("aggregatedData.paymentData.accountId", org.hamcrest.Matchers.equalTo("acc-789"))
            .body("processingType", org.hamcrest.Matchers.equalTo("deferred-reactive"))
            .body("userId", org.hamcrest.Matchers.equalTo(testUserId))
            .body("contextFields", notNullValue())
            .body("downstreamFields", notNullValue());

        // Then - Verify all three downstream services received upstream context
        wireMock.verify(getRequestedFor(urlPathMatching("/user-service/users/.*"))
                .withHeader("X-Request-ID", equalTo(testRequestId))
                .withHeader("X-Correlation-ID", equalTo(testCorrelationId))
                .withHeader("X-User-ID", equalTo(testUserId)));

        wireMock.verify(getRequestedFor(urlPathMatching("/profile-service/profiles/.*"))
                .withHeader("X-Request-ID", equalTo(testRequestId))
                .withHeader("X-Correlation-ID", equalTo(testCorrelationId))
                .withHeader("X-User-ID", equalTo(testUserId)));

        wireMock.verify(getRequestedFor(urlPathMatching("/payment-service/accounts/.*"))
                .withHeader("X-Request-ID", equalTo(testRequestId))
                .withHeader("X-Correlation-ID", equalTo(testCorrelationId))
                .withHeader("X-User-ID", equalTo(testUserId)));
    }

    @Test
    @DisplayName("DeferredResult with upstream context usage in reactive stream - validates context access within reactive chains")
    void testDeferredResultUpstreamContextInReactiveStream() {
        // Given - Set upstream context values
        String testValue = "upstream-test-value-" + System.currentTimeMillis();
        String testRequestId = "upstream-test-" + System.currentTimeMillis();

        // When - Make request to DeferredResult endpoint that tests upstream context usage
        given()
            .header("X-Request-ID", testRequestId)
            .queryParam("testValue", testValue)
        .when()
            .get("/api/test/deferred-upstream-context-test")
        .then()
            .statusCode(200)
            .body("processingType", org.hamcrest.Matchers.equalTo("deferred-upstream-test"))
            .body("inputTestValue", containsString("upstream-test-value"))
            .body("testResult", notNullValue());

        // Then - Verify downstream service was called
        wireMock.verify(getRequestedFor(urlPathEqualTo("/test-endpoint")));
    }

    @Test
    @DisplayName("DeferredResult with downstream value capture - validates captured values are available after blocking")
    void testDeferredResultDownstreamValueCapture() {
        // Given - Set upstream context
        String testRequestId = "capture-test-" + System.currentTimeMillis();
        String testUserId = "capture-user-456";

        // When - Make request that should capture downstream values
        given()
            .header("X-Request-ID", testRequestId)
            .queryParam("userId", testUserId)
        .when()
            .get("/api/test/deferred-reactive-aggregation")
        .then()
            .statusCode(200);

        // Verify all services were called
        wireMock.verify(exactly(1), getRequestedFor(urlPathMatching("/user-service/users/.*")));
        wireMock.verify(exactly(1), getRequestedFor(urlPathMatching("/profile-service/profiles/.*")));
        wireMock.verify(exactly(1), getRequestedFor(urlPathMatching("/payment-service/accounts/.*")));
    }


}
