package com.example.demo.observability;

import com.example.demo.config.props.RequestContextProperties;
import lombok.extern.slf4j.Slf4j;

/**
 * Custom WebMvc tags provider that adds RequestContext data
 * Note: WebMvcTagsProvider is not available in Spring Boot 3
 * This class is disabled for now
 */
@Slf4j
public class RequestContextWebMvcTagsProvider {

    private final RequestContextProperties properties;

    public RequestContextWebMvcTagsProvider(RequestContextProperties properties) {
        this.properties = properties;
    }

    // Disabled for Spring Boot 3 compatibility
    /*
    @Override
    public Iterable<io.micrometer.core.instrument.Tag> getTags(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Throwable exception) {

        // Get default tags
        List<io.micrometer.core.instrument.Tag> tags = new ArrayList<>();
        // Add basic tags manually since we're not extending DefaultWebMvcTagsProvider
        tags.add(io.micrometer.core.instrument.Tag.of("method", request.getMethod()));
        tags.add(io.micrometer.core.instrument.Tag.of("uri", request.getRequestURI()));
        if (response != null) {
            tags.add(io.micrometer.core.instrument.Tag.of("status", String.valueOf(response.getStatus())));
        }

        // Add RequestContext tags
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();
        if (contextOpt.isPresent()) {
            RequestContext context = contextOpt.get();

            // Add configured low cardinality fields
            properties.getFields().forEach((fieldName, fieldConfig) -> {
                if (shouldIncludeAsTag(fieldConfig)) {
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

            // Add handler tag
            String handlerName = context.get("handler");
            if (handlerName != null) {
                tags.add(io.micrometer.core.instrument.Tag.of("handler", handlerName));
            }
        }

        return tags;
    }

    private boolean shouldIncludeAsTag(FieldConfiguration fieldConfig) {
        if (fieldConfig.getObservability() == null ||
                fieldConfig.getObservability().getMetrics() == null) {
            return false;
        }

        MetricsConfig metricsConfig = fieldConfig.getObservability().getMetrics();
        CardinalityLevel cardinality = metricsConfig.getCardinality();

        // Only include low and medium cardinality fields as tags
        return metricsConfig.isEnabled() &&
                (cardinality == CardinalityLevel.LOW || cardinality == CardinalityLevel.MEDIUM);
    }

    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null && fieldConfig.getSecurity().isSensitive();
    }
    */
}
