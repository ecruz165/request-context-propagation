package com.example.demo.controller;

import com.example.demo.service.RequestContext;
import com.example.demo.service.RequestContextService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Protected controller for JWT token source tests
 * Requires authentication to access JWT claims
 */
@RestController
@RequestMapping("/api/protected")
public class ProtectedController {

    @Autowired
    private RequestContextService requestContextService;

    /**
     * Protected endpoint that requires JWT authentication
     * Used specifically for testing JWT claim extraction (PATTERN 8)
     * and fallback chains that include JWT claims (PATTERN 10)
     */
    @GetMapping("/user-info")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> getUserInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", getJwtContextFields());
        response.put("message", "User info retrieved successfully");
        
        return ResponseEntity.ok(response);
    }

    /**
     * Get context fields that include JWT claims
     * Only available in authenticated context
     */
    private Map<String, Object> getJwtContextFields() {
        Map<String, Object> fields = new HashMap<>();

        // Get current context
        RequestContext context = requestContextService.getCurrentContext().orElse(null);
        if (context == null) {
            return fields; // Return empty map if no context
        }

        // Pattern 8: JWT Claims (Extract-Only)
        fields.put("userId", context.getMaskedOrOriginal("userId"));
        fields.put("userEmail", context.getMaskedOrOriginal("userEmail"));
        fields.put("userRole", context.getMaskedOrOriginal("userRole"));

        // Pattern 10: Fallback chains (when JWT is available)
        fields.put("tenantId", context.getMaskedOrOriginal("tenantId"));

        // Other fields that might be present
        fields.put("requestId", context.getMaskedOrOriginal("requestId"));
        fields.put("correlationId", context.getMaskedOrOriginal("correlationId"));
        fields.put("traceId", context.getMaskedOrOriginal("traceId"));

        return fields;
    }
}
