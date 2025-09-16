package com.example.demo.config;


import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.filter.RequestContextFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Slf4j
@Configuration
@EnableConfigurationProperties(RequestContextProperties.class)
public class RequestContextConfig {
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

        log.info("Registered EarlyExtractionFilter with order: {}", registration.getOrder());

        return registration;
    }
}
