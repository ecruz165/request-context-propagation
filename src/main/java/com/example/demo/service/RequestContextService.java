package com.example.demo.service;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.CardinalityLevel;
import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.LogLevel;
import com.example.demo.config.props.RequestContextProperties.LoggingConfig;
import com.example.demo.config.props.RequestContextProperties.MetricsConfig;
import com.example.demo.config.props.RequestContextProperties.OutboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import com.example.demo.config.props.RequestContextProperties.TracingConfig;
import com.example.demo.service.source.SourceHandlers;
import com.example.demo.util.MaskingHelper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
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
    private final MaskingHelper maskingHelper;
    private final SourceHandlers sourceHandlers;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // Pre-computed maps for optimal performance (built on startup)
    // Store full FieldConfiguration objects to eliminate runtime parsing
    private final Map<String, FieldConfiguration> upstreamInboundFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> upstreamOutboundFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> downstreamInboundFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> downstreamOutboundFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> metricsLowCardinalityFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> metricsMediumCardinalityFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> metricsHighCardinalityFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> loggingFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> tracingFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> sensitiveFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> requiredFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> preAuthPhaseFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> postAuthPhaseFields = new ConcurrentHashMap<>();
    private final Map<String, FieldConfiguration> allConfiguredFields = new ConcurrentHashMap<>();

    // Pre-computed mappings for custom names/keys
    private final Map<String, String> metricsTagNames = new ConcurrentHashMap<>();
    private final Map<String, String> loggingMdcKeys = new ConcurrentHashMap<>();
    private final Map<String, String> tracingTagNames = new ConcurrentHashMap<>();
    private final Map<String, String> maskingPatterns = new ConcurrentHashMap<>();
    private final Map<String, String> upstreamOutboundKeys = new ConcurrentHashMap<>();
    private final Map<String, EnrichmentType> upstreamOutboundTypes = new ConcurrentHashMap<>();

    public RequestContextService(RequestContextExtractor extractor,
                                 RequestContextProperties properties,
                                 RequestContextEnricher enricher,
                                 MaskingHelper maskingHelper,
                                 SourceHandlers sourceHandlers) {
        this.extractor = extractor;
        this.properties = properties;
        this.enricher = enricher;
        this.maskingHelper = maskingHelper;
        this.sourceHandlers = sourceHandlers;
    }

    /**
     * Initialize pre-computed sets for optimal performance
     */
    @PostConstruct
    void initializeFieldSets() {
        log.info("Initializing RequestContext field sets for optimal performance");

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            // Process upstream configuration
            if (fieldConfig.getUpstream() != null) {
                if (fieldConfig.getUpstream().getInbound() != null) {
                    upstreamInboundFields.put(fieldName, fieldConfig);
                }
                if (fieldConfig.getUpstream().getOutbound() != null) {
                    upstreamOutboundFields.put(fieldName, fieldConfig);
                    OutboundConfig outbound = fieldConfig.getUpstream().getOutbound();
                    upstreamOutboundKeys.put(fieldName, outbound.getKey());
                    upstreamOutboundTypes.put(fieldName, outbound.getEnrichAs());
                }
            }

            // Process downstream configuration
            if (fieldConfig.getDownstream() != null) {
                if (fieldConfig.getDownstream().getInbound() != null) {
                    downstreamInboundFields.put(fieldName, fieldConfig);
                }
                if (fieldConfig.getDownstream().getOutbound() != null) {
                    downstreamOutboundFields.put(fieldName, fieldConfig);
                }
            }

            // Process observability configuration
            if (fieldConfig.getObservability() != null) {
                processMetricsConfiguration(fieldName, fieldConfig);
                processLoggingConfiguration(fieldName, fieldConfig);
                processTracingConfiguration(fieldName, fieldConfig);
            }

            // Process security configuration
            if (fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive()) {
                sensitiveFields.put(fieldName, fieldConfig);
                String maskingPattern = fieldConfig.getSecurity().getMasking();
                if (maskingPattern != null) {
                    maskingPatterns.put(fieldName, maskingPattern);
                }
            }

            // Process additional field categorizations
            if (isRequired(fieldConfig)) {
                requiredFields.put(fieldName, fieldConfig);
            }

            if (shouldExtractInPreAuthPhase(fieldConfig)) {
                preAuthPhaseFields.put(fieldName, fieldConfig);
            }

            if (shouldExtractInPostAuthPhase(fieldConfig)) {
                postAuthPhaseFields.put(fieldName, fieldConfig);
            }

            // Add to all configured fields
            allConfiguredFields.put(fieldName, fieldConfig);
        });

        log.info("Initialized field sets: {} upstream inbound, {} metrics fields, {} logging fields, {} tracing fields",
                upstreamInboundFields.size(),
                metricsLowCardinalityFields.size() + metricsMediumCardinalityFields.size() + metricsHighCardinalityFields.size(),
                loggingFields.size(),
                tracingFields.size());
    }

    private void processMetricsConfiguration(String fieldName, FieldConfiguration fieldConfig) {
        MetricsConfig metricsConfig = fieldConfig.getObservability().getMetrics();
        if (metricsConfig == null) return;

        // Check if metrics are implicitly or explicitly enabled
        boolean implicitlyEnabled = metricsConfig.getCardinality() != CardinalityLevel.NONE ||
                                   metricsConfig.getTagName() != null ||
                                   metricsConfig.getMetricName() != null ||
                                   metricsConfig.isHistogram();

        if (metricsConfig.isEnabled() || implicitlyEnabled) {
            CardinalityLevel cardinality = metricsConfig.getCardinality();
            switch (cardinality) {
                case LOW -> metricsLowCardinalityFields.put(fieldName, fieldConfig);
                case MEDIUM -> metricsMediumCardinalityFields.put(fieldName, fieldConfig);
                case HIGH -> metricsHighCardinalityFields.put(fieldName, fieldConfig);
                // NONE cardinality fields are not added to any set
            }

            // Store custom tag name if configured
            if (metricsConfig.getTagName() != null) {
                metricsTagNames.put(fieldName, metricsConfig.getTagName());
            }
        }
    }

    private void processLoggingConfiguration(String fieldName, FieldConfiguration fieldConfig) {
        LoggingConfig loggingConfig = fieldConfig.getObservability().getLogging();
        if (loggingConfig == null) return;

        // Check if logging is implicitly or explicitly enabled
        boolean implicitlyEnabled = loggingConfig.getMdcKey() != null ||
                                   loggingConfig.getLevel() != LogLevel.INFO;

        if (loggingConfig.isEnabled() || implicitlyEnabled) {
            loggingFields.put(fieldName, fieldConfig);

            // Store custom MDC key if configured
            if (loggingConfig.getMdcKey() != null) {
                loggingMdcKeys.put(fieldName, loggingConfig.getMdcKey());
            }
        }
    }

    private void processTracingConfiguration(String fieldName, FieldConfiguration fieldConfig) {
        TracingConfig tracingConfig = fieldConfig.getObservability().getTracing();
        if (tracingConfig == null) return;

        // Check if tracing is implicitly or explicitly enabled
        boolean implicitlyEnabled = tracingConfig.getTagName() != null ||
                                   tracingConfig.isUseNestedTags();

        if (tracingConfig.isEnabled() || implicitlyEnabled) {
            tracingFields.put(fieldName, fieldConfig);

            // Store custom tag name if configured
            if (tracingConfig.getTagName() != null) {
                tracingTagNames.put(fieldName, tracingConfig.getTagName());
            }
        }
    }

    // ========================================
    // Public API for Observability Components
    // ========================================

    /**
     * Check if field should be included in metrics for the specified cardinality level
     */
    public boolean isMetricsField(String fieldName, CardinalityLevel level) {
        return switch (level) {
            case LOW -> metricsLowCardinalityFields.containsKey(fieldName);
            case MEDIUM -> metricsMediumCardinalityFields.containsKey(fieldName);
            case HIGH -> metricsHighCardinalityFields.containsKey(fieldName);
            case NONE -> false;
        };
    }

    /**
     * Check if field should be included in logging/MDC
     */
    public boolean isLoggingField(String fieldName) {
        return loggingFields.containsKey(fieldName);
    }

    /**
     * Check if field should be included in tracing
     */
    public boolean isTracingField(String fieldName) {
        return tracingFields.containsKey(fieldName);
    }

    /**
     * Check if field contains sensitive data
     */
    public boolean isSensitiveField(String fieldName) {
        return sensitiveFields.containsKey(fieldName);
    }

    /**
     * Get custom metrics tag name or field name if no custom name configured
     */
    public String getMetricsTagName(String fieldName) {
        return metricsTagNames.getOrDefault(fieldName, fieldName);
    }

    /**
     * Get custom logging MDC key or field name if no custom key configured
     */
    public String getLoggingMdcKey(String fieldName) {
        return loggingMdcKeys.getOrDefault(fieldName, fieldName);
    }

    /**
     * Get custom tracing tag name or field name if no custom name configured
     */
    public String getTracingTagName(String fieldName) {
        return tracingTagNames.getOrDefault(fieldName, fieldName);
    }

    /**
     * Get masking pattern for sensitive field
     */
    public String getMaskingPattern(String fieldName) {
        return maskingPatterns.get(fieldName);
    }

    /**
     * Get the MaskingHelper instance for external use
     */
    public MaskingHelper getMaskingHelper() {
        return maskingHelper;
    }

    /**
     * Get all fields that should be included in metrics (any cardinality level)
     */
    public Set<String> getAllMetricsFields() {
        Set<String> allMetricsFields = new HashSet<>();
        allMetricsFields.addAll(metricsLowCardinalityFields.keySet());
        allMetricsFields.addAll(metricsMediumCardinalityFields.keySet());
        allMetricsFields.addAll(metricsHighCardinalityFields.keySet());
        return allMetricsFields;
    }

    /**
     * Get all fields that should be included in logging
     */
    public Set<String> getAllLoggingFields() {
        return new HashSet<>(loggingFields.keySet());
    }

    /**
     * Get all fields that should be included in tracing
     */
    public Set<String> getAllTracingFields() {
        return new HashSet<>(tracingFields.keySet());
    }

    /**
     * Get all fields that should be included in upstream outbound (response enrichment)
     */
    public Set<String> getAllUpstreamOutboundFields() {
        return new HashSet<>(upstreamOutboundFields.keySet());
    }

    /**
     * Get upstream outbound key for field
     */
    public String getUpstreamOutboundKey(String fieldName) {
        return upstreamOutboundKeys.getOrDefault(fieldName, fieldName);
    }

    /**
     * Get upstream outbound enrichment type for field
     */
    public EnrichmentType getUpstreamOutboundType(String fieldName) {
        return upstreamOutboundTypes.get(fieldName);
    }

    /**
     * Get all required fields
     */
    public Set<String> getAllRequiredFields() {
        return new HashSet<>(requiredFields.keySet());
    }

    /**
     * Check if field is required
     */
    public boolean isRequiredField(String fieldName) {
        return requiredFields.containsKey(fieldName);
    }

    /**
     * Get all configured field names
     */
    public Set<String> getAllConfiguredFields() {
        return new HashSet<>(allConfiguredFields.keySet());
    }

    /**
     * Check if a field is configured
     */
    public boolean isFieldConfigured(String fieldName) {
        return allConfiguredFields.containsKey(fieldName);
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
     * Enrich existing RequestContext with authenticated data (excluding BODY sources)
     * Called by RequestContextInterceptor after Spring Security authentication
     */
    public RequestContext enrichWithPostAuthPhaseData(HttpServletRequest request) {
        // Get existing context from request
        RequestContext context = RequestContext.getFromRequest(request)
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));
        log.debug("Enriching RequestContext with post auth phase data (excluding BODY)");

        // Extract configured authenticated fields excluding BODY sources
        int fieldsExtracted = extractPostAuthPhaseFieldsExcludingBody(request, context);

        log.debug("Added {} post-auth fields (excluding BODY)", fieldsExtracted);

        // Update MDC with new fields
        updateMDC(context);

        return context;
    }

    /**
     * Enrich existing RequestContext with JSON BODY sources only
     * Called after RequestBodyAdvice has captured the body content as JsonNode
     */
    public RequestContext enrichWithJsonBodySources(com.fasterxml.jackson.databind.JsonNode bodyNode) {
        // Get the current request from RequestContextHolder
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        // Get existing context from request
        RequestContext context = RequestContext.getFromRequest(request)
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));
        log.debug("Enriching RequestContext with JSON BODY sources");

        // Extract BODY source fields only
        int fieldsExtracted = extractBodySourceFields(context, bodyNode);

        log.debug("Added {} BODY source fields", fieldsExtracted);

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

            // Check if this field should be included in MDC
            if (isLoggingField(fieldName)) {
                String mdcKey = getLoggingMdcKey(fieldName);
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
        log.debug("Pre-auth phase extraction group contains {} fields: {}",
                preAuthPhaseFields.size(), preAuthPhaseFields.keySet());

        return new LinkedHashMap<>(preAuthPhaseFields);
    }

    /**
     * Get fields configured for post-authentication phase extraction
     * These are fields that require Spring Security authentication or Spring MVC processing
     * Includes: PATH, BODY, TOKEN, and CLAIM sources
     */
    public Map<String, FieldConfiguration> getPostAuthPhaseExtraction() {
        log.debug("Post-auth phase extraction group contains {} fields: {}",
                postAuthPhaseFields.size(), postAuthPhaseFields.keySet());

        return new LinkedHashMap<>(postAuthPhaseFields);
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
                // Use the new extractAndStoreValue method that handles masking automatically
                extractor.extractAndStoreValue(fieldName, fieldConfig, request, context);

                // Check if value was actually stored
                if (context.containsKey(fieldName)) {
                    count++;
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
                // Use the new extractAndStoreValue method that handles masking automatically
                extractor.extractAndStoreValue(fieldName, fieldConfig, request, context);

                // Check if value was actually stored
                if (context.containsKey(fieldName)) {
                    count++;
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
     * Extract fields from post-authentication phase group excluding BODY sources
     * Includes PATH, TOKEN, and CLAIM sources only
     * Returns number of fields extracted
     */
    private int extractPostAuthPhaseFieldsExcludingBody(HttpServletRequest request, RequestContext context) {
        Map<String, FieldConfiguration> postAuthFields = getPostAuthPhaseExtraction();
        int count = 0;

        log.debug("Extracting {} post-auth phase fields (PATH, TOKEN, CLAIM - excluding BODY)", postAuthFields.size());

        for (Map.Entry<String, FieldConfiguration> entry : postAuthFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            // Skip BODY sources
            if (isBodySource(fieldConfig)) {
                continue;
            }

            try {
                // Use the new extractAndStoreValue method that handles masking automatically
                extractor.extractAndStoreValue(fieldName, fieldConfig, request, context);

                // Check if value was actually stored
                if (context.containsKey(fieldName)) {
                    count++;
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

        log.debug("Extracted {} post-auth fields (excluding BODY) successfully", count);
        return count;
    }

    /**
     * Extract JSON BODY sources only (called after RequestBodyAdvice has captured body)
     * Returns number of fields extracted
     */
    private int extractBodySourceFields(RequestContext context, JsonNode bodyNode) {
        Map<String, FieldConfiguration> postAuthFields = getPostAuthPhaseExtraction();
        int count = 0;

        log.debug("Extracting BODY source fields only");

        for (Map.Entry<String, FieldConfiguration> entry : postAuthFields.entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            // Only process BODY sources
            if (!isBodySource(fieldConfig)) {
                continue;
            }

            try {
                // Use the new extractAndStoreValueFromBody method that handles masking automatically
                extractor.extractAndStoreValueFromBody(fieldName, fieldConfig, bodyNode, context);

                // Check if value was actually stored
                if (context.containsKey(fieldName)) {
                    count++;
                    log.trace("Extracted BODY field '{}' from source {}",
                            fieldName, fieldConfig.getUpstream().getInbound().getSource());
                }
            } catch (Exception e) {
                if (isRequired(fieldConfig)) {
                    throw new IllegalStateException(
                            "Failed to extract required BODY field: " + fieldName, e);
                }
                log.debug("Failed to extract BODY field '{}': {}", fieldName, e.getMessage());
            }
        }

        log.debug("Extracted {} BODY source fields successfully", count);
        return count;
    }

    /**
     * Check if field configuration is a BODY source
     */
    private boolean isBodySource(FieldConfiguration field) {
        return field.getUpstream() != null &&
               field.getUpstream().getInbound() != null &&
               field.getUpstream().getInbound().getSource() == RequestContextProperties.SourceType.BODY;
    }

    /**
     * Check if any BODY sources are configured
     */
    public boolean hasBodySourcesConfigured() {
        return properties.getFields().values().stream()
                .anyMatch(this::isBodySource);
    }

    /**
     * Check if a request path should be excluded from filtering
     * Uses configured exclude patterns from filter configuration
     */
    public boolean shouldExcludeFromFiltering(String requestPath) {
        String[] excludePatterns = properties.getFilterConfig().getExcludePatterns();
        if (excludePatterns != null) {
            for (String pattern : excludePatterns) {
                if (pathMatcher.match(pattern, requestPath)) {
                    log.debug("Path {} matches exclusion pattern: {}", requestPath, pattern);
                    return true;
                }
            }
        }
        return false;
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

        // Use pre-computed downstream inbound fields for optimal performance
        downstreamInboundFields.forEach((fieldName, fieldConfig) -> {
            try {
                captureFieldFromDownstreamResponse(fieldName, fieldConfig, response, context);
            } catch (Exception e) {
                log.error("Error capturing downstream field '{}': {}", fieldName, e.getMessage());
            }
        });

        // Update MDC with any new fields
        updateMDC(context);
    }



    /**
     * Capture a single field from downstream response using the helper pattern
     */
    private void captureFieldFromDownstreamResponse(String fieldName,
                                                   FieldConfiguration fieldConfig,
                                                   ClientResponse response,
                                                   RequestContext context) {

        var inbound = fieldConfig.getDownstream().getInbound();

        // Use the source handlers registry for downstream response extraction
        String value = sourceHandlers.extractFromDownstreamResponse(inbound.getSource(), response, inbound);

        if (value == null) {
            // Try default value if configured
            if (inbound.getDefaultValue() != null) {
                processAndStoreDownstreamValue(fieldName, inbound.getDefaultValue(),
                        fieldConfig, context, true);
                return;
            }

            // Log if required field is missing
            if (inbound.isRequired()) {
                log.warn("Required downstream field '{}' not found in response for field '{}' from source '{}'",
                        inbound.getKey(), fieldName, inbound.getSource());
            }
            return;
        }

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
                String maskedValue = maskingHelper.maskValue(value, fieldConfig);
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
        loggingFields.keySet().forEach(fieldName -> {
            String value = context.getMaskedOrOriginal(fieldName);
            if (value != null) {
                String mdcKey = getLoggingMdcKey(fieldName);
                MDC.put(mdcKey, value);
            }
        });
    }

    /**
     * Clear all MDC entries managed by RequestContext
     */
    public void clearMDC() {
        loggingFields.keySet().forEach(fieldName -> {
            String mdcKey = getLoggingMdcKey(fieldName);
            MDC.remove(mdcKey);
        });
    }

    // ========================================
    // Response Enrichment
    // ========================================

    /**
     * Enrich HTTP response based on configuration using the helper pattern
     */
    public void enrichResponse(HttpServletResponse response, RequestContext context) {
        int count = 0;

        // Use pre-computed upstream outbound fields for optimal performance
        for (String fieldName : upstreamOutboundFields.keySet()) {
            String value = context.get(fieldName);

            if (value != null) {
                EnrichmentType enrichmentType = getUpstreamOutboundType(fieldName);
                String key = getUpstreamOutboundKey(fieldName);

                if (enrichmentType != null) {
                    try {
                        sourceHandlers.enrichUpstreamResponse(enrichmentType, response, key, value);
                        count++;
                        log.trace("Applied response enrichment '{}' for field '{}' with type '{}'",
                                key, fieldName, enrichmentType);
                    } catch (Exception e) {
                        log.error("Error applying response enrichment for field '{}': {}", fieldName, e.getMessage());
                    }
                } else {
                    log.warn("No enrichment type configured for upstream outbound field: {}", fieldName);
                }
            }
        }

        if (count > 0) {
            log.debug("Enriched response with {} fields", count);
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

        Set<String> fieldsForLevel = switch (level) {
            case LOW -> metricsLowCardinalityFields.keySet();
            case MEDIUM -> metricsMediumCardinalityFields.keySet();
            case HIGH -> metricsHighCardinalityFields.keySet();
            case NONE -> Collections.emptySet();
        };

        fieldsForLevel.forEach(fieldName -> {
            String value = context.getMaskedOrOriginal(fieldName);
            if (value != null) {
                String tagName = getMetricsTagName(fieldName);
                metricsFields.put(tagName, value);
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
        Map<String, String> tracingFieldsMap = new LinkedHashMap<>();

        tracingFields.keySet().forEach(fieldName -> {
            String value = context.getMaskedOrOriginal(fieldName);
            if (value != null) {
                String tagName = getTracingTagName(fieldName);
                tracingFieldsMap.put(tagName, value);
            }
        });

        return tracingFieldsMap;
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

        // Use pre-computed required fields for optimal performance
        for (String fieldName : requiredFields.keySet()) {
            String value = context.get(fieldName);
            if (value == null || value.isEmpty()) {
                missingFields.add(fieldName);
            }
        }

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
               source == SourceType.QUERY  ||
               source == SourceType.COOKIE ||
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

    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null &&
                fieldConfig.getSecurity().isSensitive();
    }

    private boolean isRequired(FieldConfiguration fieldConfig) {
        return fieldConfig.getUpstream() != null &&
                fieldConfig.getUpstream().getInbound() != null &&
                fieldConfig.getUpstream().getInbound().isRequired();
    }

}