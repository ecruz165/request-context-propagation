package com.example.demo.filter;

// ============================================
// INTERCEPTOR - Adds authenticated context and handler info
// ============================================

import com.example.demo.config.RequestContext;
import com.example.demo.service.RequestContextService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;


/**
 * Spring MVC interceptor that enriches RequestContext with authenticated data and handler info.
 * Runs after Spring Security authentication but before controller execution.
 * Delegates to RequestContextService for all context operations.
 */
@Component
@Slf4j
public class RequestContextInterceptor implements HandlerInterceptor {

    private final RequestContextService contextService;

    public RequestContextInterceptor(RequestContextService contextService) {
        this.contextService = contextService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Get existing context created by filter
        RequestContext context = RequestContext.getFromRequest(request)
                .orElseThrow(() -> new IllegalStateException(
                        "RequestContext not found. Ensure RequestContextFilter is configured."));

        // Enrich with authenticated data using service
        contextService.enrichWithAuthenticatedData(request, context);

        // Extract and add handler information
        if (handler instanceof HandlerMethod) {
            HandlerMethod handlerMethod = (HandlerMethod) handler;

            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();

            // Use service to add handler TO CONTEXT
            contextService.enrichWithHandlerInfo(context, controllerName, methodName);

            log.debug("RequestContext enriched with handler: {}.{}",
                    controllerName, methodName);
        }

        // Validate required fields if configured
        try {
            contextService.validateRequiredFields(context);
        } catch (IllegalStateException e) {
            log.error("Required field validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required context fields");
            return false;
        }

        // Update MDC with complete context
        contextService.updateMDC(context);

        // Enrich response headers if configured
        contextService.enrichResponseHeaders(response, context);

        log.debug("RequestContext ready for controller with {} fields", context.size());

        return true; // Continue to controller
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {

        // Get context
        RequestContext.getFromRequest(request).ifPresent(context -> {
            // Add response status to context
            context.put("responseStatus", String.valueOf(response.getStatus()));

            log.debug("Response status {} for handler {}",
                    response.getStatus(),
                    context.get("handler"));
        });
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {

        // Get context for final logging
        RequestContext.getFromRequest(request).ifPresent(context -> {
            if (ex != null) {
                // Log error with context
                context.put("error", ex.getClass().getSimpleName());
                context.put("errorMessage", ex.getMessage());

                String summary = contextService.getContextSummary(context);
                log.error("Request failed with {} - Context: {}",
                        ex.getClass().getSimpleName(), summary, ex);
            } else if (log.isDebugEnabled()) {
                // Log successful completion
                String summary = contextService.getContextSummary(context);
                log.debug("Request completed successfully - Context: {}", summary);
            }
        });
    }
}
