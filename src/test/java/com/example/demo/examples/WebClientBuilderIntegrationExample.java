package com.example.demo.examples;

import com.example.demo.config.RequestContextWebClientBuilder;
import com.example.demo.service.RequestContextService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;

/**
 * Example showing how your WebClientBuilder module can integrate with
 * ContextAwareWebClientBuilder for automatic context propagation
 */
@Service
@Slf4j
public class WebClientBuilderIntegrationExample {

    @Autowired
    private RequestContextWebClientBuilder contextAwareBuilder;
    
    @Autowired
    private RequestContextService contextService;

    /**
     * Example 1: Basic cloneable WebClient.Builder usage
     * Your module can provide this pattern to users
     */
    public WebClient.Builder createUserServiceBuilder() {
        return contextAwareBuilder.createForSystem("user-service")
                .baseUrl("https://user-api.company.com")
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json");
        
        // Users can clone this builder:
        // WebClient.Builder cloned = userServiceBuilder.clone()
        //     .baseUrl("https://different-url.com")
        //     .defaultHeader("Custom-Header", "value");
    }

    /**
     * Example 2: Advanced customization with context propagation
     */
    public WebClient.Builder createAdvancedBuilder(String systemName, String baseUrl) {
        return contextAwareBuilder.createForSystemWithCustomization(systemName, builder -> 
            builder.baseUrl(baseUrl)
                   .defaultHeader("X-API-Version", "v2")
                   .defaultHeader("X-Client-Name", "MyWebClientModule")
                   .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(1024 * 1024))
                   .clientConnector(
                       new org.springframework.http.client.reactive.ReactorClientHttpConnector(
                           reactor.netty.http.client.HttpClient.create()
                               .responseTimeout(Duration.ofSeconds(30))
                       )
                   )
        );
    }

    /**
     * Example 3: Multiple system builders that can be cloned
     */
    public Map<String, WebClient.Builder> createSystemBuilders() {
        return Map.of(
            "user-service", contextAwareBuilder.createForSystem("user-service")
                .baseUrl("https://user-api.company.com")
                .defaultHeader("X-Service-Type", "user"),
                
            "order-service", contextAwareBuilder.createForSystem("order-service")
                .baseUrl("https://order-api.company.com")
                .defaultHeader("X-Service-Type", "order"),
                
            "payment-service", contextAwareBuilder.createForSystem("payment-service")
                .baseUrl("https://payment-api.company.com")
                .defaultHeader("X-Service-Type", "payment")
                .defaultHeader("X-Security-Level", "high")
        );
    }

    /**
     * Example 4: Demonstrating cloning and customization
     */
    public void demonstrateCloning() {
        // Base builder with context propagation
        WebClient.Builder baseBuilder = contextAwareBuilder.createForSystem("api-service")
                .baseUrl("https://api.company.com")
                .defaultHeader("X-Client", "WebClientModule");

        // Clone for different environments
        WebClient.Builder devBuilder = baseBuilder.clone()
                .baseUrl("https://dev-api.company.com")
                .defaultHeader("X-Environment", "development");

        WebClient.Builder stagingBuilder = baseBuilder.clone()
                .baseUrl("https://staging-api.company.com")
                .defaultHeader("X-Environment", "staging");

        WebClient.Builder prodBuilder = baseBuilder.clone()
                .baseUrl("https://prod-api.company.com")
                .defaultHeader("X-Environment", "production")
                .defaultHeader("X-Security-Token", "prod-token");

        // All builders automatically have context propagation!
        WebClient devClient = devBuilder.build();
        WebClient stagingClient = stagingBuilder.build();
        WebClient prodClient = prodBuilder.build();

        log.info("Created cloned WebClients with automatic context propagation");
    }

    /**
     * Example 5: Integration with your existing WebClient factory pattern
     */
    @Service
    public static class YourWebClientFactory {
        
        @Autowired
        private RequestContextWebClientBuilder contextAwareBuilder;
        
        /**
         * Your existing factory method - just replace WebClient.builder() 
         * with contextAwareBuilder.create()
         */
        public WebClient.Builder createWebClientBuilder(String systemName, String baseUrl) {
            // OLD: return WebClient.builder()
            // NEW: return contextAwareBuilder.create()
            return contextAwareBuilder.createForSystem(systemName)
                    .baseUrl(baseUrl)
                    .defaultHeader("X-Factory-Created", "true");
        }
        
        /**
         * Factory method that returns cloneable builders
         */
        public WebClient.Builder getCloneableBuilder(String systemName) {
            return contextAwareBuilder.createForSystem(systemName)
                    .defaultHeader("X-Cloneable", "true")
                    .defaultHeader("X-Factory", "YourWebClientFactory");
        }
    }

    /**
     * Example 6: Demonstrating automatic context propagation in action
     */
    public Mono<Map<String, Object>> demonstrateContextPropagation() {
        // Create a cloneable builder
        WebClient.Builder builder = contextAwareBuilder.createForSystem("demo-service")
                .baseUrl("http://localhost:8089");  // WireMock for testing

        // Clone it for specific use case
        WebClient.Builder customBuilder = builder.clone()
                .defaultHeader("X-Demo", "context-propagation");

        // Build the WebClient
        WebClient webClient = customBuilder.build();

        // Make a call - context is automatically propagated!
        return webClient.get()
                .uri("/downstream/service")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> {
                    // Context values captured from downstream response are automatically available
                    String capturedVersion = contextService.getField("downstreamServiceVersionPublic");
                    String capturedStatus = contextService.getField("downstreamResponseStatus");
                    
                    log.info("Captured from downstream - Version: {}, Status: {}", 
                            capturedVersion, capturedStatus);
                    
                    return Map.of(
                        "response", response,
                        "capturedVersion", capturedVersion != null ? capturedVersion : "null",
                        "capturedStatus", capturedStatus != null ? capturedStatus : "null",
                        "contextPropagation", "automatic"
                    );
                });
    }

    /**
     * Example 7: Selective filter usage for specific requirements
     */
    public WebClient.Builder createMinimalBuilder() {
        // Only enable propagation and capture, skip logging for performance
        return contextAwareBuilder.createWithSelectiveFilters(
            true,   // enablePropagation
            true,   // enableCapture  
            false   // enableLogging - skip for high-performance scenarios
        ).baseUrl("https://high-performance-api.company.com");
    }

    /**
     * Example 8: Conditional context usage
     */
    public Mono<String> conditionalContextUsage() {
        WebClient.Builder builder = contextAwareBuilder.create();
        
        if (contextAwareBuilder.isContextAvailable()) {
            // Context is available - use enhanced builder
            return builder.baseUrl("https://context-aware-api.company.com")
                    .build()
                    .get()
                    .uri("/context-enabled-endpoint")
                    .retrieve()
                    .bodyToMono(String.class);
        } else {
            // No context - use basic builder
            return WebClient.builder()
                    .baseUrl("https://basic-api.company.com")
                    .build()
                    .get()
                    .uri("/basic-endpoint")
                    .retrieve()
                    .bodyToMono(String.class);
        }
    }
}
