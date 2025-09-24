package com.jefelabs.modules.requestcontext.demo.config;

import com.jefelabs.modules.requestcontext.config.RequestContextWebClientBuilder;
import com.jefelabs.modules.requestcontext.filter.RequestContextWebClientPropagationFilter;
import com.jefelabs.modules.requestcontext.service.RequestContextService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Test configuration for WebClient with propagation filter and context-aware scheduler
 */
@Configuration
public class TestWebClientConfig {

    @Bean
    public WebClient webClient(RequestContextWebClientBuilder webClientBuilder) {
        return webClientBuilder.create()
                .baseUrl("http://localhost:8089")
                .build();
    }

    /**
     * Alternative: WebClient factory method that creates instances with context-aware scheduler
     */
    @Bean
    public WebClientFactory webClientFactory(RequestContextWebClientPropagationFilter propagationFilter,
                                            RequestContextService contextService) {
        return new WebClientFactory(propagationFilter, contextService);
    }

    /**
     * Factory for creating WebClient instances with context propagation
     */
    public static class WebClientFactory {
        private final RequestContextWebClientPropagationFilter propagationFilter;
        private final RequestContextService contextService;

        public WebClientFactory(RequestContextWebClientPropagationFilter propagationFilter,
                               RequestContextService contextService) {
            this.propagationFilter = propagationFilter;
            this.contextService = contextService;
        }

        /**
         * Create a WebClient for a specific base URL
         * Context propagation handled automatically by ContextAwareWebClientBuilder
         */
        public WebClient createWithContextPropagation(String baseUrl) {
            return WebClient.builder()
                    .baseUrl(baseUrl)
                    .filter(propagationFilter.createFilter())
                    .build();
        }
    }
}
