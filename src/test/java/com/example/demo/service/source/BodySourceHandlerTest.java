package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BodySourceHandlerTest {

    private BodySourceHandler bodySourceHandler;
    private ObjectMapper objectMapper;

    @Mock
    private ClientResponse clientResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        bodySourceHandler = new BodySourceHandler(objectMapper);
    }

    @Test
    void sourceType_shouldReturnBody() {
        assertThat(bodySourceHandler.sourceType()).isEqualTo(SourceType.BODY);
    }

    @Test
    void extractFromUpstreamRequestBody_shouldReturnNull() {
        // Given
        InboundConfig config = createInboundConfig("$.user.id");

        // When
        String result = bodySourceHandler.extractFromUpstreamRequestBody("any input", config);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void extractFromDownstreamResponse_withValidJsonResponse_shouldExtractValue() {
        // Given
        String responseBody = """
            {
                "data": {
                    "userId": "67890",
                    "status": "active"
                },
                "metadata": {
                    "timestamp": "2023-01-01T00:00:00Z"
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.data.userId");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo("67890");
    }

    @Test
    void extractFromDownstreamResponse_withArrayPath_shouldExtractValue() {
        // Given
        String responseBody = """
            {
                "results": [
                    {"id": "first", "name": "First Item"},
                    {"id": "second", "name": "Second Item"}
                ]
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.results[0].id");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo("first");
    }

    @Test
    void extractFromDownstreamResponse_withEntireResponseKey_shouldReturnFullResponse() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": "12345",
                    "name": "John"
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo(responseBody);
    }

    @Test
    void extractFromDownstreamResponse_withDotKey_shouldReturnFullResponse() {
        // Given
        String responseBody = """
            {"message": "success", "code": 200}
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig(".");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo(responseBody);
    }

    @Test
    void extractFromDownstreamResponse_withEmptyResponse_shouldReturnNull() {
        // Given
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(""));
        InboundConfig config = createInboundConfig("$.data.id");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void extractFromDownstreamResponse_withNullResponse_shouldReturnNull() {
        // Given
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.empty());
        InboundConfig config = createInboundConfig("$.data.id");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void extractFromDownstreamResponse_withInvalidJson_shouldReturnNullForJsonPath() {
        // Given
        String invalidJson = "not valid json";
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(invalidJson));
        InboundConfig config = createInboundConfig("$.data.id");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isNull();
    }

    @Test
    void extractFromDownstreamResponse_withInvalidJsonButEntireResponseKey_shouldReturnRawResponse() {
        // Given
        String invalidJson = "not valid json";
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(invalidJson));
        InboundConfig config = createInboundConfig("$");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo(invalidJson);
    }

    @Test
    void extractFromDownstreamResponse_withComplexObject_shouldReturnJsonString() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": "12345",
                    "preferences": {
                        "theme": "dark",
                        "notifications": true
                    }
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.user.preferences");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).contains("\"theme\":\"dark\"").contains("\"notifications\":true");
    }

    @Test
    void extractFromDownstreamResponse_withNonExistentPath_shouldReturnNull() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": "12345"
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.user.nonexistent.field");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isNull();
    }



    @Test
    void extractFromDownstreamResponse_withNumberValue_shouldReturnStringRepresentation() {
        // Given
        String responseBody = """
            {
                "user": {
                    "id": 12345,
                    "score": 98.5,
                    "active": true
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.user.score");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo("98.5");
    }

    @Test
    void extractFromDownstreamResponse_withBooleanValue_shouldReturnStringRepresentation() {
        // Given
        String responseBody = """
            {
                "user": {
                    "active": true
                }
            }
            """;
        when(clientResponse.bodyToMono(String.class)).thenReturn(Mono.just(responseBody));
        InboundConfig config = createInboundConfig("$.user.active");

        // When
        String result = bodySourceHandler.extractFromDownstreamResponse(clientResponse, config);

        // Then
        assertThat(result).isEqualTo("true");
    }

    private InboundConfig createInboundConfig(String key) {
        InboundConfig config = new InboundConfig();
        config.setSource(SourceType.BODY);
        config.setKey(key);
        return config;
    }
}
