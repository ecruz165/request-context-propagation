package com.jefelabs.modules.requestcontext.filter;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.FieldConfiguration;
import com.jefelabs.modules.requestcontext.service.RequestContext;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
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
public class RequestContextWebClientLoggingFilter {

    private final RequestContextProperties properties;
    private final RequestContextService contextService;

    public RequestContextWebClientLoggingFilter(RequestContextProperties properties,
                                               RequestContextService contextService) {
        this.properties = properties;
        this.contextService = contextService;
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

        // Create a new request with start time attribute (request.attributes() is unmodifiable)
        ClientRequest enrichedRequest = ClientRequest.from(request)
                .attribute("request.startTime", Instant.now())
                .build();

        // Simple log with MDC context (MDC fields will be included by log appender)
        log.debug("→ WebClient Request: {} {}",
                enrichedRequest.method(),
                enrichedRequest.url());

        return Mono.just(enrichedRequest);
    }

    /**
     * Logs response with MDC context
     */
    private Mono<ClientResponse> logResponse(ClientResponse response) {
        // Ensure MDC is enriched
        enrichMDC();

        // Calculate duration
        long duration = 0;

        // Log based on status - MDC fields will be included automatically
        if (response.statusCode().is2xxSuccessful()) {
            log.debug("← WebClient Response: {} [{}ms]",
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
            log.debug("← WebClient Response: {} [{}ms]",
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
        Optional<RequestContext> contextOpt = contextService.getCurrentContext();
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


}