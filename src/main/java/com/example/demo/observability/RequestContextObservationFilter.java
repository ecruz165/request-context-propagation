package com.example.demo.observability;

import com.example.demo.service.RequestContext;
import com.example.demo.service.RequestContextService;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * Observation filter to add RequestContext to traces
 */
@Slf4j
@Order(0)
@Component
@RequiredArgsConstructor
public class RequestContextObservationFilter implements ObservationFilter {

    private final RequestContextService requestContextService;
    private final Tracer tracer;

    @Override
    public Observation.Context map(Observation.Context context) {
        // Add RequestContext data to observation context
        if (context instanceof ServerRequestObservationContext serverContext) {
            enrichWithRequestContext(serverContext);
        }
        return context;
    }

    private void enrichWithRequestContext(ServerRequestObservationContext context) {
        Optional<RequestContext> requestContextOpt = RequestContext.getCurrentContext();
        if (requestContextOpt.isEmpty()) {
            return;
        }

        RequestContext requestContext = requestContextOpt.get();

        // Add trace tags for propagation
        Span currentSpan = tracer.currentSpan();
        if (currentSpan != null) {
            // Get tracing fields and add as span tags
            Map<String, String> tracingFields = requestContextService.getTracingFields(requestContext);
            tracingFields.forEach(currentSpan::tag);
        }
    }
}
