# Request Context Framework - Unified Source Handler Architecture

## Overview

The Request Context Framework uses a **Unified Source Handler** architecture that provides centralized context management with four-directional propagation (upstream/downstream, request/response). This document shows the complete flow focusing on header source type processing.

## Part 1: Upstream Request Processing Flow

This diagram shows the flow from client request through the unified source handler architecture, focusing on header extraction and enrichment.

```mermaid
sequenceDiagram
    participant Client
    participant Filter as EarlyExtractionFilter
    participant Service as RequestContextService
    participant SourceHandlers as SourceHandlers Registry
    participant HeaderHandler as HeaderSourceHandler
    participant Security as Spring Security
    participant Interceptor as RequestContextInterceptor
    participant Controller

    Note over Client,Controller: === UPSTREAM REQUEST PROCESSING (Headers Focus) ===

    Client->>Filter: HTTP Request with Headers<br/>(X-User-ID, X-Tenant-ID, etc.)
    activate Filter

    Filter->>Service: initializeContext(request)
    activate Service
    Note right of Service: Orchestrates unified source handler extraction

    Service->>SourceHandlers: extractFromUpstreamRequest(request, config)
    activate SourceHandlers
    Note right of SourceHandlers: Registry delegates to appropriate handlers

    SourceHandlers->>HeaderHandler: extractFromUpstreamRequest(request, config)
    activate HeaderHandler
    Note right of HeaderHandler: Extract header values:<br/>- X-User-ID → userId<br/>- X-Tenant-ID → tenantId<br/>- X-Request-ID → requestId
    HeaderHandler->>HeaderHandler: Extract configured header fields
    HeaderHandler->>HeaderHandler: Apply default values if missing
    HeaderHandler->>HeaderHandler: Apply transformations
    HeaderHandler-->>SourceHandlers: Return extracted header values
    deactivate HeaderHandler

    SourceHandlers-->>Service: Return all extracted values
    deactivate SourceHandlers

    Service->>Service: Store in RequestContext
    Service->>Service: Set in ThreadLocal
    Service->>Service: Update MDC with context
    Service-->>Filter: Return initialized context
    deactivate Service
    deactivate Filter

    Filter->>Security: Continue filter chain
    activate Security
    Security->>Security: Authenticate user
    deactivate Security

    Security->>Interceptor: Continue to MVC
    activate Interceptor
    Note right of Interceptor: Late-phase extraction for authenticated sources

    Interceptor->>Service: enrichWithAuthenticatedData(request)
    activate Service
    Service->>SourceHandlers: extractFromUpstreamRequest(request, authConfig)
    activate SourceHandlers
    Note right of SourceHandlers: Extract JWT claims, session data
    SourceHandlers-->>Service: Return authenticated values
    deactivate SourceHandlers
    Service-->>Interceptor: Context enriched
    deactivate Service
    deactivate Interceptor

    Interceptor->>Controller: Execute handler method
    activate Controller
    Note right of Controller: RequestContext fully available<br/>with all header fields extracted
    deactivate Controller
```

## Part 2: Downstream Service Integration Flow

This diagram shows how the controller makes calls to downstream services using the unified source handler architecture for header propagation.

```mermaid
sequenceDiagram
    participant Controller
    participant WebClient
    participant PropFilter as RequestContextWebClientPropagationFilter
    participant Enricher as RequestContextEnricher
    participant SourceHandlers as SourceHandlers Registry
    participant HeaderHandler as HeaderSourceHandler
    participant Downstream as Downstream Service

    Note over Controller,Downstream: === DOWNSTREAM HEADER PROPAGATION ===

    Controller->>WebClient: Make downstream call
    activate WebClient
    Note right of Controller: RequestContext available via ThreadLocal

    WebClient->>PropFilter: Process outbound request
    activate PropFilter

    PropFilter->>Enricher: extractForDownstreamPropagation(context)
    activate Enricher
    Note right of Enricher: Get propagation data for headers
    Enricher->>Enricher: Read downstream.outbound config
    Enricher->>Enricher: Transform header values if needed
    Enricher-->>PropFilter: Return header propagation data
    deactivate Enricher

    PropFilter->>SourceHandlers: enrichDownstreamRequest(request, data)
    activate SourceHandlers
    Note right of SourceHandlers: Apply header propagation

    SourceHandlers->>HeaderHandler: enrichDownstreamRequest(request, headerData)
    activate HeaderHandler
    Note right of HeaderHandler: Add headers to downstream request:<br/>- X-User-ID: user-123<br/>- X-Service-Name: api-service<br/>- X-User-Segment: premium
    HeaderHandler->>HeaderHandler: Add configured headers to request
    HeaderHandler-->>SourceHandlers: Headers applied
    deactivate HeaderHandler

    SourceHandlers-->>PropFilter: Request enriched
    deactivate SourceHandlers
    deactivate PropFilter

    WebClient->>Downstream: HTTP Request with propagated headers
    activate Downstream
    Note right of Downstream: Receives headers:<br/>X-User-ID, X-Service-Name, etc.
    Downstream-->>WebClient: HTTP Response with headers
    deactivate Downstream

    WebClient->>SourceHandlers: extractFromDownstreamResponse(response)
    activate SourceHandlers
    Note right of SourceHandlers: Extract response headers if configured

    SourceHandlers->>HeaderHandler: extractFromDownstreamResponse(response, config)
    activate HeaderHandler
    Note right of HeaderHandler: Extract downstream response headers:<br/>- X-Response-Time<br/>- X-Service-Version
    HeaderHandler->>HeaderHandler: Extract configured response headers
    HeaderHandler-->>SourceHandlers: Return extracted values
    deactivate HeaderHandler

    SourceHandlers-->>WebClient: Response context extracted
    deactivate SourceHandlers

    WebClient-->>Controller: Return response with context
    deactivate WebClient
    Note right of Controller: Response includes extracted<br/>downstream header values
```

## Key Points About Unified Source Handler Architecture

### Unified Source Handler Pattern

1. **SourceHandlers Registry:**
    - Central registry for all source handlers (HEADER, COOKIE, QUERY, CLAIM, SESSION)
    - Auto-wiring and strategy pattern for handler selection
    - Consistent interface for all four operations

2. **HeaderSourceHandler Operations:**
    - `extractFromUpstreamRequest()`: Extract headers from incoming requests
    - `enrichUpstreamResponse()`: Add headers to outgoing responses
    - `enrichDownstreamRequest()`: Add headers to downstream service calls
    - `extractFromDownstreamResponse()`: Extract headers from downstream responses

3. **Four-Directional Propagation:**
    - **Upstream Request**: Extract X-User-ID, X-Tenant-ID from client
    - **Upstream Response**: Add response headers back to client
    - **Downstream Request**: Propagate X-Service-Name, X-User-Segment to services
    - **Downstream Response**: Extract X-Response-Time, X-Service-Version from services

4. **Configuration-Driven:**
    - Field configurations define which headers to extract/propagate
    - Default values and transformations applied automatically
    - Sensitive field masking and validation

## Summary: Unified Processing Flow

### **Phase 1: Upstream Header Processing**
```
HTTP Request (with headers) → EarlyExtractionFilter
    ↓
RequestContextService → SourceHandlers Registry → HeaderSourceHandler
    ↓
Extract headers (X-User-ID, X-Tenant-ID) → Store in RequestContext
    ↓
Spring Security → RequestContextInterceptor → Controller
```

### **Phase 2: Downstream Header Propagation**
```
Controller → WebClient → RequestContextWebClientPropagationFilter
    ↓
RequestContextEnricher → SourceHandlers Registry → HeaderSourceHandler
    ↓
Add headers (X-Service-Name, X-User-Segment) → Downstream Service
    ↓
Extract response headers (X-Response-Time) → Update RequestContext
```

## Benefits of Header-Focused Approach

### 🎯 **Simplified Understanding**
- **Header focus**: Shows the most common use case clearly
- **Reduced complexity**: Easier to follow without multiple source types
- **Clear patterns**: Four-directional header flow is easy to understand

### 📊 **Practical Examples**
- **Phase 1** shows header extraction from client requests (X-User-ID, X-Tenant-ID)
- **Phase 2** shows header propagation to downstream services (X-Service-Name, X-User-Segment)
- **Unified pattern** applies to all source types (COOKIE, QUERY, CLAIM, SESSION)

## How Components Use the Unified Source Handler

### HeaderSourceHandler Implementation
```java
@Component
public class HeaderSourceHandler implements SourceHandler {

    // Extract headers from incoming requests
    public <T> String extractFromUpstreamRequest(T request, InboundConfig config) {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String headerName = config.getKey();
        String value = httpRequest.getHeader(headerName);

        // Apply default value if header missing
        return value != null ? value : config.getDefaultValue();
    }

    // Add headers to downstream requests
    public void enrichDownstreamRequest(ClientRequest.Builder requestBuilder,
                                       String key, String value, InboundConfig config) {
        requestBuilder.header(key, value);
        log.debug("Added header to downstream request: {}={}", key, value);
    }

    // Extract headers from downstream responses
    public String extractFromDownstreamResponse(ClientResponse response,
                                               OutboundConfig config) {
        String headerName = config.getKey();
        return response.headers().header(headerName).stream()
                .findFirst()
                .orElse(null);
    }
}
```

### SourceHandlers Registry Usage
```java
@Service
public class RequestContextService {

    private final SourceHandlers sourceHandlers;

    // Use registry to extract from all configured sources
    public void extractFromUpstreamRequest(HttpServletRequest request, RequestContext context) {
        properties.getFields().forEach((fieldName, fieldConfig) -> {
            if (fieldConfig.getUpstream() != null && fieldConfig.getUpstream().getInbound() != null) {
                // Delegate to appropriate handler via registry
                String value = sourceHandlers.extractFromUpstreamRequest(
                    request,
                    fieldConfig.getUpstream().getInbound()
                );

                if (value != null) {
                    context.put(fieldName, value);
                }
            }
        });
    }
}
```

## Complete Header Processing Flow Summary

### **Phase 1: Upstream Header Processing (Client → Controller)**

| Step | Component | Action | Headers Processed | Key Features |
|------|-----------|--------|------------------|--------------|
| 1 | **EarlyExtractionFilter → SourceHandlers → HeaderSourceHandler** | Extract headers | X-User-ID, X-Tenant-ID, X-Request-ID | **Early extraction before Spring Security** |
| 2 | **RequestContextInterceptor → SourceHandlers** | Enrich with auth headers | Authorization-derived headers | **Post-authentication enrichment** |
| 3 | **Controller** | Use context | All extracted header values available | **Complete header context access** |

### **Phase 2: Downstream Header Processing (Controller → Services)**

| Step | Component | Action | Headers Processed | Key Features |
|------|-----------|--------|------------------|--------------|
| 4 | **WebClient → RequestContextWebClientPropagationFilter → HeaderSourceHandler** | Propagate headers | X-Service-Name, X-User-Segment | **Downstream header propagation** |
| 5 | **HeaderSourceHandler** | Extract response headers | X-Response-Time, X-Service-Version | **Downstream response capture** |
| 6 | **RequestContext** | Update with response data | Response headers added to context | **Bidirectional header flow** |

## Key Architectural Improvements

### 🎯 **Unified Source Handler Design**
- **Single interface** for all source types (HEADER, COOKIE, QUERY, CLAIM, SESSION)
- **Four operations** per handler: upstream request/response, downstream request/response
- **Strategy pattern** with SourceHandlers registry for consistent access

### 🔄 **Four-Directional Header Flow**
- **Upstream Request**: Extract client headers (X-User-ID → userId)
- **Upstream Response**: Add response headers (userId → X-User-ID)
- **Downstream Request**: Propagate service headers (serviceHealth → X-Service-Name)
- **Downstream Response**: Capture response headers (X-Response-Time → downstreamResponseTime)

### 📊 **Configuration-Driven Processing**
- **Field configurations** define header mappings and transformations
- **Default values** applied when headers are missing
- **Sensitive field masking** for security compliance

### 🚀 **Consistent Pattern Across Source Types**
- **HeaderSourceHandler** for HTTP headers
- **CookieSourceHandler** for cookies (upstream-only)
- **QuerySourceHandler** for query parameters
- **ClaimSourceHandler** for JWT claims
- **SessionSourceHandler** for server-side session data

The unified source handler architecture provides a **consistent, extensible pattern** for handling all types of request context sources, with headers being the most commonly used example!