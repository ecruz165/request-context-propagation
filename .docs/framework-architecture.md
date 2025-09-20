# Request Context Propagation Framework - Complete Architecture

## 🏗️ Framework Architecture Overview

The Request Context Propagation Framework is built around a **unified source handler architecture** that provides consistent extraction, enrichment, and observability across all source types. The framework supports **seven source types** with **multi-phase processing**, **four-directional data flow**, and **comprehensive observability integration**.

### **Supported Source Types**
- **HEADER** - HTTP headers (full bidirectional support)
- **COOKIE** - HTTP cookies (upstream-only for security)
- **QUERY** - Query parameters (request-only)
- **CLAIM** - JWT claims (extract-only from Spring Security)
- **PATH** - URL path variables (extract-only)
- **BODY** - JSON request/response bodies (with JSONPath support)

## 📦 Package Structure and Components

### **Auto-Configuration Layer**
```
com.example.demo.autoconfigure/
├── EnableRequestContextV2.java          # Framework activation annotation
├── RequestContextAutoConfig.java        # Spring Boot auto-configuration
└── RequestContextBootstrap.java         # Component scanning configuration
```

### **Core Configuration Layer**
```
com.example.demo.config/
├── RequestContextConfig.java            # Main configuration class
├── RequestContextWebClientBuilder.java  # WebClient integration builder
└── props/
    └── RequestContextProperties.java    # YAML configuration properties
```

### **Core Data Structure**
```
com.example.demo.service/
└── RequestContext.java                  # Core context storage with HttpServletRequest integration
```

### **Unified Source Handler Layer**
```
com.example.demo.service.source/
├── SourceHandler.java                   # Unified interface for all source types
├── SourceHandlers.java                  # Central registry and strategy pattern
├── HeaderSourceHandler.java             # HTTP headers (bidirectional)
├── CookieSourceHandler.java             # HTTP cookies (upstream-only)
├── QuerySourceHandler.java              # Query parameters (request-only)
├── ClaimSourceHandler.java              # JWT claims (extract-only)
├── PathSourceHandler.java               # URL path variables (extract-only)
└── BodySourceHandler.java               # JSON bodies with JSONPath (response extraction)
```

### **Filter and Interceptor Layer**
```
com.example.demo.filter/
├── RequestContextFilter.java            # Early extraction filter (pre-authentication)
└── RequestContextInterceptor.java       # Post-authentication enrichment and response handling
```

### **WebClient Integration Layer**
```
com.example.demo.filter/
├── RequestContextWebClientPropagationFilter.java  # Context propagation to downstream
├── RequestContextWebClientCaptureFilter.java      # Response data capture with buffering
└── RequestContextWebClientLoggingFilter.java      # Request/response logging with MDC
```

### **Service Orchestration Layer**
```
com.example.demo.service/
├── RequestContextService.java           # Core orchestration and lifecycle management
└── RequestContextEnricher.java          # Value transformation and propagation logic
```

### **Utility and Helper Layer**
```
com.example.demo.util/
└── MaskingHelper.java                   # Sensitive data masking utilities
```

### **Logging and Structured Output Layer**
```
com.example.demo.logging/
└── RequestContextJsonProvider.java      # Custom Logback JSON provider with intelligent grouping
```

### **Observability and Monitoring Layer**
```
com.example.demo.observability/
├── RequestContextObservationConvention.java  # Micrometer observation convention
├── RequestContextObservationFilter.java      # Observation filter for span enrichment
├── RequestContextObservabilityConfig.java    # Observability beans configuration
└── RequestContextMetricsService.java         # Custom metrics with context tags
```

### **Test Infrastructure**
```
src/test/java/com/example/demo/
├── TestApplicationTests.java            # Spring Boot application context test
├── controller/
│   ├── TestController.java              # Test controller for framework validation
│   └── ProtectedController.java         # Protected endpoints for JWT testing
└── api/
    └── SimpleRequestContextFieldsTest.java  # Comprehensive API tests with REST Assured
```

## 📁 Detailed Component Descriptions

### **Auto-Configuration Components**

#### **`@EnableRequestContextV2`**
- **Purpose**: Single annotation to activate the entire framework
- **Key Features**:
  - Imports `RequestContextAutoConfig` for automatic bean registration
  - Enables component scanning for all framework packages
  - Zero-configuration activation with sensible defaults
  - Compatible with Spring Boot auto-configuration

#### **`RequestContextAutoConfig.java`**
- **Purpose**: Spring Boot auto-configuration entry point
- **Key Features**:
  - Imports `RequestContextBootstrap` for component scanning
  - Conditional bean registration based on classpath presence
  - Integration with Spring Boot's auto-configuration system

#### **`RequestContextBootstrap.java`**
- **Purpose**: Component scanning configuration
- **Key Features**:
  - Scans all framework packages: config, filter, logging, observability, service
  - Ensures all framework components are registered as Spring beans
  - Provides foundation for auto-wiring and dependency injection

### **Core Framework Components**

#### **`RequestContext.java`** - Core Data Structure
- **Purpose**: Thread-safe context storage with async/reactive compatibility
- **Key Features**:
  - **HttpServletRequest attribute storage** (primary) for request-scoped persistence
  - Thread-safe `ConcurrentHashMap` for context values and masked values
  - Static methods for context access from any thread handling the request
  - Support for sensitive data masking with separate storage
  - Utility methods for context manipulation, validation, and cleanup
  - Compatible with async processing, reactive streams, and WebClient operations

#### **`SourceHandler.java`** - Unified Interface
- **Purpose**: Standardized interface for all source type operations
- **Key Features**:
  - **Five operations**: upstream request/body, upstream response, downstream request/response
  - Generic type support for different request types (`HttpServletRequest`, `ClientRequest`)
  - Optional enrichment type specification for propagation-capable sources
  - Default implementations with unsupported operation warnings
  - Foundation for strategy pattern and consistent behavior

#### **`SourceHandlers.java`** - Central Registry
- **Purpose**: Strategy pattern implementation and central access point
- **Key Features**:
  - Auto-wired registration of all source handler implementations
  - Type-safe handler selection and method delegation
  - Overloaded methods for different operation contexts
  - Centralized error handling and logging
  - Single point of access for all source operations

### **Source Handler Implementations**

#### **`HeaderSourceHandler.java`** - HTTP Headers
- **Full bidirectional support**: ✅ Extract, ✅ Upstream Response, ✅ Downstream Request, ✅ Downstream Response
- **Use Cases**: User IDs, tenant IDs, correlation IDs, API keys, service versions
- **Features**: Header name normalization, sensitive data masking
- **Security**: Supports masking for authorization headers and sensitive data

#### **`CookieSourceHandler.java`** - HTTP Cookies
- **Upstream-only support**: ✅ Extract, ✅ Upstream Response, ❌ No downstream propagation
- **Use Cases**: Session IDs, user preferences, client state, authentication tokens
- **Security**: Prevents cookie propagation to downstream services (security best practice)
- **Features**: HttpOnly and Secure flags, configurable security constraints

#### **`QuerySourceHandler.java`** - Query Parameters
- **Request-only support**: ✅ Extract, ✅ Downstream Request, ❌ No response propagation
- **Use Cases**: API versions, format preferences, pagination parameters, client metadata
- **Features**: URL encoding support, query parameter concatenation

#### **`ClaimSourceHandler.java`** - JWT Claims
- **Extract-only support**: ✅ Extract from Spring Security context, ❌ No propagation
- **Use Cases**: User roles, permissions, nested user data, tenant information
- **Features**: Nested claims with dot notation (e.g., `user.email`, `roles[0]`)
- **Integration**: Works with Spring Security JWT authentication

#### **`PathSourceHandler.java`** - URL Path Variables
- **Extract-only support**: ✅ Extract from Spring MVC path variables, ❌ No propagation
- **Use Cases**: Resource IDs, entity identifiers, route parameters
- **Features**: Integration with Spring MVC `@PathVariable` mechanism
- **Timing**: Extracted during post-authentication phase when handler method is available

#### **`BodySourceHandler.java`** - JSON Request/Response Bodies
- **Response extraction support**: ❌ Upstream request, ✅ Upstream request body, ❌ Upstream response, ❌ Downstream request, ✅ Downstream response
- **Use Cases**: API response data, nested JSON values, complex data structures
- **Features**: JSONPath extraction, entire response capture, response body buffering
- **JSONPath Support**: Complex expressions like `$.user.profile.email`, `$.data[0].id`




### **Filter and Interceptor Components**

#### **`RequestContextFilter.java`** - Early Extraction Filter
- **Purpose**: Pre-authentication context extraction (runs before Spring Security)
- **Key Features**:
  - Extracts from non-authenticated sources: HEADER, COOKIE, QUERY, PATH
  - Initializes RequestContext and stores in HttpServletRequest
  - Sets up early MDC for structured logging
  - Handles context cleanup in finally block
  - Configurable path exclusions for health checks and static resources

#### **`RequestContextInterceptor.java`** - Post-Authentication Interceptor
- **Purpose**: Post-authentication enrichment and response handling
- **Key Features**:
  - Extracts from authenticated sources: CLAIM (JWT claims)
  - Enriches context with framework-provided fields (apiHandler)
  - Validates required fields before controller execution
  - Handles request body extraction via RequestBodyAdvice
  - Enriches upstream responses via ResponseBodyAdvice

### **WebClient Integration Components**

#### **`RequestContextWebClientPropagationFilter.java`** - Context Propagation
- **Purpose**: Propagates context to downstream service requests
- **Key Features**:
  - Multi-source propagation: HEADER, QUERY parameters
  - System-specific propagation filtering using extSysIds
  - Value transformation and expression evaluation
  - Core tracing header propagation (X-Request-ID, X-Correlation-ID)
  - Integration with RequestContextEnricher for data preparation

#### **`RequestContextWebClientCaptureFilter.java`** - Response Capture
- **Purpose**: Captures data from downstream service responses
- **Key Features**:
  - Response header extraction
  - Response body buffering to prevent consumption conflicts
  - JSONPath extraction from JSON response bodies
  - Context enrichment with captured downstream data
  - Support for both Reactor Context and ThreadLocal access

#### **`RequestContextWebClientLoggingFilter.java`** - Request/Response Logging
- **Purpose**: Structured logging for WebClient operations
- **Key Features**:
  - MDC enrichment with RequestContext fields
  - Request/response timing measurement
  - Structured log output with context correlation
  - Error tracking and performance monitoring

#### **`RequestContextWebClientBuilder.java`** - WebClient Configuration
- **Purpose**: Pre-configured WebClient builder with context filters
- **Key Features**:
  - Default WebClient builder with all context filters applied
  - System-specific WebClient builders for targeted propagation
  - Correct filter ordering: propagation → capture → logging
  - Default headers and user agent configuration

## 🏗️ Context Storage Architecture

### **HttpServletRequest Storage (Primary)**

The framework uses **HttpServletRequest attribute storage** as the primary mechanism for context persistence, solving critical limitations in modern Spring applications:

#### **Why HttpServletRequest Over ThreadLocal?**

**ThreadLocal Limitations:**
- **❌ Async Processing**: Lost when `@Async` methods execute on different threads
- **❌ Reactive Streams**: WebFlux operators don't propagate ThreadLocal
- **❌ WebClient Calls**: HTTP client operations use different thread pools
- **❌ CompletableFuture**: Async operations lose ThreadLocal context
- **❌ Scheduled Tasks**: Background tasks can't access request-scoped ThreadLocal

**HttpServletRequest Benefits:**
- **✅ Request-Scoped**: Lives for the entire HTTP request lifecycle
- **✅ Thread-Safe**: Available regardless of thread switches
- **✅ Async-Compatible**: Survives thread pool changes and reactive operations
- **✅ WebClient Integration**: Accessible from any thread handling the request
- **✅ Spring Integration**: Works seamlessly with Spring's request scope

#### **Implementation**
```java
public class RequestContext {
    public static final String REQUEST_CONTEXT_ATTRIBUTE = "request.context";

    // Store in HttpServletRequest
    static void setInRequest(HttpServletRequest request, RequestContext context) {
        request.setAttribute(REQUEST_CONTEXT_ATTRIBUTE, context);
    }

    // Retrieve from any thread handling the request
    static Optional<RequestContext> getFromRequest(HttpServletRequest request) {
        Object context = request.getAttribute(REQUEST_CONTEXT_ATTRIBUTE);
        return context instanceof RequestContext ctx ? Optional.of(ctx) : Optional.empty();
    }

    // Static access via Spring's RequestContextHolder
    public static Optional<RequestContext> getCurrentContext() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes servletAttrs) {
            return getFromRequest(servletAttrs.getRequest());
        }
        return Optional.empty();
    }
}
```

#### **Supported Use Cases**
- **Async Controllers**: `@Async` methods retain context access
- **WebClient Filters**: Downstream propagation works across threads
- **Reactive Pipelines**: Context available in reactive operators
- **Background Processing**: Scheduled tasks with request context
- **Service Mesh**: Context propagates through service boundaries

## 🏗️ Unified Source Handler Architecture

### **Core Design Pattern**

The framework uses a **unified source handler pattern** where each source type implements a single `SourceHandler` interface with **five standardized operations**:

```java
public interface SourceHandler {
    SourceType sourceType();

    // Extract from incoming requests
    <T> String extractFromUpstreamRequest(T request, InboundConfig config);

    // Extract from request bodies (BODY sources)
    <T> String extractFromUpstreamRequestBody(T requestBody, InboundConfig config);

    // Enrich outgoing responses
    void enrichUpstreamResponse(HttpServletResponse response, String key, String value);

    // Enrich downstream requests
    void enrichDownstreamRequest(ClientRequest.Builder requestBuilder, String key, String value);

    // Extract from downstream responses
    String extractFromDownstreamResponse(ClientResponse response, InboundConfig config);
}
```

### **Multi-Phase Execution Flow**

#### **Phase 1: Pre-Authentication (RequestContextFilter)**
- **Sources**: HEADER, COOKIE, QUERY, PATH
- **Operations**: `extractFromUpstreamRequest()`
- **Purpose**: Early context extraction before Spring Security
- **Features**: Request ID generation, MDC initialization, context storage

#### **Phase 2: Post-Authentication (RequestContextInterceptor)**
- **Sources**: CLAIM (JWT claims)
- **Operations**: `extractFromUpstreamRequest()`
- **Purpose**: Extract authenticated data from Spring Security context
- **Features**: Handler method capture, required field validation

#### **Phase 3: Request Body Processing (RequestBodyAdvice)**
- **Sources**: BODY
- **Operations**: `extractFromUpstreamRequestBody()`
- **Purpose**: Extract from request body content using JSONPath
- **Features**: JSON parsing, ObjectMapper integration

#### **Phase 4: Downstream Propagation (WebClient Filters)**
- **Sources**: HEADER, QUERY
- **Operations**: `enrichDownstreamRequest()`
- **Purpose**: Propagate context to downstream services
- **Features**: System-specific filtering, value transformation, core header propagation

#### **Phase 5: Response Capture (WebClient Filters)**
- **Sources**: HEADER, BODY
- **Operations**: `extractFromDownstreamResponse()`
- **Purpose**: Capture data from downstream service responses
- **Features**: Response buffering, JSONPath extraction, context enrichment

#### **Phase 6: Response Enrichment (RequestContextInterceptor)**
- **Sources**: HEADER, COOKIE
- **Operations**: `enrichUpstreamResponse()`
- **Purpose**: Enrich responses sent back to clients
- **Features**: Framework field injection, response header/cookie setting

### **Service Orchestration Components**

#### **`RequestContextService.java`** - Core Orchestration
- **Purpose**: Central orchestration for context lifecycle and operations
- **Key Features**:
  - Context initialization and cleanup
  - Multi-phase extraction coordination
  - MDC management and logging integration
  - Required field validation
  - Context summary and debugging utilities

#### **`RequestContextEnricher.java`** - Value Transformation
- **Purpose**: Centralized enrichment logic with value transformation
- **Key Features**:
  - Downstream propagation data preparation
  - Value transformation and expression evaluation
  - System-specific filtering using extSysIds
  - Sensitive data handling and masking
  - PropagationData object creation

### **Observability and Monitoring Components**

#### **`RequestContextJsonProvider.java`** - Structured Logging
- **Purpose**: Custom Logback JSON provider for intelligent log structuring
- **Key Features**:
  - Intelligent field grouping using dot notation (e.g., `user.id`, `api.handler`)
  - Nested JSON structure generation
  - MDC integration with RequestContext fields
  - Context field count metadata
  - Automatic camelCase conversion

#### **`RequestContextObservationConvention.java`** - Metrics Integration
- **Purpose**: Micrometer observation convention for metrics enrichment
- **Key Features**:
  - Configurable cardinality levels (LOW/MEDIUM/HIGH)
  - Context field to metric tag conversion
  - Integration with Spring Boot Actuator metrics
  - Automatic span tag enrichment

#### **`RequestContextObservationFilter.java`** - Tracing Integration
- **Purpose**: Observation filter for distributed tracing enrichment
- **Key Features**:
  - Span enrichment with context data
  - Tracing key configuration support
  - Integration with Spring Cloud Sleuth/Micrometer Tracing
  - Context propagation across service boundaries

#### **`RequestContextMetricsService.java`** - Custom Metrics
- **Purpose**: Service for recording custom metrics with context tags
- **Key Features**:
  - Counter and timer metrics with context tags
  - Configurable cardinality level filtering
  - Integration with Micrometer MeterRegistry
  - Context-aware metric recording

### **Key Architectural Benefits**

#### **🎯 Unified and Consistent**
- **Single Interface**: All source types use the same five operations
- **Strategy Pattern**: `SourceHandlers` registry provides centralized access
- **Predictable Behavior**: Consistent error handling and null safety

#### **🚀 Zero Configuration**
- **Auto-Configuration**: `@EnableRequestContextV2` activates everything automatically
- **Sensible Defaults**: Works out-of-the-box with minimal configuration
- **Spring Boot Integration**: Seamless integration with Spring Boot ecosystem

#### **🔒 Security by Design**
- **Cookie Security**: Upstream-only to prevent downstream propagation
- **Sensitive Data Masking**: Built-in masking for security compliance
- **Required Field Validation**: Fail-fast for missing critical data

#### **⚡ Performance Optimized**
- **HttpServletRequest Storage**: Async/reactive compatible without ThreadLocal limitations
- **Response Buffering**: Prevents WebClient body consumption conflicts
- **Cardinality Management**: Prevents metric explosion with configurable levels

#### **📊 Universal Observability**
- **Structured Logging**: Automatic MDC enrichment with intelligent grouping
- **Metrics Integration**: Configurable cardinality for metric tags
- **Distributed Tracing**: Span enrichment with context data
- **Custom Metrics**: Context-aware metric recording capabilities

#### **🛡️ Production Ready**
- **Comprehensive Error Handling**: Graceful degradation and detailed logging
- **Multi-Phase Processing**: Handles complex extraction scenarios
- **System-Specific Propagation**: Fine-grained control over downstream propagation
- **Enterprise Features**: Masking, validation, observability, and monitoring
