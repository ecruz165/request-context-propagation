package com.example.demo.service.source;

import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SourceType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

/**
 * Handler for BODY source type with support for:
 * - Extract from downstream response body (JSON using JSONPath)
 *
 * Features:
 * - JSONPath extraction for nested JSON values
 * - Support for extracting entire JSON response (use key "." or "$")
 * - Support for specific JSONPath expressions (e.g., "$.user.id", "$.data[0].name")
 * - Downstream response extraction only
 *
 * Note: No propagation support - BODY is extract-only, downstream inbound only
 */
@Slf4j
@Component
public class BodySourceHandler implements SourceHandler {
    
    private final ObjectMapper objectMapper;
    
    public BodySourceHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }
    
    @Override
    public SourceType sourceType() {
        return SourceType.BODY;
    }
    
    /**
     * BODY source does not support upstream request body extraction
     * Only downstream response extraction is supported
     */
    @Override
    public <T> String extractFromUpstreamRequestBody(T requestBody, InboundConfig config) {
        logUnsupported("upstream request body extraction - BODY source only supports downstream response extraction");
        return null;
    }
    
    /**
     * Extract from downstream response body using JSONPath
     * This method extracts JSON content from downstream service responses
     * Note: The response body should already be buffered by the WebClient capture filter
     */
    @Override
    public String extractFromDownstreamResponse(ClientResponse response, InboundConfig config) {
        try {
            // Try to get the buffered body first (non-blocking)
            String responseBody = getBufferedResponseBody(response);

            if (responseBody == null || responseBody.trim().isEmpty()) {
                log.debug("Empty response body for BODY extraction with key: {}", config.getKey());
                return null;
            }
            
            // Check if we want the entire response
            if (isEntireResponseKey(config.getKey())) {
                log.debug("Returning entire response body for key: {}", config.getKey());
                return responseBody;
            }
            
            // Parse as JSON and extract using JSONPath
            try {
                JsonNode responseNode = objectMapper.readTree(responseBody);
                return extractFromJsonNode(responseNode, config, "downstream response body");
            } catch (Exception e) {
                log.warn("Failed to parse downstream response as JSON for key {}: {}", config.getKey(), e.getMessage());
                // If JSON parsing fails but we have a response, return it as-is if it's a simple extraction
                if (config.getKey().equals("$") || config.getKey().equals(".")) {
                    return responseBody;
                }
                return null;
            }
            
        } catch (Exception e) {
            log.error("Error extracting from downstream response body for key {}: {}", config.getKey(), e.getMessage());
            return null;
        }
    }

    /**
     * Get the buffered response body from the ClientResponse
     * The WebClient capture filter should have already buffered the body to prevent consumption issues
     */
    private String getBufferedResponseBody(ClientResponse response) {
        try {
            // Try to get the body non-blocking first (if already buffered)
            Mono<String> bodyMono = response.bodyToMono(String.class);

            // Use blockOptional with a short timeout to avoid hanging
            // If the body is already buffered, this should return immediately
            return bodyMono.blockOptional(java.time.Duration.ofMillis(100)).orElse(null);

        } catch (Exception e) {
            log.warn("Failed to get buffered response body: {}. Body may not be buffered properly.", e.getMessage());
            return null;
        }
    }
    
    /**
     * Extract value from JsonNode using JSONPath
     */
    private String extractFromJsonNode(JsonNode jsonNode, InboundConfig config, String context) {
        String jsonPath = config.getKey();
        
        try {
            // Check if we want the entire JSON
            if (isEntireResponseKey(jsonPath)) {
                log.debug("Returning entire JSON for key: {} from {}", jsonPath, context);
                return jsonNode.toString();
            }
            
            // Convert JsonNode to JSON string for JSONPath processing
            String jsonString = jsonNode.toString();
            
            // Use JSONPath to extract the value
            Object extractedValue = JsonPath.read(jsonString, jsonPath);
            
            if (extractedValue == null) {
                log.debug("JSONPath {} returned null from {}", jsonPath, context);
                return null;
            }
            
            // Convert extracted value to string
            String result = convertToString(extractedValue);
            log.debug("Extracted value from {} using JSONPath {}: {}", context, jsonPath, 
                    result.length() > 100 ? result.substring(0, 100) + "..." : result);
            
            return result;
            
        } catch (PathNotFoundException e) {
            log.debug("JSONPath {} not found in {}: {}", jsonPath, context, e.getMessage());
            return null;
        } catch (Exception e) {
            log.warn("Error extracting JSONPath {} from {}: {}", jsonPath, context, e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if the key indicates we want the entire response/JSON
     */
    private boolean isEntireResponseKey(String key) {
        return "$".equals(key) || ".".equals(key) || "$.*".equals(key);
    }
    
    /**
     * Convert extracted JSONPath value to string representation
     */
    private String convertToString(Object value) {
        if (value == null) {
            return null;
        }
        
        if (value instanceof String) {
            return (String) value;
        }
        
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        
        // For complex objects (arrays, maps), convert to JSON string
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to convert extracted value to JSON string: {}", e.getMessage());
            return value.toString();
        }
    }
}
