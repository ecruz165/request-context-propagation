package com.jefelabs.modules.requestcontext.service;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.CardinalityLevel;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.EnrichmentType;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.FieldConfiguration;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.LogLevel;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.LoggingConfig;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.MetricsConfig;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.OutboundConfig;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.SourceType;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.TracingConfig;
import com.jefelabs.modules.requestcontext.service.source.SourceHandlers;
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
import org.springframework.web.method.HandlerMethod;
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
    private final RequestContextMaskingHelper maskingHelper;
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
                                 RequestContextMaskingHelper maskingHelper,
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
        log.debug("Initializing RequestContext field sets for optimal performance");

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

        log.debug("Initialized field sets: {} upstream inbound, {} metrics fields, {} logging fields, {} tracing fields",
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
     * Check if a field should be included in metrics for the specified cardinality level
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
     * Get the MaskingHelper instance for external use
     */
    public RequestContextMaskingHelper getMaskingHelper() {
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
     * Get current RequestContext from the current thread's HTTP request
     * Uses Spring's RequestContextHolder to access the current request
     * Returns Optional.empty() if context is not found (no exception)
     */
    public Optional<RequestContext> getCurrentContext() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attributes != null) {
                HttpServletRequest request = attributes.getRequest();
                return RequestContext.getFromRequest(request);
            }
        } catch (Exception e) {
            log.debug("Could not get current request context: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Get current RequestContext from the current thread's HTTP request
     * Throws exception if context is not found
     */
    public RequestContext getCurrentContextRequired() {
        return getCurrentContext()
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));
    }

    /**
     * Set RequestContext in a specific HttpServletRequest
     * Internal method for framework components
     */
    public void setContextInRequest(HttpServletRequest request, RequestContext context) {
        RequestContext.setInRequest(request, context);
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

        // Store context in request using service proxy method
        setContextInRequest(request, context);

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
     * Enrich existing RequestContext with context data (like handler method info)
     * Called by RequestContextInterceptor when handler information is available
     */
    public RequestContext enrichWithContextData(HandlerMethod handlerMethod) {
        // Get the current request from RequestContextHolder
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        // Get existing context from request using service proxy method
        RequestContext context = getCurrentContext(request);
        log.debug("Enriching RequestContext with context data (handler method info)");

        // Get configured context-generated fields
        Map<String, FieldConfiguration> contextFields = getContextGeneratedFields();
        int fieldsSet = 0;

        // Set context values for configured fields only
        for (String fieldName : contextFields.keySet()) {
            if ("apiHandler".equals(fieldName)) {
                // Set apiHandler directly - format: "ClassName/methodName"
                String className = handlerMethod.getBeanType().getSimpleName();
                String methodName = handlerMethod.getMethod().getName();
                String apiHandler = className + "/" + methodName;

                context.put(fieldName, apiHandler);
                log.debug("Set context field '{}' = '{}'", fieldName, apiHandler);
                fieldsSet++;
            }
            // Future context fields can be added here
        }

        log.debug("Set {} context fields", fieldsSet);

        // Update MDC with new fields
        updateMDC(context);

        return context;
    }

    /**
     * Enrich existing RequestContext with JSON BODY sources only
     * Called after RequestBodyAdvice has captured the body content as JsonNode
     */
    public RequestContext enrichWithJsonBodySources(JsonNode bodyNode) {
        // Get the current request from RequestContextHolder
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest();

        // Get existing context from request using service proxy method
        RequestContext context = getCurrentContext(request);
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
        getCurrentContext().ifPresent(RequestContext::clear);
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
     * Get fields configured for context generation (like handler method info)
     * These are fields that have observability config but no inbound source (auto-generated)
     */
    public Map<String, FieldConfiguration> getContextGeneratedFields() {
        Map<String, FieldConfiguration> contextFields = new LinkedHashMap<>();

        for (Map.Entry<String, FieldConfiguration> entry : properties.getFields().entrySet()) {
            FieldConfiguration fieldConfig = entry.getValue();

            // Check if this field has observability config but no inbound source (context-generated)
            if (fieldConfig.getObservability() != null &&
                    (fieldConfig.getUpstream() == null || fieldConfig.getUpstream().getInbound() == null)) {
                contextFields.put(entry.getKey(), fieldConfig);
            }
        }

        log.debug("Context-generated fields: {}", contextFields.keySet());
        return contextFields;
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
     * Check if there are any downstream fields that require body extraction
     * This is used by the capture filter to determine if response body buffering is needed
     */
    public boolean hasDownstreamBodyExtractionFields() {
        return downstreamInboundFields.values().stream()
                .anyMatch(fieldConfig -> {
                    var inbound = fieldConfig.getDownstream().getInbound();
                    return inbound.getSource() == SourceType.BODY;
                });
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

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Determine if field should be extracted in pre-authentication phase
     * Pre-auth phase includes: HEADER, QUERY, COOKIE sources
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
                source == SourceType.COOKIE;
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

    // ========================================
    // Programmatic Field Access API
    // ========================================

    /**
     * Get a field value from the current request context
     *
     * @param fieldName the name of the field to retrieve
     * @return the field value, or null if not found or no context available
     */
    public String getField(String fieldName) {
        try {
            // First try HttpServletRequest (for request threads)
            HttpServletRequest request = getCurrentRequest();
            if (request != null) {
                RequestContext context = getCurrentContextSafely(request).orElse(null);
                if (context != null) {
                    String value = context.get(fieldName);
                    if (value != null) {
                        return value;
                    }
                }
            }

            // No fallback needed for WebClient-focused architecture
            return null;
        } catch (Exception e) {
            log.warn("Error getting field '{}': {}", fieldName, e.getMessage());
            return null;
        }
    }

    /**
     * Set a field value in the current request context
     * The field will be treated exactly like any other configured field:
     * - If configured for logging, will update MDC with proper masking
     * - If configured for metrics, will be included in metric tags
     * - If configured for tracing, will be added to spans
     * - If configured as sensitive, will be masked in logs and observability
     *
     * @param fieldName  the name of the field to set
     * @param fieldValue the value to set (null to remove the field)
     * @return true if the field was set successfully, false otherwise
     */
    public boolean setField(String fieldName, String fieldValue) {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.debug("No current request available for setField({}, {})", fieldName, fieldValue);
                return false;
            }

            RequestContext context = getCurrentContextSafely(request).orElse(null);
            if (context == null) {
                log.debug("No RequestContext available for setField({}, {})", fieldName, fieldValue);
                return false;
            }

            // Set or remove the field value
            if (fieldValue != null) {
                context.put(fieldName, fieldValue);
                log.debug("Set field '{}' to value: {}", fieldName,
                        isSensitiveField(fieldName) ? "***" : fieldValue);
            } else {
                context.remove(fieldName);
                log.debug("Removed field '{}'", fieldName);
            }

            // Update all observability systems if this field is configured
            updateObservabilityForField(fieldName, fieldValue);

            return true;
        } catch (Exception e) {
            log.warn("Error setting field '{}' to '{}': {}", fieldName, fieldValue, e.getMessage());
            return false;
        }
    }

    /**
     * Get all field values from the current request context
     *
     * @return a map of all field names and values, or empty map if no context available
     */
    public Map<String, String> getAllFields() {
        try {
            HttpServletRequest request = getCurrentRequest();
            if (request == null) {
                log.debug("No current request available for getAllFields()");
                return Collections.emptyMap();
            }

            RequestContext context = getCurrentContextSafely(request).orElse(null);
            if (context == null) {
                log.debug("No RequestContext available for getAllFields()");
                return Collections.emptyMap();
            }

            return new LinkedHashMap<>(context.getAll());
        } catch (Exception e) {
            log.warn("Error getting all fields: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Check if a field exists in the current request context
     *
     * @param fieldName the name of the field to check
     * @return true if the field exists and has a non-null value, false otherwise
     */
    public boolean hasField(String fieldName) {
        String value = getField(fieldName);
        return value != null;
    }

    /**
     * Remove a field from the current request context
     * This is equivalent to calling setField(fieldName, null)
     *
     * @param fieldName the name of the field to remove
     * @return true if the field was removed successfully, false otherwise
     */
    public boolean removeField(String fieldName) {
        return setField(fieldName, null);
    }

    /**
     * Add a custom computed field to the context
     * This is useful for adding runtime-computed values that aren't extracted from requests.
     * The field will be treated exactly like any configured field:
     * - If configured in YAML, will follow all observability and security settings
     * - If not configured, will be stored but not included in logs/metrics/tracing
     * - Use this for business logic computed values, correlation IDs, etc.
     *
     * @param fieldName  the name of the custom field
     * @param fieldValue the computed value
     * @return true if the field was added successfully, false otherwise
     */
    public boolean addCustomField(String fieldName, String fieldValue) {
        if (fieldValue == null) {
            log.warn("Cannot add custom field '{}' with null value", fieldName);
            return false;
        }

        boolean success = setField(fieldName, fieldValue);
        if (success) {
            log.debug("Added custom field '{}' with value: {}", fieldName,
                    isSensitiveField(fieldName) ? "***" : fieldValue);
        }
        return success;
    }

    // ========================================
    // Helper Methods
    // ========================================

    /**
     * Get the current HttpServletRequest from RequestContextHolder
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.debug("Could not get current request: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Update all observability systems for a specific field if it's configured
     * This ensures programmatically set fields are treated exactly like extracted fields
     */
    private void updateObservabilityForField(String fieldName, String fieldValue) {
        try {
            FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
            if (fieldConfig == null || fieldConfig.getObservability() == null) {
                // Field not configured for observability - store but don't propagate
                log.trace("Field '{}' not configured for observability", fieldName);
                return;
            }

            // Update MDC for logging
            updateMDCForConfiguredField(fieldConfig, fieldName, fieldValue);

            // Note: Metrics and tracing are typically updated during request processing
            // The field will be automatically included when getMetricsFields() and
            // getTracingFields() are called by the observability components

        } catch (Exception e) {
            log.debug("Could not update observability for field '{}': {}", fieldName, e.getMessage());
        }
    }

    /**
     * Update MDC for a configured field with proper masking
     */
    private void updateMDCForConfiguredField(FieldConfiguration fieldConfig, String fieldName, String fieldValue) {
        try {
            LoggingConfig loggingConfig = fieldConfig.getObservability().getLogging();
            if (loggingConfig != null && loggingConfig.getMdcKey() != null) {
                if (fieldValue != null) {
                    // Apply masking if field is sensitive
                    String mdcValue = fieldValue;
                    if (isSensitive(fieldConfig)) {
                        // Simple masking for now - TODO: integrate with MaskingHelper properly
                        mdcValue = "***";
                    }
                    MDC.put(loggingConfig.getMdcKey(), mdcValue);
                    log.trace("Updated MDC key '{}' for field '{}' with masked value",
                            loggingConfig.getMdcKey(), fieldName);
                } else {
                    MDC.remove(loggingConfig.getMdcKey());
                    log.trace("Removed MDC key '{}' for field '{}'", loggingConfig.getMdcKey(), fieldName);
                }
            }
        } catch (Exception e) {
            log.debug("Could not update MDC for field '{}': {}", fieldName, e.getMessage());
        }
    }

    /**
     * Check if a field is configured as sensitive
     */
    private boolean isSensitiveField(String fieldName) {
        FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
        return fieldConfig != null && isSensitive(fieldConfig);
    }

    // ========================================
    // Programmatic Field Configuration API
    // ========================================

    /**
     * Add a field configuration programmatically at runtime
     * This allows dynamic registration of new fields with full observability support
     *
     * @param fieldName   the name of the field to configure
     * @param fieldConfig the complete field configuration
     * @return true if the configuration was added successfully, false otherwise
     */
    public boolean addFieldConfiguration(String fieldName, FieldConfiguration fieldConfig) {
        try {
            if (fieldName == null || fieldName.trim().isEmpty()) {
                log.warn("Cannot add field configuration with null or empty field name");
                return false;
            }

            if (fieldConfig == null) {
                log.warn("Cannot add null field configuration for field '{}'", fieldName);
                return false;
            }

            // Add to the properties map (this modifies the runtime configuration)
            properties.getFields().put(fieldName, fieldConfig);

            // Also add to the allConfiguredFields map for consistency
            allConfiguredFields.put(fieldName, fieldConfig);

            log.debug("Added programmatic field configuration for field '{}' with observability: {}",
                    fieldName, fieldConfig.getObservability() != null);

            return true;
        } catch (Exception e) {
            log.warn("Error adding field configuration for '{}': {}", fieldName, e.getMessage());
            return false;
        }
    }

    /**
     * Remove a field configuration programmatically
     * This will stop the field from being included in observability systems
     *
     * @param fieldName the name of the field configuration to remove
     * @return true if the configuration was removed successfully, false otherwise
     */
    public boolean removeFieldConfiguration(String fieldName) {
        try {
            FieldConfiguration removed = properties.getFields().remove(fieldName);
            // Also remove from allConfiguredFields for consistency
            FieldConfiguration removedFromAll = allConfiguredFields.remove(fieldName);

            if (removed != null || removedFromAll != null) {
                log.debug("Removed programmatic field configuration for field '{}'", fieldName);

                // Clean up MDC if the field was configured for logging
                FieldConfiguration configToCheck = removed != null ? removed : removedFromAll;
                if (configToCheck.getObservability() != null &&
                        configToCheck.getObservability().getLogging() != null &&
                        configToCheck.getObservability().getLogging().getMdcKey() != null) {
                    MDC.remove(configToCheck.getObservability().getLogging().getMdcKey());
                }

                return true;
            } else {
                log.debug("No field configuration found to remove for field '{}'", fieldName);
                return false;
            }
        } catch (Exception e) {
            log.warn("Error removing field configuration for '{}': {}", fieldName, e.getMessage());
            return false;
        }
    }

    /**
     * Get a field configuration
     *
     * @param fieldName the name of the field
     * @return the field configuration, or null if not configured
     */
    public FieldConfiguration getFieldConfiguration(String fieldName) {
        return properties.getFields().get(fieldName);
    }


    /**
     * Get all configured field names
     *
     * @return a set of all configured field names
     */
    public Set<String> getConfiguredFieldNames() {
        return new HashSet<>(properties.getFields().keySet());
    }

    /**
     * Add a simple field configuration for logging only
     * Convenience method for quickly adding fields that should appear in logs
     *
     * @param fieldName the name of the field
     * @param mdcKey    the MDC key to use for logging
     * @param sensitive whether the field contains sensitive data
     * @return true if the configuration was added successfully, false otherwise
     */
    public boolean addLoggingField(String fieldName, String mdcKey, boolean sensitive) {
        try {
            // Create logging configuration
            LoggingConfig loggingConfig = new LoggingConfig();
            loggingConfig.setMdcKey(mdcKey);

            // Create observability configuration
            RequestContextProperties.ObservabilityConfig observabilityConfig =
                    new RequestContextProperties.ObservabilityConfig();
            observabilityConfig.setLogging(loggingConfig);

            // Create field configuration
            FieldConfiguration fieldConfig = new FieldConfiguration();
            fieldConfig.setObservability(observabilityConfig);

            // Set up security if sensitive
            if (sensitive) {
                RequestContextProperties.SecurityConfig securityConfig =
                        new RequestContextProperties.SecurityConfig();
                securityConfig.setSensitive(true);
                securityConfig.setMasking("***");
                fieldConfig.setSecurity(securityConfig);
            }

            return addFieldConfiguration(fieldName, fieldConfig);
        } catch (Exception e) {
            log.warn("Error adding logging field configuration for '{}': {}", fieldName, e.getMessage());
            return false;
        }
    }

    /**
     * Add a field configuration for metrics with specified cardinality
     * Convenience method for adding fields that should appear in metrics
     *
     * @param fieldName   the name of the field
     * @param cardinality the cardinality level (LOW, MEDIUM, HIGH)
     * @param sensitive   whether the field contains sensitive data
     * @return true if the configuration was added successfully, false otherwise
     */
    public boolean addMetricsField(String fieldName, CardinalityLevel cardinality, boolean sensitive) {
        try {
            // Create metrics configuration
            MetricsConfig metricsConfig = new MetricsConfig();
            metricsConfig.setCardinality(cardinality);

            // Create observability configuration
            RequestContextProperties.ObservabilityConfig observabilityConfig =
                    new RequestContextProperties.ObservabilityConfig();
            observabilityConfig.setMetrics(metricsConfig);

            // Create field configuration
            FieldConfiguration fieldConfig = new FieldConfiguration();
            fieldConfig.setObservability(observabilityConfig);

            // Set up security if sensitive
            if (sensitive) {
                RequestContextProperties.SecurityConfig securityConfig =
                        new RequestContextProperties.SecurityConfig();
                securityConfig.setSensitive(true);
                securityConfig.setMasking("***");
                fieldConfig.setSecurity(securityConfig);
            }

            return addFieldConfiguration(fieldName, fieldConfig);
        } catch (Exception e) {
            log.warn("Error adding metrics field configuration for '{}': {}", fieldName, e.getMessage());
            return false;
        }
    }

    // ================================
    // WebClient-Focused Architecture (No Background Thread Support)
    // ================================
    // Note: Background thread context propagation removed for WebClient-focused architecture
    // All context operations use Reactor Context API through ContextAwareWebClientBuilder

    // ContextAwareScheduler methods removed for WebClient-focused architecture
    // Use ContextAwareWebClientBuilder for all WebClient operations with automatic context propagation

    // ================================
    // ThreadLocal Context Support (for background threads)
    // ================================

    // ThreadLocal support removed for WebClient-focused architecture

    // ================================
    // Proxy Methods for ContextSnapshot (Single Source of Truth)
    // ================================

    /**
     * Get all configured field names from the properties
     * This maintains the single source of truth for field configuration
     */
    public Set<String> getAllConfiguredFieldNames() {
        if (properties.getFields() == null) {
            return Collections.emptySet();
        }
        return properties.getFields().keySet();
    }

    /**
     * Get all programmatically set fields from the current context
     * This maintains the proxy pattern for accessing context data
     */
    public Map<String, String> getAllProgrammaticFields() {
        Optional<RequestContext> context = getCurrentContext();
        if (context.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> allFields = context.get().getValues();
        if (allFields == null) {
            return Collections.emptyMap();
        }

        // Filter to only programmatic fields (not in YAML configuration)
        Set<String> configuredFields = getAllConfiguredFieldNames();
        return allFields.entrySet().stream()
                .filter(entry -> !configuredFields.contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    // ContextSnapshot methods removed for WebClient-focused architecture

    /**
     * Get all field names that have upstream outbound configuration
     * Used for reactive context propagation
     */
    public Set<String> getUpstreamOutboundFieldNames() {
        return upstreamOutboundFields.keySet();
    }
}