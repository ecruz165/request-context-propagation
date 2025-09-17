package com.example.demo.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.demo.config.RequestContext;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

/**
 * Custom JSON provider that adds structured request context fields to logs
 * This provider extracts context from both MDC and RequestContext
 */
public class RequestContextJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> {

    public static final String FIELD_REQUEST_CONTEXT = "request_context";

    private String fieldName = FIELD_REQUEST_CONTEXT;

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        // Get context from MDC first
        Map<String, String> mdcContext = event.getMDCPropertyMap();

        // Try to get current RequestContext
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();

        if ((mdcContext != null && !mdcContext.isEmpty()) || contextOpt.isPresent()) {
            generator.writeFieldName(fieldName);
            generator.writeStartObject();

            // Write MDC context fields
            if (mdcContext != null && !mdcContext.isEmpty()) {
                for (Map.Entry<String, String> entry : mdcContext.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    if (value != null && !value.isEmpty()) {
                        // Convert snake_case to camelCase for JSON consistency
                        String jsonKey = convertToJsonKey(key);
                        generator.writeStringField(jsonKey, value);
                    }
                }
            }

            // Write additional context from RequestContext if available
            if (contextOpt.isPresent()) {
                RequestContext context = contextOpt.get();

                // Add any additional fields that might not be in MDC
                Map<String, String> allValues = context.getAllValues();
                for (Map.Entry<String, String> entry : allValues.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();

                    // Only add if not already in MDC
                    if (value != null && !value.isEmpty() &&
                        (mdcContext == null || !mdcContext.containsKey(key))) {
                        String jsonKey = convertToJsonKey(key);
                        generator.writeStringField(jsonKey, value);
                    }
                }

                // Add metadata
                generator.writeNumberField("contextFieldCount", context.getAllValues().size());
            }

            generator.writeEndObject();
        }
    }
    
    /**
     * Convert field names to JSON-friendly camelCase
     */
    private String convertToJsonKey(String key) {
        if (key == null || key.isEmpty()) {
            return key;
        }
        
        // Convert snake_case to camelCase
        if (key.contains("_")) {
            StringBuilder result = new StringBuilder();
            String[] parts = key.split("_");
            
            result.append(parts[0].toLowerCase());
            for (int i = 1; i < parts.length; i++) {
                if (!parts[i].isEmpty()) {
                    result.append(Character.toUpperCase(parts[i].charAt(0)));
                    if (parts[i].length() > 1) {
                        result.append(parts[i].substring(1).toLowerCase());
                    }
                }
            }
            return result.toString();
        }
        
        return key;
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}
