package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * Handler for CLAIM source type that extracts values from Spring Security JWT claims.
 * This handler extracts claims from the current SecurityContext authentication.
 * 
 * Supports:
 * - ✅ Extract from upstream request (via SecurityContext)
 * - ❌ Extract from upstream request body (claims don't exist in request bodies)
 * - ❌ Enrich upstream response (claims are input-only, not output)
 * - ❌ Enrich downstream request (claims are from current auth, not for forwarding)
 * - ❌ Extract from downstream response (claims don't exist in responses)
 */
@Component
public class ClaimSourceHandler implements SourceHandler {
    
    private static final Logger log = LoggerFactory.getLogger(ClaimSourceHandler.class);
    
    @Override
    public SourceType sourceType() {
        return SourceType.CLAIM;
    }

    /**
     * Extract claim value from current Spring Security JWT authentication
     */
    @Override
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null) {
            log.debug("No authentication found in SecurityContext for claim extraction: {}", config.getKey());
            return null;
        }
        
        if (!(authentication instanceof JwtAuthenticationToken)) {
            log.debug("Authentication is not JWT-based, cannot extract claim: {} (auth type: {})", 
                    config.getKey(), authentication.getClass().getSimpleName());
            return null;
        }
        
        JwtAuthenticationToken jwtAuth = (JwtAuthenticationToken) authentication;
        Jwt jwt = jwtAuth.getToken();
        
        // Extract claim using the key (supports nested claims with dot notation)
        Object claimValue = extractNestedClaim(jwt, config.getKey());
        
        if (claimValue == null) {
            log.debug("Claim not found: {}", config.getKey());
            return null;
        }
        
        return claimValue.toString();
    }
    
    /**
     * Extract nested claim using dot notation (e.g., "user.email", "roles.admin")
     */
    private Object extractNestedClaim(Jwt jwt, String claimPath) {
        String[] parts = claimPath.split("\\.");
        Object current = jwt.getClaims();
        
        for (String part : parts) {
            if (current instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> map = (java.util.Map<String, Object>) current;
                current = map.get(part);
            } else {
                return null; // Can't navigate further
            }
            
            if (current == null) {
                return null; // Claim path doesn't exist
            }
        }
        
        return current;
    }
}
