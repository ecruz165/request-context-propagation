package com.example.demo.service;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Test for RequestContextService extraction phase grouping
 */
@ExtendWith(MockitoExtension.class)
class RequestContextServiceExtractionPhasesTest {

    @Mock
    private RequestContextExtractor extractor;

    @Mock
    private RequestContextProperties properties;

    @Mock
    private RequestContextEnricher enricher;

    private RequestContextService service;

    @BeforeEach
    void setUp() {
        service = new RequestContextService(extractor, properties, enricher);
    }

    @Test
    void shouldGroupFieldsIntoPreAuthPhase() {
        // Given: Configuration with pre-auth sources
        Map<String, FieldConfiguration> fields = Map.of(
                "requestId", createFieldConfig(SourceType.HEADER),
                "sessionId", createFieldConfig(SourceType.COOKIE),
                "apiKey", createFieldConfig(SourceType.QUERY),
                "clientVersion", createFieldConfig(SourceType.FORM),
                "userSession", createFieldConfig(SourceType.SESSION)
        );
        when(properties.getFields()).thenReturn(fields);

        // When: Getting pre-auth phase extraction
        Map<String, FieldConfiguration> preAuthFields = service.getPreAuthPhaseExtraction();

        // Then: All fields should be in pre-auth phase
        assertThat(preAuthFields).hasSize(5);
        assertThat(preAuthFields.keySet()).containsExactlyInAnyOrder(
                "requestId", "sessionId", "apiKey", "clientVersion", "userSession"
        );
    }

    @Test
    void shouldGroupFieldsIntoPostAuthPhase() {
        // Given: Configuration with post-auth sources
        Map<String, FieldConfiguration> fields = Map.of(
                "userId", createFieldConfig(SourceType.CLAIM),
                "tenantId", createFieldConfig(SourceType.TOKEN),
                "resourceId", createFieldConfig(SourceType.PATH),
                "requestBody", createFieldConfig(SourceType.BODY)
        );
        when(properties.getFields()).thenReturn(fields);

        // When: Getting post-auth phase extraction
        Map<String, FieldConfiguration> postAuthFields = service.getPostAuthPhaseExtraction();

        // Then: All fields should be in post-auth phase
        assertThat(postAuthFields).hasSize(4);
        assertThat(postAuthFields.keySet()).containsExactlyInAnyOrder(
                "userId", "tenantId", "resourceId", "requestBody"
        );
    }

    @Test
    void shouldSeparateFieldsCorrectlyBetweenPhases() {
        // Given: Mixed configuration
        Map<String, FieldConfiguration> fields = Map.of(
                // Pre-auth fields
                "requestId", createFieldConfig(SourceType.HEADER),
                "sessionId", createFieldConfig(SourceType.COOKIE),
                "apiKey", createFieldConfig(SourceType.QUERY),
                "userSession", createFieldConfig(SourceType.SESSION),

                // Post-auth fields
                "userId", createFieldConfig(SourceType.CLAIM),
                "tenantId", createFieldConfig(SourceType.TOKEN),
                "resourceId", createFieldConfig(SourceType.PATH),
                "requestBody", createFieldConfig(SourceType.BODY)
        );
        when(properties.getFields()).thenReturn(fields);

        // When: Getting both phases
        Map<String, FieldConfiguration> preAuthFields = service.getPreAuthPhaseExtraction();
        Map<String, FieldConfiguration> postAuthFields = service.getPostAuthPhaseExtraction();

        // Then: Fields should be correctly separated
        assertThat(preAuthFields).hasSize(4);
        assertThat(preAuthFields.keySet()).containsExactlyInAnyOrder(
                "requestId", "sessionId", "apiKey", "userSession"
        );

        assertThat(postAuthFields).hasSize(4);
        assertThat(postAuthFields.keySet()).containsExactlyInAnyOrder(
                "userId", "tenantId", "resourceId", "requestBody"
        );

        // Verify no overlap
        Set<String> preAuthFieldNames = preAuthFields.keySet();
        Set<String> postAuthFieldNames = postAuthFields.keySet();
        assertThat(preAuthFieldNames).doesNotContainAnyElementsOf(postAuthFieldNames);
    }

    @Test
    void shouldProvideExtractionPhaseSummary() {
        // Given: Mixed configuration
        Map<String, FieldConfiguration> fields = Map.of(
                "requestId", createFieldConfig(SourceType.HEADER),
                "sessionId", createFieldConfig(SourceType.COOKIE),
                "userId", createFieldConfig(SourceType.CLAIM),
                "resourceId", createFieldConfig(SourceType.PATH)
        );
        when(properties.getFields()).thenReturn(fields);

        // When: Getting extraction phase summary
        RequestContextService.ExtractionPhaseSummary summary = service.getExtractionPhaseSummary();

        // Then: Summary should contain correct information
        assertThat(summary.preAuthPhaseFields).containsExactlyInAnyOrder("requestId", "sessionId");
        assertThat(summary.postAuthPhaseFields).containsExactlyInAnyOrder("userId", "resourceId");
        
        assertThat(summary.preAuthSources).containsExactlyInAnyOrder(
                SourceType.HEADER, SourceType.COOKIE
        );
        assertThat(summary.postAuthSources).containsExactlyInAnyOrder(
                SourceType.CLAIM, SourceType.PATH
        );

        // Verify toString works
        String summaryString = summary.toString();
        assertThat(summaryString).contains("preAuth: 2 fields");
        assertThat(summaryString).contains("postAuth: 2 fields");
    }

    @Test
    void shouldHandleEmptyConfiguration() {
        // Given: Empty configuration
        when(properties.getFields()).thenReturn(Map.of());

        // When: Getting extraction phases
        Map<String, FieldConfiguration> preAuthFields = service.getPreAuthPhaseExtraction();
        Map<String, FieldConfiguration> postAuthFields = service.getPostAuthPhaseExtraction();

        // Then: Both should be empty
        assertThat(preAuthFields).isEmpty();
        assertThat(postAuthFields).isEmpty();
    }

    @Test
    void shouldHandleFieldsWithoutUpstreamConfig() {
        // Given: Field without upstream configuration
        FieldConfiguration fieldWithoutUpstream = new FieldConfiguration();
        Map<String, FieldConfiguration> fields = Map.of(
                "invalidField", fieldWithoutUpstream,
                "validField", createFieldConfig(SourceType.HEADER)
        );
        when(properties.getFields()).thenReturn(fields);

        // When: Getting extraction phases
        Map<String, FieldConfiguration> preAuthFields = service.getPreAuthPhaseExtraction();
        Map<String, FieldConfiguration> postAuthFields = service.getPostAuthPhaseExtraction();

        // Then: Only valid field should be included
        assertThat(preAuthFields).hasSize(1);
        assertThat(preAuthFields.keySet()).containsExactly("validField");
        assertThat(postAuthFields).isEmpty();
    }

    private FieldConfiguration createFieldConfig(SourceType sourceType) {
        FieldConfiguration config = new FieldConfiguration();
        RequestContextProperties.StreamConfig upstream = new RequestContextProperties.StreamConfig();
        RequestContextProperties.InboundConfig inbound = new RequestContextProperties.InboundConfig();

        inbound.setSource(sourceType);
        inbound.setKey("test-key");
        upstream.setInbound(inbound);
        config.setUpstream(upstream);

        return config;
    }
}
