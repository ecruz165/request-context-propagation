package com.example.demo.observability;

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties.CardinalityLevel;
import com.example.demo.service.RequestContextService;
import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.observation.Observation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.observation.DefaultServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.http.server.observation.ServerRequestObservationConvention;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Observation convention that enriches metrics and traces with RequestContext data
 */
@Component
@Slf4j
public class RequestContextObservationConvention implements ServerRequestObservationConvention {

    private final RequestContextService requestContextService;
    private final ServerRequestObservationConvention defaultConvention;

    public RequestContextObservationConvention(RequestContextService requestContextService) {
        this.requestContextService = requestContextService;
        this.defaultConvention = new DefaultServerRequestObservationConvention();
    }

    @Override
    public String getName() {
        return "http.server.requests";
    }

    @Override
    public String getContextualName(ServerRequestObservationContext context) {
        return defaultConvention.getContextualName(context);
    }

    @Override
    public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
        // Start with default key values
        KeyValues defaultKeyValues = defaultConvention.getLowCardinalityKeyValues(context);

        // Get RequestContext
        Optional<RequestContext> requestContextOpt = getRequestContext(context);
        if (requestContextOpt.isEmpty()) {
            return defaultKeyValues;
        }

        RequestContext requestContext = requestContextOpt.get();
        List<KeyValue> additionalKeyValues = new ArrayList<>();

        // Add low cardinality fields from RequestContext
        Map<String, String> lowCardinalityFields = requestContextService.getMetricsFields(requestContext, CardinalityLevel.LOW);
        lowCardinalityFields.forEach((tagName, value) ->
            additionalKeyValues.add(KeyValue.of(tagName, value))
        );

        // Combine default and additional key values
        return defaultKeyValues.and(additionalKeyValues.toArray(new KeyValue[0]));
    }

    @Override
    public KeyValues getHighCardinalityKeyValues(ServerRequestObservationContext context) {
        // Start with default high cardinality key values
        KeyValues defaultKeyValues = defaultConvention.getHighCardinalityKeyValues(context);

        // Get RequestContext
        Optional<RequestContext> requestContextOpt = getRequestContext(context);
        if (requestContextOpt.isEmpty()) {
            return defaultKeyValues;
        }

        RequestContext requestContext = requestContextOpt.get();
        List<KeyValue> additionalKeyValues = new ArrayList<>();

        // Add medium cardinality fields
        Map<String, String> mediumCardinalityFields = requestContextService.getMetricsFields(requestContext, CardinalityLevel.MEDIUM);
        mediumCardinalityFields.forEach((tagName, value) ->
            additionalKeyValues.add(KeyValue.of(tagName, value))
        );

        // Add high cardinality fields
        Map<String, String> highCardinalityFields = requestContextService.getMetricsFields(requestContext, CardinalityLevel.HIGH);
        highCardinalityFields.forEach((tagName, value) ->
            additionalKeyValues.add(KeyValue.of(tagName, value))
        );

        return defaultKeyValues.and(additionalKeyValues.toArray(new KeyValue[0]));
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return context instanceof ServerRequestObservationContext;
    }

    /**
     * Get RequestContext from observation context
     */
    private Optional<RequestContext> getRequestContext(ServerRequestObservationContext context) {
        HttpServletRequest request = context.getCarrier();
        Object contextAttr = request.getAttribute(RequestContext.REQUEST_CONTEXT_ATTRIBUTE);
        if (contextAttr instanceof RequestContext requestContext) {
            return Optional.of(requestContext);
        }
        return RequestContext.getCurrentContext();
    }

}