package com.example.demo.observability;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.CardinalityLevel;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.MetricsConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Service to record custom metrics with RequestContext
 */
@Component
@Slf4j
public class RequestContextMetricsService {

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final RequestContextProperties properties;

    public RequestContextMetricsService(
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            RequestContextProperties properties) {
        this.meterRegistry = meterRegistry;
        this.properties = properties;
    }

    /**
     * Record a custom metric with RequestContext tags
     */
    public void recordMetric(String metricName, double value) {
        RequestContext.getCurrentContext().ifPresent(context -> {
            List<io.micrometer.core.instrument.Tag> tags = buildTags(context);
            meterRegistry.counter(metricName, tags).increment(value);
        });
    }

    /**
     * Record timing with RequestContext tags
     */
    public void recordTiming(String metricName, long milliseconds) {
        RequestContext.getCurrentContext().ifPresent(context -> {
            List<io.micrometer.core.instrument.Tag> tags = buildTags(context);
            meterRegistry.timer(metricName, tags)
                    .record(java.time.Duration.ofMillis(milliseconds));
        });
    }

    /**
     * Build tags from RequestContext based on configuration
     */
    private List<io.micrometer.core.instrument.Tag> buildTags(RequestContext context) {
        List<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();

        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMetrics(fieldConfig)) {
                String value = context.get(fieldName);
                if (value != null) {
                    // Use masked value for sensitive fields
                    if (isSensitive(fieldConfig)) {
                        value = context.getMaskedOrOriginal(fieldName);
                    }
                    tags.add(io.micrometer.core.instrument.Tag.of(fieldName, value));
                }
            }
        });

        // Always add handler if available
        String handler = context.get("handler");
        if (handler != null) {
            tags.add(io.micrometer.core.instrument.Tag.of("handler", handler));
        }

        return tags;
    }

    private boolean shouldIncludeInMetrics(FieldConfiguration fieldConfig) {
        if (fieldConfig.getObservability() == null ||
                fieldConfig.getObservability().getMetrics() == null) {
            return false;
        }

        MetricsConfig metricsConfig = fieldConfig.getObservability().getMetrics();
        CardinalityLevel cardinality = metricsConfig.getCardinality();

        // Only include low and medium cardinality fields
        return metricsConfig.isEnabled() &&
                (cardinality == CardinalityLevel.LOW || cardinality == CardinalityLevel.MEDIUM);
    }

    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive();
    }
}
