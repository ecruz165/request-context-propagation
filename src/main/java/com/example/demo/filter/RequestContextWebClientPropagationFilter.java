package com.example.demo.filter;


import com.example.demo.config.RequestContext;
import com.example.demo.service.RequestContextEnricher;
import com.example.demo.service.RequestContextEnricher.PropagationData;
import com.example.demo.util.MaskingHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

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
public class RequestContextWebClientPropagationFilter {

    private final RequestContextEnricher enricher;
    private final MaskingHelper maskingHelper;
    private final com.example.demo.service.source.SourceHandlers sourceHandlers;
    private static final String DEFAULT_REQUEST_ID_HEADER = "X-Request-Id";
    private static final String DEFAULT_CORRELATION_ID_HEADER = "X-Correlation-Id";

    public RequestContextWebClientPropagationFilter(RequestContextEnricher enricher,
                                                    MaskingHelper maskingHelper,
                                                    com.example.demo.service.source.SourceHandlers sourceHandlers) {
        this.enricher = enricher;
        this.maskingHelper = maskingHelper;
        this.sourceHandlers = sourceHandlers;
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

        // Use enricher to get propagation data
        Map<String, PropagationData> propagationData = enricher.extractForDownstreamPropagation(context);

        // Apply propagation data to request
        propagationData.forEach((fieldName, data) -> {
            applyPropagationData(requestBuilder, data, fieldName);
        });

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
     * Apply propagation data to the request builder using unified source handler strategy
     */
    private void applyPropagationData(ClientRequest.Builder requestBuilder, PropagationData data, String fieldName) {
        try {
            // Use the unified source handler strategy pattern
            sourceHandlers.enrichDownstreamRequest(
                data.getEnrichmentType(),
                requestBuilder,
                data.getKey(),
                data.getValue()
            );

            // Log with masking where appropriate
            log.debug("Applied downstream {} enrichment {} = {} for field {}",
                    data.getEnrichmentType(),
                    data.getKey(),
                    data.getMaskedValue(maskingHelper),
                    fieldName);

        } catch (Exception e) {
            log.error("Failed to apply propagation data for field {}: {}", fieldName, e.getMessage());
        }
    }








}