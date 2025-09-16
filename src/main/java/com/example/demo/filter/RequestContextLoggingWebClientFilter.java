package com.example.demo.filter;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Optional;

/**
 * WebClient filter that enriches MDC with RequestContext fields for logging
 */
@Component
@Slf4j
public class RequestContextLoggingWebClientFilter {

    private final RequestContextProperties properties;

    public RequestContextLoggingWebClientFilter(RequestContextProperties properties) {
        this.properties = properties;
    }

    /**
     * Creates a filter that enriches MDC and logs requests/responses
     */
    public ExchangeFilterFunction createFilter() {
        return ExchangeFilterFunction.ofRequestProcessor(this::logRequest)
                .andThen(ExchangeFilterFunction.ofResponseProcessor(this::logResponse));
    }

    /**
     * Logs outbound request with MDC context
     */
    private Mono<ClientRequest> logRequest(ClientRequest request) {
        // Enrich MDC with RequestContext fields
        enrichMDC();

        // Store start time for duration calculation
        request.attributes().put("request.startTime", Instant.now());

        // Simple log with MDC context (MDC fields will be included by log appender)
        log.info("→ WebClient Request: {} {}",
                request.method(),
                request.url());

        return Mono.just(request);
    }

    /**
     * Logs response with MDC context
     */
    private Mono<ClientResponse> logResponse(ClientResponse response) {
        // Ensure MDC is enriched
        enrichMDC();

        // Calculate duration
        long duration = 0;
        // Note: attributes() method not available in this version
        // if (response.request().attributes().get("request.startTime") instanceof Instant startTime) {
        //     duration = Duration.between(startTime, Instant.now()).toMillis();
        //     MDC.put("duration_ms", String.valueOf(duration));
        // }

        // Log based on status - MDC fields will be included automatically
        if (response.statusCode().is2xxSuccessful()) {
            log.info("← WebClient Response: {} [{}ms]",
                    response.statusCode().value(),
                    duration);
        } else if (response.statusCode().is4xxClientError()) {
            log.warn("← WebClient Response Error: {} [{}ms]",
                    response.statusCode().value(),
                    duration);
                    // response.request().url() not available in this version
        } else if (response.statusCode().is5xxServerError()) {
            log.error("← WebClient Response Error: {} [{}ms]",
                    response.statusCode().value(),
                    duration);
                    // response.request().url() not available in this version
        } else {
            log.info("← WebClient Response: {} [{}ms]",
                    response.statusCode().value(),
                    duration);
        }

        // Clean up duration from MDC
        MDC.remove("duration_ms");

        return Mono.just(response);
    }

    /**
     * Enriches MDC with configured RequestContext fields
     */
    private void enrichMDC() {
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();
        if (contextOpt.isEmpty()) {
            return;
        }

        RequestContext context = contextOpt.get();

        // Add fields configured for logging to MDC
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (shouldIncludeInMDC(fieldConfig)) {
                String value = context.get(fieldName);
                if (value != null) {
                    // Use masked value for sensitive fields
                    if (isSensitive(fieldConfig)) {
                        value = context.getMaskedOrOriginal(fieldName);
                    }

                    // Use configured MDC key or field name
                    String mdcKey = getMdcKey(fieldConfig, fieldName);
                    MDC.put(mdcKey, value);
                }
            }
        });

        // Always add core fields if present
        addToMdcIfPresent(context, "requestId", "request_id");
        addToMdcIfPresent(context, "correlationId", "correlation_id");
        addToMdcIfPresent(context, "handler", "handler");
        addToMdcIfPresent(context, "principal", "user");
    }

    /**
     * Check if field should be included in MDC
     */
    private boolean shouldIncludeInMDC(FieldConfiguration fieldConfig) {
        return fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getLogging() != null &&
                fieldConfig.getObservability().getLogging().isEnabled();
    }

    /**
     * Get MDC key for field
     */
    private String getMdcKey(FieldConfiguration fieldConfig, String fieldName) {
        if (fieldConfig.getObservability() != null &&
                fieldConfig.getObservability().getLogging() != null &&
                fieldConfig.getObservability().getLogging().getMdcKey() != null) {
            return fieldConfig.getObservability().getLogging().getMdcKey();
        }
        return fieldName;
    }

    /**
     * Check if field is sensitive
     */
    private boolean isSensitive(FieldConfiguration fieldConfig) {
        return fieldConfig.getSecurity() != null &&
                fieldConfig.getSecurity().isSensitive();
    }

    /**
     * Add field to MDC if present in context
     */
    private void addToMdcIfPresent(RequestContext context, String fieldName, String mdcKey) {
        String value = context.get(fieldName);
        if (value != null) {
            MDC.put(mdcKey, value);
        }
    }
}