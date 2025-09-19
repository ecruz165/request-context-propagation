package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

/**
 * Unified handler interface for all source operations across the request/response lifecycle.
 * Handlers implement all methods but log warnings for unsupported operations.
 */
public interface SourceHandler {

    SourceType sourceType();

    /**
     * Returns the enrichment type this handler supports, or null if it's extraction-only
     */
    default EnrichmentType enrichmentType() {
        return null; // Most handlers are extraction-only
    }

    /**
     * Extract from upstream request (incoming)
     */
    default <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        logUnsupported("upstream request extraction");
        return null;
    }

    /**
     * Extract from upstream request body (incoming)
     */
    default <T> String extractFromUpstreamRequestBody(T requestBody, InboundConfig config) {
        logUnsupported("upstream request body extraction");
        return null;
    }

    /**
     * Enrich upstream response (outgoing)
     */
    default void enrichUpstreamResponse(HttpServletResponse response, String key, String value) {
        logUnsupported("upstream response enrichment");
    }

    /**
     * Enrich downstream request (outgoing)
     */
    default void enrichDownstreamRequest(ClientRequest.Builder requestBuilder, String key, String value) {
        logUnsupported("downstream request enrichment");
    }

    /**
     * Extract from downstream response (incoming)
     */
    default String extractFromDownstreamResponse(ClientResponse response, InboundConfig config) {
        logUnsupported("downstream response extraction");
        return null;
    }

    /**
     * Log warning for unsupported operations
     */
    default void logUnsupported(String operation) {
        // Implementations can override this or use a logger
        System.out.println("WARN: " + sourceType() + " source does not support " + operation);
    }
}
