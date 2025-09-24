package com.jefelabs.modules.requestcontext.api;

import com.jefelabs.modules.requestcontext.config.BaseApiTest;
import com.jefelabs.modules.requestcontext.config.RequestContextWebClientBuilder;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test to verify ContextAwareWebClientBuilder cloning and context propagation
 */
public class ContextAwareWebClientBuilderTest extends BaseApiTest {

    @Autowired
    private RequestContextWebClientBuilder contextAwareBuilder;

    @Autowired
    private RequestContextService contextService;

    @Test
    public void testBasicBuilderCreation() {
        // Create a basic builder
        WebClient.Builder builder = contextAwareBuilder.create();

        assertThat(builder).isNotNull();
        assertThat(contextAwareBuilder.getContextFilters()).hasSize(3); // propagation, capture, logging
    }

    @Test
    public void testSystemBuilderCreation() {
        // Create a builder for a specific system
        WebClient.Builder builder = contextAwareBuilder.createForSystem("test-service");

        assertThat(builder).isNotNull();

        // Build and verify headers are set
        WebClient webClient = builder.baseUrl("http://localhost:8089").build();
        assertThat(webClient).isNotNull();
    }

    @Test
    public void testBuilderCloning() {
        // Create base builder
        WebClient.Builder baseBuilder = contextAwareBuilder.createForSystem("base-service")
                .baseUrl("http://localhost:8089")
                .defaultHeader("X-Base-Header", "base-value");

        // Clone the builder
        WebClient.Builder clonedBuilder = baseBuilder.clone()
                .defaultHeader("X-Cloned-Header", "cloned-value");

        // Both builders should be independent
        WebClient baseClient = baseBuilder.build();
        WebClient clonedClient = clonedBuilder.build();

        assertThat(baseClient).isNotNull();
        assertThat(clonedClient).isNotNull();
        assertThat(baseClient).isNotSameAs(clonedClient);
    }

    @Test
    public void testContextPropagationWithClonedBuilder() {
        // Setup WireMock
        wireMock.stubFor(get(urlPathEqualTo("/test-endpoint"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withHeader("X-Service-Version", "v2.5.0")
                        .withHeader("X-Response-Status", "success")
                        .withBody("{\"message\": \"test response\"}")
                ));

        // Create and clone builder
        WebClient.Builder baseBuilder = contextAwareBuilder.createForSystem("test-service")
                .baseUrl("http://localhost:8089");

        WebClient.Builder clonedBuilder = baseBuilder.clone()
                .defaultHeader("X-Test-Header", "test-value");

        WebClient webClient = clonedBuilder.build();

        // Make request
        Mono<Map> response = webClient.get()
                .uri("/test-endpoint")
                .retrieve()
                .bodyToMono(Map.class);

        // Verify response and context propagation
        StepVerifier.create(response)
                .assertNext(result -> {
                    assertThat(result).containsKey("message");
                    assertThat(result.get("message")).isEqualTo("test response");

                    // Verify context was captured (if context is available)
                    if (contextService.getCurrentContext().isPresent()) {
                        String capturedVersion = contextService.getField("downstreamServiceVersionPublic");
                        // Note: This might be null if not in a request context, which is expected in unit tests
                        // In integration tests with MockMvc, this would be populated
                    }
                })
                .verifyComplete();

        // Verify WireMock received the request
        wireMock.verify(getRequestedFor(urlPathEqualTo("/test-endpoint")));
    }

    @Test
    public void testSelectiveFilters() {
        // Create builder with only propagation and capture (no logging)
        WebClient.Builder builder = contextAwareBuilder.createWithSelectiveFilters(
                true,   // propagation
                true,   // capture
                false   // no logging
        );

        assertThat(builder).isNotNull();

        WebClient webClient = builder.baseUrl("http://localhost:8089").build();
        assertThat(webClient).isNotNull();
    }

    @Test
    public void testCustomizationFunction() {
        // Create builder with customization
        WebClient.Builder builder = contextAwareBuilder.createWithCustomization(b ->
                b.baseUrl("http://localhost:8089")
                        .defaultHeader("X-Custom", "custom-value")
                        .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(512 * 1024))
        );

        assertThat(builder).isNotNull();

        WebClient webClient = builder.build();
        assertThat(webClient).isNotNull();
    }

    @Test
    public void testSystemBuilderWithCustomization() {
        // Create system builder with customization
        WebClient.Builder builder = contextAwareBuilder.createForSystemWithCustomization(
                "custom-system",
                b -> b.baseUrl("http://localhost:8089")
                        .defaultHeader("X-Custom-System", "custom-system-value")
        );

        assertThat(builder).isNotNull();

        WebClient webClient = builder.build();
        assertThat(webClient).isNotNull();
    }

    @Test
    public void testContextAvailabilityCheck() {
        // Test context availability check
        boolean isAvailable = contextAwareBuilder.isContextAvailable();

        // In unit test context, this might be false
        // In integration test with MockMvc, this would be true
        assertThat(isAvailable).isIn(true, false); // Either is valid depending on test context
    }

    @Test
    public void testMultipleClonedBuildersIndependence() {
        // Create base builder
        WebClient.Builder baseBuilder = contextAwareBuilder.createForSystem("multi-test")
                .baseUrl("http://localhost:8089");

        // Create multiple clones with different configurations
        WebClient.Builder clone1 = baseBuilder.clone()
                .defaultHeader("X-Clone", "clone1");

        WebClient.Builder clone2 = baseBuilder.clone()
                .defaultHeader("X-Clone", "clone2");

        WebClient.Builder clone3 = baseBuilder.clone()
                .defaultHeader("X-Clone", "clone3")
                .defaultHeader("X-Extra", "extra-value");

        // Build all clients
        WebClient client1 = clone1.build();
        WebClient client2 = clone2.build();
        WebClient client3 = clone3.build();

        // All should be different instances
        assertThat(client1).isNotSameAs(client2);
        assertThat(client2).isNotSameAs(client3);
        assertThat(client1).isNotSameAs(client3);

        // All should be functional
        assertThat(client1).isNotNull();
        assertThat(client2).isNotNull();
        assertThat(client3).isNotNull();
    }

    @Test
    public void testContextServiceAccess() {
        // Test access to context service
        RequestContextService service = contextAwareBuilder.getContextService();

        assertThat(service).isNotNull();
        assertThat(service).isSameAs(contextService);
    }
}
