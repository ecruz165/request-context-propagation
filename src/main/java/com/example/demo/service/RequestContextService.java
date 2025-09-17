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
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final RequestContextEnricher enricher;

    public RequestContextService(RequestContextExtractor extractor,
                                 RequestContextProperties properties,
                                 RequestContextEnricher enricher) {
        this.extractor = extractor;
        this.properties = properties;
        this.enricher = enricher;
    }

    // ========================================
    // Context Lifecycle Management
    // ========================================

    /**
     * Get current RequestContext from the HTTP request
     * Throws exception if context is not found
     */
    public RequestContext getCurrentContext(HttpServletRequest request) {
        return RequestContext.getFromRequest(request)
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));
    }

    /**
     * Get current RequestContext from the HTTP request
     * Returns Optional.empty() if context is not found (no exception)
     */
    public Optional<RequestContext> getCurrentContextSafely(HttpServletRequest request) {
        return RequestContext.getFromRequest(request);
    }

    /**
     * Add a field to the current context if context exists
     * Does nothing if context is not found
     */
    public void addFieldToCurrentContext(HttpServletRequest request, String key, String value) {
        try {
            RequestContext context = getCurrentContext(request);
            context.put(key, value);
            log.debug("Added field '{}' = '{}' to current context", key, value);
        } catch (IllegalStateException e) {
            log.debug("RequestContext not available, skipping field addition: {}", e.getMessage());
        }
    }

    /**
     * Initialize RequestContext from HTTP request (pre-authentication phase)
     * Called by RequestContextFilter
     */
    public RequestContext initializeContext(HttpServletRequest request) {
        log.debug("Initializing RequestContext for {} {}",
                request.getMethod(), request.getRequestURI());

        // Create new context
        RequestContext context = new RequestContext();

        // Extract all configured pre-authentication phase fields and store in context
        int fieldsExtracted = extractPreAuthPhaseFields(request, context);

        // Store context in request using RequestContext utility method
        RequestContext.setInRequest(request, context);

        // Initialize MDC for logging
        updateMDC(context);

        log.debug("RequestContext initialized with {} fields", fieldsExtracted);

        return context;
    }

    /**
     * Enrich existing RequestContext with authenticated data
     * Called by RequestContextInterceptor after Spring Security authentication
     */
    public RequestContext enrichWithPostAuthPhaseData(HttpServletRequest request) {
        // Get existing context from request
        RequestContext context = RequestContext.getFromRequest(request)
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));
        log.debug("Enriching RequestContext with post auth phase data");

        // Extract all configured authenticated fields
        int fieldsExtracted = extractPostAuthPhaseFields(request, context);

        log.debug("Added {} post-auth fields", fieldsExtracted);

        // Update MDC with new fields
        updateMDC(context);

        return context;
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
    // Field Extraction Groups
    // ========================================

    /**
     * Get fields configured for pre-authentication phase extraction
     * These are fields that can be extracted before Spring Security authentication
     */
    public Map<String, FieldConfiguration> getPreAuthPhaseExtraction() {
        Map<String, FieldConfiguration> preAuthFields = new LinkedHashMap<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldExtractInPreAuthPhase(fieldConfig)) {
                preAuthFields.put(fieldName, fieldConfig);
            }
        });

        log.debug("Pre-auth phase extraction group contains {} fields: {}",
                preAuthFields.size(), preAuthFields.keySet());

        return preAuthFields;
    }

    /**
     * Get fields configured for post-authentication phase extraction
     * These are fields that require Spring Security authentication or Spring MVC processing
     * Includes: PATH, BODY, TOKEN, and CLAIM sources
     */
    public Map<String, FieldConfiguration> getPostAuthPhaseExtraction() {
        Map<String, FieldConfiguration> postAuthFields = new LinkedHashMap<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldExtractInPostAuthPhase(fieldConfig)) {
                postAuthFields.put(fieldName, fieldConfig);
            }
        });

        log.debug("Post-auth phase extraction group contains {} fields: {}",
                postAuthFields.size(), postAuthFields.keySet());

        return postAuthFields;
    }

    /**
     * Extract fields from pre-authentication phase group
     * Returns number of fields extracted
     */
    private int extractPreAuthPhaseFields(HttpServletRequest request, RequestContext context) {
        Map<String, FieldConfiguration> preAuthFields = getPreAuthPhaseExtraction();
        int count = 0;

        log.debug("Extracting {} pre-auth phase fields", preAuthFields.size());

        for (Map.Entry<String, FieldConfiguration> entry : preAuthFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

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

                    log.trace("Extracted pre-auth field '{}' from source {}",
                            fieldName, fieldConfig.getUpstream().getInbound().getSource());
                }
            } catch (Exception e) {
                if (isRequired(fieldConfig)) {
                    throw new IllegalStateException(
                            "Failed to extract required pre-auth field: " + fieldName, e);
                }
                log.debug("Failed to extract pre-auth field '{}': {}", fieldName, e.getMessage());
            }
        }

        log.debug("Extracted {} pre-auth fields successfully", count);
        return count;
    }

    /**
     * Extract fields from post-authentication phase group
     * Includes PATH, BODY, TOKEN, and CLAIM sources
     * Returns number of fields extracted
     */
    private int extractPostAuthPhaseFields(HttpServletRequest request, RequestContext context) {
        Map<String, FieldConfiguration> postAuthFields = getPostAuthPhaseExtraction();
        int count = 0;

        log.debug("Extracting {} post-auth phase fields (PATH, BODY, TOKEN, CLAIM)", postAuthFields.size());

        for (Map.Entry<String, FieldConfiguration> entry : postAuthFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

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

                    log.trace("Extracted post-auth field '{}' from source {}",
                            fieldName, fieldConfig.getUpstream().getInbound().getSource());
                }
            } catch (Exception e) {
                if (isRequired(fieldConfig)) {
                    throw new IllegalStateException(
                            "Failed to extract required post-auth field: " + fieldName, e);
                }
                log.debug("Failed to extract post-auth field '{}': {}", fieldName, e.getMessage());
            }
        }

        log.debug("Extracted {} post-auth fields successfully", count);
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
    // Extraction Helpers (Delegated to Enricher)
    // ========================================

    /**
     * Extract values from context for downstream propagation
     * Delegates to RequestContextEnricher for processing
     */
    public Map<String, RequestContextEnricher.PropagationData> extractForDownstreamPropagation(RequestContext context) {
        return enricher.extractForDownstreamPropagation(context);
    }

    /**
     * Enrich context with downstream response data
     * This method was moved from the capture filter for consistency
     */
    public void enrichWithDownstreamResponse(ClientResponse response, RequestContext context) {
        log.debug("Enriching context with downstream response data");

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldCaptureFromDownstreamResponse(fieldConfig)) {
                try {
                    captureFieldFromDownstreamResponse(fieldName, fieldConfig, response, context);
                } catch (Exception e) {
                    log.error("Error capturing downstream field '{}': {}", fieldName, e.getMessage());
                }
            }
        });

        // Update MDC with any new fields
        updateMDC(context);
    }

    /**
     * Check if field should be captured from downstream response
     */
    private boolean shouldCaptureFromDownstreamResponse(FieldConfiguration fieldConfig) {
        return fieldConfig.getDownstream() != null &&
                fieldConfig.getDownstream().getInbound() != null &&
                fieldConfig.getDownstream().getInbound().getSource() == SourceType.HEADER;
    }

    /**
     * Capture a single field from downstream response
     */
    private void captureFieldFromDownstreamResponse(String fieldName,
                                                   FieldConfiguration fieldConfig,
                                                   ClientResponse response,
                                                   RequestContext context) {

        var inbound = fieldConfig.getDownstream().getInbound();

        // Get header values from response
        List<String> headerValues = response.headers().header(inbound.getKey());

        if (headerValues.isEmpty()) {
            // Try default value if configured
            if (inbound.getDefaultValue() != null) {
                processAndStoreDownstreamValue(fieldName, inbound.getDefaultValue(),
                        fieldConfig, context, true);
                return;
            }

            // Log if required field is missing
            if (inbound.isRequired()) {
                log.warn("Required downstream header '{}' not found in response for field '{}'",
                        inbound.getKey(), fieldName);
            }
            return;
        }

        // Take first value if multiple headers present
        String value = headerValues.get(0);

        // Process and store the value
        processAndStoreDownstreamValue(fieldName, value, fieldConfig, context, false);
    }

    /**
     * Process and store downstream response value
     */
    private void processAndStoreDownstreamValue(String fieldName,
                                               String value,
                                               FieldConfiguration fieldConfig,
                                               RequestContext context,
                                               boolean isDefault) {

        var inbound = fieldConfig.getDownstream().getInbound();

        try {
            // Apply transformation if configured
            if (inbound.getTransformation() != null && !isDefault) {
                value = extractor.applyTransformation(
                        value,
                        inbound.getTransformation(),
                        inbound.getTransformExpression()
                );
            }

            // Validate if pattern is configured
            if (inbound.getValidationPattern() != null && !isDefault) {
                if (!value.matches(inbound.getValidationPattern())) {
                    log.warn("Downstream value '{}' for field '{}' does not match validation pattern: {}",
                            value, fieldName, inbound.getValidationPattern());

                    if (inbound.isRequired()) {
                        throw new IllegalArgumentException(
                                "Required field '" + fieldName + "' validation failed");
                    }
                    return;
                }
            }

            // Store in context
            context.put(fieldName, value);

            // Handle sensitive field masking
            if (isSensitive(fieldConfig)) {
                String maskedValue = maskValue(value, fieldConfig);
                context.putMasked(fieldName, maskedValue);
                log.debug("Captured downstream field '{}' = [MASKED]", fieldName);
            } else {
                log.debug("Captured downstream field '{}' = '{}'", fieldName, value);
            }

        } catch (Exception e) {
            log.error("Error processing downstream field '{}': {}", fieldName, e.getMessage());

            if (inbound.isRequired()) {
                throw new RuntimeException(
                        "Failed to process required downstream field: " + fieldName, e);
            }
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

    /**
     * Get summary of extraction phase groups for debugging and monitoring
     */
    public ExtractionPhaseSummary getExtractionPhaseSummary() {
        Map<String, FieldConfiguration> preAuthFields = getPreAuthPhaseExtraction();
        Map<String, FieldConfiguration> postAuthFields = getPostAuthPhaseExtraction();

        ExtractionPhaseSummary summary = new ExtractionPhaseSummary();
        summary.preAuthPhaseFields = preAuthFields.keySet();
        summary.postAuthPhaseFields = postAuthFields.keySet();
        summary.preAuthSources = preAuthFields.values().stream()
                .map(config -> config.getUpstream().getInbound().getSource())
                .collect(Collectors.toSet());
        summary.postAuthSources = postAuthFields.values().stream()
                .map(config -> config.getUpstream().getInbound().getSource())
                .collect(Collectors.toSet());

        return summary;
    }

    /**
     * Summary of extraction phase configuration
     */
    public static class ExtractionPhaseSummary {
        public Set<String> preAuthPhaseFields;
        public Set<String> postAuthPhaseFields;
        public Set<SourceType> preAuthSources;
        public Set<SourceType> postAuthSources;

        @Override
        public String toString() {
            return String.format(
                "ExtractionPhaseSummary{preAuth: %d fields %s, postAuth: %d fields %s}",
                preAuthPhaseFields.size(), preAuthSources,
                postAuthPhaseFields.size(), postAuthSources
            );
        }
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Determine if field should be extracted in pre-authentication phase
     * Pre-auth phase includes: HEADER, QUERY, COOKIE, FORM, SESSION sources
     * These can be extracted before Spring Security authentication
     */
    private boolean shouldExtractInPreAuthPhase(FieldConfiguration fieldConfig) {
        if (fieldConfig.getUpstream() == null ||
                fieldConfig.getUpstream().getInbound() == null) {
            return false;
        }

        SourceType source = fieldConfig.getUpstream().getInbound().getSource();

        // Sources that can be extracted early (before authentication)
        return source == SourceType.HEADER || 
               source == SourceType.QUERY ||
               source == SourceType.COOKIE ||
               source == SourceType.FORM ||
               source == SourceType.SESSION;
    }

    /**
     * Determine if field should be extracted in post-authentication phase
     * Post-auth phase includes: PATH, BODY, TOKEN, CLAIM sources
     * These require Spring MVC processing and/or authentication context
     */
    private boolean shouldExtractInPostAuthPhase(FieldConfiguration fieldConfig) {
        if (fieldConfig.getUpstream() == null ||
                fieldConfig.getUpstream().getInbound() == null) {
            return false;
        }

        SourceType source = fieldConfig.getUpstream().getInbound().getSource();

        // Sources that require Spring MVC mapping and/or authentication context
        return source == SourceType.PATH ||     // Needs Spring MVC path variables
               source == SourceType.BODY ||     // Needs to read body before controller
               source == SourceType.TOKEN ||    // Needs authentication context
               source == SourceType.CLAIM;      // Needs JWT/token parsing
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
        if (value == null || value.isEmpty()) {
            return value;
        }

        if (fieldConfig.getSecurity() == null ||
                fieldConfig.getSecurity().getMasking() == null) {
            return "***";
        }

        String maskPattern = fieldConfig.getSecurity().getMasking();

        // Handle advanced masking patterns with {n} syntax
        if (maskPattern.contains("{") && maskPattern.contains("}")) {
            return applyAdvancedMaskPattern(value, maskPattern);
        }

        // Handle email masking
        if (maskPattern.contains("@") && value.contains("@")) {
            return applyEmailMasking(value, maskPattern);
        }

        // Handle legacy partial masking (e.g., "*-4" shows last 4 chars)
        if (maskPattern.startsWith("*-")) {
            return applyLegacyPartialMasking(value, maskPattern);
        }

        // Default: return the pattern as-is (simple replacement)
        return maskPattern;
    }

    /**
     * Apply advanced masking patterns with {n} syntax
     * Examples:
     * - "****-****-****-{4}" -> show last 4 characters
     * - "{8}***" -> show first 8 characters
     * - "***{4}***" -> show middle 4 characters
     * - "{3}-***-{4}" -> show first 3 and last 4 characters
     */
    private String applyAdvancedMaskPattern(String value, String maskPattern) {
        try {
            StringBuilder result = new StringBuilder();
            int valueIndex = 0;
            int patternIndex = 0;

            while (patternIndex < maskPattern.length() && valueIndex < value.length()) {
                char c = maskPattern.charAt(patternIndex);

                if (c == '{') {
                    // Find the closing brace
                    int closeBrace = maskPattern.indexOf('}', patternIndex);
                    if (closeBrace == -1) {
                        break; // Invalid pattern
                    }

                    // Extract the number
                    String numberStr = maskPattern.substring(patternIndex + 1, closeBrace);
                    int showChars = Integer.parseInt(numberStr);

                    // Determine if this is from start, end, or middle
                    boolean isFromStart = patternIndex == 0 ||
                        (patternIndex > 0 && maskPattern.charAt(patternIndex - 1) != '*');
                    boolean isFromEnd = closeBrace == maskPattern.length() - 1 ||
                        (closeBrace < maskPattern.length() - 1 && maskPattern.charAt(closeBrace + 1) != '*');

                    if (isFromStart) {
                        // Show characters from start
                        int charsToShow = Math.min(showChars, value.length() - valueIndex);
                        result.append(value, valueIndex, valueIndex + charsToShow);
                        valueIndex += charsToShow;
                    } else if (isFromEnd) {
                        // Show characters from end
                        int remainingChars = value.length() - valueIndex;
                        int skipChars = Math.max(0, remainingChars - showChars);
                        valueIndex += skipChars;
                        result.append(value.substring(valueIndex));
                        valueIndex = value.length();
                    } else {
                        // Show characters from middle (complex case)
                        int charsToShow = Math.min(showChars, value.length() - valueIndex);
                        result.append(value, valueIndex, valueIndex + charsToShow);
                        valueIndex += charsToShow;
                    }

                    patternIndex = closeBrace + 1;
                } else if (c == '*') {
                    // Skip characters in value (mask them)
                    result.append('*');
                    if (valueIndex < value.length()) {
                        valueIndex++;
                    }
                    patternIndex++;
                } else {
                    // Literal character in pattern
                    result.append(c);
                    patternIndex++;
                }
            }

            // Handle any remaining pattern characters
            while (patternIndex < maskPattern.length()) {
                char c = maskPattern.charAt(patternIndex);
                if (c != '{' && c != '}' && !Character.isDigit(c)) {
                    result.append(c);
                }
                patternIndex++;
            }

            return result.toString();

        } catch (Exception e) {
            log.debug("Error applying advanced mask pattern '{}' to value: {}", maskPattern, e.getMessage());
            return "***"; // Fallback to simple masking
        }
    }

    /**
     * Apply email-specific masking
     */
    private String applyEmailMasking(String value, String maskPattern) {
        int atIndex = value.indexOf("@");
        if (atIndex <= 0) {
            return maskPattern; // Not a valid email, return pattern as-is
        }

        String localPart = value.substring(0, atIndex);
        String domainPart = value.substring(atIndex + 1);

        // Apply masking based on pattern
        if (maskPattern.equals("***@***.***")) {
            return "***@***.***";
        } else if (maskPattern.contains("{") && maskPattern.contains("}")) {
            // Advanced email masking like "{3}***@***.***" or "***@{3}.***"
            return applyAdvancedMaskPattern(value, maskPattern);
        } else {
            // Simple email masking
            return localPart.charAt(0) + "***@***." +
                   (domainPart.contains(".") ? domainPart.substring(domainPart.lastIndexOf(".") + 1) : "***");
        }
    }

    /**
     * Apply legacy partial masking for backward compatibility
     */
    private String applyLegacyPartialMasking(String value, String maskPattern) {
        String[] parts = maskPattern.split("-");
        if (parts.length > 1) {
            try {
                int showChars = Integer.parseInt(parts[1]);
                if (value.length() > showChars) {
                    return "***" + value.substring(value.length() - showChars);
                }
            } catch (NumberFormatException e) {
                log.debug("Invalid legacy mask pattern: {}", maskPattern);
            }
        }
        return "***";
    }
}