package com.jefelabs.modules.requestcontext.observability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for building span tag structures from dot notation.
 * Converts flat tags like "principal.userId" into nested JSON structures
 * that observability platforms like DataDog can recognize and organize.
 * Used primarily for tracing span tags and structured logging.
 */
@Slf4j
public class SpanTagBuilderHelper {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Builds nested tag structures from flat tag map.
     * 
     * Input: {"principal.userId": "user123", "principal.role": "admin", "org.tenantId": "acme"}
     * Output: {"principal": {"userId": "user123", "role": "admin"}, "org": {"tenantId": "acme"}}
     * 
     * @param flatTags Map of flat tag names to values
     * @return Map of nested tag structures
     */
    public static Map<String, Object> buildNestedTags(Map<String, String> flatTags) {
        Map<String, Object> nestedTags = new HashMap<>();

        for (Map.Entry<String, String> entry : flatTags.entrySet()) {
            String tagName = entry.getKey();
            String value = entry.getValue();

            if (tagName.contains(".")) {
                // Handle nested tag (e.g., "principal.userId")
                addNestedTag(nestedTags, tagName, value);
            } else {
                // Handle flat tag (e.g., "requestId")
                nestedTags.put(tagName, value);
            }
        }

        return nestedTags;
    }

    /**
     * Adds a nested tag to the structure.
     * Supports multiple levels of nesting (e.g., "org.department.team")
     * 
     * @param nestedTags The root nested tags map
     * @param tagName The dot-separated tag name
     * @param value The tag value
     */
    @SuppressWarnings("unchecked")
    private static void addNestedTag(Map<String, Object> nestedTags, String tagName, String value) {
        String[] parts = tagName.split("\\.");
        Map<String, Object> current = nestedTags;

        // Navigate/create nested structure
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            current.computeIfAbsent(part, k -> new HashMap<String, Object>());
            
            Object next = current.get(part);
            if (next instanceof Map) {
                current = (Map<String, Object>) next;
            } else {
                // Handle conflict: existing value is not a map
                log.warn("Tag name conflict: {} already exists as a simple value, cannot create nested structure", 
                        String.join(".", java.util.Arrays.copyOf(parts, i + 1)));
                return;
            }
        }

        // Set the final value
        String finalKey = parts[parts.length - 1];
        current.put(finalKey, value);
    }

    /**
     * Converts nested tag structure to JSON string for span tags.
     * 
     * @param nestedValue The nested object to serialize
     * @return JSON string representation
     */
    public static String toJsonString(Object nestedValue) {
        if (nestedValue instanceof String) {
            return (String) nestedValue;
        }

        try {
            return objectMapper.writeValueAsString(nestedValue);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize nested tag to JSON: {}", e.getMessage());
            return nestedValue.toString();
        }
    }

    /**
     * Builds flat tags for systems that don't support nested structures.
     * This is the fallback approach for metrics and other flat tag systems.
     * 
     * @param flatTags Map of flat tag names to values
     * @return Map of flat tags (unchanged)
     */
    public static Map<String, String> buildFlatTags(Map<String, String> flatTags) {
        return new HashMap<>(flatTags);
    }

    /**
     * Checks if a tag name indicates nested structure.
     * 
     * @param tagName The tag name to check
     * @return true if the tag name contains dots (indicating nesting)
     */
    public static boolean isNestedTagName(String tagName) {
        return tagName != null && tagName.contains(".");
    }

    /**
     * Gets the root namespace from a nested tag name.
     * 
     * @param tagName The tag name (e.g., "principal.userId")
     * @return The root namespace (e.g., "principal")
     */
    public static String getRootNamespace(String tagName) {
        if (tagName == null || !tagName.contains(".")) {
            return tagName;
        }
        return tagName.substring(0, tagName.indexOf("."));
    }

    /**
     * Gets the leaf key from a nested tag name.
     * 
     * @param tagName The tag name (e.g., "principal.userId")
     * @return The leaf key (e.g., "userId")
     */
    public static String getLeafKey(String tagName) {
        if (tagName == null || !tagName.contains(".")) {
            return tagName;
        }
        return tagName.substring(tagName.lastIndexOf(".") + 1);
    }
}
