# Request Context Propagation Framework
==========================

A declarative, configuration-driven framework for extracting, storing, and propagating request context across distributed services. This framework runs **BEFORE** Spring Security to capture context information even for failed authentication attempts.

## 🎯 Overview

The Request Context Propagation Framework provides a zero-code solution for managing request context (user IDs, tenant IDs, correlation IDs, etc.) across your microservices architecture. Define your context requirements in YAML, and the framework automatically:

- **Extracts** context from incoming requests (JWT, headers, cookies, etc.)
- **Enriches** observability spans with context for DataDog/tracing
- **Propagates** context to downstream services via WebClient/RestTemplate
- **Injects** context into logs via MDC
- **Validates** required context values

## 🏗️ Project Architecture

### Core Components

#### **Application Entry Point**
- **`DemoApplication.java`** - Main Spring Boot application class with `@EnableConfigurationProperties` for request context configuration

#### **Configuration Layer**
- **`config/RequestContextConfig.java`** - Main configuration class that registers filters and interceptors
- **`config/RequestContext.java`** - Core data structure that holds extracted context values with ThreadLocal support
- **`config/props/RequestContextProperties.java`** - Configuration properties class that maps YAML configuration to Java objects
- **`config/DemoSecurityConfig.java`** - Spring Security configuration with OAuth2 and HTTP Basic authentication
- **`config/DemoWebClientConfig.java`** - WebClient configuration with context propagation filters

#### **Filter Layer (Request Processing Pipeline)**
- **`filter/RequestContextFilter.java`** - **Early extraction filter** that runs BEFORE Spring Security to capture non-authenticated data
- **`filter/RequestContextInterceptor.java`** - Handler interceptor that runs AFTER Spring Security to capture authenticated data and handler information

#### **WebClient Integration (Outbound Requests)**
- **`filter/RequestContextPropagationWebClientFilter.java`** - Propagates context headers to downstream services using RequestContextEnricher for transformation and condition evaluation
- **`filter/RequestContextCaptureWebClientFilter.java`** - Captures response data from downstream services using RequestContextService for consistent context management
- **`filter/RequestContextLoggingWebClientFilter.java`** - Logs outbound requests with context information and MDC enrichment for correlation

#### **Service Layer**
- **`service/RequestContextService.java`** - Core business logic for context lifecycle management, validation, MDC updates, and downstream response enrichment
- **`service/RequestContextExtractor.java`** - Low-level extraction logic for different source types (headers, JWT, cookies, query params, path variables, session, attributes, form data, request body)
- **`service/RequestContextEnricher.java`** - **NEW** - Centralized enrichment and extraction logic for downstream propagation with value transformation, expression evaluation, and condition checking

#### **Logging Layer**
- **`logging/RequestContextJsonProvider.java`** - **NEW** - Custom Logback JSON provider for structured logging with request context integration and camelCase field conversion

#### **Observability Layer**
- **`observability/RequestContextObservationConvention.java`** - Micrometer observation convention for adding context to metrics and traces
- **`observability/RequestContextObservationFilter.java`** - Observation filter that enriches spans with context data
- **`observability/RequestContextObservabilityConfig.java`** - Configuration for observability beans (ObservedAspect, metrics, etc.)
- **`observability/RequestContextMetricsService.java`** - Service for recording custom metrics with context tags

#### **Controller Layer (Demo/Testing)**
- **`controller/DemoTestController.java`** - Comprehensive test controller with endpoints for testing all framework features
- **`controller/DemoTestService.java`** - Service class demonstrating context usage in business logic

### Test Infrastructure
- **`test/java/com/example/demo/DemoApplicationTests.java`** - Basic Spring Boot application context test

## 📁 Detailed File Descriptions

### Core Framework Files

#### **`DemoApplication.java`**
- **Purpose**: Main Spring Boot application entry point
- **Key Features**:
  - Enables configuration properties with `@EnableConfigurationProperties(RequestContextProperties.class)`
  - Bootstraps the entire request context propagation framework
  - Configures component scanning for all framework components

#### **`config/RequestContext.java`**
- **Purpose**: Core data structure for storing request context
- **Key Features**:
  - Thread-safe `ConcurrentHashMap` for storing context values
  - ThreadLocal support for static access across the request lifecycle
  - Separate storage for masked sensitive values
  - Request attribute integration for servlet-based access
  - Utility methods for context manipulation and cleanup

#### **`config/props/RequestContextProperties.java`**
- **Purpose**: Configuration properties mapping from YAML to Java objects
- **Key Features**:
  - Maps `request-context` YAML configuration to strongly-typed Java classes
  - Supports field-level configuration for extraction, propagation, and observability
  - Validation annotations for configuration integrity
  - Nested configuration classes for different aspects (upstream, downstream, security, observability)

#### **`config/RequestContextConfig.java`**
- **Purpose**: Spring configuration for registering framework components
- **Key Features**:
  - Registers `RequestContextFilter` with highest precedence order
  - Configures `RequestContextInterceptor` for handler-level processing
  - Sets up WebClient filters for outbound request processing
  - Manages filter ordering and exclusion patterns

### Filter and Interceptor Layer

#### **`filter/RequestContextFilter.java`**
- **Purpose**: Early extraction filter that runs BEFORE Spring Security
- **Key Features**:
  - Highest precedence order (`Ordered.HIGHEST_PRECEDENCE + 1`)
  - Extracts non-authenticated data (headers, cookies, query parameters)
  - Generates unique request IDs for tracing
  - Initializes MDC for early logging
  - Exception handling with graceful degradation
  - Request timing and duration tracking

#### **`filter/RequestContextInterceptor.java`**
- **Purpose**: Handler interceptor for post-authentication processing
- **Key Features**:
  - Runs AFTER Spring Security authentication
  - Extracts JWT claims and authenticated user data
  - Captures handler method information (controller, method names)
  - Validates required fields based on configuration
  - Updates MDC with complete context
  - Enriches response headers
  - Records completion metrics and timing

### Service Layer

#### **`service/RequestContextService.java`**
- **Purpose**: Core business logic for context lifecycle management and orchestration
- **Key Features**:
  - Context initialization and cleanup with early extraction support
  - Field validation based on configuration with detailed error reporting
  - MDC management for logging integration with automatic updates
  - Response header enrichment for upstream context propagation
  - Integration with authentication systems (JWT, OAuth2, Basic Auth)
  - Handler information extraction and storage for observability
  - Downstream response enrichment using RequestContextEnricher delegation
  - Context summary generation for debugging and monitoring
  - Sensitive data masking and security handling
  - Metrics and tracing field extraction with cardinality control

#### **`service/RequestContextExtractor.java`**
- **Purpose**: Low-level extraction logic for different data sources
- **Key Features**:
  - Multi-source extraction (headers, JWT claims, cookies, query parameters, path variables, session, attributes, form data, request body)
  - JWT parsing without signature validation for early extraction
  - Configurable extraction strategies per field with transformation support
  - Error handling and fallback mechanisms with graceful degradation
  - Support for default values and required field validation
  - Token type support (JWT, OAuth2, Basic Auth) with claim path extraction
  - Path pattern matching for URL parameter extraction
  - Session and attribute extraction for stateful applications

#### **`service/RequestContextEnricher.java`** ⭐ **NEW**
- **Purpose**: Centralized enrichment and extraction logic for downstream propagation
- **Key Features**:
  - Value transformation (BASE64, URL encoding, JSON, number, boolean, expression evaluation)
  - Expression evaluation with placeholder replacement (#fieldName syntax)
  - Condition evaluation for conditional field propagation
  - Structured PropagationData for organized field transfer
  - Integration with RequestContextService for consistent processing
  - Support for multiple enrichment types (HEADER, QUERY, COOKIE, ATTRIBUTE)
  - Sensitive data handling and masking support
  - Reusable transformation logic across the framework

### WebClient Integration Layer

#### **`filter/RequestContextPropagationWebClientFilter.java`**
- **Purpose**: Propagates request context to downstream services using RequestContextEnricher
- **Key Features**:
  - Automatic header propagation based on field configuration
  - Support for different propagation methods (headers, query params, cookies, attributes)
  - Core tracing header propagation (X-Request-Id, X-Correlation-Id)
  - Value transformation and expression evaluation via RequestContextEnricher
  - Condition-based field propagation for dynamic context control
  - Configurable field-level propagation rules with sensitive data handling
  - Debug logging for propagation tracking and troubleshooting

#### **`filter/RequestContextCaptureWebClientFilter.java`**
- **Purpose**: Captures response data from downstream services using RequestContextService
- **Key Features**:
  - Extracts context from downstream service responses (headers primarily)
  - Leverages RequestContextService for consistent context management
  - Configurable field mapping and transformation via service layer
  - Error handling for downstream failures with graceful degradation
  - Integration with sensitive data masking and validation
  - Automatic MDC updates when new fields are captured
  - Support for selective header capture with createSelectiveFilter()

#### **`filter/RequestContextLoggingWebClientFilter.java`**
- **Purpose**: Logs outbound requests with context information and MDC enrichment
- **Key Features**:
  - Request/response logging with context correlation
  - MDC enrichment with configured RequestContext fields
  - Performance timing and metrics for outbound calls
  - Error logging and debugging support with context preservation
  - Configurable log levels and detail based on field configuration
  - Integration with structured logging and sensitive data masking
  - Core field propagation to MDC (requestId, correlationId, handler, principal)

### Logging and Structured Output Layer

#### **`logging/RequestContextJsonProvider.java`** ⭐ **NEW**
- **Purpose**: Custom Logback JSON provider for structured logging with request context integration
- **Key Features**:
  - Extracts context from both MDC and RequestContext for comprehensive logging
  - Converts snake_case field names to camelCase for JSON consistency
  - Integrates with LoggingEventCompositeJsonEncoder for enhanced JSON output
  - Provides structured context field under configurable field name (default: "context")
  - Handles both masked and original values based on sensitivity configuration
  - Adds context metadata (field count, creation time) for debugging
  - Supports ISO-8601 timestamp formatting with UTC timezone
  - Compatible with ELK stack, DataDog, and other log aggregation systems

### Observability and Monitoring Layer

#### **`observability/RequestContextObservationConvention.java`**
- **Purpose**: Micrometer observation convention for metrics and tracing
- **Key Features**:
  - Automatic context injection into metrics and traces
  - Configurable tag generation from context fields
  - Cardinality control for high-volume metrics
  - Sensitive data filtering for observability
  - Integration with DataDog, Prometheus, and other monitoring systems

#### **`observability/RequestContextObservationFilter.java`**
- **Purpose**: Observation filter for span enrichment
- **Key Features**:
  - Enriches tracing spans with context data
  - Baggage propagation for distributed tracing
  - Configurable field inclusion/exclusion
  - Performance-optimized context reading
  - Integration with OpenTelemetry and Zipkin

#### **`observability/RequestContextObservabilityConfig.java`**
- **Purpose**: Configuration for observability infrastructure
- **Key Features**:
  - Bean configuration for `ObservedAspect` (requires AspectJ)
  - Integration with Spring Boot Actuator
  - Micrometer registry configuration
  - Observation-based tracing support

#### **`observability/RequestContextMetricsService.java`**
- **Purpose**: Service for recording custom metrics with context
- **Key Features**:
  - Context-aware metric recording
  - Custom counter, timer, and gauge support
  - Automatic tag application from context
  - Performance monitoring and alerting support
  - Integration with business logic metrics

### Security and Configuration Layer

#### **`config/DemoSecurityConfig.java`**
- **Purpose**: Spring Security configuration with context integration
- **Key Features**:
  - OAuth2 Resource Server configuration for JWT validation
  - HTTP Basic authentication support
  - Security filter chain with proper ordering
  - Integration with request context for authentication data
  - CORS and CSRF configuration for API access

#### **`config/DemoWebClientConfig.java`**
- **Purpose**: WebClient configuration with context propagation
- **Key Features**:
  - WebClient bean configuration with context filters
  - Filter chain setup for outbound request processing
  - Connection pooling and timeout configuration
  - Error handling and retry mechanisms
  - Integration with service discovery and load balancing

### Demo and Testing Layer

#### **`controller/DemoTestController.java`**
- **Purpose**: Comprehensive test controller for framework demonstration
- **Key Features**:
  - Multiple endpoints showcasing different context scenarios
  - JWT authentication testing endpoints
  - WebClient integration demonstrations
  - Error handling and validation examples
  - Async processing with context propagation
  - Response header enrichment examples

#### **`controller/DemoTestService.java`**
- **Purpose**: Service layer demonstrating context usage in business logic
- **Key Features**:
  - Context access patterns in service methods
  - Business logic integration with request context
  - Validation and error handling examples
  - Integration with external services
  - Metrics and logging demonstrations

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

## 🚀 Getting Started

1. **Configure your context fields** in `src/main/resources/application.yaml`
2. **Run the application** with `./mvnw spring-boot:run`
3. **Test endpoints** using the demo controller at `/api/test/*`
4. **Monitor observability** through `/actuator/metrics` and distributed tracing
5. **View logs** with structured JSON format including context fields

The framework is production-ready with comprehensive error handling, performance optimization, and enterprise-grade observability features.

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

## 🚀 Quick Start

### Architecture: Enhanced Filter + Service + Enricher Pattern

1. **RequestContextFilter (Runs BEFORE Spring Security)**
   - Delegates to RequestContextService for context initialization
   - Extracts non-authenticated sources (headers, cookies, query params, path variables, etc.)
   - Generates requestId and captures request metadata
   - Sets up basic MDC for early logging with structured JSON support
   - Provides request timing and duration tracking

2. **RequestContextInterceptor (Runs AFTER Spring Security, BEFORE Controller)**
   - Uses RequestContextService for authenticated data enrichment
   - Extracts JWT claims and principal information
   - Captures handler information (controller, method, annotations)
   - Updates MDC with complete context for comprehensive logging
   - Enriches response headers and validates required fields

3. **RequestContextService (Core Orchestration Layer)**
   - Coordinates all context operations with RequestContextExtractor and RequestContextEnricher
   - Manages context lifecycle, validation, and MDC updates
   - Provides metrics and tracing field extraction with cardinality control
   - Handles downstream response enrichment and sensitive data masking

4. **RequestContextEnricher (NEW - Transformation and Propagation)**
   - Centralizes value transformation (BASE64, URL encoding, JSON, expressions)
   - Handles condition evaluation and expression processing
   - Provides structured PropagationData for WebClient filters
   - Supports multiple enrichment types (HEADER, QUERY, COOKIE, ATTRIBUTE)

5. **RequestContextExtractor (Low-Level Extraction)**
   - Handles multi-source extraction from 10+ source types
   - Supports JWT parsing, path pattern matching, and session extraction
   - Provides error handling and fallback mechanisms

### Key Benefits

- **Complete Context in Controller**: All data (headers, JWT claims, handler info) is available when your controller executes
- **Structured JSON Logging**: Enhanced Logback configuration with custom RequestContextJsonProvider for comprehensive log correlation
- **Centralized Enrichment**: RequestContextEnricher provides reusable transformation and condition logic across the framework
- **Service-Oriented Architecture**: Clear separation of concerns with RequestContextService orchestrating all operations
- **Enhanced WebClient Integration**: Filters now leverage service layer for consistent context management and transformation
- **Handler Information**: The interceptor captures detailed handler metadata:
  - Controller class name and method name
  - Custom annotations (@ApiOperation, @RequestMapping)
  - Request mapping patterns and HTTP methods
- **Multi-Source Extraction**: Support for 10+ source types including JWT claims, path variables, session, attributes, form data, and request body
- **Production-Ready Logging**: Multiple profile support (dev, json, prod) with async logging and file rotation

- **Rich Logging**: Your logs will include:
  ```
  INFO  [requestId=abc-123, handler=UserController.getUser, principal=john.doe, applicationId=app-1]
  Getting user details...
  ```

- **Metrics with Context**: You can record metrics tagged with handler names, making it easy to track performance per endpoint
- **Two-Phase Extraction**:
  - Early extraction for non-auth data (useful for rate limiting, early logging)
  - Late extraction for auth data and handler info (complete context for business logic)

### Execution Flow
1. **Request arrives** → HTTP request enters the application
2. **RequestContextFilter** → Extract headers, cookies, generate requestId (BEFORE Spring Security)
3. **Spring Security** → Authentication and authorization processing
4. **RequestContextInterceptor.preHandle()** → Extract JWT claims + handler info (AFTER Spring Security)
5. **Controller executes** → Business logic runs with complete context available
6. **RequestContextInterceptor.postHandle()** → Capture response headers and enrich response
7. **RequestContextInterceptor.afterCompletion()** → Record metrics, log completion, cleanup
8. **RequestContextFilter finally block** → Final cleanup and context removal
   