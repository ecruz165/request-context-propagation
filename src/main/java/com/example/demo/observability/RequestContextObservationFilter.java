package com.example.demo.observability;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Observation filter to add RequestContext to traces
 */
@Component
@Order(0)
@Slf4j
public class RequestContextObservationFilter implements ObservationFilter {

    private final RequestContextProperties properties;
    private final Tracer tracer;

    public RequestContextObservationFilter(RequestContextProperties properties, Tracer tracer) {
        this.properties = properties;
        this.tracer = tracer;
    }

    @Override
    public Observation.Context map(Observation.Context context) {
        // Add RequestContext data to observation context
        if (context instanceof ServerRequestObservationContext) {
            ServerRequestObservationContext serverContext = (ServerRequestObservationContext) context;
            enrichWithRequestContext(serverContext);
        }
        return context;
    }

    private void enrichWithRequestContext(ServerRequestObservationContext context) {
        Optional<RequestContext> requestContextOpt = RequestContext.getCurrentContext();
        if (requestContextOpt.isEmpty()) {
            return;
        }

        RequestContext requestContext = requestContextOpt.get();

        // Add trace baggage for propagation
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            // Add fields configured for tracing
            properties.getFields().forEach((fieldName, fieldConfig) -> {
                if (shouldIncludeInTracing(fieldConfig)) {
                    String value = requestContext.get(fieldName);
                    if (value != null && !isSensitive(fieldConfig)) {
                        String tagName = getTraceTagName(fieldConfig, fieldName);
                        currentSpan.tag(tagName, value);

                        // Add to baggage if propagation is enabled
                        if (shouldPropagate(fieldConfig)) {
                            // currentSpan.baggage(tagName, value); // Method not available in this version
                        }
                    }
                }
            });

            // Always add core fields
            String requestId = requestContext.get("requestId");
            if (requestId != null) {
                currentSpan.tag("request.id", requestId);
                // currentSpan.baggage("request.id", requestId); // Method not available in this version
            }

            String handler = requestContext.get("handler");
            if (handler != null) {
                currentSpan.tag("handler", handler);
            }
        }
    }

    private boolean shouldIncludeInTracing(FieldConfiguration fieldConfig) {
        return fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getTracing() != null &&
                fieldConfig.getObservability().getTracing().isEnabled();
    }

    private boolean shouldPropagate(FieldConfiguration fieldConfig) {
        return fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getTracing() != null;
                // && fieldConfig.getObservability().getTracing().isPropagate(); // Method not available
    }

    private String getTraceTagName(FieldConfiguration fieldConfig, String fieldName) {
        if (fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getTracing() != null &&
                fieldConfig.getObservability().getTracing().getTagName() != null) {
            return fieldConfig.getObservability().getTracing().getTagName();
        }
        return fieldName;
    }

    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive();
    }
}
