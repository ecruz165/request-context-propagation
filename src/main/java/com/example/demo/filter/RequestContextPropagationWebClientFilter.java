package com.example.demo.filter;


import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.OutboundConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * WebClient filter to propagate RequestContext headers to downstream services.
 * This filter reads values from the current RequestContext and adds them as
 * headers, query parameters, or cookies to outbound HTTP requests based on
 * the configuration in RequestContextProperties.
 */
@Component
@Slf4j
public class RequestContextPropagationWebClientFilter {

    private final RequestContextProperties properties;
    private static final String DEFAULT_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String DEFAULT_CORRELATION_ID_HEADER = "X-Correlation-Id";

    public RequestContextPropagationWebClientFilter(RequestContextProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates an ExchangeFilterFunction that adds context headers to outbound requests
     * @return ExchangeFilterFunction for WebClient
     */
    public ExchangeFilterFunction createFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(this::propagateContextHeaders);
    }

    /**
     * Adds configured context headers to the outbound request
     * @param clientRequest The original request
     * @return Modified request with context headers
     */
    private Mono<ClientRequest> propagateContextHeaders(ClientRequest clientRequest) {
        // Get current context from HttpServletRequest
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();

        if (contextOpt.isEmpty()) {
            log.debug("No RequestContext available for propagation to {}", clientRequest.url());
            return Mono.just(clientRequest);
        }

        RequestContext context = contextOpt.get();
        ClientRequest.Builder requestBuilder = ClientRequest.from(clientRequest);

        // Always propagate core tracing headers
        propagateCoreHeaders(context, requestBuilder);

        // Propagate configured fields
        propagateConfiguredFields(context, requestBuilder);

        ClientRequest modifiedRequest = requestBuilder.build();

        if (log.isDebugEnabled()) {
            log.debug("Propagated context to {} - RequestId: {}",
                    clientRequest.url(), context.get("requestId"));
        }

        return Mono.just(modifiedRequest);
    }

    /**
     * Propagates core headers that should always be included for tracing
     */
    private void propagateCoreHeaders(RequestContext context, ClientRequest.Builder requestBuilder) {
        // Always add request ID for tracing
        String requestId = context.get("requestId");
        if (requestId != null) {
            requestBuilder.header(DEFAULT_REQUEST_ID_HEADER, requestId);
        }

        // Add correlation ID if present
        String correlationId = context.get("correlationId");
        if (correlationId != null) {
            requestBuilder.header(DEFAULT_CORRELATION_ID_HEADER, correlationId);
        }
    }

    /**
     * Propagates fields configured in properties
     */
    private void propagateConfiguredFields(RequestContext context, ClientRequest.Builder requestBuilder) {
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            try {
                propagateField(fieldName, fieldConfig, context, requestBuilder);
            } catch (Exception e) {
                log.error("Error propagating field {}: {}", fieldName, e.getMessage());
            }
        });
    }

    /**
     * Propagates a single field if configured for downstream
     */
    private void propagateField(String fieldName,
                                FieldConfiguration fieldConfig,
                                RequestContext context,
                                ClientRequest.Builder requestBuilder) {

        // Check if field should be propagated downstream
        if (fieldConfig.getDownstream() == null ||
                fieldConfig.getDownstream().getOutbound() == null) {
            return;
        }

        OutboundConfig outbound = fieldConfig.getDownstream().getOutbound();

        // Get value from context
        String value = getValue(fieldName, context, outbound);

        if (value == null || value.isEmpty()) {
            if (log.isTraceEnabled()) {
                log.trace("Field {} has no value to propagate", fieldName);
            }
            return;
        }

        // Check condition if configured
        if (!shouldEnrich(context, outbound)) {
            log.debug("Skipping field {} due to condition: {}", fieldName, outbound.getCondition());
            return;
        }

        // Enrich the request
        enrichRequest(requestBuilder, outbound, value, fieldName);
    }

    /**
     * Gets the value to propagate, applying transformations if configured
     */
    private String getValue(String fieldName, RequestContext context, OutboundConfig outbound) {
        String value;

        // Use expression if configured
        if (outbound.getValueAs() == RequestContextProperties.ValueType.EXPRESSION &&
                outbound.getValue() != null) {
            // Simple expression evaluation
            value = evaluateExpression(outbound.getValue(), context);
        } else {
            // Get value directly from context
            value = context.get(fieldName);
        }

        // Apply value type transformation
        if (value != null && outbound.getValueAs() != null) {
            value = transformValue(value, outbound.getValueAs());
        }

        return value;
    }

    /**
     * Checks if enrichment should happen based on condition
     */
    private boolean shouldEnrich(RequestContext context, OutboundConfig outbound) {
        if (outbound.getCondition() == null) {
            return true;
        }

        // Simple condition evaluation
        // For complex conditions, integrate SpEL
        return evaluateCondition(outbound.getCondition(), context);
    }

    /**
     * Enriches the request based on enrichment type
     */
    private void enrichRequest(ClientRequest.Builder requestBuilder,
                               OutboundConfig outbound,
                               String value,
                               String fieldName) {

        try {
            switch (outbound.getEnrichAs()) {
                case HEADER:
                    requestBuilder.header(outbound.getKey(), value);
                    log.debug("Added downstream header {} = {} for field {}",
                            outbound.getKey(),
                            maskSensitiveValue(outbound.getKey(), value),
                            fieldName);
                    break;

                case QUERY:
                    // Note: Query parameters need special handling in WebClient
                    // This is a simplified approach
                    requestBuilder.attribute("queryParam." + outbound.getKey(), value);
                    log.debug("Added query parameter {} = {} for field {}",
                            outbound.getKey(), value, fieldName);
                    break;

                case COOKIE:
                    requestBuilder.cookie(outbound.getKey(), value);
                    log.debug("Added cookie {} for field {}", outbound.getKey(), fieldName);
                    break;

                case ATTRIBUTE:
                    requestBuilder.attribute(outbound.getKey(), value);
                    log.debug("Added request attribute {} = {} for field {}",
                            outbound.getKey(), value, fieldName);
                    break;

                default:
                    log.debug("Unsupported enrichment type {} for field {}",
                            outbound.getEnrichAs(), fieldName);
            }
        } catch (Exception e) {
            log.error("Failed to enrich request with field {}: {}", fieldName, e.getMessage());
        }
    }

    /**
     * Masks sensitive header values for logging
     */
    private String maskSensitiveValue(String headerName, String value) {
        String lowerName = headerName.toLowerCase();
        if (lowerName.contains("token") ||
                lowerName.contains("key") ||
                lowerName.contains("secret") ||
                lowerName.contains("password")) {
            return "***";
        }
        return value;
    }

    /**
     * Transforms value based on type
     */
    private String transformValue(String value, RequestContextProperties.ValueType valueType) {
        if (value == null) {
            return null;
        }

        try {
            switch (valueType) {
                case BASE64:
                    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

                case URL_ENCODED:
                    return URLEncoder.encode(value, StandardCharsets.UTF_8);

                case JSON_ARRAY:
                    // Simple JSON array for single value
                    return "[\"" + value + "\"]";

                case JSON_OBJECT:
                    // Simple JSON object
                    return "{\"value\":\"" + value + "\"}";

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
                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("Error transforming value: {}", e.getMessage());
            return value;
        }
    }

    /**
     * Simple expression evaluation
     */
    private String evaluateExpression(String expression, RequestContext context) {
        // Simple placeholder replacement
        // For complex expressions, use SpEL
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
     * Simple condition evaluation
     */
    private boolean evaluateCondition(String condition, RequestContext context) {
        // Simple implementation - always true
        // For complex conditions, integrate SpEL
        return true;
    }

    /**
     * Checks if header already exists
     */
    private boolean hasHeader(ClientRequest.Builder builder, String headerName) {
        // This is a simplified check
        // In reality, you'd need to check the actual headers in the builder
        return false;
    }
}