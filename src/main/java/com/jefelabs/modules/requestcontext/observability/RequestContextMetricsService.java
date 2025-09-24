package com.jefelabs.modules.requestcontext.observability;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.CardinalityLevel;
import com.jefelabs.modules.requestcontext.service.RequestContext;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to record custom metrics with RequestContext
 */
@Component
@Slf4j
public class RequestContextMetricsService {

    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final RequestContextService requestContextService;

    public RequestContextMetricsService(
            io.micrometer.core.instrument.MeterRegistry meterRegistry,
            RequestContextService requestContextService) {
        this.meterRegistry = meterRegistry;
        this.requestContextService = requestContextService;
    }

    /**
     * Record a custom metric with RequestContext tags
     */
    public void recordMetric(String metricName, double value) {
        requestContextService.getCurrentContext().ifPresent(context -> {
            List<io.micrometer.core.instrument.Tag> tags = buildTags(context, CardinalityLevel.LOW);
            meterRegistry.counter(metricName, tags).increment(value);
        });
    }

    /**
     * Record timing with RequestContext tags
     */
    public void recordTiming(String metricName, long milliseconds) {
        requestContextService.getCurrentContext().ifPresent(context -> {
            List<io.micrometer.core.instrument.Tag> tags = buildTags(context, CardinalityLevel.LOW);
            meterRegistry.timer(metricName, tags)
                    .record(java.time.Duration.ofMillis(milliseconds));
        });
    }

    /**
     * Build tags from RequestContext based on configuration
     */
    private List<io.micrometer.core.instrument.Tag> buildTags(RequestContext context, CardinalityLevel level) {
        List<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();

        // Get metrics fields for the specified cardinality level
        Map<String, String> metricsFields = requestContextService.getMetricsFields(context, level);

        // Convert to Micrometer tags
        metricsFields.forEach((tagName, value) ->
            tags.add(io.micrometer.core.instrument.Tag.of(tagName, value))
        );

        return tags;
    }
}
