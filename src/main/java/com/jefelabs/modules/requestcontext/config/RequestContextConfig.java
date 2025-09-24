package com.jefelabs.modules.requestcontext.config;


import com.jefelabs.modules.requestcontext.config.props.RequestContextProperties;
import com.jefelabs.modules.requestcontext.filter.RequestContextFilter;
import com.jefelabs.modules.requestcontext.filter.RequestContextInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Slf4j
@Configuration
@EnableConfigurationProperties(RequestContextProperties.class)
public class RequestContextConfig implements WebMvcConfigurer {
    
    private final RequestContextInterceptor requestContextInterceptor;
    
    public RequestContextConfig(RequestContextInterceptor requestContextInterceptor) {
        this.requestContextInterceptor = requestContextInterceptor;
    }
    
    /**
     * Register the RequestContextInterceptor for post-authentication processing
     * This handles PATH, BODY, and TOKEN source extraction that requires Spring MVC context
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(requestContextInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/error/**");
        
        log.debug("Registered RequestContextInterceptor for late-phase extraction (PATH, BODY, TOKEN sources)");
    }
    
    /**
     * Alternative way to register the filter with explicit ordering
     * This ensures the filter runs at the servlet container level before Spring Security
     */
    @Bean
    public FilterRegistrationBean<RequestContextFilter> earlyExtractionFilterRegistration(
            RequestContextFilter filter) {

        FilterRegistrationBean<RequestContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 5); // Even earlier than the filter's own order
        registration.setName("earlyExtractionFilter");

        log.debug("Registered EarlyExtractionFilter with order: {}", registration.getOrder());

        return registration;
    }
}
