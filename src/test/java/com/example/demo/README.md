# Request Context Propagation Framework - API-First Testing Strategy & PRD
==========================

## 📋 Executive Summary

The Request Context Propagation Framework is an enhanced, service-oriented solution for managing request context across distributed microservices. Built on Spring Boot 3.5.5 with Java 21, it features a sophisticated architecture with RequestContextService orchestration, RequestContextEnricher transformation capabilities, and comprehensive structured JSON logging.

This document defines a **comprehensive API-first testing strategy** where **ALL scenarios are validated through REST API tests** using REST Assured, WireMock, and JaCoCo coverage analysis. Every feature, edge case, and integration point must be testable and verifiable through HTTP API calls.

### Key Business Value
- **Service-oriented architecture** - Clean separation of concerns with centralized orchestration
- **Enhanced transformation capabilities** - Built-in value transformation, expression evaluation, and condition processing
- **Structured JSON logging** - Production-ready logging with camelCase conversion and context integration
- **Multi-source extraction** - Support for 10+ source types including JWT, headers, cookies, path variables, session, attributes, form data, and request body
- **Centralized enrichment** - RequestContextEnricher provides reusable transformation logic across the framework
- **Enhanced observability** - Automatic span enrichment with cardinality control and sensitive data masking
- **Production-ready** - Comprehensive error handling, performance optimization, and enterprise-grade features

## 🎯 API-First Testing Strategy Overview

### **Core Principle: Everything Must Be API Testable**
**ALL functionality, edge cases, and integrations MUST be validated through REST API calls.** No feature is considered complete unless it can be tested and verified via HTTP endpoints using REST Assured.

### High-Level API Testing Approach
Create comprehensive **REST API tests** with full coverage for each feature set:
- **Request Context Extraction API Tests** - Multi-source extraction via HTTP requests
- **Service Orchestration API Tests** - RequestContextService coordination via endpoints
- **Transformation & Enrichment API Tests** - RequestContextEnricher via API calls
- **Structured JSON Logging API Tests** - Log validation via API responses
- **Downstream Propagation API Tests** - WebClient integration via WireMock verification
- **Downstream Response Extraction API Tests** - Response capture via API validation
- **Upstream Response Enrichment API Tests** - Response header validation via API
- **Security & Edge Cases API Tests** - Comprehensive edge case validation via HTTP
- **Error Handling API Tests** - Exception and fallback testing via API responses
- **Observability API Tests** - Metrics and tracing validation via actuator endpoints

### API Testing Coverage Goals
- **API Endpoint Coverage**: 100% of all REST endpoints tested
- **HTTP Method Coverage**: All GET, POST, PUT, DELETE methods tested
- **Request Variation Coverage**: All header, query, body, cookie combinations tested
- **Response Validation Coverage**: All response formats, status codes, headers validated
- **Line Coverage**: ≥ 95% achieved through API tests (targeting 100% for critical paths)
- **Branch Coverage**: ≥ 85% achieved through API test scenarios
- **Service Integration Coverage**: 100% of service orchestration paths via API calls
- **Error Handling Coverage**: 100% of error scenarios via API error responses
- **Edge Cases Coverage**: All boundary conditions tested via API requests

## 📋 API Testing Requirements - Complete Feature List

### **Mandatory API Test Endpoints**
Every feature MUST have corresponding REST endpoints that can be tested via HTTP calls. No functionality should exist without an API test path.

### 1. Context Extraction API Tests (Multi-Source)
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **JWT Token Extraction** | `GET /api/v1/profile` | Bearer token in Authorization header | Validate `response.context.partyId`, `response.context.tenantId` |
| **HTTP Headers** | `GET /api/v1/profile` | X-Party-ID, X-Tenant-ID headers | Verify header extraction in `response.context` |
| **Cookies** | `GET /api/v1/profile` | Multiple cookies in Cookie header | Validate cookie extraction in response |
| **Query Parameters** | `GET /api/v1/profile?param=value` | URL parameters | Verify query extraction in response |
| **Path Variables** | `GET /api/v1/users/{userId}/profile` | REST path parameters | Validate path variable extraction |
| **Session Attributes** | `POST /api/v1/login` → `GET /api/v1/profile` | Session-based requests | Verify session context persistence |
| **Form Data** | `POST /api/v1/form` | Form-encoded payload | Validate form field extraction |
| **Request Body** | `POST /api/v1/data` | JSON/XML request body | Verify body field extraction |
| **Request Attributes** | `GET /api/v1/profile` | Custom servlet attributes | Validate attribute extraction |

### 2. Service Orchestration API Tests (RequestContextService)
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **Context Initialization** | `GET /api/v1/health/context` | Service health check | Validate service status in response |
| **Field Validation** | `POST /api/v1/validate` | Missing required fields | Verify `400` error with validation details |
| **MDC Management** | `GET /api/v1/profile` | Any request with context | Check structured logs for MDC fields |
| **Handler Information** | `GET /api/v1/profile` | Controller method capture | Validate `response.metadata.handler` |
| **Response Enrichment** | `GET /api/v1/profile` | Response header addition | Check response headers for context data |
| **Context Lifecycle** | `GET /api/v1/debug/context` | Context inspection | Validate context state and lifecycle |

### 3. Transformation & Enrichment API Tests (RequestContextEnricher)
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **Value Transformations** | `GET /api/v1/downstream-test` | Trigger downstream call | WireMock verification of transformed headers |
| **Expression Evaluation** | `POST /api/v1/expression-test` | Expression in request | Validate expression result in response |
| **Condition Processing** | `GET /api/v1/conditional-test` | Conditional fields | Verify conditional logic in downstream calls |
| **Propagation Data** | `GET /api/v1/propagation-test` | Structured data creation | Validate PropagationData in response |

### 4. Downstream Integration API Tests (WebClient)
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **Header Propagation** | `GET /api/v1/call-downstream` | Trigger WebClient call | WireMock request verification |
| **Query Parameter Propagation** | `GET /api/v1/call-downstream` | URL parameters | WireMock query parameter verification |
| **Cookie Propagation** | `GET /api/v1/call-downstream` | Cookie headers | WireMock cookie verification |
| **Request Attribute Propagation** | `GET /api/v1/call-downstream` | Custom attributes | WireMock attribute verification |
| **Response Capture** | `GET /api/v1/call-downstream` | Response processing | Validate captured response data |

### 5. Structured JSON Logging API Tests (RequestContextJsonProvider)
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **JSON Structure** | `GET /api/v1/profile` | Any request | Analyze log output for JSON structure |
| **Context Integration** | `GET /api/v1/profile` | Context fields | Verify MDC fields in logs |
| **Profile Support** | `GET /api/v1/profile` | Different profiles | Validate log format per profile |
| **Performance** | `GET /api/v1/performance-test` | High-volume requests | Monitor logging performance metrics |

### 6. Observability & Monitoring API Tests
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **Metrics Integration** | `GET /actuator/metrics/context.*` | Actuator metrics | Validate context-related metrics |
| **Trace Enrichment** | `GET /api/v1/profile` | Tracing headers | Verify span tags and baggage |
| **Debug Endpoints** | `GET /debug/context` | Context inspection | Validate debug response structure |
| **Health Checks** | `GET /actuator/health` | Health endpoint | Verify component health status |

### 7. Security & Edge Cases API Tests
| Feature | Required API Endpoint | API Test Method | Verification |
|---------|---------------------|----------------|-------------|
| **Sensitive Data Masking** | `GET /api/v1/profile` | Sensitive headers | Verify masking in logs and response |
| **Input Validation** | `POST /api/v1/malicious-test` | XSS/injection payloads | Validate sanitization and error handling |
| **Concurrent Access** | `GET /api/v1/concurrent-test` | Parallel requests | Verify context isolation |
| **Memory Management** | `POST /api/v1/large-payload` | Large request bodies | Monitor memory usage and limits |
| **Error Handling** | `GET /api/v1/error-test` | Force exceptions | Validate error responses and fallbacks |

## 📦 Comprehensive Test Setup with JaCoCo & REST Assured

### Maven Configuration with Enhanced Testing (`pom.xml`)
```xml
<!-- JaCoCo Plugin for Coverage Analysis -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.95</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>

<!-- Dependencies for Enhanced Testing -->
<dependencies>
    <!-- REST Assured for API Testing -->
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- WireMock for Downstream Service Mocking -->
    <dependency>
        <groupId>com.github.tomakehurst</groupId>
        <artifactId>wiremock-jre8</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers for Integration Testing -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### Test Execution Commands
```bash
# Run all tests with coverage
mvn clean test

# Generate coverage report
mvn jacoco:report

# Check coverage thresholds
mvn jacoco:check

# Run specific test categories
mvn test -Dtest="*ServiceTest" jacoco:report
mvn test -Dtest="*EnricherTest" jacoco:report
mvn test -Dtest="*EdgeCaseTest" jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

## 🎯 Enhanced Core Features & Testing Strategy

### 1. Service-Oriented Context Extraction

#### 1.1 Enhanced JWT Token Extraction with Service Orchestration
**Feature**: Extract context from JWT claims via RequestContextService and RequestContextExtractor

```bash
# Generate a sample JWT with claims (for testing)
# Payload: {"sub":"user-123","tenant_id":"acme-corp","application_id":"mobile-app","department":"engineering"}
JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInRlbmFudF9pZCI6ImFjbWUtY29ycCIsImFwcGxpY2F0aW9uX2lkIjoibW9iaWxlLWFwcCIsImRlcGFydG1lbnQiOiJlbmdpbmVlcmluZyIsImlhdCI6MTUxNjIzOTAyMn0.abc123"

# Test enhanced JWT extraction with service orchestration
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer ${JWT}" \
  -H "Accept: application/json" | jq

# Expected Response with enhanced context:
{
  "context": {
    "partyId": "user-123",
    "tenantId": "acme-corp",
    "applicationId": "mobile-app",
    "department": "engineering",
    "requestId": "req-generated-uuid",
    "handler": "ProfileController.getProfile"
  },
  "metadata": {
    "extractedFields": 6,
    "extractionTime": "2025-09-16T17:35:52.341Z",
    "serviceVersion": "enhanced-v2"
  }
}

# Test JWT extraction with claim path navigation
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer ${JWT_WITH_NESTED_CLAIMS}" \
  -H "Accept: application/json" | jq
```

#### 1.2 Enhanced Multi-Source Extraction (10+ Source Types)
**Feature**: Extract context from multiple sources via RequestContextExtractor

```bash
# Test comprehensive header extraction with service orchestration
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-456" \
  -H "X-Tenant-ID: tenant-789" \
  -H "X-Application-ID: web-app" \
  -H "X-Correlation-ID: corr-abc-123" \
  -H "X-Request-ID: req-custom-123" \
  -H "Accept: application/json" | jq

# Test case-insensitive headers with enhanced processing
curl -X GET http://localhost:8080/api/v1/profile \
  -H "x-party-id: party-456" \
  -H "X-TENANT-ID: tenant-789" \
  -H "x-custom-field: custom-value" | jq
```

#### 1.3 Enhanced Cookie and Session Extraction
**Feature**: Extract context from cookies and session attributes

```bash
# Test cookie extraction with session support
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Cookie: sessionId=sess-123456; userId=user-789; preferences=theme:dark" \
  -H "Accept: application/json" | jq

# Test session attribute extraction (when session is available)
curl -X POST http://localhost:8080/api/v1/login \
  -H "Content-Type: application/json" \
  -d '{"username": "user123", "sessionData": {"department": "engineering"}}' | jq
```

#### 1.4 Enhanced Query Parameter and Form Data Extraction
**Feature**: Extract context from URL query parameters and form data

```bash
# Test query parameter extraction with multiple values
curl -X GET "http://localhost:8080/api/v1/profile?tenant=tenant-123&session=sess-456&department=engineering&role=admin" \
  -H "Accept: application/json" | jq

# Test form data extraction
curl -X POST http://localhost:8080/api/v1/form-submit \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "userId=user-123&tenantId=tenant-456&department=sales" | jq
```

#### 1.5 Enhanced Path Variable and Request Body Extraction
**Feature**: Extract context from URL path and request body

```bash
# Test path variable extraction with pattern matching
# Route: /api/v1/tenants/{tenantId}/users/{userId}/departments/{deptId}
curl -X GET http://localhost:8080/api/v1/tenants/acme-corp/users/user-123/departments/engineering \
  -H "Accept: application/json" | jq

# Test request body extraction (JSON path support)
curl -X POST http://localhost:8080/api/v1/process \
  -H "Content-Type: application/json" \
  -d '{
    "user": {"id": "user-123", "tenant": "acme-corp"},
    "metadata": {"department": "engineering", "role": "admin"},
    "context": {"source": "mobile-app"}
  }' | jq
```

### 2. Enhanced Context Transformation via RequestContextEnricher

#### 2.1 Advanced Value Transformations
**Feature**: Apply sophisticated transformations via RequestContextEnricher

```bash
# Test lowercase transformation with service orchestration
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Tenant-ID: ACME-CORP" \
  -H "Accept: application/json" | jq

# Expected: tenantId should be "acme-corp" via RequestContextEnricher

# Test BASE64 encoding transformation
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Sensitive-Data: confidential-info" \
  -H "Accept: application/json" | jq

# Test URL encoding transformation
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Query-Data: param with spaces & symbols" \
  -H "Accept: application/json" | jq

# Test JSON transformation
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Complex-Data: {\"nested\": {\"value\": \"test\"}}" \
  -H "Accept: application/json" | jq

# Test expression evaluation with placeholder replacement
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-User-ID: user-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "Accept: application/json" | jq
# Expected: Expression "#userId-#tenantId" becomes "user-123-acme-corp"
```

#### 2.2 Condition-Based Transformation
**Feature**: Apply transformations based on conditions

```bash
# Test conditional transformation (only apply if tenant is "premium")
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Tenant-ID: premium-tenant" \
  -H "X-Feature-Flag: advanced-features" \
  -H "Accept: application/json" | jq

# Test condition evaluation with multiple criteria
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-User-Role: admin" \
  -H "X-Department: engineering" \
  -H "Accept: application/json" | jq
```

### 3. Enhanced Context Validation via RequestContextService

#### 3.1 Service-Orchestrated Required Fields Validation
**Feature**: Validate required context fields through RequestContextService

```bash
# Test missing required field with enhanced error reporting
curl -X POST http://localhost:8080/api/v1/transaction \
  -H "X-Party-ID: party-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}' | jq

# Expected Enhanced Response (400 Bad Request):
{
  "error": "Missing required context fields",
  "missing": ["applicationId", "tenantId"],
  "provided": ["partyId"],
  "validationDetails": {
    "requiredFields": ["partyId", "applicationId", "tenantId"],
    "extractedFields": 1,
    "validationTime": "2025-09-16T17:35:52.341Z"
  },
  "suggestions": [
    "Add X-Application-ID header",
    "Add X-Tenant-ID header"
  ]
}

# Test with all required fields and service validation
curl -X POST http://localhost:8080/api/v1/transaction \
  -H "X-Party-ID: party-123" \
  -H "X-Application-ID: mobile-app" \
  -H "X-Tenant-ID: acme-corp" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}' | jq

# Expected Enhanced Response (200 OK):
{
  "transactionId": "txn-xxx",
  "status": "SUCCESS",
  "context": {
    "validatedFields": 3,
    "handler": "TransactionController.createTransaction",
    "processingTime": "15ms"
  }
}

# Test field validation with custom validation rules
curl -X POST http://localhost:8080/api/v1/secure-transaction \
  -H "X-Party-ID: party-123" \
  -H "X-Application-ID: mobile-app" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-Security-Level: high" \
  -H "Content-Type: application/json" \
  -d '{"amount": 10000}' | jq
```

### 4. Enhanced Context Propagation via RequestContextEnricher

#### 4.1 Advanced Downstream Propagation with Transformation
**Feature**: Context propagated via RequestContextEnricher with sophisticated transformation

```bash
# Test enhanced downstream propagation with transformation
curl -X GET http://localhost:8080/api/v1/aggregate \
  -H "X-Party-ID: party-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-Correlation-ID: corr-test-456" \
  -H "X-Custom-Field: needs-transformation" \
  -H "Accept: application/json" | jq

# Verify downstream service receives transformed headers
# Expected: Downstream receives headers with applied transformations
# - X-Party-ID: party-123 (unchanged)
# - X-Tenant-ID: acme-corp (lowercase applied)
# - X-Correlation-ID: corr-test-456 (unchanged)
# - X-Custom-Field: NEEDS-TRANSFORMATION (uppercase applied)

# Test propagation with multiple enrichment types
curl -X GET http://localhost:8080/api/v1/multi-downstream \
  -H "X-User-ID: user-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "Accept: application/json" | jq

# Expected: Context propagated as headers, query parameters, and cookies based on configuration
```

#### 4.2 Enhanced Correlation ID and Request ID Management
**Feature**: Auto-generate correlation ID and request ID with service orchestration

```bash
# Request without correlation ID - service generates one
curl -X GET http://localhost:8080/api/v1/aggregate \
  -H "X-Party-ID: party-123" \
  -v | jq

# Expected Enhanced Response Headers:
# X-Correlation-ID: <generated-uuid>
# X-Request-ID: <generated-uuid>
# X-Handler: AggregateController.getAggregate
# X-Processing-Time: 25ms

# Test with custom request ID
curl -X GET http://localhost:8080/api/v1/aggregate \
  -H "X-Party-ID: party-123" \
  -H "X-Request-ID: custom-req-123" \
  -v | jq

# Expected: Custom request ID preserved, correlation ID generated if missing
```

### 5. Enhanced Structured JSON Logging

#### 5.1 RequestContextJsonProvider Integration
**Feature**: Structured JSON logging with context integration and camelCase conversion

```bash
# Test with JSON logging profile
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-Request-ID: req-json-test" \
  -H "Accept: application/json" | jq

# Check application logs for structured JSON output:
# Expected JSON Log Structure:
{
  "@timestamp": "2025-09-16T17:35:52.341Z",
  "level": "INFO",
  "logger": "com.example.demo.controller.ProfileController",
  "message": "Processing profile request",
  "thread": "http-nio-8080-exec-1",
  "service": "request-context-propagation",
  "version": "0.0.1-SNAPSHOT",
  "environment": "local",
  "context": {
    "partyId": "party-123",
    "tenantId": "acme-corp",
    "requestId": "req-json-test",
    "handler": "ProfileController.getProfile",
    "traceId": "6d22ce173f0892ab6b373b2fb1c0982d",
    "spanId": "df70492ac2d57290"
  }
}

# Test with different logging profiles
# Development profile (pattern logging)
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-dev" \
  --header "X-Profile: dev"

# Production profile (async JSON logging)
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-prod" \
  --header "X-Profile: prod"
```

### 6. Response Header Enrichment

#### 6.1 Enhanced Response Header Enrichment via Service Layer
**Feature**: Return enriched context values in response headers through RequestContextService

```bash
# Test enhanced response header enrichment
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-123" \
  -H "X-Correlation-ID: corr-789" \
  -H "X-Tenant-ID: acme-corp" \
  -v 2>&1 | grep "X-"

# Expected Enhanced Response Headers:
< X-Correlation-ID: corr-789
< X-Request-ID: <generated-uuid>
< X-Party-ID: party-123
< X-Tenant-ID: acme-corp
< X-Handler: ProfileController.getProfile
< X-Processing-Time: 15ms
< X-Context-Fields: 4
< X-Service-Version: enhanced-v2

# Test response enrichment with downstream context capture
curl -X GET http://localhost:8080/api/v1/aggregate-with-downstream \
  -H "X-Party-ID: party-456" \
  -H "X-Correlation-ID: corr-downstream-test" \
  -v 2>&1 | grep "X-"

# Expected: Response includes context captured from downstream services
< X-Downstream-Service-A: service-a-response-data
< X-Downstream-Service-B: service-b-response-data
```

### 7. Enhanced Observability Integration

#### 7.1 Enhanced Debug Context Endpoint with Service Insights
**Feature**: Inspect current context, configuration, and service architecture

```bash
# Get enhanced context with service architecture details
curl -X GET http://localhost:8080/debug/context \
  -H "X-Party-ID: party-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-API-Key: secret-key-123" | jq

# Expected Enhanced Response:
{
  "allContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp",
    "requestId": "req-debug-uuid",
    "handler": "DebugController.getContext"
    // Note: API key not shown (sensitive)
  },
  "serviceArchitecture": {
    "requestContextService": "active",
    "requestContextExtractor": "10-source-types",
    "requestContextEnricher": "transformation-enabled",
    "jsonProvider": "structured-logging-active"
  },
  "extractionDetails": {
    "extractedFields": 4,
    "extractionTime": "2025-09-16T17:35:52.341Z",
    "sourceTypes": ["HEADER", "JWT", "QUERY", "PATH"],
    "transformationsApplied": 2
  },
  "configuredKeys": ["partyId", "tenantId", "applicationId", "apiKey", "requestId", "handler"],
  "downstreamContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp",
    "transformedFields": ["tenantId:lowercase"]
  },
  "observabilityContext": {
    "party-id": "party-123",
    "tenant-id": "acme-corp",
    "cardinality-level": "low"
  },
  "loggingContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp",
    "jsonProvider": "camelCase-conversion"
  },
  "enricherCapabilities": {
    "transformations": ["BASE64", "URL", "JSON", "EXPRESSION"],
    "conditions": ["field-based", "expression-based"],
    "propagationTypes": ["HEADER", "QUERY", "COOKIE", "ATTRIBUTE"]
  }
}

# Get context with sensitive data and service diagnostics
curl -X GET "http://localhost:8080/debug/context?includeSensitive=true&includeServiceDiagnostics=true" \
  -H "X-Party-ID: party-123" \
  -H "X-API-Key: secret-key-123" | jq
```

#### 6.2 Metrics Endpoint
**Feature**: View context propagation metrics

```bash
# Check metrics
curl -X GET http://localhost:8080/actuator/metrics/http.server.requests \
  -H "Accept: application/json" | jq

# Check specific context tags in metrics
curl -X GET "http://localhost:8080/actuator/metrics/http.server.requests?tag=tenant.id:acme-corp" | jq
```

#### 6.3 Trace Endpoint
**Feature**: View traces with enriched context

```bash
# View recent traces
curl -X GET http://localhost:8080/actuator/traces \
  -H "Accept: application/json" | jq

# Traces should include context tags
```

### 7. Security Features

#### 7.1 Early Extraction (Before Spring Security)
**Feature**: Capture context even for failed authentication

```bash
# Test with invalid JWT (malformed)
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer invalid-jwt" \
  -H "X-Correlation-ID: failed-auth-123" \
  -v

# Expected: 401 Unauthorized, but correlation ID should be in logs

# Test with missing authentication
curl -X GET http://localhost:8080/api/v1/secure-endpoint \
  -H "X-Party-ID: party-123" \
  -H "X-Correlation-ID: no-auth-456" \
  -v

# Expected: 401/403, but context should be captured in security audit logs
```

#### 7.2 Sensitive Data Handling
**Feature**: Mask sensitive values in logs

```bash
# Test sensitive data handling
curl -X POST http://localhost:8080/api/v1/authenticate \
  -H "X-API-Key: super-secret-key" \
  -H "X-Client-Secret: confidential-123" \
  -H "Content-Type: application/json" \
  -d '{"username": "user"}' | jq

# Check logs - sensitive values should be [REDACTED]
```

### 8. Dynamic Context Management

#### 8.1 Set Custom Context
**Feature**: Dynamically set context values

```bash
# Set custom context values
curl -X POST http://localhost:8080/context/set \
  -H "Content-Type: application/json" \
  -d '{
    "customField1": "value1",
    "customField2": "value2",
    "department": "engineering"
  }' | jq

# Verify custom context is stored
curl -X GET http://localhost:8080/debug/context | jq
```

#### 8.2 Clear Context
**Feature**: Clear all context values

```bash
# Clear context
curl -X DELETE http://localhost:8080/context \
  -H "X-Party-ID: party-123"

# Verify context is cleared
curl -X GET http://localhost:8080/debug/context | jq
```

### 9. Multi-Tenant Scenarios

#### 9.1 Tenant Isolation
**Feature**: Ensure tenant context is preserved

```bash
# Test tenant A
curl -X GET http://localhost:8080/api/v1/tenant-data \
  -H "X-Tenant-ID: tenant-a" \
  -H "X-User-ID: user-123" | jq

# Test tenant B with same user ID
curl -X GET http://localhost:8080/api/v1/tenant-data \
  -H "X-Tenant-ID: tenant-b" \
  -H "X-User-ID: user-123" | jq

# Each should return tenant-specific data
```

### 10. Error Scenarios

#### 10.1 Downstream Service Failure
**Feature**: Context preserved in error scenarios

```bash
# Trigger a call to failing downstream service
curl -X GET http://localhost:8080/api/v1/failing-endpoint \
  -H "X-Correlation-ID: error-test-123" \
  -H "X-Party-ID: party-456" \
  -v | jq

# Check logs for correlation ID in error messages
# Expected: Error logs should contain correlation ID for debugging
```

#### 10.2 Timeout Scenarios
**Feature**: Context preserved across retries

```bash
# Trigger a slow endpoint that times out
curl -X GET http://localhost:8080/api/v1/slow-endpoint \
  -H "X-Correlation-ID: timeout-test-789" \
  -H "X-Party-ID: party-123" \
  --max-time 5 \
  -v

# Check if context is preserved in retry attempts
```

## 📊 Performance Testing

### Load Test with Context
```bash
# Simple load test with ab (Apache Bench)
ab -n 1000 -c 10 \
  -H "X-Party-ID: party-load-test" \
  -H "X-Tenant-ID: tenant-load" \
  -H "X-Correlation-ID: load-test-123" \
  http://localhost:8080/api/v1/profile

# Using curl in a loop
for i in {1..100}; do
  curl -X GET http://localhost:8080/api/v1/profile \
    -H "X-Party-ID: party-$i" \
    -H "X-Correlation-ID: test-$i" \
    -H "Accept: application/json" &
done
wait
```

## 🔍 Verification Commands

### Check Application Health
```bash
# Health check
curl http://localhost:8080/actuator/health | jq

# Check if context propagation is enabled
curl http://localhost:8080/actuator/info | jq '.context'
```

### View Configuration
```bash
# View current configuration
curl http://localhost:8080/actuator/env/request-context | jq

# View configured context keys
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.requestContextProperties'
```

### Monitor Real-time Logs
```bash
# Follow logs with context
tail -f application.log | grep -E "partyId|tenantId|correlationId"

# Watch specific correlation ID
tail -f application.log | grep "corr-abc-123"
```

## 🚨 Alerting Test Cases

### Missing Required Context
```bash
# Should trigger alert for missing required context
curl -X POST http://localhost:8080/api/v1/critical-operation \
  -H "Content-Type: application/json" \
  -d '{"data": "test"}'
```

### Invalid Tenant Access
```bash
# Should trigger security alert
curl -X GET http://localhost:8080/api/v1/tenants/unauthorized-tenant/data \
  -H "X-Tenant-ID: different-tenant" \
  -H "X-User-ID: user-123"
```

## 📈 Enhanced Success Metrics

| Metric | Target | Measurement Command | Enhanced Features |
|--------|--------|-------------------|------------------|
| Context Extraction Success Rate | >99.9% | `curl /actuator/metrics/context.extraction.success` | Multi-source extraction (10+ types) |
| Service Orchestration Performance | <2ms | `curl /actuator/metrics/context.service.duration` | RequestContextService coordination |
| Transformation Success Rate | >99.5% | `curl /actuator/metrics/context.transformation.success` | RequestContextEnricher processing |
| Propagation Latency | <1ms | `curl /actuator/metrics/context.propagation.duration` | Enhanced with condition evaluation |
| Missing Context Errors | <0.1% | `curl /actuator/metrics/context.missing.count` | Improved validation reporting |
| Downstream Propagation Rate | 100% | Check downstream service logs | Multiple enrichment types |
| JSON Logging Performance | <0.5ms | `curl /actuator/metrics/logging.json.duration` | RequestContextJsonProvider |
| Field Validation Success | >99.8% | `curl /actuator/metrics/context.validation.success` | Service-layer validation |
| Expression Evaluation Performance | <0.2ms | `curl /actuator/metrics/context.expression.duration` | Enricher expression processing |

## 🎓 Training Examples

### Basic Usage
```bash
# Minimal required context
curl -X GET http://localhost:8080/api/v1/hello \
  -H "X-Party-ID: training-user-1"

# Full context
curl -X GET http://localhost:8080/api/v1/hello \
  -H "X-Party-ID: training-user-1" \
  -H "X-Tenant-ID: training-tenant" \
  -H "X-Application-ID: training-app" \
  -H "X-Correlation-ID: training-correlation-1"
```

### Advanced Usage
```bash
# Chained service calls with context preservation
curl -X POST http://localhost:8080/api/v1/workflow/start \
  -H "X-Party-ID: workflow-user" \
  -H "X-Tenant-ID: workflow-tenant" \
  -H "X-Correlation-ID: workflow-123" \
  -H "Content-Type: application/json" \
  -d '{
    "workflow": "order-processing",
    "steps": ["validate", "process", "notify"]
  }' | jq
```

## 🐛 Troubleshooting

### Context Not Appearing in Logs
```bash
# Check if MDC is configured
curl http://localhost:8080/actuator/loggers/com.example.context | jq

# Enable debug logging
curl -X POST http://localhost:8080/actuator/loggers/com.example.context \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

### Context Not Propagating to Downstream
```bash
# Enable WebClient debug logging
curl -X POST http://localhost:8080/actuator/loggers/org.springframework.web.reactive.function.client \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Test with trace enabled
curl -X GET http://localhost:8080/api/v1/downstream-test \
  -H "X-Party-ID: debug-party" \
  -H "X-Debug: true" \
  -v
```

## 📋 Enhanced Acceptance Criteria

### Core Functionality
- [ ] **Multi-source extraction** works from all 10+ configured source types (headers, JWT, cookies, query, path, session, attributes, form, body)
- [ ] **Service orchestration** via RequestContextService coordinates all context operations efficiently
- [ ] **Enhanced transformation** via RequestContextEnricher supports BASE64, URL, JSON, and expression evaluation
- [ ] **Condition-based processing** evaluates field conditions and expressions correctly
- [ ] **Required field validation** prevents processing without mandatory context with detailed error reporting

### Propagation & Integration
- [ ] **Enhanced context propagation** to downstream services with multiple enrichment types (headers, query, cookies, attributes)
- [ ] **Correlation and Request ID generation** when missing with service-level coordination
- [ ] **Downstream response capture** enriches context from service responses via RequestContextService
- [ ] **WebClient integration** leverages RequestContextEnricher for consistent transformation

### Observability & Logging
- [ ] **Structured JSON logging** via RequestContextJsonProvider with camelCase conversion
- [ ] **Multiple logging profiles** (dev, json, prod) work correctly with appropriate configurations
- [ ] **Context appears in DataDog/APM traces** with enhanced field configuration and cardinality control
- [ ] **Sensitive data masking** works in logs and observability output
- [ ] **Enhanced debug endpoints** provide service architecture insights and diagnostics

### Performance & Reliability
- [ ] **Performance impact** is less than 2ms per request (including service orchestration)
- [ ] **Context preservation** across async boundaries and service calls
- [ ] **Failed requests** still have context in error logs with structured JSON output
- [ ] **Service layer resilience** handles errors gracefully with fallback mechanisms
- [ ] **Memory efficiency** with proper ThreadLocal cleanup and context lifecycle management

### Advanced Features
- [ ] **Expression evaluation** with placeholder replacement (#fieldName syntax) works correctly
- [ ] **Multi-tenant isolation** preserves tenant context across service boundaries
- [ ] **Handler information capture** includes controller and method details in context
- [ ] **Response status enrichment** adds HTTP status to context for observability
- [ ] **Configuration-driven behavior** allows runtime changes without code deployment

## 📦 Test Setup with JaCoCo Coverage Analysis

### Maven Configuration with JaCoCo (`pom.xml`)
```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.8</version>
    <executions>
        <execution>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.95</minimum>
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.85</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

### Test Dependencies
```xml
<dependencies>
    <!-- REST Assured for API Testing -->
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- WireMock for Downstream Service Mocking -->
    <dependency>
        <groupId>com.github.tomakehurst</groupId>
        <artifactId>wiremock-jre8</artifactId>
        <scope>test</scope>
    </dependency>
</dependencies>
```

## 🧪 API-First Testing Strategy

### **Core Testing Principle: API-Only Validation**
**ALL testing MUST be performed through REST API calls.** No direct unit testing of internal classes - everything must be validated through HTTP endpoints using REST Assured.

### API Test Organization Structure
- **Each API test class targets specific endpoints** with comprehensive HTTP scenarios
- **REST Assured helper classes** for consistent API call patterns and WireMock setup
- **Separate API test classes** for each feature set (extraction, transformation, propagation, etc.)
- **Parameterized API tests** for comprehensive edge case coverage via HTTP requests
- **WireMock integration** for all downstream service interactions

### API Test Verification Methods
- **HTTP Response validation** - All context extraction and enrichment verified via API responses
- **Response header analysis** - Context propagation verified through response headers
- **WireMock request verification** - Downstream call validation through mock service verification
- **Structured log analysis** - Context propagation verified through log output from API calls
- **Actuator endpoint validation** - Performance and health monitoring via management endpoints
- **Error response validation** - All error scenarios tested via HTTP error responses

### API Testing Approach (No Direct Unit Tests)
- **RequestContextService API Tests**: Verify service orchestration via `/api/v1/health/context` and `/debug/context` endpoints
- **RequestContextExtractor API Tests**: Test multi-source extraction via `/api/v1/profile` with different input combinations
- **RequestContextEnricher API Tests**: Validate transformation logic via `/api/v1/downstream-test` and WireMock verification
- **RequestContextJsonProvider API Tests**: Ensure structured JSON logging via log analysis from API calls
- **End-to-End API Integration Tests**: Verify complete flow from API request through propagation to downstream services

### Performance Testing
- **Service Layer Performance**: Measure RequestContextService coordination overhead
- **Transformation Performance**: Benchmark RequestContextEnricher processing time
- **JSON Logging Performance**: Validate RequestContextJsonProvider impact
- **Memory Usage**: Monitor ThreadLocal cleanup and context lifecycle management

### Security Testing
- **Sensitive Data Masking**: Verify masking works across all components (service, enricher, logger)
- **JWT Parsing Security**: Test malformed JWT handling without signature validation
- **Context Isolation**: Ensure tenant context isolation across concurrent requests

## 🎯 Critical Edge Cases to Test

### Edge Case Coverage Matrix
| Edge Case Category | Test Coverage Target | Priority | Verification Method |
|-------------------|---------------------|----------|-------------------|
| **Null/Empty Values** | 100% | HIGH | Response validation + error handling |
| **Boundary Values** | 95% | HIGH | Header size limits + parameter validation |
| **Special Characters** | 100% | CRITICAL | XSS/injection prevention + encoding |
| **Malformed JWT** | 100% | CRITICAL | Error responses + security validation |
| **Concurrent Requests** | 90% | HIGH | Context isolation + thread safety |
| **Timeouts/Retries** | 85% | MEDIUM | Context preservation + retry logic |
| **Circuit Breaker** | 80% | MEDIUM | Fallback behavior + context retention |
| **Memory Stress** | 75% | LOW | Performance + memory management |
| **Encoding Issues** | 90% | MEDIUM | Character encoding + internationalization |
| **Missing Fields** | 100% | HIGH | Validation errors + required field logic |

### Sample API Edge Case Test Implementation
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class RequestContextApiEdgeCaseTest {

    @LocalServerPort
    private int port;

    private static WireMockServer wireMockServer;
    private static final int WIREMOCK_PORT = 8089;

    @BeforeAll
    static void setup() {
        wireMockServer = new WireMockServer(WIREMOCK_PORT);
        wireMockServer.start();
        configureFor("localhost", WIREMOCK_PORT);
    }

    @BeforeEach
    void setupRestAssured() {
        RestAssured.port = port;
        RestAssured.basePath = "/api/v1";
    }

    @Test
    @DisplayName("API Test: Null context values handled gracefully via REST endpoint")
    void testNullContextValuesViaApi() {
        given()
            .header("X-Party-ID", (String) null)
            .header("X-Tenant-ID", "")
            .header("X-Correlation-ID", "   ")
        .when()
            .get("/profile")  // API endpoint test
        .then()
            .statusCode(200)
            .body("context.partyId", nullValue())
            .body("context.correlationId", notNullValue()) // Should generate one
            .body("metadata.extractionMethod", equalTo("API"));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 255, 1024, 8192})
    @DisplayName("Edge Case: Header size boundaries")
    void testHeaderSizeBoundaries(int size) {
        String largeValue = "X".repeat(size);

        Response response = given()
            .header("X-Party-ID", largeValue)
        .when()
            .get("/profile")
        .then()
            .extract().response();

        if (size <= 8192) {
            assertEquals(200, response.statusCode());
        } else {
            assertThat(response.statusCode(), anyOf(is(400), is(431)));
        }
    }

    @Test
    @DisplayName("Edge Case: Concurrent requests with context isolation")
    void testConcurrentContextIsolation() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(20);
        ConcurrentHashMap<String, String> results = new ConcurrentHashMap<>();
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String partyId = "party-" + i;
            final String correlationId = UUID.randomUUID().toString();

            executor.submit(() -> {
                try {
                    Response response = given()
                        .header("X-Party-ID", partyId)
                        .header("X-Correlation-ID", correlationId)
                    .when()
                        .get("/concurrent-test")
                    .then()
                        .statusCode(200)
                        .extract().response();

                    String returnedPartyId = response.jsonPath().getString("context.partyId");
                    results.put(correlationId, returnedPartyId);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(30, TimeUnit.SECONDS));
        assertEquals(threadCount, results.size());
        executor.shutdown();
    }
}
```

## 📊 JaCoCo Coverage Analysis

### Running Coverage Analysis
```bash
# Run all tests with coverage
mvn clean test

# Generate coverage report
mvn jacoco:report

# Check coverage thresholds
mvn jacoco:check

# View coverage report
open target/site/jacoco/index.html
```

### Coverage Analysis Script
```bash
#!/bin/bash
echo "🔍 Running Coverage Analysis..."

mvn clean test jacoco:report

COVERAGE_FILE="target/site/jacoco/jacoco.csv"
if [ -f "$COVERAGE_FILE" ]; then
    echo "📊 Coverage Summary:"
    LINE_COVERAGE=$(awk -F',' '{sum+=$5+$6; total+=$5+$6+$7} END {printf "%.2f%%", (sum/total)*100}' $COVERAGE_FILE)
    BRANCH_COVERAGE=$(awk -F',' '{sum+=$9+$10; total+=$9+$10+$11} END {printf "%.2f%%", (sum/total)*100}' $COVERAGE_FILE)

    echo "Line Coverage: $LINE_COVERAGE"
    echo "Branch Coverage: $BRANCH_COVERAGE"

    echo "🎯 Critical Class Coverage:"
    grep -E "(RequestContextService|RequestContextEnricher|RequestContextExtractor)" $COVERAGE_FILE | \
        awk -F',' '{printf "%-40s Line: %.1f%% Branch: %.1f%%\n", $2, ($5+$6)/($5+$6+$7)*100, ($9+$10)/($9+$10+$11)*100}'
fi
```

## 🚀 Enhanced Deployment Verification

```bash
# Enhanced production smoke test with service architecture validation
curl -X GET https://api.production.com/health/context \
  -H "X-Party-ID: smoke-test" \
  -H "X-Correlation-ID: deploy-verification-$(date +%s)" \
  -H "X-Test-Service-Architecture: true" \
  -v

# Verify service layer components
curl -X GET https://api.production.com/debug/service-health \
  -H "X-Deployment-Test: enhanced-architecture" | jq

# Expected Response:
{
  "requestContextService": "healthy",
  "requestContextExtractor": "healthy",
  "requestContextEnricher": "healthy",
  "requestContextJsonProvider": "healthy",
  "extractionSources": 10,
  "transformationTypes": 4,
  "loggingProfiles": 4
}

# Verify in DataDog with enhanced context
# Search for: @correlation_id:"deploy-verification-*" AND @service_architecture:"enhanced"

# Test structured JSON logging in production
curl -X GET https://api.production.com/api/v1/health \
  -H "X-JSON-Logging-Test: production-verification" \
  -H "X-Correlation-ID: json-test-$(date +%s)" \
  -v

# Verify enhanced observability
curl -X GET https://api.production.com/actuator/metrics/context.service.duration | jq
curl -X GET https://api.production.com/actuator/metrics/context.transformation.success | jq
curl -X GET https://api.production.com/actuator/metrics/logging.json.duration | jq
```

## 🎯 Testing Roadmap

### Phase 1: Core Service Testing
1. **RequestContextService** unit and integration tests
2. **RequestContextExtractor** multi-source extraction tests
3. **Service orchestration** performance benchmarks

### Phase 2: Enhancement Testing
1. **RequestContextEnricher** transformation and condition tests
2. **Expression evaluation** with placeholder replacement
3. **Propagation enhancement** with multiple enrichment types

### Phase 3: Observability Testing
1. **RequestContextJsonProvider** structured logging tests
2. **Multiple logging profiles** configuration validation
3. **Enhanced debug endpoints** functionality verification

### Phase 4: Production Readiness
1. **Load testing** with enhanced architecture
2. **Security validation** across all components
3. **Deployment verification** with service health checks

## 📋 API Test Execution Commands

### Core API Testing Commands
```bash
# Run all API tests with coverage
mvn clean test

# Run specific API test categories
mvn test -Dtest="*ApiTest"               # All API tests
mvn test -Dtest="*ExtractionApiTest"     # Context extraction API tests
mvn test -Dtest="*EnrichmentApiTest"     # Enrichment API tests
mvn test -Dtest="*PropagationApiTest"    # Propagation API tests
mvn test -Dtest="*EdgeCaseApiTest"       # Edge case API tests
mvn test -Dtest="*IntegrationApiTest"    # End-to-end API tests

# Generate and view coverage from API tests
mvn jacoco:report
open target/site/jacoco/index.html

# Check coverage thresholds achieved via API testing
mvn jacoco:check

# Run API tests with specific profiles
mvn test -Dspring.profiles.active=test
mvn test -Dspring.profiles.active=json
```

### Performance Testing Commands
```bash
# Load testing with ab (Apache Bench)
ab -n 1000 -c 10 \
  -H "X-Party-ID: load-test" \
  -H "X-Tenant-ID: tenant-load" \
  http://localhost:8080/api/v1/profile

# Memory stress testing
mvn test -Dtest="*MemoryStressTest" -Xmx512m

# Concurrent testing
mvn test -Dtest="*ConcurrentTest" -Dtest.threads=50
```

### Verification Commands
```bash
# Health check
curl http://localhost:8080/actuator/health | jq

# Context debug
curl http://localhost:8080/debug/context \
  -H "X-Party-ID: debug-test" | jq

# Metrics validation
curl http://localhost:8080/actuator/metrics/context.extraction.success | jq
curl http://localhost:8080/actuator/metrics/context.service.duration | jq

# Log analysis
tail -f application.log | grep -E "partyId|tenantId|correlationId"
```

## ✅ API Testing Success Criteria Checklist

### API Coverage Requirements
- [ ] Overall line coverage ≥ 95% **achieved through API tests only**
- [ ] Branch coverage ≥ 85% **achieved through API test scenarios**
- [ ] Service layer coverage ≥ 98% **via API endpoint testing**
- [ ] Critical classes coverage ≥ 95% **through REST API calls**
- [ ] Error handling coverage = 100% **via API error response testing**

### API Functional Testing
- [ ] All 10+ extraction sources tested **via dedicated API endpoints**
- [ ] Service orchestration validated **through `/debug/context` and health endpoints**
- [ ] Transformation logic verified **via downstream API calls and WireMock**
- [ ] Downstream propagation confirmed **through WireMock verification of API-triggered calls**
- [ ] JSON logging validated **via log analysis from API requests**
- [ ] Edge cases covered **through comprehensive API test scenarios**

### API Performance & Security Testing
- [ ] Concurrent access tested **via parallel API requests**
- [ ] Memory limits validated **through large payload API tests**
- [ ] Security edge cases covered **via malicious input API tests**
- [ ] Performance benchmarks met **through API load testing**
- [ ] Load testing completed **using REST API endpoints**

### API Test Infrastructure
- [ ] All features accessible via REST endpoints
- [ ] WireMock integration for downstream service testing
- [ ] REST Assured test framework fully implemented
- [ ] Actuator endpoints for observability testing
- [ ] Debug endpoints for context inspection

**Core Principle Validation**: Every line of code coverage must be achievable through REST API calls. No functionality should exist that cannot be tested via HTTP endpoints.

This comprehensive **API-first testing strategy** ensures complete validation of the Request Context Propagation Framework through REST API testing with measurable coverage metrics and thorough edge case handling!