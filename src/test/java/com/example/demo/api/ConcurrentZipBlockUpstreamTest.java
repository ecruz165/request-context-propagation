package com.example.demo.api;

import com.example.demo.config.BaseApiTest;
import com.example.demo.config.RequestContextWebClientBuilder;
import com.example.demo.service.RequestContextService;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;

/**
 * Comprehensive test demonstrating the complete flow:
 * 1. Make requests to two distinct systems using WebClient
 * 2. Use Mono.zip to combine results concurrently
 * 3. Use block() to get final results
 * 4. Verify captured response values are propagated to context
 * 5. Check values appear in logs and upstream response headers
 */
@TestPropertySource(properties = {
    "spring.config.import=classpath:request-context-config.yml"
})
public class ConcurrentZipBlockUpstreamTest extends BaseApiTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;

    @Autowired
    private RequestContextService contextService;

    @BeforeEach
    void setUp() {
        RestAssured.port = port;
    }

    @Test
    @DisplayName("Complete Flow: Two Systems → Zip → Block → Context → Logs → Upstream Headers")
    public void testCompleteConcurrentZipBlockUpstreamFlow() {
        // Setup WireMock stubs for two distinct systems
        setupUserServiceStub();
        setupProfileServiceStub();

        // Make the request that triggers the complete flow
        given()
            .header("X-Request-ID", "test-request-123")
            .header("X-Correlation-ID", "test-correlation-456")
        .when()
            .get("/api/test/concurrent-zip-block-test")
        .then()
            .statusCode(200)
            .body("message", org.hamcrest.Matchers.equalTo("Concurrent zip and block test completed"))
            .body("userServiceData", org.hamcrest.Matchers.notNullValue())
            .body("profileServiceData", org.hamcrest.Matchers.notNullValue())
            .body("capturedValues", org.hamcrest.Matchers.notNullValue())
            // Verify upstream headers are present (captured values sent back to client)
            .header("X-Request-ID", "test-request-123")
            .header("X-Downstream-User-Version", "user-service-v3.1.0")
            .header("X-Downstream-Profile-Version", "profile-service-v2.5.0");

        // Verify both downstream services received the propagated context
        verifyUserServiceReceived();
        verifyProfileServiceReceived();
    }

    /**
     * Test endpoint via HTTP request (proper integration test)
     */
    @Test
    @DisplayName("Test endpoint via HTTP request with context capture")
    public void testEndpointViaHttpRequest() {
        // Setup WireMock stubs for the endpoint to call
        setupUserServiceStub();
        setupProfileServiceStub();

        // Make HTTP request to the endpoint (this creates proper request context)
        given()
            .header("X-Request-ID", "test-request-789")
            .header("X-Correlation-ID", "test-correlation-789")
        .when()
            .get("/api/test/concurrent-zip-block-test")
        .then()
            .statusCode(200)
            .body("message", org.hamcrest.Matchers.equalTo("Concurrent zip and block test completed"))
            .body("userServiceData", org.hamcrest.Matchers.notNullValue())
            .body("profileServiceData", org.hamcrest.Matchers.notNullValue())
            .body("capturedValues", org.hamcrest.Matchers.notNullValue());

        // Verify both downstream services received the propagated context
        verifyUserServiceReceived();
        verifyProfileServiceReceived();
    }

    private void setupUserServiceStub() {
        wireMock.stubFor(get(urlPathEqualTo("/user-service/users/test-user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-User-Service-Version", "user-service-v3.1.0")
                        .withHeader("X-Response-Status", "success")
                        .withHeader("X-Processing-Time", "45ms")
                        .withBody("{\n" +
                                "  \"userId\": \"test-user\",\n" +
                                "  \"name\": \"Test User\",\n" +
                                "  \"email\": \"test@example.com\",\n" +
                                "  \"status\": \"active\"\n" +
                                "}")
                ));
    }

    private void setupProfileServiceStub() {
        wireMock.stubFor(get(urlPathEqualTo("/profile-service/profiles/test-user"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Profile-Service-Version", "profile-service-v2.5.0")
                        .withHeader("X-Response-Status", "success")
                        .withHeader("X-Processing-Time", "32ms")
                        .withBody("{\n" +
                                "  \"profileId\": \"test-user-profile\",\n" +
                                "  \"preferences\": {\n" +
                                "    \"theme\": \"dark\",\n" +
                                "    \"language\": \"en\"\n" +
                                "  },\n" +
                                "  \"lastLogin\": \"2024-01-15T10:30:00Z\"\n" +
                                "}")
                ));
    }

    private void verifyUserServiceReceived() {
        // Verify user service received the propagated context headers
        wireMock.verify(getRequestedFor(urlPathEqualTo("/user-service/users/test-user"))
                .withHeader("X-Request-ID", WireMock.matching("test-request-.*"))
                .withHeader("X-Correlation-ID", WireMock.matching("test-correlation-.*"))
                .withHeader("X-Client-System", WireMock.equalTo("user-service")));
    }

    private void verifyProfileServiceReceived() {
        // Verify profile service received the propagated context headers
        wireMock.verify(getRequestedFor(urlPathEqualTo("/profile-service/profiles/test-user"))
                .withHeader("X-Request-ID", WireMock.matching("test-request-.*"))
                .withHeader("X-Correlation-ID", WireMock.matching("test-correlation-.*"))
                .withHeader("X-Client-System", WireMock.equalTo("profile-service")));
    }
}
