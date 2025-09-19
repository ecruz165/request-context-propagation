package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.CookieConfig;
import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler for COOKIE source type with limited propagation support:
 * 1. Extract from incoming HTTP request cookies
 * 2. Enrich upstream HTTP response cookies (backward propagation)
 * 
 * Note: Cookies don't propagate in downstream requests or responses
 */
@Slf4j
@Component
public class CookieSourceHandler implements SourceHandler {
    
    private final RequestContextProperties properties;
    
    public CookieSourceHandler(RequestContextProperties properties) {
        this.properties = properties;
    }
    
    @Override
    public SourceType sourceType() {
        return SourceType.COOKIE;
    }
    
    @Override
    public EnrichmentType enrichmentType() {
        return EnrichmentType.COOKIE;
    }
    
    /**
     * Extract cookie value from upstream HTTP request
     */
    @Override
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        if (!(request instanceof HttpServletRequest)) {
            logUnsupported("COOKIE source handler only supports HttpServletRequest");
            return null;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (httpRequest.getCookies() == null) return null;
        
        CookieConfig cookieConfig = properties.getSourceConfiguration().getCookie();
        
        for (Cookie cookie : httpRequest.getCookies()) {
            if (cookie.getName().equals(config.getKey())) {
                // Check security constraints
                if (cookieConfig.isHttpOnly() && !cookie.isHttpOnly()) {
                    log.debug("Cookie {} is not httpOnly, skipping", config.getKey());
                    continue;
                }
                if (cookieConfig.isSecure() && !cookie.getSecure()) {
                    log.debug("Cookie {} is not secure, skipping", config.getKey());
                    continue;
                }
                return cookie.getValue();
            }
        }
        return null;
    }
    
    /**
     * Enrich upstream HTTP response with cookie
     */
    @Override
    public void enrichUpstreamResponse(HttpServletResponse response, String key, String value) {
        Cookie cookie = new Cookie(key, value);
        // Set some reasonable defaults - these could be made configurable
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    /**
     * Cookies are not propagated to downstream requests - they are upstream-only
     */
    @Override
    public void enrichDownstreamRequest(org.springframework.web.reactive.function.client.ClientRequest.Builder requestBuilder, String key, String value) {
        logUnsupported("downstream request enrichment (cookies are upstream-only)");
    }

    /**
     * Override to provide specific logging for this source type
     */
    @Override
    public void logUnsupported(String operation) {
        log.warn("COOKIE source does not support {}", operation);
    }
}
