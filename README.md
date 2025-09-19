# Request Context Propagation Framework
==========================

A **Spring Boot Starter** module for declarative, configuration-driven request context management across distributed services. This framework runs **BEFORE** Spring Security to capture context information even for failed authentication attempts.

## 🎯 Overview

The Request Context Propagation Framework is a **Spring Boot Starter** that provides zero-code context management for microservices. Simply add `@EnableRequestContextV2` to your configuration class and define your context requirements in YAML. The framework automatically:

- **Extracts** context from incoming requests using unified source handlers (JWT, headers, cookies, etc.)
- **Enriches** observability spans with context for DataDog/tracing
- **Propagates** context to downstream services via WebClient
- **Injects** context into logs via MDC with structured JSON support
- **Validates** required context values with comprehensive error handling

## 🚀 Quick Start

### 1. Add the Dependency
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>request-context-propagation</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable the Framework
```java
@SpringBootApplication
@EnableRequestContextV2
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. Configure Context Fields
```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: HEADER
          key: X-User-ID
      downstream:
        outbound:
          enrichAs: HEADER
          key: X-User-ID
      observability:
        logging:
          mdcKey: "user.id"
```

That's it! The framework will automatically extract, propagate, and log your context fields.

## 🏗️ Framework Architecture

The Request Context Propagation Framework uses a **unified source handler architecture** that provides consistent extraction and enrichment across all source types. The framework is built around a central `SourceHandler` interface with four standardized operations covering the complete request/response lifecycle.

**📖 For detailed architecture documentation, see: [`.docs/framework-architecture.md`](.docs/framework-architecture.md)**

### **Key Architectural Components:**

- **🎯 Unified Source Handler Pattern**: Single interface for all source types (HEADER, COOKIE, QUERY, CLAIM, SESSION)
- **🔧 Central Registry**: `SourceHandlers` provides strategy pattern access to all handlers
- **🚀 Auto-Configuration**: `@EnableRequestContextV2` activates everything automatically
- **⚡ HttpServletRequest Storage**: Async/reactive compatible context storage
- **🔒 Security by Design**: Upstream-only cookie handling, sensitive data masking
- **📊 Universal Observability**: Automatic logging, metrics, and tracing integration





## 🔧 Technical Features

### Framework Capabilities

1. **Early Context Extraction**
   - Runs before Spring Security for complete request coverage
   - Captures context even for failed authentication attempts
   - Non-blocking extraction with graceful error handling

2. **Declarative Configuration**
   - YAML-based field configuration
   - No code changes required for new context fields
   - Runtime configuration updates support

3. **Multi-Source Data Extraction**
   - HTTP headers, cookies, query parameters
   - JWT claims (with and without signature validation)
   - Request attributes and session data
   - Custom extraction strategies

4. **Comprehensive Observability**
   - Automatic metrics tagging with context
   - Distributed tracing integration
   - Structured logging with MDC
   - Performance monitoring and alerting

5. **WebClient Integration**
   - Automatic context propagation to downstream services
   - Response data capture and processing
   - Request/response logging and monitoring
   - Error handling and circuit breaker integration

6. **Security and Compliance**
   - Sensitive data masking and filtering
   - Configurable field-level security policies
   - Audit logging and compliance reporting
   - Integration with security frameworks

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

**Configuration Approach**: The framework is **declarative by default** using YAML configuration, but also supports **programmatic adjustment** for dynamic business logic requirements.

```yaml
# Declarative Configuration (Primary)
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: HEADER
          key: X-User-ID
```

```java
// Programmatic Configuration (For Business Logic)
@Autowired
private RequestContextService requestContextService;

// Runtime field access
String userId = requestContextService.getField("userId");
requestContextService.setField("dynamicField", computedValue);

// Runtime configuration management
requestContextService.addFieldConfiguration("newField", fieldConfig);
```

**Context Storage**: Uses HttpServletRequest as primary storage mechanism for async/reactive compatibility.

```java
// Internal framework methods (not for application use)
// Storage in HttpServletRequest
static void setInRequest(HttpServletRequest request, RequestContext context) {
    request.setAttribute("request.context", context);
}

// Retrieval from any thread handling the request
static Optional<RequestContext> getFromRequest(HttpServletRequest request) {
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

### **Why HttpServletRequest Over ThreadLocal?**
The framework uses HttpServletRequest storage exclusively (no ThreadLocal) because:
- **Async/Reactive Safe**: Works across thread boundaries and reactive streams
- **WebClient Compatible**: Context available in all downstream calls
- **Spring Integration**: Leverages Spring's request scope lifecycle
- **Thread Pool Safe**: Survives thread switches in async processing

### Testing Infrastructure

- **JaCoCo Test Coverage**: Comprehensive code coverage reporting with exclusions for configuration classes
- **REST Assured Integration**: API testing framework for endpoint validation
- **WireMock Support**: Service mocking for integration testing
- **JSON Structured Logging**: Logstash encoder for structured log analysis
- **AspectJ Observability**: AOP-based observability with `@Observed` annotation support

## 📚 Additional Documentation

- **`HOW_TO_TEST.md`** - Comprehensive testing guide and examples
- **`request-flow.md`** - Detailed request flow diagrams and sequence documentation
- **`README.md` (test)** - Product requirements document and technical specifications

## 🚀 Integration Guide

### **For Host Applications**

1. **Add Dependency** to your `pom.xml`:
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>request-context-propagation</artifactId>
    <version>1.0.0</version>
</dependency>
```

2. **Enable Framework** in your configuration:
```java
@SpringBootApplication
@EnableRequestContextV2
public class YourApplication {
    // Framework auto-configures everything
}
```

3. **Configure Context Fields** in `application.yaml`:
```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: HEADER
          key: X-User-ID
      observability:
        logging:
          mdcKey: "user.id"
```

4. **Use Context in Your Code**:
```java
@RestController
public class YourController {

    @GetMapping("/api/users")
    public ResponseEntity<?> getUsers() {
        RequestContext context = RequestContext.getCurrentContext().orElse(null);
        String userId = context != null ? context.get("userId") : null;
        // Your business logic with context
        return ResponseEntity.ok(result);
    }
}
```

The framework automatically handles extraction, propagation, logging, and observability with zero additional code!

## 📊 Structured JSON Logging

The framework includes comprehensive structured JSON logging support with multiple configuration options:

### **Logback Configuration Profiles**

- **`dev/development/local`** - Console logging with pattern format for development
- **`json/json-dev`** - JSON console and file logging for development testing
- **`prod/production`** - JSON console and async file logging for production
- **`test`** - Minimal console logging for testing
- **Default** - JSON logging when no profile is active

### **JSON Logging Features**

- **Enhanced JSON Structure** with LoggingEventCompositeJsonEncoder
- **Custom RequestContextJsonProvider** for structured context fields
- **ISO-8601 timestamps** with UTC timezone
- **Service metadata** (name, version, environment)
- **Request context integration** with camelCase field conversion
- **Sensitive data masking** in log output
- **File rotation** with compression and size limits
- **Async logging** for production performance

### **Usage Examples**

```bash
# Run with JSON logging
./mvnw spring-boot:run -Dspring-boot.run.profiles=json

# Run with production JSON logging
./mvnw spring-boot:run -Dspring-boot.run.profiles=prod

# Test with context headers
curl -X GET "http://localhost:8080/api/v1/hello" \
  -H "X-Request-ID: test-123" \
  -H "X-User-ID: user-456" \
  -H "X-Tenant-ID: tenant-789"
```

**Sample JSON Log Output:**
```json
{
  "@timestamp": "2025-09-16T17:35:52.341Z",
  "level": "DEBUG",
  "logger": "com.example.demo.filter.RequestContextFilter",
  "message": "RequestContext initialized for GET /api/v1/hello with 2 fields",
  "thread": "http-nio-8080-exec-1",
  "service": "request-context-propagation",
  "version": "0.0.1-SNAPSHOT",
  "environment": "local",
  "context": {
    "traceId": "6d22ce173f0892ab6b373b2fb1c0982d",
    "spanId": "df70492ac2d57290",
    "requestId": "test-123"
  }
}
```

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

### **Rich Context in Logs**
```json
{
  "@timestamp": "2025-09-19T10:30:45.123Z",
  "level": "INFO",
  "message": "Processing user request",
  "context": {
    "requestId": "abc-123",
    "userId": "john.doe",
    "tenantId": "acme-corp",
    "apiHandler": "UserController/getUser"
  }
}
```

## 📚 Additional Documentation

- **[`.docs/framework-architecture.md`](.docs/framework-architecture.md)** - Complete framework architecture documentation with detailed component descriptions and design patterns
- **[`.docs/overview-of-source-types.md`](.docs/overview-of-source-types.md)** - Comprehensive source type support matrix with detailed descriptions and configuration examples
- **[`.docs/request-flow.md`](.docs/request-flow.md)** - Detailed sequence diagrams showing unified source handler architecture and four-directional propagation flow
- **`TEST_STATUS.md`** - Source Type Support Matrix and capabilities grid
- **`src/test/java/com/example/demo/README.md`** - Comprehensive testing strategy and API examples

## 🧪 Testing and Validation

The framework includes comprehensive test coverage with:
- **REST Assured API Tests**: Full HTTP endpoint testing with context validation
- **WireMock Integration**: Downstream service mocking for propagation testing
- **JaCoCo Coverage**: 74.5% line coverage with detailed reporting
- **Multiple Test Patterns**: 16 different configuration patterns covering all source types

## 🎯 Production Readiness

- **✅ Spring Boot Starter**: Zero-configuration activation with `@EnableRequestContextV2`
- **✅ Auto-Configuration**: Automatic bean registration and filter chain setup
- **✅ Async/Reactive Support**: HttpServletRequest storage for thread-safe context propagation
- **✅ Security Hardened**: Sensitive data masking and secure cookie handling
- **✅ Enterprise Observability**: Structured JSON logging, metrics, and distributed tracing
- **✅ Performance Optimized**: Early extraction, lazy evaluation, and request-scoped caching

The framework is production-ready and battle-tested for enterprise microservices architectures.
   