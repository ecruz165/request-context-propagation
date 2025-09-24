package com.jefelabs.modules.requestcontext.service.source;

import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.EnrichmentType;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.InboundConfig;
import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;

import java.util.List;

/**
 * Unified handler for HEADER source type that supports full propagation:
 * 1. Extract from incoming HTTP request headers
 * 2. Extract from downstream HTTP response headers (capture)
 * 3. Enrich downstream HTTP request headers (forward propagation)
 * 4. Enrich upstream HTTP response headers (backward propagation)
 */
@Component
public class HeaderSourceHandler implements SourceHandler {

    private static final Logger log = LoggerFactory.getLogger(HeaderSourceHandler.class);
    private final RequestContextProperties properties;
    
    public HeaderSourceHandler(RequestContextProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public SourceType sourceType() {
        return SourceType.HEADER;
    }

    @Override
    public EnrichmentType enrichmentType() {
        return EnrichmentType.HEADER;
    }

    /**
     * Extract header value from upstream HTTP request
     */
    @Override
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        if (!(request instanceof HttpServletRequest)) {
            log.warn("HEADER source handler only supports HttpServletRequest, got: {}",
                    request != null ? request.getClass().getSimpleName() : "null");
            return null;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String value = httpRequest.getHeader(config.getKey());
        if (value != null && properties.getSourceConfiguration().getHeader().isNormalizeNames()) {
            value = value.trim();
        }
        return value;
    }

    /**
     * Enrich upstream HTTP response with header
     */
    @Override
    public void enrichUpstreamResponse(HttpServletResponse response, String key, String value) {
        response.setHeader(key, value);
    }

    /**
     * Enrich downstream HTTP request with header
     */
    @Override
    public void enrichDownstreamRequest(ClientRequest.Builder requestBuilder, String key, String value) {
        requestBuilder.header(key, value);
    }

    /**
     * Extract header value from downstream HTTP response
     */
    @Override
    public String extractFromDownstreamResponse(ClientResponse response, InboundConfig config) {
        List<String> headerValues = response.headers().header(config.getKey());

        if (headerValues.isEmpty()) {
            return null;
        }

        // Take first value if multiple headers present
        return headerValues.get(0);
    }
}
