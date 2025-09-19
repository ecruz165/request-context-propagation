package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;

/**
 * Handler for QUERY source type with limited propagation support:
 * 1. Extract from incoming HTTP request query parameters
 * 2. Enrich downstream HTTP request query parameters (forward propagation)
 * 
 * Note: Query parameters don't exist in responses
 */
@Slf4j
@Component
public class QuerySourceHandler implements SourceHandler {
    
    @Override
    public SourceType sourceType() {
        return SourceType.QUERY;
    }
    
    @Override
    public EnrichmentType enrichmentType() {
        return EnrichmentType.QUERY;
    }
    
    /**
     * Extract query parameter value from upstream HTTP request
     */
    @Override
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        if (!(request instanceof HttpServletRequest)) {
            logUnsupported("QUERY source handler only supports HttpServletRequest");
            return null;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        return httpRequest.getParameter(config.getKey());
    }
    
    /**
     * Enrich downstream HTTP request with query parameter
     */
    @Override
    public void enrichDownstreamRequest(ClientRequest.Builder requestBuilder, String key, String value) {
        // For query parameters, we need to modify the existing URL
        // Get the current URL and add the query parameter
        ClientRequest currentRequest = requestBuilder.build();
        String currentUrl = currentRequest.url().toString();

        // Add query parameter to URL
        String separator = currentUrl.contains("?") ? "&" : "?";
        String newUrl = currentUrl + separator + key + "=" + value;

        // Create new request with modified URL
        try {
            requestBuilder.url(java.net.URI.create(newUrl));
        } catch (Exception e) {
            log.error("Failed to create URI from URL: {}", newUrl, e);
        }
        log.debug("Added query parameter to downstream request: {} = {}", key, value);
    }
    
    /**
     * Override to provide specific logging for this source type
     */
    @Override
    public void logUnsupported(String operation) {
        log.warn("QUERY source does not support {} (query parameters only exist in requests)", operation);
    }
}
