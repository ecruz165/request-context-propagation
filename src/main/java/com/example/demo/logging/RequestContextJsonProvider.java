package com.example.demo.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.example.demo.service.RequestContext;
import com.example.demo.observability.SpanTagBuilderHelper;
import com.example.demo.service.RequestContextService;
import com.fasterxml.jackson.core.JsonGenerator;
import net.logstash.logback.composite.AbstractFieldJsonProvider;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Custom JSON provider that adds structured request context fields to logs
 * This provider extracts context from both MDC and RequestContext and supports
 * nested JSON organization for logical grouping (e.g., principal.*, org.*, request.*)
 */
public class RequestContextJsonProvider extends AbstractFieldJsonProvider<ILoggingEvent> implements ApplicationContextAware {

    public static final String FIELD_REQUEST_CONTEXT = "request_context";

    private String fieldName = FIELD_REQUEST_CONTEXT;
    private ApplicationContext applicationContext;
    private RequestContextService requestContextService;

    @Override
    public void writeTo(JsonGenerator generator, ILoggingEvent event) throws IOException {
        // Get context from MDC first
        Map<String, String> mdcContext = event.getMDCPropertyMap();

        // Try to get current RequestContext
        Optional<RequestContext> contextOpt = RequestContext.getCurrentContext();

        if ((mdcContext != null && !mdcContext.isEmpty()) || contextOpt.isPresent()) {
            generator.writeFieldName(fieldName);
            generator.writeStartObject();

            // Collect all context fields for intelligent grouping
            Map<String, String> allContextFields = new HashMap<>();

            // Add MDC context fields
            if (mdcContext != null && !mdcContext.isEmpty()) {
                allContextFields.putAll(mdcContext);
            }

            // Add additional context from RequestContext if available
            if (contextOpt.isPresent()) {
                RequestContext context = contextOpt.get();
                Map<String, String> allValues = context.getAllValues();

                // Add fields that are configured for logging but might not be in MDC
                if (requestContextService != null) {
                    // Get logging fields with their custom MDC keys
                    Map<String, String> loggingFields = new HashMap<>();
                    for (String fieldName : requestContextService.getAllLoggingFields()) {
                        String value = context.getMaskedOrOriginal(fieldName);
                        if (value != null && !value.isEmpty()) {
                            String mdcKey = requestContextService.getLoggingMdcKey(fieldName);
                            loggingFields.put(mdcKey, value);
                        }
                    }
                    allContextFields.putAll(loggingFields);
                } else {
                    // Fallback: add all values not already in MDC
                    for (Map.Entry<String, String> entry : allValues.entrySet()) {
                        String key = entry.getKey();
                        String value = entry.getValue();
                        if (value != null && !value.isEmpty() && !allContextFields.containsKey(key)) {
                            allContextFields.put(key, value);
                        }
                    }
                }
            }

            // Build nested structure from collected fields
            if (!allContextFields.isEmpty()) {
                writeNestedFields(generator, allContextFields);
            }

            // Add metadata if RequestContext is available
            if (contextOpt.isPresent()) {
                generator.writeNumberField("contextFieldCount", contextOpt.get().getAllValues().size());
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

    /**
     * Writes nested fields to JSON generator based on dot notation in keys
     */
    private void writeNestedFields(JsonGenerator generator, Map<String, String> fields) throws IOException {
        // Build nested structure from flat keys
        Map<String, Object> nestedStructure = SpanTagBuilderHelper.buildNestedTags(fields);

        // Write the nested structure to JSON
        writeNestedObject(generator, nestedStructure);
    }

    /**
     * Recursively writes nested objects to JSON generator
     */
    @SuppressWarnings("unchecked")
    private void writeNestedObject(JsonGenerator generator, Map<String, Object> nestedMap) throws IOException {
        for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            // Convert key to JSON-friendly format
            String jsonKey = convertToJsonKey(key);

            if (value instanceof Map) {
                // Nested object
                generator.writeFieldName(jsonKey);
                generator.writeStartObject();
                writeNestedObject(generator, (Map<String, Object>) value);
                generator.writeEndObject();
            } else {
                // Simple value
                generator.writeStringField(jsonKey, value.toString());
            }
        }
    }



    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        try {
            this.requestContextService = applicationContext.getBean(RequestContextService.class);
        } catch (Exception e) {
            // Service not available, will use fallback behavior
        }
    }

    public String getFieldName() {
        return fieldName;
    }

    public void setFieldName(String fieldName) {
        this.fieldName = fieldName;
    }
}
