package com.example.demo.observability;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.CardinalityLevel;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.MetricsConfig;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Observation convention that enriches metrics and traces with RequestContext data
 */
@Component
@Slf4j
public class RequestContextObservationConvention implements ServerRequestObservationConvention {

    private final RequestContextProperties properties;
    private final ServerRequestObservationConvention defaultConvention;

    public RequestContextObservationConvention(RequestContextProperties properties) {
        this.properties = properties;
        this.defaultConvention = new DefaultServerRequestObservationConvention();
    }

    @Override
    public String getName() {
        return "http.server.requests";
    }

    @Override
    public String getContextualName(ServerRequestObservationContext context) {
        return defaultConvention.getContextualName(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        // Start with default key values
        KeyValues defaultKeyValues = defaultConvention.getLowCardinalityKeyValues(context);

        // Get RequestContext
        Optional<RequestContext> requestContextOpt = getRequestContext(context);
        if (requestContextOpt.isEmpty()) {
            return defaultKeyValues;
        }

        RequestContext requestContext = requestContextOpt.get();
        List<KeyValue> additionalKeyValues = new ArrayList<>();

        // Add low cardinality fields from RequestContext
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMetrics(fieldConfig, CardinalityLevel.LOW)) {
                String value = requestContext.get(fieldName);
                if (value != null) {
                    // Use masked value for sensitive fields
                    if (isSensitive(fieldConfig)) {
                        value = requestContext.getMaskedOrOriginal(fieldName);
                    }
                    additionalKeyValues.add(KeyValue.of(fieldName, value));
                }
            }
        });

        // Add handler information if available
        String handler = requestContext.get("handler");
        if (handler != null) {
            additionalKeyValues.add(KeyValue.of("handler", handler));
        }

        // Combine default and additional key values
        return defaultKeyValues.and(additionalKeyValues.toArray(new KeyValue[0]));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        // Start with default high cardinality key values
        KeyValues defaultKeyValues = defaultConvention.getHighCardinalityKeyValues(context);

        // Get RequestContext
        Optional<RequestContext> requestContextOpt = getRequestContext(context);
        if (requestContextOpt.isEmpty()) {
            return defaultKeyValues;
        }

        RequestContext requestContext = requestContextOpt.get();
        List<KeyValue> additionalKeyValues = new ArrayList<>();

        // Always add request ID for tracing
        String requestId = requestContext.get("requestId");
        if (requestId != null) {
            additionalKeyValues.add(KeyValue.of("request.id", requestId));
        }

        // Add medium and high cardinality fields
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            CardinalityLevel cardinality = getCardinality(fieldConfig);
            if (cardinality == CardinalityLevel.MEDIUM || cardinality == CardinalityLevel.HIGH) {
                if (shouldIncludeInMetrics(fieldConfig, cardinality)) {
                    String value = requestContext.get(fieldName);
                    if (value != null && !isSensitive(fieldConfig)) {
                        additionalKeyValues.add(KeyValue.of(fieldName, value));
                    }
                }
            }
        });

        // Add principal if authenticated
        String principal = requestContext.get("principal");
        if (principal != null && !"anonymous".equals(principal)) {
            additionalKeyValues.add(KeyValue.of("user", principal));
        }

        return defaultKeyValues.and(additionalKeyValues.toArray(new KeyValue[0]));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    /**
     * Get RequestContext from observation context
     */
    private Optional<RequestContext> getRequestContext(ServerRequestObservationContext context) {
        HttpServletRequest request = context.getCarrier();
        if (request != null) {
            Object contextAttr = request.getAttribute(RequestContext.REQUEST_CONTEXT_ATTRIBUTE);
            if (contextAttr instanceof RequestContext) {
                return Optional.of((RequestContext) contextAttr);
            }
        }
        return RequestContext.getCurrentContext();
    }

    /**
     * Check if field should be included in metrics
     */
    private boolean shouldIncludeInMetrics(FieldConfiguration fieldConfig, CardinalityLevel level) {
        if (fieldConfig.getObservability() == null ||
                fieldConfig.getObservability().getMetrics() == null) {
            return false;
        }

        MetricsConfig metricsConfig = fieldConfig.getObservability().getMetrics();
        return metricsConfig.isEnabled() && metricsConfig.getCardinality() == level;
    }

    /**
     * Get cardinality level for field
     */
    private CardinalityLevel getCardinality(FieldConfiguration fieldConfig) {
        if (fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getMetrics() != null) {
            return fieldConfig.getObservability().getMetrics().getCardinality();
        }
        return CardinalityLevel.NONE;
    }

    /**
     * Check if field is sensitive
     */
    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive();
    }
}