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
**ALL functionality, edge cases, and integrations MUST be validated through REST API calls.** No feature is considered complete unless it can be tested and verified via HTTP endpoints using REST Assured and if needed WireMock for downstream service mocking.

### High-Level API Testing Approach
Create comprehensive **REST API tests** with full coverage for each feature set:
**Request Context Extraction API Tests:**
 - Upstream Inbound Header extraction via HTTP requests
 - Upstream Inbound Query extraction via HTTP requests
 - Upstream Inbound Path extraction via HTTP requests
 - Upstream Inbound Token extraction via HTTP requests
 - Upstream Inbound RequestBody extraction via HTTP requests
 - Downstream Inbound Header extraction via HTTP requests
 - Downstream Inbound Query extraction via HTTP requests
 - Downstream Inbound RequestBody extraction via HTTP requests

**Request Context Enrichment API Tests:**
 - Downstream Outbound Header enrichment via HTTP requests
 - Downstream Outbound Path enrichment via HTTP requests
 - Downstream Outbound Query enrichment via HTTP requests

**Validation Approach: Extraction & Enrichment**
- extracted values should be seen in logs
- extracted values should be seen in response headers
- extracted values should be seen in downstream requests
- extracted values should be seen in actuator endpoints
- Downstream Outbound enrichment verifications should use WireMock to verify requests enrichments

**Validation Approach: Masking**
- Masking patterns should be verified via API calls and log analysis
- Masking should be verified via API calls and log analysis

### API Testing Coverage Goals
- **API Endpoint Coverage**: 100% of all REST endpoints tested
- **Request Variation Coverage**: All header, query, param, body nad token variations tested
- **Line Coverage**: ≥ 95% achieved through API tests (targeting 100% for critical paths)
- **Branch Coverage**: ≥ 85% achieved through API test scenarios
- **Service Integration Coverage**: 100% of service orchestration paths via API calls

## Common Identifiers
sessionId: 123456
traceparent: 00-6d22ce173f0892ab6b373b2fb1c0982d-df70492ac2d57290-01
partyId: 654321
visionId: 123456

## TestSuite Package Structure
src/test/java/com/example/contextpropagation/
├── config/
│   ├── TestContextConfig.java
│   ├── TestExecutorConfig.java
│   └── ObservabilityConfig.java
├── framework/
│   ├── ContextPropagationTestBase.java
│   ├── ContextAssertions.java
│   └── ContextInterceptor.java
├── endpoints/
│   ├── async/
│   │   ├── AsyncControllerTest.java
│   │   ├── CompletableFutureControllerTest.java
│   │   └── DeferredResultControllerTest.java
│   ├── reactive/
│   │   ├── MonoControllerTest.java
│   │   ├── FluxControllerTest.java
│   │   └── ParallelFluxControllerTest.java
│   ├── streaming/
│   │   ├── SseControllerTest.java
│   │   └── StreamingResponseTest.java
│   ├── parallel/
│   │   ├── ParallelStreamControllerTest.java
│   │   └── ForkJoinControllerTest.java
│   └── hybrid/
│       ├── MixedAsyncControllerTest.java
│       └── ReactiveWithBlockingTest.java
└── integration/
├── CrossThreadPropagationTest.java
├── NestedAsyncPropagationTest.java
└── ErrorHandlingContextTest.java


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
JWT=
```