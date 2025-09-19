package com.example.demo.service;

import com.example.demo.service.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test for programmatic field access and configuration methods in RequestContextService
 */
@SpringBootTest
@ActiveProfiles("test")
class RequestContextServiceProgrammaticTest {

    @Autowired
    private RequestContextService requestContextService;

    private MockHttpServletRequest mockRequest;

    @BeforeEach
    void setUp() {
        // Set up mock request context
        mockRequest = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(mockRequest));
        
        // Initialize a RequestContext in the mock request using service proxy
        RequestContext context = new RequestContext();
        requestContextService.setContextInRequest(mockRequest, context);
    }

    @Test
    void testProgrammaticFieldAccess() {
        // Test setting and getting field values
        boolean setResult = requestContextService.setField("customField", "customValue");
        assertThat(setResult).isTrue();

        String retrievedValue = requestContextService.getField("customField");
        assertThat(retrievedValue).isEqualTo("customValue");

        // Test checking if field exists
        boolean hasField = requestContextService.hasField("customField");
        assertThat(hasField).isTrue();

        // Test getting all fields
        Map<String, String> allFields = requestContextService.getAllFields();
        assertThat(allFields).containsEntry("customField", "customValue");

        // Test removing field
        boolean removeResult = requestContextService.removeField("customField");
        assertThat(removeResult).isTrue();

        String removedValue = requestContextService.getField("customField");
        assertThat(removedValue).isNull();

        boolean hasFieldAfterRemoval = requestContextService.hasField("customField");
        assertThat(hasFieldAfterRemoval).isFalse();
    }

    @Test
    void testAddCustomField() {
        // Test adding custom computed field
        boolean addResult = requestContextService.addCustomField("computedField", "computedValue");
        assertThat(addResult).isTrue();

        String retrievedValue = requestContextService.getField("computedField");
        assertThat(retrievedValue).isEqualTo("computedValue");

        // Test that null values are rejected
        boolean nullResult = requestContextService.addCustomField("nullField", null);
        assertThat(nullResult).isFalse();
    }

    @Test
    void testProgrammaticFieldConfiguration() {
        // Test adding a field configuration
        RequestContextProperties.FieldConfiguration fieldConfig = new RequestContextProperties.FieldConfiguration();

        // Set up observability for logging
        RequestContextProperties.ObservabilityConfig observabilityConfig =
            new RequestContextProperties.ObservabilityConfig();
        RequestContextProperties.LoggingConfig loggingConfig = new RequestContextProperties.LoggingConfig();
        loggingConfig.setMdcKey("test_field");
        observabilityConfig.setLogging(loggingConfig);
        fieldConfig.setObservability(observabilityConfig);

        boolean configResult = requestContextService.addFieldConfiguration("testField", fieldConfig);
        assertThat(configResult).isTrue();

        // Test checking if field is configured
        boolean isConfigured = requestContextService.isFieldConfigured("testField");
        assertThat(isConfigured).isTrue();

        // Test getting field configuration
        RequestContextProperties.FieldConfiguration retrievedConfig =
            requestContextService.getFieldConfiguration("testField");
        assertThat(retrievedConfig).isNotNull();
        assertThat(retrievedConfig.getObservability().getLogging().getMdcKey()).isEqualTo("test_field");

        // Test removing field configuration
        boolean removeConfigResult = requestContextService.removeFieldConfiguration("testField");
        assertThat(removeConfigResult).isTrue();

        boolean isConfiguredAfterRemoval = requestContextService.isFieldConfigured("testField");
        assertThat(isConfiguredAfterRemoval).isFalse();
    }

    @Test
    void testConvenienceConfigurationMethods() {
        // Test adding logging field
        boolean loggingResult = requestContextService.addLoggingField("logField", "log_field", false);
        assertThat(loggingResult).isTrue();

        boolean isConfigured = requestContextService.isFieldConfigured("logField");
        assertThat(isConfigured).isTrue();

        RequestContextProperties.FieldConfiguration config =
            requestContextService.getFieldConfiguration("logField");
        assertThat(config.getObservability().getLogging().getMdcKey()).isEqualTo("log_field");

        // Test adding metrics field
        boolean metricsResult = requestContextService.addMetricsField("metricField",
            RequestContextProperties.CardinalityLevel.LOW, true);
        assertThat(metricsResult).isTrue();

        boolean isMetricConfigured = requestContextService.isFieldConfigured("metricField");
        assertThat(isMetricConfigured).isTrue();

        RequestContextProperties.FieldConfiguration metricConfig =
            requestContextService.getFieldConfiguration("metricField");
        assertThat(metricConfig.getObservability().getMetrics().getCardinality())
            .isEqualTo(RequestContextProperties.CardinalityLevel.LOW);
        assertThat(metricConfig.getSecurity().isSensitive()).isTrue();
    }

    @Test
    void testGetConfiguredFieldNames() {
        // Add some field configurations
        requestContextService.addLoggingField("field1", "field_1", false);
        requestContextService.addMetricsField("field2", RequestContextProperties.CardinalityLevel.MEDIUM, false);

        var configuredFields = requestContextService.getConfiguredFieldNames();
        
        // Should include our new fields plus any existing ones from configuration
        assertThat(configuredFields).contains("field1", "field2");
    }

    @Test
    void testFieldAccessWithoutContext() {
        // Clear the request context
        RequestContextHolder.resetRequestAttributes();

        // All operations should return false/null gracefully
        String value = requestContextService.getField("anyField");
        assertThat(value).isNull();

        boolean setResult = requestContextService.setField("anyField", "anyValue");
        assertThat(setResult).isFalse();

        Map<String, String> allFields = requestContextService.getAllFields();
        assertThat(allFields).isEmpty();

        boolean hasField = requestContextService.hasField("anyField");
        assertThat(hasField).isFalse();
    }
}
