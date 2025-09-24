package com.jefelabs.modules.requestcontext.service;

import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Request context holder that stores extracted values from HTTP requests.
 * Context is stored as an attribute in HttpServletRequest for the lifecycle of the request.
 */
@Data
public class RequestContext {

    // Unique attribute key for storing in HttpServletRequest
    public static final String REQUEST_CONTEXT_ATTRIBUTE = "request.context";

    // Instance fields
    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, String> maskedValues = new ConcurrentHashMap<>();

    /**
     * Store a value in the context
     */
    public void put(String key, String value) {
        if (key != null && value != null) {
            values.put(key, value);
        }
    }

    /**
     * Store a masked version of a sensitive value
     */
    public void putMasked(String key, String maskedValue) {
        if (key != null && maskedValue != null) {
            maskedValues.put(key, maskedValue);
        }
    }

    /**
     * Get a value from the context
     */
    public String get(String key) {
        return values.get(key);
    }

    /**
     * Get the masked value for a key
     */
    public String getMasked(String key) {
        return maskedValues.get(key);
    }

    /**
     * Get masked value if available, otherwise original value
     */
    public String getMaskedOrOriginal(String key) {
        String masked = maskedValues.get(key);
        return masked != null ? masked : values.get(key);
    }

    /**
     * Get all values (returns a copy)
     */
    public Map<String, String> getAllValues() {
        return new HashMap<>(values);
    }

    /**
     * Alias for getAllValues() for compatibility
     */
    public Map<String, String> getAll() {
        return getAllValues();
    }

    /**
     * Check if a key exists in the context
     */
    public boolean containsKey(String key) {
        return values.containsKey(key);
    }

    /**
     * Remove a value from the context
     */
    public String remove(String key) {
        maskedValues.remove(key); // Also remove masked value if exists
        return values.remove(key);
    }

    /**
     * Get the number of values in the context
     */
    public int size() {
        return values.size();
    }

    /**
     * Check if the context is empty
     */
    public boolean isEmpty() {
        return values.isEmpty();
    }

    /**
     * Clear all values from the context
     */
    public void clear() {
        values.clear();
        maskedValues.clear();
    }

    /**
     * Get all field names
     */
    public java.util.Set<String> keySet() {
        return values.keySet();
    }

    /**
     * Create a summary string of the context for logging
     */
    public String toSummary() {
        if (values.isEmpty()) {
            return "empty";
        }
        return String.format("%d fields: %s", values.size(), String.join(", ", values.keySet()));
    }

    @Override
    public String toString() {
        return String.format("RequestContext{values=%d, masked=%d}", values.size(), maskedValues.size());
    }

    // ========================================
    // Package-private methods for RequestContextService access only
    // ========================================

    /**
     * Get RequestContext from a specific HttpServletRequest
     * Package-private - should only be called by RequestContextService
     *
     * @param request The HTTP request
     * @return Optional containing the RequestContext if available
     */
    static Optional<RequestContext> getFromRequest(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        Object context = request.getAttribute(REQUEST_CONTEXT_ATTRIBUTE);
        if (context instanceof RequestContext ctx) {
            return Optional.of(ctx);
        }
        return Optional.empty();
    }

    /**
     * Set RequestContext in a specific HttpServletRequest
     * Package-private - should only be called by RequestContextService
     *
     * @param request The HTTP request
     * @param context The RequestContext to store
     */
    static void setInRequest(HttpServletRequest request, RequestContext context) {
        if (request != null && context != null) {
            request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
        }
    }
}
