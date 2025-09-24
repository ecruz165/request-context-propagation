package com.jefelabs.modules.requestcontext.filter;

// ============================================
// FILTER - Initializes context in HttpServletRequest
// ============================================

import com.jefelabs.modules.requestcontext.service.RequestContext;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
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
            // Initialize context using service (which uses extractor internally)
            RequestContext context = contextService.initializeContext(request);

            log.debug("RequestContext initialized for {} {} with {} fields",
                    request.getMethod(),
                    request.getRequestURI(),
                    context.size());

            // Continue with filter chain
            filterChain.doFilter(request, response);

        } finally {
            try {
                // Calculate and log request duration
                long duration = System.currentTimeMillis() - startTime;

                // Get context to log final state using service proxy
                contextService.getCurrentContextSafely(request).ifPresent(context -> {
                    // Log request completion with context summary
                    if (log.isInfoEnabled()) {
                        String summary = contextService.getContextSummary(context);
                        log.debug("Request completed: {} {} [{}ms] - {}",
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



    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        String path = request.getRequestURI();

        // Use service to check exclusion patterns
        return contextService.shouldExcludeFromFiltering(path);
    }
}