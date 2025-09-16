package com.example.demo.config;

import com.example.demo.filter.RequestContextFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Security configuration that demonstrates the early extraction filter
 * running before Spring Security's authentication filters
 */
@Slf4j
@Configuration
@EnableWebSecurity
public class DemoSecurityConfig {

    private final RequestContextFilter earlyExtractionFilter;

    public DemoSecurityConfig(@Qualifier("customRequestContextFilter") RequestContextFilter earlyExtractionFilter) {
        this.earlyExtractionFilter = earlyExtractionFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        log.info("Configuring Security Filter Chain");
        
        http
            // Disable CSRF for demo purposes (enable in production as needed)
            .csrf(AbstractHttpConfigurer::disable)
            
            // Configure session management
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            
            // Configure authorization rules
            .authorizeHttpRequests(authz -> authz
                // Allow actuator endpoints
                .requestMatchers("/actuator/**").permitAll()
                // Allow public endpoints
                .requestMatchers("/public/**", "/health", "/info").permitAll()
                // Require authentication for all other requests
                .anyRequest().authenticated())
            
            // Add the early extraction filter BEFORE Spring Security's authentication filters
            .addFilterBefore(earlyExtractionFilter, UsernamePasswordAuthenticationFilter.class)
            
            // For demo purposes, we'll use HTTP Basic authentication instead of JWT
            // This allows us to demonstrate the early extraction filter without complex JWT setup
            .httpBasic(httpBasic -> {
                // Basic authentication configuration
                // This will prompt for username/password for protected endpoints
            });

        return http.build();
    }


}
