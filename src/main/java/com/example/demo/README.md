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

## 🚀 Quick Start
Architecture: Filter + HandlerInterceptor
1. RequestContextFilter (Runs BEFORE Spring Security)

Extracts non-authenticated sources (headers, cookies, query params)
Generates requestId
Captures request metadata (path, method, timestamp)
Sets up basic MDC for early logging

2. RequestContextInterceptor (Runs AFTER Spring Security, BEFORE Controller)

Extracts JWT claims (now authenticated)
Captures handler information (controller, method, annotations)
Updates MDC with complete context
Enriches response headers
Records metrics

Key Benefits:

Complete Context in Controller: All data (headers, JWT claims, handler info) is available when your controller executes
Handler Information: The interceptor has access to:

Controller class name
Method name
Custom annotations (@ApiOperation)
Request mapping patterns


Rich Logging: Your logs will include:

INFO  [requestId=abc-123, handler=UserController.getUser, principal=john.doe, applicationId=app-1]
Getting user details...

Metrics with Context: You can record metrics tagged with handler names, making it easy to track performance per endpoint
Two-Phase Extraction:

Early extraction for non-auth data (useful for rate limiting, early logging)
Late extraction for auth data and handler info (complete context for business logic)



Execution Flow:
1. Request arrives
2. RequestContextFilter → Extract headers, cookies, generate requestId
3. Spring Security → Authentication happens
4. RequestContextInterceptor.preHandle() → Extract JWT claims + handler info
5. Controller executes → Has complete context
6. RequestContextInterceptor.postHandle() → Capture response headers
7. RequestContextInterceptor.afterCompletion() → Record metrics, log completion
8. RequestContextFilter finally block → Cleanup
   