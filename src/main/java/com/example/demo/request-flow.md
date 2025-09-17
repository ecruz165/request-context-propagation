# Request Context Framework - Enhanced Processing Flow

## Overview

The Request Context Framework now uses a sophisticated **Service + Enricher** architecture that provides centralized context management, transformation capabilities, and structured JSON logging. This document shows the complete flow from request initiation through downstream service calls.

## Part 1: Upstream Request Processing Flow

This diagram shows the flow from client request through Spring Security to controller execution, highlighting how the RequestContext is built and enriched.

```mermaid
sequenceDiagram
    participant Client
    participant ObsFilter as ServerHttpObservationFilter
    participant Filter as RequestContextFilter
    participant Service as RequestContextService
    participant Extractor as RequestContextExtractor
    participant Security as Spring Security
    participant Interceptor as RequestContextInterceptor
    participant Convention as RequestContextObservationConvention
    participant Controller
    participant JsonProvider as RequestContextJsonProvider

    Note over Client,Controller: === UPSTREAM REQUEST PROCESSING ===

    Client->>ObsFilter: HTTP Request
    activate ObsFilter
    ObsFilter->>ObsFilter: Start Observation/Span
    deactivate ObsFilter

    ObsFilter->>Filter: Continue chain
    activate Filter
    Note right of Filter: Delegates to RequestContextService
    Filter->>Service: initializeContext(request)
    activate Service
    Note right of Service: Orchestrates context creation
    Service->>Service: Create new RequestContext
    Service->>Extractor: extractNonAuthenticatedFields()
    activate Extractor
    Note right of Extractor: Multi-source extraction:<br/>- Headers, cookies, query params<br/>- Path variables, session<br/>- Attributes, form data
    Extractor->>Extractor: Extract from 10+ source types
    Extractor-->>Service: Return extracted values
    deactivate Extractor
    Service->>Service: Store in HttpServletRequest attribute
    Service->>Service: Set in ThreadLocal
    Service->>Service: Generate requestId if needed
    Service->>Service: Update MDC with context
    Service->>JsonProvider: Enrich structured logs
    activate JsonProvider
    JsonProvider->>JsonProvider: Convert to camelCase JSON
    JsonProvider->>JsonProvider: Add context metadata
    deactivate JsonProvider
    Service-->>Filter: Return initialized context
    deactivate Service
    Note over Filter,Controller: RequestContext now has:<br/>- requestId, traceId, spanId<br/>- requestPath, requestMethod<br/>- All configured non-auth fields<br/>- Structured JSON logging
    deactivate Filter
    
    Filter->>Security: Continue filter chain
    activate Security
    Security->>Security: Authenticate user
    Security->>Security: Validate JWT/Token
    Security->>Security: Set SecurityContext
    deactivate Security

    Security->>Interceptor: Continue to MVC
    activate Interceptor
    Note right of Interceptor: Uses RequestContextService for enrichment
    Interceptor->>Service: enrichWithAuthenticatedData(request, context)
    activate Service
    Service->>Extractor: Extract JWT claims and principal
    activate Extractor
    Extractor->>Extractor: Parse authenticated sources
    Extractor-->>Service: Return auth data
    deactivate Extractor
    Service->>Service: Update context with auth fields
    Service-->>Interceptor: Context enriched
    deactivate Service
    Interceptor->>Service: enrichWithHandlerInfo(context, controller, method)
    activate Service
    Service->>Service: Add handler metadata
    Service->>Service: Update MDC with complete context
    Service-->>Interceptor: Handler info added
    deactivate Service
    Interceptor->>Service: validateRequiredFields(context)
    activate Service
    Service->>Service: Check required field validation
    Service-->>Interceptor: Validation complete
    deactivate Service
    Note over Interceptor,Controller: RequestContext now complete:<br/>- All authenticated fields<br/>- Handler info (controller.method)<br/>- Principal and security context<br/>- Validated required fields
    deactivate Interceptor

    Interceptor->>Convention: Observation enrichment
    activate Convention
    Note right of Convention: Read from enriched RequestContext
    Convention->>Convention: getLowCardinalityKeyValues()<br/>(reads RequestContext fields)
    Convention->>Convention: getHighCardinalityKeyValues()<br/>(reads RequestContext fields)
    Convention->>Convention: Add as metrics tags
    Convention->>Convention: Add as trace span tags
    Note over Convention: Uses field configurations to:<br/>- Respect cardinality levels<br/>- Mask sensitive values<br/>- Apply configured tag names
    deactivate Convention

    Convention->>Controller: Execute handler method
    activate Controller
    Note right of Controller: RequestContext fully available<br/>via ThreadLocal and HttpServletRequest
    deactivate Controller
```

## Part 2: Downstream Service Integration Flow

This diagram shows how the controller makes calls to downstream services, with context propagation, transformation, and response capture.

```mermaid
sequenceDiagram
    participant Controller
    participant WebClient
    participant PropFilter as RequestContextPropagationFilter
    participant Enricher as RequestContextEnricher
    participant CaptureFilter as RequestContextCaptureFilter
    participant LogFilter as RequestContextLoggingWebClientFilter
    participant Service as RequestContextService
    participant JsonProvider as RequestContextJsonProvider
    participant Downstream as Downstream Service
    Note over Controller,Downstream: === DOWNSTREAM SERVICE INTEGRATION ===

    Controller->>WebClient: Make downstream call
    activate WebClient
    Note right of Controller: RequestContext available via<br/>ThreadLocal for propagation

    WebClient->>PropFilter: Request processing
    activate PropFilter
    Note right of PropFilter: Uses RequestContextEnricher for transformation
    PropFilter->>Enricher: extractForDownstreamPropagation(context)
    activate Enricher
    Note right of Enricher: Centralized transformation logic
    Enricher->>Enricher: Transform values (BASE64, URL, JSON, expressions)
    Enricher->>Enricher: Evaluate conditions for propagation
    Enricher->>Enricher: Create PropagationData structures
    Enricher-->>PropFilter: Return structured propagation data
    deactivate Enricher
    PropFilter->>PropFilter: Apply propagation data to request<br/>(headers, query, cookies, attributes)
    deactivate PropFilter

    WebClient->>LogFilter: Logging
    activate LogFilter
    LogFilter->>LogFilter: Read RequestContext from ThreadLocal
    LogFilter->>LogFilter: Enrich MDC from context
    LogFilter->>JsonProvider: Structure log output
    activate JsonProvider
    JsonProvider->>JsonProvider: Add context fields to JSON
    JsonProvider->>JsonProvider: Convert to camelCase
    deactivate JsonProvider
    LogFilter->>LogFilter: Log outbound request with enhanced MDC
    deactivate LogFilter

    WebClient->>Downstream: HTTP Request with propagated context
    activate Downstream
    Note right of Downstream: Receives headers/query/cookies<br/>with transformed context values
    Downstream-->>WebClient: HTTP Response with context headers
    deactivate Downstream

    WebClient->>CaptureFilter: Response processing
    activate CaptureFilter
    Note right of CaptureFilter: Uses RequestContextService for consistency
    CaptureFilter->>Service: enrichWithDownstreamResponse(response, context)
    activate Service
    Service->>Service: Extract configured response headers
    Service->>Service: Apply transformations and validation
    Service->>Service: Update context with downstream data
    Service->>Service: Update MDC with new fields
    Service-->>CaptureFilter: Context enriched with response data
    deactivate Service
    CaptureFilter->>JsonProvider: Log response capture
    activate JsonProvider
    JsonProvider->>JsonProvider: Structure response log with context
    deactivate JsonProvider
    deactivate CaptureFilter

    WebClient-->>Controller: Return enriched response
    deactivate WebClient
    Note right of Controller: Response includes any context<br/>captured from downstream service
```

## Key Points About Enhanced Architecture

### Service-Oriented Context Management

1. **RequestContextFilter delegates to RequestContextService:**
    - Service orchestrates context creation and initialization
    - RequestContextExtractor handles multi-source extraction (10+ source types)
    - Automatic MDC updates and structured JSON logging integration
    - ThreadLocal and HttpServletRequest attribute storage

2. **RequestContextInterceptor uses RequestContextService for enrichment:**
    - Service coordinates authenticated data extraction
    - Handler information capture and validation
    - Complete MDC updates with all context fields
    - Required field validation with detailed error reporting

3. **RequestContextEnricher provides centralized transformation:**
    - Value transformation (BASE64, URL encoding, JSON, expressions)
    - Expression evaluation with placeholder replacement
    - Condition evaluation for dynamic field propagation
    - Structured PropagationData for WebClient integration

4. **RequestContextObservationConvention reads the enriched context:**
    - Reads fields from fully-populated RequestContext
    - Applies field configurations (cardinality, sensitivity)
    - Adds as metrics tags and trace span tags
    - Respects masking for sensitive data in observability

## Summary: Two-Phase Processing Flow

### **Phase 1: Upstream Request Processing**
```
HTTP Request → ServerHttpObservationFilter → RequestContextFilter
    ↓
RequestContextService → RequestContextExtractor (multi-source extraction)
    ↓
Spring Security (authentication) → RequestContextInterceptor
    ↓
RequestContextService (auth enrichment) → RequestContextObservationConvention
    ↓
Controller (fully enriched context available)
```

### **Phase 2: Downstream Service Integration**
```
Controller → WebClient → RequestContextPropagationFilter
    ↓
RequestContextEnricher (transformation & propagation)
    ↓
Downstream Service Call → RequestContextCaptureFilter
    ↓
RequestContextService (response enrichment) → RequestContextJsonProvider
    ↓
Structured Logging & Response Return
```

## Benefits of Split Diagram Approach

### 🎯 **Improved Readability**
- **Focused scope**: Each diagram covers a specific phase of processing
- **Reduced complexity**: Easier to follow the flow without overwhelming detail
- **Clear separation**: Upstream vs downstream concerns are distinct

### 📊 **Better Understanding**
- **Phase 1** shows how the RequestContext is built and enriched before controller execution
- **Phase 2** shows how the enriched context is used for downstream service integration
- **Service orchestration** is clearly visible in both phases

## How Components Use the Enhanced RequestContext

### RequestContextService Orchestration
```java
// Service orchestrates context initialization
public RequestContext initializeContext(HttpServletRequest request) {
    RequestContext context = new RequestContext();

    // Delegate to extractor for multi-source extraction
    int fieldsExtracted = extractNonAuthenticatedFields(request, context);

    // Store in request attribute and ThreadLocal
    request.setAttribute(RequestContext.REQUEST_CONTEXT_ATTRIBUTE, context);
    RequestContext.setCurrentContext(context);

    // Update MDC for structured logging
    updateMDC(context);

    return context;
}
```

### RequestContextEnricher Transformation
```java
// Enricher provides centralized transformation logic
public Map<String, PropagationData> extractForDownstreamPropagation(RequestContext context) {
    Map<String, PropagationData> propagationData = new LinkedHashMap<>();

    properties.getFields().forEach((fieldName, fieldConfig) -> {
        if (shouldPropagateDownstream(fieldConfig)) {
            // Transform value based on configuration
            String value = transformValue(context.get(fieldName), outbound.getValueAs());

            // Evaluate condition for propagation
            if (evaluateCondition(outbound.getCondition(), context)) {
                propagationData.put(fieldName, new PropagationData(
                    outbound.getEnrichAs(),
                    outbound.getKey(),
                    value,
                    isSensitive(fieldConfig)
                ));
            }
        }
    });

    return propagationData;
}
```

### RequestContextObservationConvention Reading
```java
// Convention reads from the fully-enriched RequestContext
public KeyValues getLowCardinalityKeyValues(ServerRequestObservationContext context) {
    // Get the RequestContext enriched by Service + Extractor + Enricher
    RequestContext requestContext = getRequestContext(context);

    // Read fields that were extracted and transformed
    String userId = requestContext.get("userId");        // Added by Service via Interceptor
    String tenantId = requestContext.get("tenantId");    // Added by Service via Filter
    String handler = requestContext.get("handler");       // Added by Service via Interceptor

    // Add as metrics tags based on field configuration
    return KeyValues.of(
        "user.id", userId,
        "tenant.id", tenantId,
        "handler", handler
    );
}
```

## Complete Processing Flow Summary

### **Phase 1: Upstream Processing (Client → Controller)**

| Step | Component | Action | RequestContext State | Key Features |
|------|-----------|--------|---------------------|--------------|
| 1 | **Filter → Service → Extractor** | Create & enrich | Has: requestId, headers, cookies, query params, path variables, session, attributes | **Multi-source extraction (10+ types)** |
| 2 | **Interceptor → Service** | Enrich with auth | Adds: JWT claims, handler, principal, validation | **Service orchestration, validation** |
| 3 | **ObservationConvention** | **READ** | Uses complete context for metrics/traces | **Enhanced field configuration** |
| 4 | **Controller** | Use | Full context available via ThreadLocal/attribute | **Comprehensive context access** |

### **Phase 2: Downstream Processing (Controller → Services)**

| Step | Component | Action | RequestContext State | Key Features |
|------|-----------|--------|---------------------|--------------|
| 5 | **WebClient → Enricher** | Transform & propagate | Uses enricher for value transformation | **Centralized transformation logic** |
| 6 | **WebClient → Service** | Capture response | Enriches context from downstream responses | **Downstream response integration** |
| 7 | **JsonProvider** | Structure logs | Converts context to structured JSON | **Enhanced JSON logging** |

## Key Architectural Improvements

### 🎯 **Service-Oriented Design**
- **RequestContextService** orchestrates all context operations
- Clear separation of concerns between extraction, enrichment, and transformation
- Centralized validation, MDC management, and error handling

### 🔄 **RequestContextEnricher Integration**
- Centralized transformation logic (BASE64, URL encoding, JSON, expressions)
- Condition evaluation for dynamic field propagation
- Reusable across WebClient filters and other components

### 📊 **Enhanced JSON Logging**
- **RequestContextJsonProvider** for structured log output
- camelCase field conversion for JSON consistency
- Integration with LoggingEventCompositeJsonEncoder

### 🚀 **Multi-Source Extraction**
- Support for 10+ source types (headers, JWT, cookies, query, path, session, attributes, form, body)
- Configurable extraction strategies per field
- Error handling and fallback mechanisms

The key insight remains: `RequestContextObservationConvention` is a **consumer** of the RequestContext, but now it reads from a much more sophisticated context that has been enriched through a service-oriented architecture with centralized transformation capabilities!