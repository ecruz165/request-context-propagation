# Request Context Propagation Framework - Architecture

## 🏗️ Framework Architecture

### **Unified Source Handler Pattern**

The framework is built around a **unified source handler architecture** that provides consistent extraction and enrichment across all source types.

#### **Auto-Configuration Layer**
- **`@EnableRequestContextV2`** - Annotation to activate the framework in host applications
- **`RequestContextAutoConfig.java`** - Spring Boot auto-configuration for automatic bean registration
- **`RequestContextBootstrap.java`** - Component scanning configuration for framework components

#### **Core Configuration Layer**
- **`config/RequestContextConfig.java`** - Main configuration class that registers filters and interceptors
- **`config/RequestContext.java`** - Core data structure with **HttpServletRequest storage** for async/reactive compatibility
- **`config/props/RequestContextProperties.java`** - Configuration properties mapping from YAML to Java objects

#### **Unified Source Handler Layer**
- **`service/source/SourceHandler.java`** - Unified interface for all source types with four operations:
  - `extractFromUpstreamRequest()` - Extract from incoming requests
  - `enrichUpstreamResponse()` - Enrich outgoing responses  
  - `enrichDownstreamRequest()` - Enrich downstream requests
  - `extractFromDownstreamResponse()` - Extract from downstream responses
- **`service/source/SourceHandlers.java`** - Registry and strategy pattern for managing all source handlers
- **`service/source/HeaderSourceHandler.java`** - HTTP header extraction and enrichment
- **`service/source/CookieSourceHandler.java`** - Cookie extraction (upstream-only for security)
- **`service/source/QuerySourceHandler.java`** - Query parameter extraction and enrichment
- **`service/source/ClaimSourceHandler.java`** - JWT claim extraction from Spring Security context
- **`service/source/SessionSourceHandler.java`** - HTTP session attribute extraction (extract-only)

#### **Filter and Interceptor Layer**
- **`filter/RequestContextFilter.java`** - Early extraction filter (runs BEFORE Spring Security)
- **`filter/RequestContextInterceptor.java`** - Handler interceptor (runs AFTER Spring Security)

#### **WebClient Integration Layer**
- **`filter/RequestContextWebClientPropagationFilter.java`** - Propagates context to downstream services using unified source handlers
- **`filter/RequestContextWebClientCaptureFilter.java`** - Captures response data from downstream services
- **`filter/RequestContextWebClientLoggingFilter.java`** - Logs outbound requests with context correlation

#### **Service Orchestration Layer**
- **`service/RequestContextService.java`** - Core orchestration for context lifecycle, validation, and MDC management
- **`service/RequestContextExtractor.java`** - Low-level extraction logic for legacy source types
- **`service/RequestContextEnricher.java`** - Centralized enrichment with value transformation and condition evaluation

#### **Utility Layer**
- **`util/MaskingHelper.java`** - Sensitive data masking utilities
- **`observability/SpanTagBuilderHelper.java`** - Observability span tag construction helpers

#### **Logging and Structured Output Layer**
- **`logging/RequestContextJsonProvider.java`** - Custom Logback JSON provider for structured logging with request context integration and camelCase field conversion

#### **Observability and Monitoring Layer**
- **`observability/RequestContextObservationConvention.java`** - Micrometer observation convention for adding context to metrics and traces
- **`observability/RequestContextObservationFilter.java`** - Observation filter that enriches spans with context data
- **`observability/RequestContextObservabilityConfig.java`** - Configuration for observability beans (ObservedAspect, metrics, etc.)
- **`observability/RequestContextMetricsService.java`** - Service for recording custom metrics with context tags

### **Test Infrastructure**
- **`test/java/com/example/demo/TestApplicationTests.java`** - Spring Boot application context test
- **`test/java/com/example/demo/controller/TestController.java`** - Test controller for framework validation
- **`test/java/com/example/demo/controller/ProtectedController.java`** - Protected endpoints for JWT testing
- **`test/java/com/example/demo/api/SimpleRequestContextFieldsTest.java`** - Comprehensive API tests with REST Assured and WireMock

## 📁 Detailed Component Descriptions

### **Auto-Configuration Components**

#### **`@EnableRequestContextV2`**
- **Purpose**: Activation annotation for host applications
- **Key Features**:
  - Imports `RequestContextAutoConfig` for automatic bean registration
  - Enables component scanning for all framework components
  - Zero-configuration activation of the entire framework

#### **`RequestContextAutoConfig.java`**
- **Purpose**: Spring Boot auto-configuration class
- **Key Features**:
  - Conditional bean registration based on classpath presence
  - Automatic filter and interceptor registration
  - WebClient filter chain configuration
  - Integration with Spring Boot's auto-configuration system

### **Core Framework Components**

#### **`config/RequestContext.java`**
- **Purpose**: Core data structure for storing request context
- **Key Features**:
  - Thread-safe `ConcurrentHashMap` for storing context values
  - **HttpServletRequest attribute storage** (primary) for async/reactive compatibility
  - ThreadLocal support for static access across the request lifecycle
  - Separate storage for masked sensitive values
  - Request attribute integration for servlet-based access
  - Utility methods for context manipulation and cleanup

#### **`service/source/SourceHandler.java`** - Core Interface
- **Purpose**: Unified interface for all source type operations
- **Key Features**:
  - Four standardized operations covering complete request/response lifecycle
  - Generic type support for different request types (`HttpServletRequest`, `ClientRequest`)
  - Optional enrichment type specification for propagation-capable sources
  - Consistent error handling and null safety across all implementations
  - Strategy pattern foundation for the entire framework

#### **`service/source/SourceHandlers.java`** - Central Registry
- **Purpose**: Central registry and strategy pattern implementation
- **Key Features**:
  - Auto-wired registration of all source handler implementations
  - Unified access to all four operations across source types
  - Overloaded methods for different operation contexts
  - Type-safe handler selection and execution
  - Centralized error handling and logging

### **Source Handler Implementations**

#### **`HeaderSourceHandler.java`** - HTTP Headers
- **Full bidirectional support**: ✅ Extract, ✅ Upstream Response, ✅ Downstream Request, ✅ Downstream Response
- **Use Cases**: User IDs, tenant IDs, correlation IDs, API keys
- **Security**: Supports sensitive data masking

#### **`CookieSourceHandler.java`** - HTTP Cookies  
- **Upstream-only support**: ✅ Extract, ✅ Upstream Response, ❌ No downstream propagation (security best practice)
- **Use Cases**: Session IDs, user preferences, client state
- **Security**: Prevents cookie propagation to downstream services

#### **`QuerySourceHandler.java`** - Query Parameters
- **Request-only support**: ✅ Extract, ✅ Downstream Request, ❌ No response propagation
- **Use Cases**: API versions, format preferences, pagination parameters
- **Features**: URL encoding support for special characters

#### **`ClaimSourceHandler.java`** - JWT Claims
- **Extract-only support**: ✅ Extract from Spring Security context, ❌ No propagation (authentication data)
- **Use Cases**: User roles, permissions, nested user data
- **Features**: Supports nested claims with dot notation (e.g., `user.email`)

#### **`SessionSourceHandler.java`** - HTTP Session
- **Extract-only support**: ✅ Extract from HttpSession, ❌ No propagation (interface limitation)
- **Use Cases**: Server-side user state, shopping cart, preferences
- **Features**: Stateful application support with session attribute access

## 🏗️ Context Storage Architecture

### **HttpServletRequest Storage (Primary)**

The framework uses **HttpServletRequest attribute storage** as the primary mechanism for context persistence, instead of ThreadLocal. This architectural decision solves critical limitations in modern Spring applications:

#### **Why Not ThreadLocal?**
ThreadLocal has significant limitations in modern Spring applications:
- **❌ Async Processing**: Context is lost when `@Async` methods execute on different threads
- **❌ Reactive Streams**: WebFlux and reactive operators don't propagate ThreadLocal
- **❌ WebClient Calls**: HTTP client operations often use different thread pools
- **❌ CompletableFuture**: Async operations lose ThreadLocal context
- **❌ Scheduled Tasks**: Background tasks can't access request-scoped ThreadLocal

#### **HttpServletRequest Benefits**
Using HttpServletRequest attributes provides robust context propagation:
- **✅ Request-Scoped**: Lives for the entire HTTP request lifecycle
- **✅ Thread-Safe Propagation**: Available regardless of thread switches
- **✅ Async-Friendly**: Survives thread pool changes and reactive operations
- **✅ WebClient Integration**: Accessible from any thread handling the request
- **✅ Spring Integration**: Works seamlessly with Spring's request scope

#### **Implementation Details**
```java
// Storage in HttpServletRequest
public static void setInRequest(HttpServletRequest request, RequestContext context) {
    request.setAttribute("request.context", context);
}

// Retrieval from any thread handling the request
public static Optional<RequestContext> getFromRequest(HttpServletRequest request) {
    Object context = request.getAttribute("request.context");
    return context instanceof RequestContext ctx ? Optional.of(ctx) : Optional.empty();
}
```

#### **Use Cases Supported**
This storage approach enables the framework to work correctly in:
- **Async Controllers**: `@Async` methods can access context
- **WebClient Filters**: Downstream propagation works across threads
- **Reactive Pipelines**: Context available in reactive operators
- **Background Processing**: Scheduled tasks with request context
- **Service Mesh**: Context propagates through service boundaries

### **ThreadLocal Support (Secondary)**
ThreadLocal is still supported for convenience and backward compatibility, but HttpServletRequest storage is the primary mechanism ensuring reliable context propagation in all scenarios.

## 🏗️ Unified Source Handler Architecture

### **Core Design Pattern**

The framework uses a **unified source handler pattern** where each source type implements a single `SourceHandler` interface with four standardized operations:

```java
public interface SourceHandler {
    // Extract from incoming requests
    <T> String extractFromUpstreamRequest(T request, InboundConfig config);
    
    // Enrich outgoing responses  
    void enrichUpstreamResponse(HttpServletResponse response, String value, OutboundConfig config);
    
    // Enrich downstream requests
    void enrichDownstreamRequest(ClientRequest.Builder requestBuilder, String value, OutboundConfig config);
    
    // Extract from downstream responses
    String extractFromDownstreamResponse(ClientResponse response, InboundConfig config);
}
```

### **Execution Flow with Unified Handlers**

1. **RequestContextFilter (Pre-Authentication)**
   - Uses `SourceHandlers.extractFromUpstreamRequest()` for non-auth sources
   - Generates requestId and initializes context storage
   - Sets up early MDC for structured logging

2. **RequestContextInterceptor (Post-Authentication)**  
   - Uses `SourceHandlers.extractFromUpstreamRequest()` for auth sources (JWT claims)
   - Captures handler method information for observability
   - Uses `SourceHandlers.enrichUpstreamResponse()` for response enrichment

3. **WebClient Propagation Filter**
   - Uses `SourceHandlers.enrichDownstreamRequest()` for context propagation
   - Automatic header, query, and session propagation based on configuration

4. **WebClient Capture Filter**
   - Uses `SourceHandlers.extractFromDownstreamResponse()` for response data capture
   - Updates context with downstream service data

### **Key Benefits of Unified Architecture**

- **🎯 Consistent Interface**: All source types use the same four operations, making the framework predictable and maintainable
- **🔧 Strategy Pattern**: `SourceHandlers` registry provides centralized access to all source type operations
- **🚀 Zero Configuration**: `@EnableRequestContextV2` activates everything automatically via Spring Boot auto-configuration
- **🔒 Security by Design**: Cookie sources are upstream-only, preventing security issues in downstream propagation
- **⚡ Performance Optimized**: HttpServletRequest storage enables async/reactive compatibility without ThreadLocal limitations
- **📊 Universal Observability**: All source types automatically support logging, metrics, and tracing
- **🛡️ Production Ready**: Comprehensive error handling, sensitive data masking, and enterprise-grade features

### **Framework Capabilities**

- **🔄 Bidirectional Propagation**: Headers support full upstream/downstream propagation
- **🍪 Secure Cookie Handling**: Cookies extracted but never propagated (security best practice)  
- **🔑 JWT Integration**: Claims extracted from Spring Security context with nested support
- **📝 Structured Logging**: Custom JSON provider with camelCase conversion and context integration
- **📈 Rich Observability**: Automatic span enrichment with configurable cardinality levels
- **🎛️ Flexible Configuration**: YAML-driven field configuration with no code changes required
