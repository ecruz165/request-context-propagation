package com.example.demo.filter;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import com.example.demo.service.RequestContextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

/**
 * WebClient filter to capture downstream response headers into RequestContext.
 * Depends on RequestContextExtractor for value transformation and validation.
 */
@Component
@Slf4j
public class RequestContextCaptureWebClientFilter {

    private final RequestContextProperties properties;
    private final RequestContextExtractor extractor;

    public RequestContextCaptureWebClientFilter(RequestContextProperties properties,
                                                RequestContextExtractor extractor) {
        this.properties = properties;
        this.extractor = extractor;
    }

    /**
     * Creates an ExchangeFilterFunction that captures downstream response headers
     * @return ExchangeFilterFunction for WebClient
     */
    public ExchangeFilterFunction createFilter() {
        return ExchangeFilterFunction.ofResponseProcessor(this::captureResponseHeaders);
    }

    /**
     * Captures configured downstream response headers into RequestContext
     * @param clientResponse The response from downstream service
     * @return Mono with the same response
     */
    private Mono<ClientResponse> captureResponseHeaders(ClientResponse clientResponse) {
        // Get current context from HttpServletRequest
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();

        if (contextOpt.isEmpty()) {
            log.debug("No RequestContext available for capturing response headers");
            return Mono.just(clientResponse);
        }

        RequestContext context = contextOpt.get();
        int capturedCount = 0;

        // Process each configured field
        for (var entry : properties.getFields().entrySet()) {
            String fieldName = entry.getKey();
            FieldConfiguration fieldConfig = entry.getValue();

            if (shouldCaptureFromResponse(fieldConfig)) {
                boolean captured = captureFieldFromResponse(
                        fieldName, fieldConfig, clientResponse, context);
                if (captured) {
                    capturedCount++;
                }
            }
        }

        if (capturedCount > 0) {
            log.debug("Captured {} fields from downstream response for RequestId: {}",
                    capturedCount, context.get("requestId"));
        }

        return Mono.just(clientResponse);
    }

    /**
     * Check if field should be captured from downstream response
     */
    private boolean shouldCaptureFromResponse(FieldConfiguration fieldConfig) {
        return fieldConfig.getDownstream() != null &&
                fieldConfig.getDownstream().getInbound() != null &&
                fieldConfig.getDownstream().getInbound().getSource() == SourceType.HEADER;
    }

    /**
     * Captures a single field from the response if configured
     * @return true if field was successfully captured
     */
    private boolean captureFieldFromResponse(String fieldName,
                                             FieldConfiguration fieldConfig,
                                             ClientResponse response,
                                             RequestContext context) {

        InboundConfig inbound = fieldConfig.getDownstream().getInbound();

        // Get header values from response
        List<String> headerValues = response.headers().header(inbound.getKey());

        if (headerValues.isEmpty()) {
            // Try default value if configured
            if (inbound.getDefaultValue() != null) {
                processAndStoreValue(fieldName, inbound.getDefaultValue(),
                        fieldConfig, context, true);
                return true;
            }

            // Log if required field is missing
            if (inbound.isRequired()) {
                log.warn("Required downstream header '{}' not found in response for field '{}'",
                        inbound.getKey(), fieldName);
            }
            return false;
        }

        // Take first value if multiple headers present
        String value = headerValues.get(0);

        // Process and store the value
        return processAndStoreValue(fieldName, value, fieldConfig, context, false);
    }

    /**
     * Process the captured value (transform, validate) and store in context
     * @return true if value was successfully processed and stored
     */
    private boolean processAndStoreValue(String fieldName,
                                         String value,
                                         FieldConfiguration fieldConfig,
                                         RequestContext context,
                                         boolean isDefault) {

        InboundConfig inbound = fieldConfig.getDownstream().getInbound();

        try {
            // Apply transformation if configured
            if (inbound.getTransformation() != null && !isDefault) {
                value = applyTransformation(value, inbound);
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
                    return false;
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

            return true;

        } catch (Exception e) {
            log.error("Error processing downstream field '{}': {}", fieldName, e.getMessage());

            if (inbound.isRequired()) {
                throw new RuntimeException(
                        "Failed to process required downstream field: " + fieldName, e);
            }
            return false;
        }
    }

    /**
     * Apply transformation to the captured value.
     * Delegates to extractor's transformation logic for consistency.
     */
    private String applyTransformation(String value, InboundConfig inbound) {
        if (value == null) {
            return null;
        }

        // Use the shared transformation method from RequestContextExtractor
        return extractor.applyTransformation(
                value,
                inbound.getTransformation(),
                inbound.getTransformExpression()
        );
    }

    /**
     * Apply custom transformation using expression
     */
    private String applyCustomTransformation(String value, String expression) {
        // This is now handled by extractor.applyTransformation
        return value;
    }

    /**
     * Hash value using SHA-256
     */
    private String hashSHA256(String value) {
        // Use the shared method from RequestContextExtractor
        return extractor.hashSHA256(value);
    }

    /**
     * Check if field is sensitive
     */
    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null &&
                fieldConfig.getSecurity().isSensitive();
    }

    /**
     * Mask sensitive value according to pattern
     */
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

    /**
     * Creates a capture filter that only processes specific headers
     */
    public static ExchangeFilterFunction createSelectiveFilter(
            RequestContextProperties properties,
            RequestContextExtractor extractor,
            String... headerNames) {

        RequestContextCaptureWebClientFilter filter =
                new RequestContextCaptureWebClientFilter(properties, extractor);

        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            // Only process if response contains one of the specified headers
            boolean hasTargetHeader = false;
            for (String headerName : headerNames) {
                if (!response.headers().header(headerName).isEmpty()) {
                    hasTargetHeader = true;
                    break;
                }
            }

            if (hasTargetHeader) {
                return filter.captureResponseHeaders(response);
            }

            return Mono.just(response);
        });
    }
}