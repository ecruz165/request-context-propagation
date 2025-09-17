package com.example.demo.filter;

// ============================================
// FILTER - Initializes context in HttpServletRequest
// ============================================

import com.example.demo.config.RequestContext;
import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.service.RequestContextService;
import com.example.demo.util.CachedBodyHttpServletRequest;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that initializes RequestContext early in the request lifecycle.
 * Runs before Spring Security to extract non-authenticated context.
 * Delegates to RequestContextService for all context operations.
 */
@Component("customRequestContextFilter")
@Slf4j
@RequiredArgsConstructor
public class RequestContextFilter extends OncePerRequestFilter implements Ordered {

    private final RequestContextService contextService;
    private final RequestContextProperties properties;
    private final int order = Ordered.HIGHEST_PRECEDENCE + 1;

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            // Wrap request with cached body if BODY sources are configured
            HttpServletRequest processedRequest = wrapRequestIfNeeded(request);

            // Initialize context using service (which uses extractor internally)
            RequestContext context = contextService.initializeContext(processedRequest);

            // Add request start time for duration calculation
            context.put("requestStartTime", String.valueOf(startTime));

            log.debug("RequestContext initialized for {} {} with {} fields",
                    processedRequest.getMethod(),
                    processedRequest.getRequestURI(),
                    context.size());

            // Continue with filter chain
            filterChain.doFilter(processedRequest, response);

        } finally {
            try {
                // Calculate and log request duration
                long duration = System.currentTimeMillis() - startTime;

                // Get context to log final state
                RequestContext.getFromRequest(request).ifPresent(context -> {
                    context.put("requestDuration", String.valueOf(duration));

                    // Log request completion with context summary
                    if (log.isInfoEnabled()) {
                        String summary = contextService.getContextSummary(context);
                        log.info("Request completed: {} {} [{}ms] - {}",
                                request.getMethod(),
                                request.getRequestURI(),
                                duration,
                                summary);
                    }
                });

            } finally {
                // Clean up context and MDC
                contextService.clearContext();
            }
        }
    }

    /**
     * Wrap the request with cached body wrapper if BODY sources are configured
     */
    private HttpServletRequest wrapRequestIfNeeded(HttpServletRequest request) {
        // Check if any BODY sources are configured
        boolean hasBodySources = properties.getFields().values().stream()
                .filter(field -> field.getUpstream() != null && field.getUpstream().getInbound() != null)
                .anyMatch(field -> field.getUpstream().getInbound().getSource() == RequestContextProperties.SourceType.BODY);

        if (hasBodySources && isJsonRequest(request)) {
            try {
                return new CachedBodyHttpServletRequest(request);
            } catch (IOException e) {
                log.warn("Failed to wrap request with cached body: {}", e.getMessage());
                return request;
            }
        }

        return request;
    }

    /**
     * Check if the request has JSON content type
     */
    private boolean isJsonRequest(HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.contains("application/json");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        // Could check for excluded patterns here if needed
        String path = request.getRequestURI();

        // Example: skip filter for static resources
        return path.startsWith("/static/") ||
                path.startsWith("/favicon.ico") ||
                path.startsWith("/actuator/health");
    }
}