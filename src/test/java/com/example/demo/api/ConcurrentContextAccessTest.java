package com.example.demo.api;

import com.example.demo.config.BaseApiTest;
import com.example.demo.service.RequestContextService;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify context behavior in concurrent scenarios
 * This tests the HttpServletRequest storage mechanism and its limitations
 *
 * IMPORTANT: HttpServletRequest storage means context is only available to:
 * 1. The original request processing thread
 * 2. Spring-managed async processing (WebAsyncManager)
 * 3. NOT available to arbitrary background threads or thread pools
 */
@Disabled
@TestPropertySource(properties = {
    "spring.config.import=classpath:request-context-config.yml"
})
public class ConcurrentContextAccessTest extends BaseApiTest {

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
                        .withBody("{\"status\": \"success\"}")));
    }

    @Test
    @DisplayName("Background threads should NOT have access to request context (expected behavior)")
    void testBackgroundThreadsCannotAccessContext() throws Exception {
        String testRequestId = "concurrent-test-" + System.currentTimeMillis();
        String testCorrelationId = "corr-concurrent-" + System.currentTimeMillis();

        given()
            .header("X-Request-ID", testRequestId)
            .header("X-Correlation-ID", testCorrelationId)
        .when()
            .get("/api/test/concurrent-threads")
        .then()
            .statusCode(200)
            .body("success", org.hamcrest.Matchers.is(false))  // Expected: background threads can't access context
            .body("threadsAccessed", org.hamcrest.Matchers.is(0))  // Expected: no threads have context
            .body("allThreadsHadContext", org.hamcrest.Matchers.is(false))  // Expected: context not available
            .body("originalRequestId", org.hamcrest.Matchers.equalTo(testRequestId))  // But original thread has it
            .body("originalCorrelationId", org.hamcrest.Matchers.equalTo(testCorrelationId));
    }

    @Test
    @DisplayName("Reactive streams should NOT propagate context to different thread pools (expected behavior)")
    void testReactiveContextLimitations() {
        String testRequestId = "reactive-test-" + System.currentTimeMillis();
        String testCorrelationId = "corr-reactive-" + System.currentTimeMillis();

        given()
            .header("X-Request-ID", testRequestId)
            .header("X-Correlation-ID", testCorrelationId)
        .when()
            .get("/api/test/reactive-context")
        .then()
            .statusCode(200)
            .body("success", org.hamcrest.Matchers.is(true))
            .body("originalRequestId", org.hamcrest.Matchers.equalTo(testRequestId))  // Original thread has context
            .body("originalCorrelationId", org.hamcrest.Matchers.equalTo(testCorrelationId))
            // Reactive streams on different schedulers lose context (expected)
            .body("allHadRequestId", org.hamcrest.Matchers.is(false))
            .body("allHadCorrelationId", org.hamcrest.Matchers.is(false));
    }

    @Test
    @DisplayName("DeferredResult background threads should NOT have context access (expected behavior)")
    void testDeferredResultContextLimitations() {
        String testRequestId = "deferred-test-" + System.currentTimeMillis();
        String testCorrelationId = "corr-deferred-" + System.currentTimeMillis();

        given()
            .header("X-Request-ID", testRequestId)
            .header("X-Correlation-ID", testCorrelationId)
        .when()
            .get("/api/test/deferred-context")
        .then()
            .statusCode(200)
            .body("success", org.hamcrest.Matchers.is(true))
            .body("originalRequestId", org.hamcrest.Matchers.equalTo(testRequestId))  // Original thread has context
            .body("originalCorrelationId", org.hamcrest.Matchers.equalTo(testCorrelationId))
            // Background CompletableFuture thread loses context (expected)
            .body("contextAvailableInThread", org.hamcrest.Matchers.is(false))
            .body("requestIdMatched", org.hamcrest.Matchers.is(false))
            .body("correlationIdMatched", org.hamcrest.Matchers.is(false));
    }


}
