package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.EnrichmentType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Handler for SESSION source type that manages server-side session attributes.
 * This handler provides secure, server-side storage for context data that persists
 * across multiple requests within a user session.
 * 
 * Supports:
 * - ✅ Extract from upstream request (read session attributes)
 * - ❌ Extract from upstream request body (sessions don't exist in request bodies)
 * - ❌ Enrich upstream response (interface limitation - no access to HttpServletRequest)
 * - ❌ Enrich downstream request (sessions are local to current server)
 * - ❌ Extract from downstream response (sessions don't exist in responses)
 */
@Component
public class SessionSourceHandler implements SourceHandler {
    
    private static final Logger log = LoggerFactory.getLogger(SessionSourceHandler.class);
    
    @Override
    public SourceType sourceType() {
        return SourceType.SESSION;
    }
    
    @Override
    public EnrichmentType enrichmentType() {
        return EnrichmentType.SESSION;
    }

    /**
     * Extract session attribute value from upstream HTTP request
     */
    @Override
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        if (!(request instanceof HttpServletRequest)) {
            logUnsupported("SESSION source handler only supports HttpServletRequest");
            return null;
        }
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpSession session = httpRequest.getSession(false); // Don't create session if it doesn't exist
        
        if (session == null) {
            log.debug("No HTTP session found for session attribute extraction: {}", config.getKey());
            return null;
        }
        
        Object attributeValue = session.getAttribute(config.getKey());
        if (attributeValue == null) {
            log.debug("Session attribute not found: {}", config.getKey());
            return null;
        }
        
        return attributeValue.toString();
    }
    
    /**
     * Enrich upstream HTTP response by setting session attributes
     */
    @Override
    public void enrichUpstreamResponse(HttpServletResponse response, String key, String value) {
        if (value == null) {
            log.debug("Skipping session attribute enrichment - null value for key: {}", key);
            return;
        }

        // We need access to the request to get the session, but the interface only provides response
        // This is a limitation of the current interface design for SESSION enrichment
        logUnsupported("upstream response enrichment (SESSION requires access to HttpServletRequest)");
    }
}
