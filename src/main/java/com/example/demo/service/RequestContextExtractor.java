package com.example.demo.service;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.GeneratorType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.TransformationType;
import com.example.demo.service.source.SourceHandlers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Extractor for extracting context values from HTTP requests based on configuration.
 * This class handles the low-level extraction logic for different source types.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RequestContextExtractor {

    private final RequestContextProperties properties;
    private final MaskingHelper maskingHelper;
    private final ObjectMapper objectMapper;
    private final SourceHandlers sourceHandlers;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    /**
     * Extracts a value for a field from the HTTP request
     * @param fieldName The field name to extract
     * @param request The HTTP request
     * @return The extracted value or null if not found
     */
    public String extractValue(String fieldName, HttpServletRequest request) {
        FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
        if (fieldConfig == null) {
            log.debug("No configuration found for field: {}", fieldName);
            return null;
        }

        if (fieldConfig.getUpstream() == null || fieldConfig.getUpstream().getInbound() == null) {
            log.debug("No upstream inbound configuration for field: {}", fieldName);
            return null;
        }

        InboundConfig inboundConfig = fieldConfig.getUpstream().getInbound();
        return extractFromSource(inboundConfig, request, fieldName);
    }

    /**
     * Extracts and stores a value in the context with appropriate masking
     * @param fieldName The field name to extract
     * @param fieldConfig The field configuration
     * @param request The HTTP request
     * @param context The context to store values in
     */
    public void extractAndStoreValue(String fieldName, FieldConfiguration fieldConfig,
                                   HttpServletRequest request, RequestContext context) {
        if (fieldConfig.getUpstream() == null || fieldConfig.getUpstream().getInbound() == null) {
            log.debug("No upstream inbound configuration for field: {}", fieldName);
            return;
        }

        InboundConfig inboundConfig = fieldConfig.getUpstream().getInbound();
        String rawValue = extractFromSource(inboundConfig, request, fieldName);

        if (rawValue != null) {
            // Store original value
            context.put(fieldName, rawValue);

            // Store masked value if field is sensitive
            if (fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive()) {
                String maskedValue = maskingHelper.maskValue(rawValue, fieldConfig);
                context.putMasked(fieldName, maskedValue);
                log.debug("Stored masked value for sensitive field: {}", fieldName);
            }
        }
    }

    /**
     * Extracts value from the configured source with fallback support
     */
    private String extractFromSource(InboundConfig config, HttpServletRequest request, String fieldName) {
        String value = null;

        try {
            // Delegate raw extraction to the source handlers
            value = sourceHandlers.extractFromUpstreamRequest(config.getSource(), request, config);

            // If registry didn't handle it, log a debug message (this is normal when the source doesn't contain the field)
            if (value == null) {
                log.debug("No value extracted from source type: {} for field: {}", config.getSource(), fieldName);
            }

            // Try fallback if primary source returned null
            if (value == null && config.getFallback() != null) {
                log.debug("Primary source {} returned null for field {}, trying fallback",
                        config.getSource(), fieldName);
                value = extractFromSource(config.getFallback(), request, fieldName);
            }

            // Use default value if still null
            if (value == null && config.getDefaultValue() != null) {
                log.debug("Using default value for field {}: {}", fieldName, config.getDefaultValue());
                value = config.getDefaultValue();
            }

            // Generate if absent and configured
            if (value == null && config.isGenerateIfAbsent()) {
                value = generateValue(config.getGenerator());
                log.debug("Generated value for field {}: {}", fieldName, value);
            }

            // Apply transformation if configured
            if (value != null && config.getTransformation() != null) {
                value = applyTransformation(value, config.getTransformation(), config.getTransformExpression());
            }

            // Validate if pattern is configured
            if (value != null && config.getValidationPattern() != null) {
                if (!value.matches(config.getValidationPattern())) {
                    log.warn("Value '{}' for field {} does not match validation pattern: {}",
                            value, fieldName, config.getValidationPattern());
                    if (config.isRequired()) {
                        throw new IllegalArgumentException(
                                "Required field " + fieldName + " validation failed");
                    }
                    return null;
                }
            }

            // Check required constraint
            if (value == null && config.isRequired()) {
                throw new IllegalArgumentException("Required field " + fieldName + " is missing");
            }

        } catch (Exception e) {
            log.error("Error extracting field {}: {}", fieldName, e.getMessage());
            if (config.isRequired()) {
                throw new RuntimeException("Failed to extract required field: " + fieldName, e);
            }
        }

        return value;
    }

    /**
     * Extract value from JsonNode using field configuration
     * Used when body is already available as JsonNode from RequestBodyAdvice
     */
    public String extractValueFromBody(String fieldName, com.fasterxml.jackson.databind.JsonNode bodyNode) {
        FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
        if (fieldConfig == null || fieldConfig.getUpstream() == null ||
            fieldConfig.getUpstream().getInbound() == null) {
            return null;
        }

        InboundConfig config = fieldConfig.getUpstream().getInbound();
        if (config.getSource() != RequestContextProperties.SourceType.BODY) {
            return null;
        }

        try {
            // Delegate to the source handlers for BODY extraction
            String extractedValue = sourceHandlers.extractFromUpstreamRequestBody(config.getSource(), bodyNode, config);

            // If extraction returns null (JSONPath not found), use default value
            if (extractedValue == null && config.getDefaultValue() != null) {
                log.debug("Using default value for field {}: {}", fieldName, config.getDefaultValue());
                return config.getDefaultValue();
            }

            return extractedValue;
        } catch (Exception e) {
            log.warn("Error extracting value from JsonNode for field {}: {}", fieldName, e.getMessage());
            // Return default value if extraction fails
            return config.getDefaultValue();
        }
    }

    /**
     * Extract and store value from JsonNode with appropriate masking
     * Used when body is already available as JsonNode from RequestBodyAdvice
     */
    public void extractAndStoreValueFromBody(String fieldName, FieldConfiguration fieldConfig,
                                           com.fasterxml.jackson.databind.JsonNode bodyNode,
                                           RequestContext context) {
        if (fieldConfig.getUpstream() == null || fieldConfig.getUpstream().getInbound() == null) {
            return;
        }

        InboundConfig config = fieldConfig.getUpstream().getInbound();
        if (config.getSource() != RequestContextProperties.SourceType.BODY) {
            return;
        }

        String rawValue = extractValueFromBody(fieldName, bodyNode);

        if (rawValue != null) {
            // Store original value
            context.put(fieldName, rawValue);

            // Store masked value if field is sensitive
            if (fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive()) {
                String maskedValue = maskingHelper.maskValue(rawValue, fieldConfig);
                context.putMasked(fieldName, maskedValue);
                log.debug("Stored masked value for sensitive field: {}", fieldName);
            }
        }
    }



    /**
     * Generate value based on generator type - package-private for reuse
     */
    String generateValue(GeneratorType generator) {
        if (generator == null) {
            generator = GeneratorType.UUID;
        }

        switch (generator) {
            case UUID:
                return UUID.randomUUID().toString();

            case ULID:
                return generateULID();

            case TIMESTAMP:
                return String.valueOf(System.currentTimeMillis());

            case SEQUENCE:
                return String.valueOf(sequenceGenerator.incrementAndGet());

            case RANDOM:
                return String.valueOf(ThreadLocalRandom.current().nextLong());

            case NANOID:
                return generateNanoId();

            default:
                return UUID.randomUUID().toString();
        }
    }

    /**
     * Apply transformation to value - package-private for reuse
     */
    public String applyTransformation(String value, TransformationType transformation, String expression) {
        if (value == null) {
            return null;
        }

        try {
            switch (transformation) {
                case UPPERCASE:
                    return value.toUpperCase();

                case LOWERCASE:
                    return value.toLowerCase();

                case TRIM:
                    return value.trim();

                case BASE64_ENCODE:
                    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

                case BASE64_DECODE:
                    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);

                case URL_ENCODE:
                    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);

                case URL_DECODE:
                    return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);

                case HASH_SHA256:
                    return hashSHA256(value);

                case CUSTOM:
                    return applyCustomTransformation(value, expression);

                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("Error applying transformation {}: {}", transformation, e.getMessage());
            return value;
        }
    }

    /**
     * Generate ULID
     */
    private String generateULID() {
        // Simple ULID implementation
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return timestamp + random;
    }

    /**
     * Generate Nano ID
     */
    private String generateNanoId() {
        // Simple NanoID implementation
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            id.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return id.toString();
    }

    /**
     * Hash value using SHA-256 - package-private for reuse
     */
    public String hashSHA256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("SHA-256 hashing failed", e);
            return value;
        }
    }

    /**
     * Apply custom transformation using expression
     */
    private String applyCustomTransformation(String value, String expression) {
        // Simple implementation - could integrate SpEL for complex transformations
        if (expression != null && expression.contains("substring")) {
            // Example: substring(0,5)
            try {
                String params = expression.substring(expression.indexOf('(') + 1, expression.indexOf(')'));
                String[] indices = params.split(",");
                int start = Integer.parseInt(indices[0].trim());
                int end = indices.length > 1 ? Integer.parseInt(indices[1].trim()) : value.length();
                return value.substring(start, Math.min(end, value.length()));
            } catch (Exception e) {
                log.debug("Custom transformation failed: {}", e.getMessage());
            }
        }
        return value;
    }




}