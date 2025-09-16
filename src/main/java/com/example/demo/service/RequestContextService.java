package com.example.demo.service;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.CardinalityLevel;
import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.MetricsConfig;
import com.example.demo.config.props.RequestContextProperties.OutboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * High-level service for managing RequestContext lifecycle and operations.
 * Completely configuration-driven with no hardcoded field names.
 */
@Service
@Slf4j
public class RequestContextService {

    private final RequestContextExtractor extractor;
    private final RequestContextProperties properties;

    public RequestContextService(RequestContextExtractor extractor,
                                 RequestContextProperties properties) {
        this.extractor = extractor;
        this.properties = properties;
    }

    // ========================================
    // Context Lifecycle Management
    // ========================================

    /**
     * Initialize RequestContext from HTTP request (pre-authentication phase)
     * Called by RequestContextFilter
     */
    public RequestContext initializeContext(HttpServletRequest request) {
        log.debug("Initializing RequestContext for {} {}",
                request.getMethod(), request.getRequestURI());

        // Create new context
        RequestContext context = new RequestContext();

        // Extract all configured non-authenticated fields
        int fieldsExtracted = extractNonAuthenticatedFields(request, context);

        // Store in request attribute for later access
        request.setAttribute(RequestContext.REQUEST_CONTEXT_ATTRIBUTE, context);

        // Set in ThreadLocal for static access
        RequestContext.setCurrentContext(context);

        // Initialize MDC for logging
        updateMDC(context);

        log.debug("RequestContext initialized with {} fields", fieldsExtracted);

        return context;
    }

    /**
     * Enrich existing RequestContext with authenticated data
     * Called by RequestContextInterceptor after Spring Security authentication
     */
    public void enrichWithAuthenticatedData(HttpServletRequest request, RequestContext context) {
        log.debug("Enriching RequestContext with authenticated data");

        // Extract all configured authenticated fields
        int fieldsExtracted = extractAuthenticatedFields(request, context);

        log.debug("Added {} authenticated fields", fieldsExtracted);

        // Update MDC with new fields
        updateMDC(context);
    }

    /**
     * Add custom fields to context programmatically
     * Useful for adding runtime-computed values
     */
    public void addCustomField(RequestContext context, String fieldName, String value) {
        if (value != null) {
            context.put(fieldName, value);

            // Check if this field has configuration for MDC
            FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
            if (fieldConfig != null && shouldIncludeInMDC(fieldConfig)) {
                String mdcKey = getMdcKey(fieldConfig, fieldName);
                MDC.put(mdcKey, value);
            }
        }
    }

    /**
     * Clear context and MDC
     * Called by RequestContextFilter in finally block
     */
    public void clearContext() {
        clearMDC();
        RequestContext.getCurrentContext().ifPresent(RequestContext::clear);
    }

    // ========================================
    // Field Extraction
    // ========================================

    /**
     * Extract non-authenticated fields based on configuration
     * Returns number of fields extracted
     */
    private int extractNonAuthenticatedFields(HttpServletRequest request, RequestContext context) {
        int count = 0;

        for (Map.Entry<String, FieldConfiguration> entry : properties.getFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            if (shouldExtractInPreAuthPhase(fieldConfig)) {
                try {
                    String value = extractor.extractValue(fieldName, request);
                    if (value != null) {
                        context.put(fieldName, value);
                        count++;

                        // Handle sensitive masking
                        if (isSensitive(fieldConfig)) {
                            String maskedValue = maskValue(value, fieldConfig);
                            context.putMasked(fieldName, maskedValue);
                        }

                        log.trace("Extracted field '{}' from source {}",
                                fieldName, fieldConfig.getUpstream().getInbound().getSource());
                    }
                } catch (Exception e) {
                    if (isRequired(fieldConfig)) {
                        throw new IllegalStateException(
                                "Failed to extract required field: " + fieldName, e);
                    }
                    log.debug("Failed to extract field '{}': {}", fieldName, e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Extract authenticated fields based on configuration
     * Returns number of fields extracted
     */
    private int extractAuthenticatedFields(HttpServletRequest request, RequestContext context) {
        int count = 0;

        for (Map.Entry<String, FieldConfiguration> entry : properties.getFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            if (shouldExtractInPostAuthPhase(fieldConfig)) {
                try {
                    String value = extractor.extractValue(fieldName, request);
                    if (value != null) {
                        context.put(fieldName, value);
                        count++;

                        // Handle sensitive masking
                        if (isSensitive(fieldConfig)) {
                            String maskedValue = maskValue(value, fieldConfig);
                            context.putMasked(fieldName, maskedValue);
                        }

                        log.trace("Extracted authenticated field '{}' from source {}",
                                fieldName, fieldConfig.getUpstream().getInbound().getSource());
                    }
                } catch (Exception e) {
                    if (isRequired(fieldConfig)) {
                        throw new IllegalStateException(
                                "Failed to extract required authenticated field: " + fieldName, e);
                    }
                    log.debug("Failed to extract authenticated field '{}': {}",
                            fieldName, e.getMessage());
                }
            }
        }

        return count;
    }

    /**
     * Enrich context with handler information
     */
    public void enrichWithHandlerInfo(RequestContext context, String handlerMethod, String handlerClass) {
        if (handlerMethod != null) {
            context.put("handler", handlerMethod);
        }
        if (handlerClass != null) {
            context.put("handlerClass", handlerClass);
        }
    }

    // ========================================
    // MDC Management
    // ========================================

    /**
     * Update MDC with all configured fields from context
     */
    public void updateMDC(RequestContext context) {
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMDC(fieldConfig)) {
                String value = context.getMaskedOrOriginal(fieldName);
                if (value != null) {
                    String mdcKey = getMdcKey(fieldConfig, fieldName);
                    MDC.put(mdcKey, value);
                }
            }
        });
    }

    /**
     * Clear all MDC entries managed by RequestContext
     */
    public void clearMDC() {
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMDC(fieldConfig)) {
                String mdcKey = getMdcKey(fieldConfig, fieldName);
                MDC.remove(mdcKey);
            }
        });
    }

    // ========================================
    // Response Enrichment
    // ========================================

    /**
     * Enrich HTTP response headers based on configuration
     */
    public void enrichResponseHeaders(HttpServletResponse response, RequestContext context) {
        int count = 0;

        for (Map.Entry<String, FieldConfiguration> entry : properties.getFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            if (shouldEnrichUpstreamResponse(fieldConfig)) {
                OutboundConfig outbound = fieldConfig.getUpstream().getOutbound();
                String value = context.get(fieldName);

                if (value != null && outbound.getEnrichAs() == EnrichmentType.HEADER) {
                    response.setHeader(outbound.getKey(), value);
                    count++;
                    log.trace("Added response header '{}' for field '{}'",
                            outbound.getKey(), fieldName);
                }
            }
        }

        if (count > 0) {
            log.debug("Enriched response with {} headers", count);
        }
    }

    // ========================================
    // Observability Support
    // ========================================

    /**
     * Get fields configured for metrics with specific cardinality
     */
    public Map<String, String> getMetricsFields(RequestContext context, CardinalityLevel level) {
        Map<String, String> metricsFields = new LinkedHashMap<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMetrics(fieldConfig, level)) {
                String value = context.getMaskedOrOriginal(fieldName);
                if (value != null) {
                    metricsFields.put(fieldName, value);
                }
            }
        });

        return metricsFields;
    }

    /**
     * Get all fields configured for metrics (all cardinality levels)
     */
    public Map<String, String> getAllMetricsFields(RequestContext context) {
        Map<String, String> allFields = new LinkedHashMap<>();

        // Add fields in order of cardinality (low to high)
        allFields.putAll(getMetricsFields(context, CardinalityLevel.LOW));
        allFields.putAll(getMetricsFields(context, CardinalityLevel.MEDIUM));
        allFields.putAll(getMetricsFields(context, CardinalityLevel.HIGH));

        return allFields;
    }

    /**
     * Get fields configured for tracing
     */
    public Map<String, String> getTracingFields(RequestContext context) {
        Map<String, String> tracingFields = new LinkedHashMap<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInTracing(fieldConfig)) {
                String value = context.getMaskedOrOriginal(fieldName);
                if (value != null) {
                    String tagName = getTraceTagName(fieldConfig, fieldName);
                    tracingFields.put(tagName, value);
                }
            }
        });

        return tracingFields;
    }

    // ========================================
    // Validation and Utilities
    // ========================================

    /**
     * Validate that all required fields are present
     *
     * @throws IllegalStateException if required fields are missing
     */
    public void validateRequiredFields(RequestContext context) {
        List<String> missingFields = new ArrayList<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (isRequired(fieldConfig)) {
                String value = context.get(fieldName);
                if (value == null || value.isEmpty()) {
                    missingFields.add(fieldName);
                }
            }
        });

        if (!missingFields.isEmpty()) {
            String message = "Missing required fields: " + String.join(", ", missingFields);
            log.error(message);
            throw new IllegalStateException(message);
        }
    }

    /**
     * Get a summary of context for logging
     * Includes only low cardinality fields to avoid log explosion
     */
    public String getContextSummary(RequestContext context) {
        Map<String, String> summaryFields = getMetricsFields(context, CardinalityLevel.LOW);

        return summaryFields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining(", "));
    }

    /**
     * Get all configured field names
     */
    public Set<String> getConfiguredFields() {
        return properties.getFields().keySet();
    }

    /**
     * Check if a field is configured
     */
    public boolean isFieldConfigured(String fieldName) {
        return properties.getFields().containsKey(fieldName);
    }

    /**
     * Extract a single field value on demand
     * Useful for lazy extraction or re-extraction
     */
    public String extractField(String fieldName, HttpServletRequest request) {
        if (!isFieldConfigured(fieldName)) {
            log.warn("Attempted to extract unconfigured field: {}", fieldName);
            return null;
        }
        return extractor.extractValue(fieldName, request);
    }

    // ========================================
    // Helper Methods
    // ========================================

    private boolean shouldExtractInPreAuthPhase(FieldConfiguration fieldConfig) {
        if (fieldConfig.getUpstream() == null ||
                fieldConfig.getUpstream().getInbound() == null) {
            return false;
        }

        SourceType source = fieldConfig.getUpstream().getInbound().getSource();
        // TOKEN and CLAIM sources require authentication
        return source != SourceType.TOKEN && source != SourceType.CLAIM;
    }

    private boolean shouldExtractInPostAuthPhase(FieldConfiguration fieldConfig) {
        if (fieldConfig.getUpstream() == null ||
                fieldConfig.getUpstream().getInbound() == null) {
            return false;
        }

        SourceType source = fieldConfig.getUpstream().getInbound().getSource();
        // Only TOKEN and CLAIM sources require authentication
        return source == SourceType.TOKEN || source == SourceType.CLAIM;
    }

    private boolean shouldIncludeInMDC(FieldConfiguration fieldConfig) {
        return fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getLogging() != null &&
                fieldConfig.getObservability().getLogging().isEnabled();
    }

    private boolean shouldIncludeInMetrics(FieldConfiguration fieldConfig,
                                           CardinalityLevel level) {
        if (fieldConfig.getObservability() == null ||
                fieldConfig.getObservability().getMetrics() == null) {
            return false;
        }

        MetricsConfig metricsConfig = fieldConfig.getObservability().getMetrics();
        return metricsConfig.isEnabled() && metricsConfig.getCardinality() == level;
    }

    private boolean shouldIncludeInTracing(FieldConfiguration fieldConfig) {
        return fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getTracing() != null &&
                fieldConfig.getObservability().getTracing().isEnabled();
    }

    private boolean shouldEnrichUpstreamResponse(FieldConfiguration fieldConfig) {
        return fieldConfig.getUpstream() != null &&
                fieldConfig.getUpstream().getOutbound() != null;
    }

    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null &&
                fieldConfig.getSecurity().isSensitive();
    }

    private boolean isRequired(FieldConfiguration fieldConfig) {
        return fieldConfig.getUpstream() != null &&
                fieldConfig.getUpstream().getInbound() != null &&
                fieldConfig.getUpstream().getInbound().isRequired();
    }

    private String getMdcKey(FieldConfiguration fieldConfig, String fieldName) {
        if (fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getLogging() != null &&
                fieldConfig.getObservability().getLogging().getMdcKey() != null) {
            return fieldConfig.getObservability().getLogging().getMdcKey();
        }
        return fieldName;
    }

    private String getTraceTagName(FieldConfiguration fieldConfig, String fieldName) {
        if (fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getTracing() != null &&
                fieldConfig.getObservability().getTracing().getTagName() != null) {
            return fieldConfig.getObservability().getTracing().getTagName();
        }
        return fieldName;
    }

    private String maskValue(String value, FieldConfiguration fieldConfig) {
        if (fieldConfig.getSecurity() == null ||
                fieldConfig.getSecurity().getMasking() == null) {
            return "***";
        }

        String maskPattern = fieldConfig.getSecurity().getMasking();

        // Handle email masking
        if (maskPattern.contains("@") && value.contains("@")) {
            int atIndex = value.indexOf("@");
            if (atIndex > 0) {
                return "***@***.***";
            }
        }

        // Handle partial masking (e.g., "*-4" shows last 4 chars)
        if (maskPattern.startsWith("*-")) {
            String[] parts = maskPattern.split("-");
            if (parts.length > 1) {
                try {
                    int showChars = Integer.parseInt(parts[1]);
                    if (value.length() > showChars) {
                        return "***" + value.substring(value.length() - showChars);
                    }
                } catch (NumberFormatException e) {
                    log.debug("Invalid mask pattern: {}", maskPattern);
                }
            }
        }

        return maskPattern;
    }
}