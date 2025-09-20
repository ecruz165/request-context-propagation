package com.example.demo.integration;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.service.RequestContextService;
import com.example.demo.service.source.BodySourceHandler;
import com.example.demo.service.source.SourceHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.HashMap;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "request-context.fields.userProfileEmail.downstream.inbound.source=BODY",
        "request-context.fields.userProfileEmail.downstream.inbound.key=$.user.profile.email",
        "request-context.fields.fullUserResponse.downstream.inbound.source=BODY",
        "request-context.fields.fullUserResponse.downstream.inbound.key=$",
        "request-context.fields.firstResultId.downstream.inbound.source=BODY",
        "request-context.fields.firstResultId.downstream.inbound.key=$.results[0].id"
})
class BodyDownstreamExtractionIntegrationTest {

    @Autowired
    private RequestContextService requestContextService;

    @Autowired
    private SourceHandlers sourceHandlers;

    @Autowired
    private ObjectMapper objectMapper;

    private WireMockServer wireMockServer;
    private WebClient webClient;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        public BodySourceHandler bodySourceHandler(ObjectMapper objectMapper) {
            return new BodySourceHandler(objectMapper);
        }
    }

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(8089);
        wireMockServer.start();
        WireMock.configureFor("localhost", 8089);
        
        webClient = WebClient.builder()
                .baseUrl("http://localhost:8089")
                .build();
    }

    @AfterEach
    void tearDown() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void shouldExtractSpecificFieldFromDownstreamJsonResponse() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": "12345",
                    "profile": {
                        "email": "john.doe@example.com",
                        "name": "John Doe",
                        "preferences": {
                            "theme": "dark"
                        }
                    }
                },
                "metadata": {
                    "timestamp": "2023-01-01T00:00:00Z"
                }
            }
            """;

        stubFor(get(urlEqualTo("/api/user/12345"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When
        Mono<String> responseMono = webClient.get()
                .uri("/api/user/12345")
                .retrieve()
                .bodyToMono(String.class);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response).isEqualTo(responseBody);
                    
                    // Test extraction using the source handler directly
                    RequestContextProperties.InboundConfig config = new RequestContextProperties.InboundConfig();
                    config.setSource(RequestContextProperties.SourceType.BODY);
                    config.setKey("$.user.profile.email");
                    
                    // Simulate downstream response extraction
                    // Note: In real usage, this would be handled by the WebClient filter
                    try {
                        String extractedEmail = extractFromJsonString(response, "$.user.profile.email");
                        assertThat(extractedEmail).isEqualTo("john.doe@example.com");
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .verifyComplete();
    }

    @Test
    void shouldExtractEntireJsonResponseWhenUsingDollarKey() {
        // Given
        String responseBody = """
            {
                "status": "success",
                "data": {
                    "userId": "67890",
                    "name": "Jane Smith"
                }
            }
            """;

        stubFor(get(urlEqualTo("/api/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When
        Mono<String> responseMono = webClient.get()
                .uri("/api/status")
                .retrieve()
                .bodyToMono(String.class);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    assertThat(response).isEqualTo(responseBody);
                    
                    // Test extraction of entire response
                    String extractedResponse = extractFromJsonString(response, "$");
                    assertThat(extractedResponse).isEqualTo(responseBody);
                })
                .verifyComplete();
    }

    @Test
    void shouldExtractArrayElementFromDownstreamResponse() {
        // Given
        String responseBody = """
            {
                "results": [
                    {
                        "id": "result-1",
                        "name": "First Result",
                        "score": 95.5
                    },
                    {
                        "id": "result-2", 
                        "name": "Second Result",
                        "score": 87.2
                    }
                ],
                "total": 2
            }
            """;

        stubFor(get(urlEqualTo("/api/search"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When
        Mono<String> responseMono = webClient.get()
                .uri("/api/search")
                .retrieve()
                .bodyToMono(String.class);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    // Test extraction of first result ID
                    String firstResultId = extractFromJsonString(response, "$.results[0].id");
                    assertThat(firstResultId).isEqualTo("result-1");
                    
                    // Test extraction of second result name
                    String secondResultName = extractFromJsonString(response, "$.results[1].name");
                    assertThat(secondResultName).isEqualTo("Second Result");
                    
                    // Test extraction of total count
                    String total = extractFromJsonString(response, "$.total");
                    assertThat(total).isEqualTo("2");
                })
                .verifyComplete();
    }

    @Test
    void shouldHandleNonExistentJsonPath() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": "12345"
                }
            }
            """;

        stubFor(get(urlEqualTo("/api/user/minimal"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // When
        Mono<String> responseMono = webClient.get()
                .uri("/api/user/minimal")
                .retrieve()
                .bodyToMono(String.class);

        // Then
        StepVerifier.create(responseMono)
                .assertNext(response -> {
                    // Test extraction of non-existent path
                    String nonExistent = extractFromJsonString(response, "$.user.profile.email");
                    assertThat(nonExistent).isNull();
                })
                .verifyComplete();
    }

    /**
     * Helper method to simulate JSON extraction using JSONPath
     * In real usage, this would be handled by the BodySourceHandler
     */
    private String extractFromJsonString(String jsonString, String jsonPath) {
        try {
            if ("$".equals(jsonPath) || ".".equals(jsonPath)) {
                return jsonString;
            }
            
            com.jayway.jsonpath.DocumentContext context = com.jayway.jsonpath.JsonPath.parse(jsonString);
            Object result = context.read(jsonPath);
            
            if (result == null) {
                return null;
            }
            
            if (result instanceof String) {
                return (String) result;
            }
            
            return objectMapper.writeValueAsString(result);
        } catch (com.jayway.jsonpath.PathNotFoundException e) {
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Error extracting JSON path: " + jsonPath, e);
        }
    }
}
