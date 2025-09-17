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

        // Enrich with post auth data using service
        contextService.enrichWithPostAuthPhaseData(request);

        // Get context for remaining operations
        RequestContext context = contextService.getCurrentContext(request);

        // Extract and add handler information
        if (handler instanceof HandlerMethod handlerMethod) {
            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();
            String apiHandler = controllerName + "/" + methodName;

            // add apiHandler to context
            contextService.addCustomField(context, "apiHandler", apiHandler);

            log.atDebug().log("RequestContext enriched with apiHandler: {}.{}", controllerName, methodName);
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
        // No additional operations needed after controller execution
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {

        // Handle error logging and final context logging
        if (ex != null) {
            // Add error info to context (service handles missing context gracefully)
            contextService.addFieldToCurrentContext(request, "error", ex.getClass().getSimpleName());
            contextService.addFieldToCurrentContext(request, "errorMessage", ex.getMessage());

            // Log error with context if available
            contextService.getCurrentContextSafely(request).ifPresentOrElse(
                    context -> {
                        String summary = contextService.getContextSummary(context);
                        log.error("Request failed with {} - Context: {}",
                                ex.getClass().getSimpleName(), summary, ex);
                    },
                    () -> log.error("Request failed with {} - No context available",
                            ex.getClass().getSimpleName(), ex)
            );
        } else if (log.isDebugEnabled()) {
            // Log successful completion with context if available
            contextService.getCurrentContextSafely(request).ifPresent(context -> {
                String summary = contextService.getContextSummary(context);
                log.debug("Request completed successfully - Context: {}", summary);
            });
        }
    }
}
