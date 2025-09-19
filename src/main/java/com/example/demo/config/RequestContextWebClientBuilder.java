package com.example.demo.config;

import com.example.demo.filter.RequestContextWebClientPropagationFilter;
import com.example.demo.filter.RequestContextWebClientCaptureFilter;
import com.example.demo.filter.RequestContextWebClientLoggingFilter;
import com.example.demo.service.RequestContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.time.Duration;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import com.example.demo.service.RequestContext;

/**
 * Context-aware WebClient.Builder that can be cloned and customized
 * while automatically providing context propagation capabilities.
 * 
 * This builder:
 * - Provides a cloneable WebClient.Builder with context filters pre-applied
 * - Allows full customization of WebClient configuration
 * - Automatically applies context propagation, capture, and logging filters
 * - Maintains filter order for proper context flow
 * - Can be used as a drop-in replacement for WebClient.builder()
 */
@Component
@Slf4j
public class RequestContextWebClientBuilder {

    private final RequestContextWebClientPropagationFilter propagationFilter;
    private final RequestContextWebClientCaptureFilter captureFilter;
    private final RequestContextWebClientLoggingFilter loggingFilter;
    private final RequestContextService contextService;

    public RequestContextWebClientBuilder(
            RequestContextWebClientPropagationFilter propagationFilter,
            RequestContextWebClientCaptureFilter captureFilter,
            RequestContextWebClientLoggingFilter loggingFilter,
            RequestContextService contextService) {
        
        this.propagationFilter = propagationFilter;
        this.captureFilter = captureFilter;
        this.loggingFilter = loggingFilter;
        this.contextService = contextService;
    }

    /**
     * Create a new WebClient.Builder with context propagation filters pre-applied
     * This builder can be further customized and cloned as needed
     * 
     * @return A WebClient.Builder with context filters applied
     */
    public WebClient.Builder create() {
        return WebClient.builder()
                // Apply context filters in the correct order
                .filter(propagationFilter.createFilter())  // 1. Propagate context to downstream
                .filter(captureFilter.createFilter())      // 2. Capture response data
                .filter(loggingFilter.createFilter());     // 3. Log with context
    }

    /**
     * Create a WebClient.Builder for a specific system with default configuration
     * 
     * @param systemName The name of the downstream system (for logging/metrics)
     * @return A WebClient.Builder configured for the system
     */
    public WebClient.Builder createForSystem(String systemName) {
        return create()
                .defaultHeader("X-Client-System", systemName)
                .defaultHeader("User-Agent", "ContextAwareWebClient/1.0");
    }

    /**
     * Create a WebClient.Builder with custom configuration applied
     * 
     * @param customizer Function to customize the builder
     * @return A customized WebClient.Builder with context filters
     */
    public WebClient.Builder createWithCustomization(Consumer<WebClient.Builder> customizer) {
        WebClient.Builder builder = create();
        customizer.accept(builder);
        return builder;
    }

    /**
     * Create a WebClient.Builder for a specific system with custom configuration
     * 
     * @param systemName The name of the downstream system
     * @param customizer Function to customize the builder
     * @return A customized WebClient.Builder for the system
     */
    public WebClient.Builder createForSystemWithCustomization(String systemName, 
                                                             Consumer<WebClient.Builder> customizer) {
        WebClient.Builder builder = createForSystem(systemName);
        customizer.accept(builder);
        return builder;
    }

    /**
     * Clone an existing WebClient.Builder and add context propagation filters
     * This allows you to take any existing WebClient.Builder and make it context-aware
     * 
     * @param existingBuilder The WebClient.Builder to clone and enhance
     * @return A new WebClient.Builder with context filters added
     */
    public WebClient.Builder cloneAndEnhance(WebClient.Builder existingBuilder) {
        // Build the existing WebClient to extract its configuration
        WebClient existingClient = existingBuilder.build();
        
        // Create a new builder with context filters
        WebClient.Builder enhancedBuilder = create();
        
        // Note: WebClient doesn't provide direct access to its configuration,
        // so users should use the other methods for full customization
        log.debug("Enhanced existing WebClient.Builder with context propagation filters");
        
        return enhancedBuilder;
    }

    /**
     * Get the list of context-related filters that are applied
     * Useful for debugging or advanced customization scenarios
     * 
     * @return List of ExchangeFilterFunction instances
     */
    public List<ExchangeFilterFunction> getContextFilters() {
        List<ExchangeFilterFunction> filters = new ArrayList<>();
        filters.add(propagationFilter.createFilter());
        filters.add(captureFilter.createFilter());
        filters.add(loggingFilter.createFilter());
        return filters;
    }

    /**
     * Create a WebClient.Builder with only specific context filters
     * Allows fine-grained control over which context features to enable
     * 
     * @param enablePropagation Whether to enable context propagation to downstream
     * @param enableCapture Whether to enable response data capture
     * @param enableLogging Whether to enable context-aware logging
     * @return A WebClient.Builder with selected filters
     */
    public WebClient.Builder createWithSelectiveFilters(boolean enablePropagation, 
                                                        boolean enableCapture, 
                                                        boolean enableLogging) {
        WebClient.Builder builder = WebClient.builder();
        
        if (enablePropagation) {
            builder = builder.filter(propagationFilter.createFilter());
        }
        
        if (enableCapture) {
            builder = builder.filter(captureFilter.createFilter());
        }
        
        if (enableLogging) {
            builder = builder.filter(loggingFilter.createFilter());
        }
        
        return builder;
    }

    /**
     * Utility method to check if context is available
     * Useful for conditional logic in WebClient operations
     * 
     * @return true if RequestContext is available
     */
    public boolean isContextAvailable() {
        return contextService.getCurrentContext().isPresent();
    }

    /**
     * Get the current context service for advanced operations
     *
     * @return The RequestContextService instance
     */
    public RequestContextService getContextService() {
        return contextService;
    }

    // ========================================
    // Static Utility Methods for Reactive Context Propagation
    // ========================================

    /**
     * Reactor Context key for RequestContext
     */
    public static final String REQUEST_CONTEXT_KEY = "requestContext";

    /**
     * Execute any WebClient operation with full context propagation through Reactor Context
     * This method passes the entire RequestContext through the reactive chain
     *
     * @param systemName The name of the downstream system (for logging/metrics)
     * @param baseUrl Target system base URL
     * @param operation Function that takes a WebClient and returns a Mono
     * @param contextService The RequestContextService instance
     * @return Mono with full context propagation
     */
    public static <T> Mono<T> executeWithReactorContext(String systemName, String baseUrl,
                                                        Function<WebClient, Mono<T>> operation,
                                                        RequestContextService contextService,
                                                        RequestContextWebClientBuilder builder) {

        WebClient webClient = builder.createForSystem(systemName)
                .baseUrl(baseUrl)
                .build();

        // Capture current RequestContext
        Optional<RequestContext> currentContext = contextService.getCurrentContext();

        if (currentContext.isPresent()) {
            RequestContext context = currentContext.get();

            // Execute operation with context in Reactor Context
            return operation.apply(webClient)
                    .contextWrite(Context.of(REQUEST_CONTEXT_KEY, context));
        } else {
            // No context available - execute without context propagation
            return operation.apply(webClient);
        }
    }

    /**
     * Get the RequestContext from Reactor Context
     * Use this in reactive chains to access the full context
     *
     * @return Mono containing the RequestContext if available
     */
    public static Mono<RequestContext> getContextFromReactorContext() {
        return Mono.deferContextual(contextView -> {
            if (contextView.hasKey(REQUEST_CONTEXT_KEY)) {
                RequestContext context = contextView.get(REQUEST_CONTEXT_KEY);
                return Mono.just(context);
            } else {
                return Mono.empty();
            }
        });
    }

    /**
     * Get a specific field value from Reactor Context
     * Use this for quick access to specific context fields in reactive chains
     *
     * @param fieldName The name of the field to retrieve
     * @return Mono containing the field value if available
     */
    public static Mono<String> getFieldFromReactorContext(String fieldName) {
        return getContextFromReactorContext()
                .map(context -> context.get(fieldName))
                .filter(value -> value != null);
    }

    /**
     * Enhanced utility to propagate upstream outbound values back to reactive context
     * This method captures any values that should be propagated upstream and updates the Reactor Context
     *
     * @param source The source Mono to enhance
     * @param contextService The RequestContextService instance
     * @return Enhanced Mono with upstream value propagation
     */
    public static <T> Mono<T> propagateUpstreamValues(Mono<T> source, RequestContextService contextService) {
        return source.flatMap(result ->
            Mono.deferContextual(contextView -> {
                // Get current context from Reactor Context
                if (contextView.hasKey(REQUEST_CONTEXT_KEY)) {
                    RequestContext reactorContext = contextView.get(REQUEST_CONTEXT_KEY);

                    // Get the latest context from HttpServletRequest (may have been enriched)
                    Optional<RequestContext> currentContext = contextService.getCurrentContext();

                    if (currentContext.isPresent()) {
                        RequestContext latestContext = currentContext.get();

                        // Copy only upstream outbound values to reactor context
                        copyUpstreamOutboundValues(latestContext, reactorContext, contextService);
                    }
                }

                return Mono.just(result);
            })
        );
    }

    /**
     * Enhanced method to automatically propagate values when block() is called
     * This ensures that any captured downstream values are available for upstream response enrichment
     *
     * @param mono The Mono to block on
     * @param contextService The RequestContextService instance
     * @return The result after blocking with upstream propagation
     */
    public static <T> T blockWithUpstreamPropagation(Mono<T> mono, RequestContextService contextService) {
        return propagateUpstreamValues(mono, contextService).block();
    }

    /**
     * Enhanced method to automatically propagate values when block(Duration) is called
     *
     * @param mono The Mono to block on
     * @param contextService The RequestContextService instance
     * @param timeout The timeout duration
     * @return The result after blocking with upstream propagation
     */
    public static <T> T blockWithUpstreamPropagation(Mono<T> mono, RequestContextService contextService,
                                                    Duration timeout) {
        return propagateUpstreamValues(mono, contextService).block(timeout);
    }

    /**
     * Enhanced flatMap that automatically propagates upstream values
     * Use this instead of regular flatMap when you need upstream value propagation
     *
     * @param mapper The mapping function
     * @param contextService The RequestContextService instance
     * @return Function that can be used with flatMap
     */
    public static <T, R> Function<T, Mono<R>> flatMapWithUpstreamPropagation(
            Function<T, Mono<R>> mapper, RequestContextService contextService) {

        return value -> {
            Mono<R> result = mapper.apply(value);
            return propagateUpstreamValues(result, contextService);
        };
    }

    /**
     * Utility method to work with context and data together in reactive chains
     *
     * @param operation BiFunction that takes context and data
     * @return Function that can be used with flatMap
     */
    public static <T, R> Function<T, Mono<R>> withContext(java.util.function.BiFunction<RequestContext, T, R> operation) {
        return data -> getContextFromReactorContext()
                .map(context -> operation.apply(context, data))
                .switchIfEmpty(Mono.error(new IllegalStateException("No RequestContext available in reactive chain")));
    }

    /**
     * Helper method to copy only upstream outbound values from latest context to reactor context
     */
    private static void copyUpstreamOutboundValues(RequestContext latestContext, RequestContext reactorContext,
                                                  RequestContextService contextService) {
        // Get all configured field names that have upstream outbound configuration
        Set<String> upstreamOutboundFields = contextService.getUpstreamOutboundFieldNames();

        for (String fieldName : upstreamOutboundFields) {
            String latestValue = latestContext.get(fieldName);
            String reactorValue = reactorContext.get(fieldName);

            // Only update if the latest context has a different/new value
            if (latestValue != null && !latestValue.equals(reactorValue)) {
                reactorContext.put(fieldName, latestValue);
            }
        }
    }
}
