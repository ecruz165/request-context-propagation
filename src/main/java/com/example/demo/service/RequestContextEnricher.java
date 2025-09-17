package com.example.demo.service;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.OutboundConfig;
import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles enrichment operations for RequestContext
 * Provides extraction and transformation capabilities for outbound propagation
 */
@Component
@Slf4j
public class RequestContextEnricher {

    private final RequestContextProperties properties;

    public RequestContextEnricher(RequestContextProperties properties) {
        this.properties = properties;
    }

    /**
     * Extract values from context for downstream propagation
     * Returns a map of field names to their propagation data
     */
    public Map<String, PropagationData> extractForDownstreamPropagation(RequestContext context) {
        Map<String, PropagationData> propagationData = new LinkedHashMap<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldPropagateDownstream(fieldConfig)) {
                try {
                    PropagationData data = extractPropagationData(fieldName, fieldConfig, context);
                    if (data != null) {
                        propagationData.put(fieldName, data);
                    }
                } catch (Exception e) {
                    log.error("Error extracting field {} for propagation: {}", fieldName, e.getMessage());
                }
            }
        });

        log.debug("Extracted {} fields for downstream propagation", propagationData.size());
        return propagationData;
    }

    /**
     * Transform value based on configured type
     */
    public String transformValue(String value, RequestContextProperties.ValueType valueType) {
        if (value == null || valueType == null) {
            return value;
        }

        try {
            switch (valueType) {
                case BASE64:
                    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

                case URL_ENCODED:
                    return URLEncoder.encode(value, StandardCharsets.UTF_8);

                case JSON_ARRAY:
                    // Simple JSON array for single value
                    return "[\"" + value.replace("\"", "\\\"") + "\"]";

                case JSON_OBJECT:
                    // Simple JSON object
                    return "{\"value\":\"" + value.replace("\"", "\\\"") + "\"}";

                case NUMBER:
                    // Validate it's a number
                    try {
                        Double.parseDouble(value);
                        return value;
                    } catch (NumberFormatException e) {
                        log.warn("Value {} is not a valid number", value);
                        return null;
                    }

                case BOOLEAN:
                    // Convert to boolean string
                    return Boolean.parseBoolean(value) ? "true" : "false";

                case STRING:
                case EXPRESSION:
                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("Error transforming value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Evaluate expression with placeholder replacement
     */
    public String evaluateExpression(String expression, RequestContext context) {
        if (expression == null || context == null) {
            return expression;
        }

        // Simple placeholder replacement
        // For complex expressions, integrate SpEL
        String result = expression;

        for (Map.Entry<String, String> entry : context.getAllValues().entrySet()) {
            String placeholder = "#" + entry.getKey();
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, entry.getValue());
            }
        }

        return result;
    }

    /**
     * Evaluate condition for field propagation
     */
    public boolean evaluateCondition(String condition, RequestContext context) {
        if (condition == null || condition.trim().isEmpty()) {
            return true;
        }

        // Simple implementation - for complex conditions, integrate SpEL
        // For now, support basic existence checks like "hasValue(fieldName)"
        if (condition.startsWith("hasValue(") && condition.endsWith(")")) {
            String fieldName = condition.substring(9, condition.length() - 1);
            String value = context.get(fieldName);
            return value != null && !value.trim().isEmpty();
        }

        // Default to true for unknown conditions
        log.debug("Unknown condition format: {}, defaulting to true", condition);
        return true;
    }

    // ========================================
    // Private Helper Methods
    // ========================================

    /**
     * Check if field should be propagated to downstream services
     */
    private boolean shouldPropagateDownstream(FieldConfiguration fieldConfig) {
        return fieldConfig.getDownstream() != null &&
                fieldConfig.getDownstream().getOutbound() != null;
    }

    /**
     * Extract propagation data for a single field
     */
    private PropagationData extractPropagationData(String fieldName, 
                                                  FieldConfiguration fieldConfig, 
                                                  RequestContext context) {
        
        var outbound = fieldConfig.getDownstream().getOutbound();
        
        // Get value from context (with expression support)
        String value = extractValue(fieldName, fieldConfig, context, outbound);
        if (value == null || value.isEmpty()) {
            return null;
        }

        // Check condition if configured
        if (!evaluateCondition(outbound.getCondition(), context)) {
            log.debug("Skipping field {} due to condition: {}", fieldName, outbound.getCondition());
            return null;
        }

        // Apply value transformation
        String transformedValue = transformValue(value, outbound.getValueAs());

        return new PropagationData(
                outbound.getEnrichAs(),
                outbound.getKey(),
                transformedValue,
                isSensitive(fieldConfig)
        );
    }

    /**
     * Extract value for propagation, handling expressions
     */
    private String extractValue(String fieldName, 
                               FieldConfiguration fieldConfig,
                               RequestContext context, 
                               OutboundConfig outbound) {
        
        // Use expression if configured
        if (outbound.getValueAs() == RequestContextProperties.ValueType.EXPRESSION &&
                outbound.getValue() != null) {
            return evaluateExpression(outbound.getValue(), context);
        } else {
            // Get value directly from context
            return context.get(fieldName);
        }
    }

    /**
     * Check if field is sensitive
     */
    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null &&
                fieldConfig.getSecurity().isSensitive();
    }

    // ========================================
    // Data Classes
    // ========================================

    /**
     * Data structure for propagation information
     */
    public static class PropagationData {
        private final EnrichmentType enrichmentType;
        private final String key;
        private final String value;
        private final boolean sensitive;
        private final String maskingPattern;

        public PropagationData(EnrichmentType enrichmentType, String key, String value, boolean sensitive) {
            this(enrichmentType, key, value, sensitive, "***");
        }

        public PropagationData(EnrichmentType enrichmentType, String key, String value, boolean sensitive, String maskingPattern) {
            this.enrichmentType = enrichmentType;
            this.key = key;
            this.value = value;
            this.sensitive = sensitive;
            this.maskingPattern = maskingPattern != null ? maskingPattern : "***";
        }

        public EnrichmentType getEnrichmentType() { return enrichmentType; }
        public String getKey() { return key; }
        public String getValue() { return value; }
        public boolean isSensitive() { return sensitive; }
        public String getMaskingPattern() { return maskingPattern; }

        public String getMaskedValue() {
            if (!sensitive || value == null) {
                return value;
            }

            // Apply custom masking pattern
            return applyMaskingPattern(value, maskingPattern);
        }

        /**
         * Apply masking pattern to value
         * Supports patterns like:
         * - "***" (simple masking)
         * - "*-4" (show last 4 characters)
         * - "****-****-****-{4}" (show last 4 with formatting)
         * - "{8}***" (show first 8 characters)
         * - "***@***.***" (email masking)
         */
        private String applyMaskingPattern(String value, String pattern) {
            if (value == null || value.isEmpty() || pattern == null) {
                return "***";
            }

            // Handle advanced patterns with {n} syntax
            if (pattern.contains("{") && pattern.contains("}")) {
                return applyAdvancedPattern(value, pattern);
            }

            // Handle email masking
            if (pattern.contains("@") && value.contains("@")) {
                return applyEmailPattern(value, pattern);
            }

            // Handle legacy *-n pattern
            if (pattern.startsWith("*-")) {
                return applyLegacyPattern(value, pattern);
            }

            // Default: return pattern as-is
            return pattern;
        }

        private String applyAdvancedPattern(String value, String pattern) {
            try {
                // Simple implementation for common patterns
                if (pattern.endsWith("{4}")) {
                    // Show last 4 characters
                    int showChars = 4;
                    if (value.length() > showChars) {
                        String prefix = pattern.substring(0, pattern.indexOf("{"));
                        return prefix + value.substring(value.length() - showChars);
                    }
                } else if (pattern.startsWith("{")) {
                    // Show first n characters
                    int endBrace = pattern.indexOf("}");
                    if (endBrace > 1) {
                        int showChars = Integer.parseInt(pattern.substring(1, endBrace));
                        if (value.length() > showChars) {
                            String suffix = pattern.substring(endBrace + 1);
                            return value.substring(0, showChars) + suffix;
                        }
                    }
                }
            } catch (Exception e) {
                // Fallback to simple masking
            }
            return "***";
        }

        private String applyEmailPattern(String value, String pattern) {
            int atIndex = value.indexOf("@");
            if (atIndex > 0) {
                if (pattern.equals("***@***.***")) {
                    return "***@***.***";
                }
                // More sophisticated email masking could be added here
                return value.charAt(0) + "***@***.***";
            }
            return "***";
        }

        private String applyLegacyPattern(String value, String pattern) {
            String[] parts = pattern.split("-");
            if (parts.length > 1) {
                try {
                    int showChars = Integer.parseInt(parts[1]);
                    if (value.length() > showChars) {
                        return "***" + value.substring(value.length() - showChars);
                    }
                } catch (NumberFormatException e) {
                    // Fallback
                }
            }
            return "***";
        }

        @Override
        public String toString() {
            return String.format("PropagationData{type=%s, key='%s', value='%s', sensitive=%s, pattern='%s'}",
                    enrichmentType, key, getMaskedValue(), sensitive, maskingPattern);
        }
    }
}
