package com.example.demo.filter;

// ============================================
// INTERCEPTOR - Adds authenticated context and handler info
// ============================================

import com.example.demo.service.RequestContext;
import com.example.demo.service.RequestContextService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.io.IOException;
import java.lang.reflect.Type;


/**
 * Spring MVC interceptor that enriches RequestContext with authenticated data and handler info.
 * Runs after Spring Security authentication but before controller execution.
 * Delegates to RequestContextService for all context operations.
 */
@Slf4j
@Component
@ControllerAdvice
@RequiredArgsConstructor
public class RequestContextInterceptor implements HandlerInterceptor, RequestBodyAdvice, ResponseBodyAdvice<Object> {

    private final RequestContextService contextService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        // Enrich with post auth data using service (excluding BODY sources)
        contextService.enrichWithPostAuthPhaseData(request);

        // Extract context fields (like apiHandler) if handler is available
        if (handler instanceof HandlerMethod) {
            contextService.enrichWithContextData((HandlerMethod) handler);
        }

        // Get context for remaining operations
        RequestContext context = contextService.getCurrentContext(request);

        // Validate required fields if configured
        try {
            contextService.validateRequiredFields(context);
        } catch (IllegalStateException e) {
            log.error("Required field validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                    "Missing required context fields");
            return false;
        }

        log.debug("RequestContext ready for controller with {} fields", context.size());

        return true; // Continue to controller
    }

    @Override
    public void postHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler,
                           ModelAndView modelAndView) throws Exception {
        // Response enrichment is now handled by ResponseBodyAdvice
        // No additional operations needed after controller execution
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) throws Exception {

        // Handle error logging and final context logging
        if (ex != null) {
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

    // RequestBodyAdvice implementation for BODY source extraction

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                           Class<? extends HttpMessageConverter<?>> converterType) {
        // Only activate if BODY sources are configured
        return contextService.hasBodySourcesConfigured();
    }

    @Override
    public HttpInputMessage beforeBodyRead(HttpInputMessage inputMessage, MethodParameter parameter,
                                         Type targetType, Class<? extends HttpMessageConverter<?>> converterType)
            throws IOException {
        // No modification needed before reading
        return inputMessage;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                               Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {

        // Convert body to JsonNode for JSONPath extraction
        // This works regardless of the original content type (JSON, XML, etc.)
        JsonNode bodyNode = objectMapper.valueToTree(body);

        // Extract BODY sources immediately now that body content is available
        contextService.enrichWithJsonBodySources(bodyNode);
        log.debug("BODY sources extracted successfully in RequestBodyAdvice");

        return body;
    }

    @Override
    public Object handleEmptyBody(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                 Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        // No special handling needed for empty body
        return body;
    }

    // ResponseBodyAdvice implementation for upstream response enrichment

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Always support response enrichment
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                 Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                 ServerHttpRequest request, ServerHttpResponse response) {

        try {
            // Get the current context (may have been enriched with downstream response data)
            contextService.getCurrentContext().ifPresent(context -> {
                try {
                    // Convert ServerHttpResponse to HttpServletResponse for enrichment
                    if (response instanceof org.springframework.http.server.ServletServerHttpResponse) {
                        HttpServletResponse servletResponse =
                            ((org.springframework.http.server.ServletServerHttpResponse) response).getServletResponse();

                        // Enrich response headers with captured values (including downstream response data)
                        contextService.enrichResponse(servletResponse, context);

                        log.debug("Response enriched with upstream outbound fields via ResponseBodyAdvice");
                    }
                } catch (Exception e) {
                    log.error("Error enriching response in ResponseBodyAdvice: {}", e.getMessage(), e);
                }
            });
        } catch (Exception e) {
            log.error("Error getting context in ResponseBodyAdvice: {}", e.getMessage(), e);
        }

        return body;
    }
}
