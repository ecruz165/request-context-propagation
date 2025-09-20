package com.example.demo.filter;

import com.example.demo.service.RequestContext;
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
     * Captures configured downstream response data into RequestContext
     * Handles response body buffering to prevent consumption issues
     * @param clientResponse The response from downstream service
     * @return Mono with a new response that can be consumed by the application
     */
    private Mono<ClientResponse> captureResponseHeaders(ClientResponse clientResponse) {
        return Mono.deferContextual(contextView -> {
            // Try to get context from Reactor Context first
            if (contextView.hasKey("REQUEST_CONTEXT")) {
                RequestContext context = contextView.get("REQUEST_CONTEXT");
                return processResponseWithContext(clientResponse, context, "Reactor Context");
            }

            // Fallback to service if Reactor Context is not available
            Optional<RequestContext> contextOpt = contextService.getCurrentContext();

            if (contextOpt.isEmpty()) {
                log.debug("No RequestContext available for capturing response data");
                return Mono.just(clientResponse);
            }

            RequestContext context = contextOpt.get();
            return processResponseWithContext(clientResponse, context, "ThreadLocal");
        });
    }

    /**
     * Process response with context, handling body buffering if needed
     */
    private Mono<ClientResponse> processResponseWithContext(ClientResponse clientResponse, RequestContext context, String contextSource) {
        // Check if we need to extract from response body
        if (contextService.hasDownstreamBodyExtractionFields()) {
            // Buffer the response body to allow multiple reads
            return bufferResponseBody(clientResponse)
                    .map(bufferedResponse -> {
                        // Use service to enrich context with downstream response data
                        contextService.enrichWithDownstreamResponse(bufferedResponse, context);

                        log.debug("Processed downstream response for RequestId: {} (from {})",
                                context.get("requestId"), contextSource);

                        return bufferedResponse;
                    });
        } else {
            // No body extraction needed, process normally
            contextService.enrichWithDownstreamResponse(clientResponse, context);

            log.debug("Processed downstream response for RequestId: {} (from {})",
                    context.get("requestId"), contextSource);

            return Mono.just(clientResponse);
        }
    }

    /**
     * Buffer the response body to allow multiple reads
     * This prevents the "body already consumed" issue
     */
    private Mono<ClientResponse> bufferResponseBody(ClientResponse response) {
        return response.bodyToMono(String.class)
                .defaultIfEmpty("")
                .map(bodyContent -> {
                    // Create a new ClientResponse with the buffered body
                    return ClientResponse.create(response.statusCode())
                            .headers(headers -> headers.addAll(response.headers().asHttpHeaders()))
                            .cookies(cookies -> response.cookies().forEach(cookies::addAll))
                            .body(bodyContent)
                            .build();
                })
                .onErrorResume(throwable -> {
                    log.warn("Failed to buffer response body, returning original response: {}", throwable.getMessage());
                    return Mono.just(response);
                });
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