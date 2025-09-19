package com.example.demo.filter;

import com.example.demo.config.RequestContext;
import com.example.demo.service.RequestContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * WebClient filter to capture downstream response headers into RequestContext.
 * Leverages RequestContextService for consistent context management.
 */
@Component
@Slf4j
public class RequestContextWebClientCaptureFilter {

    private final RequestContextService contextService;

    public RequestContextWebClientCaptureFilter(RequestContextService contextService) {
        this.contextService = contextService;
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
        // Get current context from ThreadLocal
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();

        if (contextOpt.isEmpty()) {
            log.debug("No RequestContext available for capturing response headers");
            return Mono.just(clientResponse);
        }

        RequestContext context = contextOpt.get();

        // Use service to enrich context with downstream response data
        contextService.enrichWithDownstreamResponse(clientResponse, context);

        log.debug("Processed downstream response for RequestId: {}", context.get("requestId"));

        return Mono.just(clientResponse);
    }



    /**
     * Creates a capture filter that only processes specific headers
     */
    public static ExchangeFilterFunction createSelectiveFilter(
            RequestContextService contextService,
            String... headerNames) {

        RequestContextWebClientCaptureFilter filter =
                new RequestContextWebClientCaptureFilter(contextService);

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