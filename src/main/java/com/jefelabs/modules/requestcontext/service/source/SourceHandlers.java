package com.jefelabs.modules.requestcontext.service.source;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.EnrichmentType;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.InboundConfig;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry for managing all source handlers with auto-wiring support.
 * Provides unified access to all source operations across the request/response lifecycle.
 */
@Component
@Slf4j
public class SourceHandlers {

    private final Map<SourceType, SourceHandler> handlers;
    
    @Autowired
    public SourceHandlers(List<SourceHandler> sourceHandlers) {
        this.handlers = sourceHandlers.stream()
                .collect(Collectors.toMap(SourceHandler::sourceType, Function.identity()));

        log.debug("Registered {} source handlers: {}",
                handlers.size(),
                handlers.keySet().stream().map(Enum::name).sorted().toList());
    }

    /**
     * Extract value from upstream request using appropriate handler
     */
    public <T> String extractFromUpstreamRequest(SourceType sourceType, T request, InboundConfig config) {
        SourceHandler handler = handlers.get(sourceType);
        if (handler == null) {
            log.warn("No source handler registered for type: {}", sourceType);
            return null;
        }

        try {
            return handler.extractFromUpstreamRequest(request, config);
        } catch (Exception e) {
            log.error("Error extracting from upstream request for source type {}: {}", sourceType, e.getMessage());
            return null;
        }
    }

    /**
     * Extract value from upstream request body using appropriate handler
     */
    public <T> String extractFromUpstreamRequestBody(SourceType sourceType, T requestBody, InboundConfig config) {
        SourceHandler handler = handlers.get(sourceType);
        if (handler == null) {
            log.warn("No source handler registered for type: {}", sourceType);
            return null;
        }

        try {
            return handler.extractFromUpstreamRequestBody(requestBody, config);
        } catch (Exception e) {
            log.error("Error extracting from upstream request body for source type {}: {}", sourceType, e.getMessage());
            return null;
        }
    }

    /**
     * Enrich upstream response using appropriate handler (by SourceType)
     */
    public void enrichUpstreamResponse(SourceType sourceType, HttpServletResponse response, String key, String value) {
        SourceHandler handler = handlers.get(sourceType);
        if (handler == null) {
            log.warn("No source handler registered for type: {}", sourceType);
            return;
        }

        try {
            handler.enrichUpstreamResponse(response, key, value);
        } catch (Exception e) {
            log.error("Error enriching upstream response for source type {}: {}", sourceType, e.getMessage());
        }
    }

    /**
     * Enrich upstream response using appropriate handler (by EnrichmentType)
     */
    public void enrichUpstreamResponse(EnrichmentType enrichmentType, HttpServletResponse response, String key, String value) {
        // Find handler that supports this enrichment type
        SourceHandler handler = handlers.values().stream()
                .filter(h -> enrichmentType.equals(h.enrichmentType()))
                .findFirst()
                .orElse(null);

        if (handler == null) {
            log.warn("No source handler registered for enrichment type: {}", enrichmentType);
            return;
        }

        try {
            handler.enrichUpstreamResponse(response, key, value);
        } catch (Exception e) {
            log.error("Error enriching upstream response for enrichment type {}: {}", enrichmentType, e.getMessage());
        }
    }

    /**
     * Enrich downstream request using appropriate handler by SourceType
     */
    public void enrichDownstreamRequest(SourceType sourceType, ClientRequest.Builder requestBuilder, String key, String value) {
        SourceHandler handler = handlers.get(sourceType);
        if (handler == null) {
            log.warn("No source handler registered for type: {}", sourceType);
            return;
        }

        try {
            handler.enrichDownstreamRequest(requestBuilder, key, value);
        } catch (Exception e) {
            log.error("Error enriching downstream request for source type {}: {}", sourceType, e.getMessage());
        }
    }

    /**
     * Enrich downstream request using appropriate handler by EnrichmentType (strategy pattern)
     */
    public void enrichDownstreamRequest(EnrichmentType enrichmentType, ClientRequest.Builder requestBuilder, String key, String value) {
        // Find the handler that supports this enrichment type
        SourceHandler handler = handlers.values().stream()
            .filter(h -> enrichmentType.equals(h.enrichmentType()))
            .findFirst()
            .orElse(null);

        if (handler == null) {
            log.warn("No source handler registered for enrichment type: {}", enrichmentType);
            return;
        }

        try {
            handler.enrichDownstreamRequest(requestBuilder, key, value);
        } catch (Exception e) {
            log.error("Error enriching downstream request for enrichment type {}: {}", enrichmentType, e.getMessage());
        }
    }

    /**
     * Extract value from downstream response using appropriate handler
     */
    public String extractFromDownstreamResponse(SourceType sourceType, ClientResponse response, InboundConfig config) {
        SourceHandler handler = handlers.get(sourceType);
        if (handler == null) {
            log.warn("No source handler registered for type: {}", sourceType);
            return null;
        }

        try {
            return handler.extractFromDownstreamResponse(response, config);
        } catch (Exception e) {
            log.error("Error extracting from downstream response for source type {}: {}", sourceType, e.getMessage());
            return null;
        }
    }
    
    /**
     * Get handler for specific source type
     */
    public SourceHandler getHandler(SourceType sourceType) {
        return handlers.get(sourceType);
    }
    
    /**
     * Check if handler exists for source type
     */
    public boolean hasHandler(SourceType sourceType) {
        return handlers.containsKey(sourceType);
    }
}
