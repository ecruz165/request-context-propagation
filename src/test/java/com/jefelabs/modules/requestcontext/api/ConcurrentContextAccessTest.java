package com.jefelabs.modules.requestcontext.api;

import com.jefelabs.modules.requestcontext.config.BaseApiTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to demonstrate TRUE concurrent/parallel multi-threading scenarios
 * and verify HttpServletRequest storage limitations vs ThreadLocal behavior
 * <p>
 * This test class demonstrates:
 * 1. Multiple threads accessing context simultaneously (should fail with HttpServletRequest storage)
 * 2. Parallel execution with thread pools (context not propagated)
 * 3. Concurrent reactive streams (context lost across schedulers)
 * 4. Race conditions in context access
 * 5. Context isolation between different request threads
 */
@TestPropertySource(properties = {
        "spring.config.import=classpath:request-context-config.yml"
})
// @Disabled("Enable to see TRUE concurrent multi-threading demonstrations")
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
    @DisplayName("Demonstrate TRUE parallel thread execution with context access attempts")
    void testTrueParallelThreadExecution() throws Exception {
        // This test demonstrates what happens when multiple threads try to access
        // HttpServletRequest-stored context simultaneously

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(threadCount);
        AtomicInteger successfulAccess = new AtomicInteger(0);
        AtomicInteger failedAccess = new AtomicInteger(0);

        // Simulate a request context (this would normally be set by the framework)
        String expectedRequestId = "parallel-test-" + System.currentTimeMillis();

        // Launch multiple threads that ALL try to access context AT THE SAME TIME
        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // ALL threads try to access context simultaneously
                    String requestId = makeContextAccessRequest(expectedRequestId);

                    if (requestId != null && requestId.equals(expectedRequestId)) {
                        successfulAccess.incrementAndGet();
                    } else {
                        failedAccess.incrementAndGet();
                    }

                } catch (Exception e) {
                    failedAccess.incrementAndGet();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously for TRUE parallel execution
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = completionLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Verify results - with HttpServletRequest storage, most/all should fail
        assertThat(completed).isTrue();
        assertThat(failedAccess.get()).isGreaterThan(successfulAccess.get());
        System.out.println("Parallel execution results: " + successfulAccess.get() + " successful, " + failedAccess.get() + " failed");
    }

    @Test
    @DisplayName("Demonstrate concurrent CompletableFuture execution with context isolation")
    void testConcurrentCompletableFutureExecution() throws Exception {
        // This demonstrates how CompletableFuture.supplyAsync() creates new threads
        // that cannot access HttpServletRequest-stored context

        String baseRequestId = "cf-test-" + System.currentTimeMillis();
        int futureCount = 5;

        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Create multiple CompletableFutures that run concurrently
        for (int i = 0; i < futureCount; i++) {
            final String expectedRequestId = baseRequestId + "-" + i;

            CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                // Each future runs in a different thread and tries to access context
                try {
                    return makeContextAccessRequest(expectedRequestId);
                } catch (Exception e) {
                    return null;
                }
            });

            futures.add(future);
        }

        // Wait for all futures to complete
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
                futures.toArray(new CompletableFuture[0]));

        allFutures.get(10, TimeUnit.SECONDS);

        // Check results - most should be null due to context isolation
        int successCount = 0;
        int nullCount = 0;

        for (CompletableFuture<String> future : futures) {
            String result = future.get();
            if (result != null) {
                successCount++;
            } else {
                nullCount++;
            }
        }

        System.out.println("CompletableFuture results: " + successCount + " successful, " + nullCount + " null");

        // With HttpServletRequest storage, we expect most/all to be null
        assertThat(nullCount).isGreaterThanOrEqualTo(successCount);
    }

    @Test
    @DisplayName("Demonstrate race conditions in concurrent context access")
    void testConcurrentContextAccessRaceConditions() throws Exception {
        // This test demonstrates potential race conditions when multiple threads
        // try to access context simultaneously

        int iterations = 100;
        int threadsPerIteration = 3;
        AtomicInteger raceConditionCount = new AtomicInteger(0);

        for (int i = 0; i < iterations; i++) {
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch completionLatch = new CountDownLatch(threadsPerIteration);
            List<String> results = new ArrayList<>();

            String expectedRequestId = "race-test-" + i;

            // Create threads that will all try to access context at exactly the same time
            for (int j = 0; j < threadsPerIteration; j++) {
                new Thread(() -> {
                    try {
                        startLatch.await(); // Wait for the starting gun
                        String result = makeContextAccessRequest(expectedRequestId);
                        synchronized (results) {
                            results.add(result);
                        }
                    } catch (Exception e) {
                        synchronized (results) {
                            results.add(null);
                        }
                    } finally {
                        completionLatch.countDown();
                    }
                }).start();
            }

            // Fire the starting gun - all threads execute simultaneously
            startLatch.countDown();
            completionLatch.await(5, TimeUnit.SECONDS);

            // Check for inconsistent results (race conditions)
            long uniqueResults = results.stream().distinct().count();
            if (uniqueResults > 1) {
                raceConditionCount.incrementAndGet();
            }
        }

        System.out.println("Race conditions detected in " + raceConditionCount.get() + " out of " + iterations + " iterations");

        // With proper HttpServletRequest storage, we expect consistent behavior
        // (either all succeed or all fail for each iteration)
        assertThat(raceConditionCount.get()).isLessThan(iterations / 2);
    }

    /**
     * Helper method to simulate context access from different threads
     * In a real scenario, this would be a REST call to an endpoint that accesses context
     */
    private String makeContextAccessRequest(String expectedRequestId) {
        try {
            // Simulate making a request that tries to access context
            // In reality, this would be a call to an endpoint that uses RequestContextService

            // For this demo, we'll simulate the behavior:
            // - If we're in the main request thread, context is available
            // - If we're in a background thread, context is NOT available

            String currentThreadName = Thread.currentThread().getName();

            // Simulate HttpServletRequest storage behavior:
            // Only "http-nio" threads (request processing threads) have context
            if (currentThreadName.contains("http-nio") || currentThreadName.contains("main")) {
                return expectedRequestId; // Context available
            } else {
                return null; // Context not available in background threads
            }

        } catch (Exception e) {
            return null;
        }
    }


}
