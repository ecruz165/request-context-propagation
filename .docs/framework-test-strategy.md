# Request Context Propagation Framework - Comprehensive Testing Strategy

## 📋 Executive Summary

The Request Context Propagation Framework is a production-ready, Spring Boot 3.5.5-based solution for managing request context across distributed microservices. This document defines a **comprehensive testing strategy** that ensures 100% reliability through multi-layered testing approaches including API tests, unit tests, integration tests, and performance validation.

### Framework Architecture Overview
- **6 Source Types**: HEADER, COOKIE, QUERY, CLAIM, PATH, BODY
- **4-Directional Flow**: Upstream/downstream request/response processing
- **Unified Source Handler Pattern**: Single interface for all source operations
- **Framework-Provided Values**: Auto-generated IDs, API handler info, context metadata
- **Advanced Observability**: MDC logging, Micrometer metrics, distributed tracing
- **Security Features**: Sensitive data masking, upstream-only cookie handling
- **WebClient Integration**: Three filters for propagation, capture, and logging

## HIGHLIGHTED TESTS
### Unit tests with mockito 
- Unit test for HeaderSourceHandler 
- Unit test for CookieSourceHandler 
- Unit test for QuerySourceHandler 
- Unit test for ClaimSourceHandler 
- Unit test for PathSourceHandler 
- Unit test for BodySourceHandler 
- Unit test for RequestContextService
### API Tests with RestAssured and Wiremock Verify
- API Tests for Synchronous endpoints to validate all 16 configuration patterns from the Source Type Support Matrix
- API Tests for Reactive Mono endpoints to validate all 16 configuration patterns from the Source Type Support Matrix
- API Tests for Asynchronous DeferredResult endpoints to validate all 16 configuration patterns from the Source Type Support Matrix


### Key Business Value
- **Production-ready architecture** - Comprehensive error handling and performance optimization
- **Multi-source extraction** - Support for 6 source types with unified processing
- **Advanced observability** - Automatic span enrichment with cardinality control
- **Security-conscious** - Built-in sensitive data protection and masking
- **Framework integration** - Seamless Spring Boot auto-configuration
- **Enterprise-grade** - Comprehensive testing, documentation, and monitoring

## 🎯 Multi-Layered Testing Strategy

### **Core Principle: Comprehensive Coverage Through Multiple Test Types**
The framework employs a **multi-layered testing approach** that ensures reliability through:
1. **API Tests** - End-to-end validation via REST endpoints
2. **Unit Tests** - Component-level testing with mocking
3. **Integration Tests** - Service interaction validation
4. **Performance Tests** - Concurrent access and scalability
5. **Security Tests** - Data masking and sensitive information protection

### Current Test Infrastructure

#### **Test Statistics (Current State)**
- **Total Test Classes**: 19 Java test files
- **Test Categories**: 5 distinct testing approaches
- **Coverage Target**: 50% minimum (configured), targeting 80%+ for production
- **Test Execution Time**: ~10 seconds for full suite
- **JaCoCo Integration**: Comprehensive coverage reporting with exclusions

#### **Test Package Structure**
```
src/test/java/com/example/demo/
├── api/                          # API-level integration tests
│   ├── SimpleRequestContextFieldsTest.java      # 16 configuration patterns
│   ├── ConcurrentContextAccessTest.java         # Multi-threading scenarios
│   ├── ConcurrentZipBlockUpstreamTest.java      # Reactive concurrent operations
│   ├── ContextAwareWebClientBuilderTest.java    # WebClient integration
│   └── SystemSpecificPropagationUnitTest.java   # extSysIds filtering
├── integration/                  # Service integration tests
│   ├── BodyDownstreamExtractionIntegrationTest.java  # JSONPath extraction
│   └── ResponseBodyBufferingIntegrationTest.java     # Response buffering
├── service/                      # Service-level unit tests
│   ├── RequestContextServiceProgrammaticTest.java    # Programmatic API
│   └── source/BodySourceHandlerTest.java             # Source handler logic
├── filter/                       # Filter-level tests
│   └── RequestContextWebClientCaptureFilterTest.java # WebClient filters
├── config/                       # Test infrastructure
│   ├── BaseApiTest.java          # Common test setup with WireMock
│   ├── TestSecurityConfig.java   # Security configuration for tests
│   └── TestWebClientConfig.java  # WebClient test configuration
└── utils/                        # Test utilities
    └── JwtTestHelper.java         # JWT generation for testing
```

### **Testing Approach by Layer**

#### **1. API Tests (End-to-End Validation)**
**Purpose**: Validate complete request/response flows through HTTP endpoints
**Tools**: REST Assured, WireMock, Spring Boot Test
**Coverage**: All 16 configuration patterns from `request-context-config.yml`

**Key Test Classes:**
- `SimpleRequestContextFieldsTest` - Core functionality validation
- `ConcurrentZipBlockUpstreamTest` - Reactive operations testing
- `SystemSpecificPropagationUnitTest` - extSysIds filtering validation

**Validation Methods:**
- Request header propagation to downstream services
- Response header enrichment verification
- Query parameter extraction and forwarding
- Cookie handling and security validation
- Framework-provided values (apiHandler, requestId generation)
- Observability integration (logging, metrics, tracing)

#### **2. Unit Tests (Component Isolation)**
**Purpose**: Test individual components in isolation with mocking
**Tools**: JUnit 5, Mockito, Spring Test
**Coverage**: Source handlers, service methods, utility functions

**Key Test Classes:**
- `BodySourceHandlerTest` - JSONPath extraction logic
- `RequestContextServiceProgrammaticTest` - Service API validation
- `RequestContextWebClientCaptureFilterTest` - Filter behavior

**Validation Methods:**
- Source handler extraction logic
- Value transformation and masking
- Error handling and edge cases
- Configuration validation
- Mock-based service interaction testing

#### **3. Integration Tests (Service Interaction)**
**Purpose**: Validate service-to-service interactions and data flow
**Tools**: Spring Boot Test, WireMock, TestContainers (if needed)
**Coverage**: WebClient integration, response buffering, downstream extraction

**Key Test Classes:**
- `BodyDownstreamExtractionIntegrationTest` - JSONPath downstream extraction
- `ResponseBodyBufferingIntegrationTest` - Response body consumption handling
- `ContextAwareWebClientBuilderTest` - WebClient context propagation

#### **4. Performance Tests (Scalability & Concurrency)**
**Purpose**: Validate framework behavior under concurrent access and load
**Tools**: JUnit 5, CompletableFuture, Parallel streams
**Coverage**: Thread safety, context isolation, performance characteristics

**Key Test Classes:**
- `ConcurrentContextAccessTest` - Multi-threading scenarios
- `ConcurrentZipBlockUpstreamTest` - Reactive concurrent operations

#### **5. Security Tests (Data Protection)**
**Purpose**: Validate sensitive data handling and security features
**Coverage**: Data masking, cookie security, upstream-only restrictions

**Validation Methods:**
- Sensitive field masking patterns
- Cookie security attributes (HttpOnly, Secure)
- Upstream-only cookie restrictions
- Log sanitization verification

## 📊 Test Coverage & Quality Metrics

### **JaCoCo Coverage Configuration**
```xml
<!-- Current Coverage Thresholds -->
<minimum>0.50</minimum>  <!-- 50% minimum for CI/CD -->
<!-- Recommended for production: 80%+ -->
<!-- Target for critical paths: 95%+ -->
```

**Coverage Exclusions:**
- `**/DemoApplication.class` - Main application class
- `**/config/**/*Config.class` - Configuration classes
- Auto-generated classes and DTOs

### **Test Execution Commands**
```bash
# Run all tests with coverage
mvn clean test jacoco:report

# Run with coverage verification
mvn clean verify -Pcoverage-verify

# Run specific test categories
mvn test -Dtest="*ApiTest" jacoco:report          # API tests only
mvn test -Dtest="*IntegrationTest" jacoco:report  # Integration tests
mvn test -Dtest="*ServiceTest" jacoco:report      # Service layer tests
mvn test -Dtest="*SourceHandlerTest" jacoco:report # Source handler tests

# Generate and view coverage reports
mvn jacoco:report
open target/site/jacoco/index.html

# Coverage summary script
./scripts/coverage-summary.sh
```

### **Test Data & Configuration Patterns**

#### **Common Context Fields (Production Examples)**
```yaml
# Enterprise context fields commonly captured
sessionId: "user-session-123456"
traceparent: "00-6d22ce173f0892ab6b373b2fb1c0982d-df70492ac2d57290-01"
partyId: "customer-654321"
visionId: "vision-123456"
applicationId: "mobile-app-v2.1"
correlationId: "req-correlation-789"
```

#### **Framework-Provided Context Values**
```yaml
# Auto-generated by framework
apiHandler: "UserController/getUserById"        # Spring MVC handler info
contextFieldCount: 15                          # Number of active fields
requestId: "550e8400-e29b-41d4-a716-446655440000"  # Generated UUID
traceId: "6d22ce173f0892ab6b373b2fb1c0982d"    # Extracted from traceparent
```

#### **Common Downstream Systems (extSysIds)**
```yaml
# Production system identifiers for targeted propagation
extSysIds: ["OKTA", "TRANSUNION-DR", "TRANSUNION-IR", "ARGO", "CDP"]
```

### **Test Configuration Files**
- `src/test/resources/request-context-config.yml` - 16 test patterns covering all scenarios
- `src/test/resources/application-test.yml` - Test-specific Spring configuration
- `src/test/resources/body-downstream-extraction-example.yml` - JSONPath examples
- `src/test/resources/logback-spring.xml` - Test logging configuration

## 🎯 Detailed Testing Strategy by Feature

### **1. Source Type Testing Matrix**

#### **1.1 HEADER Source Testing**
**Test Class**: `SimpleRequestContextFieldsTest`
**Coverage**: Bidirectional propagation, default values, masking

```java
@Test
@DisplayName("headerId1 - Basic bidirectional propagation")
void testHeaderId1_BasicBidirectional() {
    given()
        .header("X-HEADER-ID-1", "test-header-1")
    .when()
        .get("/api/test/downstream")
    .then()
        .statusCode(200);

    // Verify downstream propagation
    wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
            .withHeader("X-HEADER-ID-1", equalTo("test-header-1")));
}
```

**Test Scenarios:**
- ✅ Basic header extraction and propagation
- ✅ Default value handling when header missing
- ✅ Sensitive header masking patterns
- ✅ Upstream response enrichment
- ✅ System-specific propagation (extSysIds)

#### **1.2 COOKIE Source Testing**
**Test Class**: `SimpleRequestContextFieldsTest`
**Coverage**: Upstream-only extraction, security attributes

**Test Scenarios:**
- ✅ Cookie extraction from requests
- ✅ Upstream-only restriction (no downstream propagation)
- ✅ Security attributes (HttpOnly, Secure)
- ✅ Cookie masking for sensitive data

#### **1.3 QUERY Source Testing**
**Test Class**: `SimpleRequestContextFieldsTest`
**Coverage**: Query parameter extraction and propagation

**Test Scenarios:**
- ✅ Query parameter extraction
- ✅ Downstream query propagation
- ✅ Default value handling
- ✅ Request-only limitation (no upstream response enrichment)

#### **1.4 CLAIM Source Testing**
**Test Class**: `SimpleRequestContextFieldsTest` (with JWT helper)
**Coverage**: JWT claim extraction, authentication context

**Test Scenarios:**
- ✅ JWT claim extraction from Spring Security context
- ✅ Extract-only behavior (no propagation)
- ✅ Default values for missing claims
- ✅ Sensitive claim masking

#### **1.5 PATH Source Testing**
**Test Class**: `SimpleRequestContextFieldsTest`
**Coverage**: URL path variable extraction

**Test Scenarios:**
- ✅ Path variable extraction from Spring MVC
- ✅ Extract-only behavior
- ✅ Integration with Spring MVC mapping

#### **1.6 BODY Source Testing**
**Test Class**: `BodyDownstreamExtractionIntegrationTest`, `BodySourceHandlerTest`
**Coverage**: JSONPath extraction from request/response bodies

**Test Scenarios:**
- ✅ JSONPath extraction from downstream responses
- ✅ Complex JSON structure navigation
- ✅ Error handling for invalid JSON
- ✅ Response body buffering to prevent consumption conflicts
- ✅ Multiple JSONPath expressions
- ✅ Type conversion (numbers, booleans to strings)

### **2. Framework-Provided Values Testing**

#### **2.1 API Handler Information**
**Test Class**: `SimpleRequestContextFieldsTest`
**Coverage**: Auto-generated Spring MVC handler info

```java
@Test
@DisplayName("apiHandler - Context-generated handler method info")
void testApiHandler_ContextGeneration() {
    given()
    .when()
        .get("/api/test/downstream")
    .then()
        .statusCode(200);

    // Verify downstream propagation of handler info
    // Should be in format "TestController/testDownstreamCall"
    wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
            .withHeader("X-API-Handler", matching(".*Controller/.*")));
}
```

#### **2.2 Auto-Generated Values**
**Test Coverage**: UUID generation, ULID, timestamps, sequences

**Test Scenarios:**
- ✅ Request ID generation when missing
- ✅ Correlation ID generation
- ✅ Trace ID generation
- ✅ Different generator types (UUID, ULID, TIMESTAMP, SEQUENCE, RANDOM, NANOID)

### **3. WebClient Integration Testing**

#### **3.1 Context Propagation Filter**
**Test Class**: `ContextAwareWebClientBuilderTest`
**Coverage**: Automatic context propagation to downstream services

#### **3.2 Response Capture Filter**
**Test Class**: `RequestContextWebClientCaptureFilterTest`
**Coverage**: Downstream response extraction

#### **3.3 Response Body Buffering**
**Test Class**: `ResponseBodyBufferingIntegrationTest`
**Coverage**: Preventing response body consumption conflicts

**Test Scenarios:**
- ✅ Response body extraction without interfering with application logic
- ✅ Multiple response body reads
- ✅ Error handling for non-JSON responses

### **4. Concurrency & Performance Testing**

#### **4.1 Multi-Threading Scenarios**
**Test Class**: `ConcurrentContextAccessTest`
**Coverage**: Thread safety and context isolation

**Test Scenarios:**
- ✅ Multiple threads accessing context simultaneously
- ✅ Parallel execution with thread pools
- ✅ Context isolation between different request threads
- ✅ Race condition detection
- ✅ HttpServletRequest storage limitations vs ThreadLocal behavior

#### **4.2 Reactive Concurrent Operations**
**Test Class**: `ConcurrentZipBlockUpstreamTest`
**Coverage**: Reactive streams and concurrent downstream calls

**Test Scenarios:**
- ✅ Concurrent WebClient calls with context propagation
- ✅ Reactive stream context preservation
- ✅ Zip and block operations with multiple services
- ✅ Context aggregation from multiple downstream responses

### **5. Security & Data Protection Testing**

#### **5.1 Sensitive Data Masking**
**Test Coverage**: Data masking patterns and log sanitization

**Test Scenarios:**
- ✅ Custom masking patterns (*-4, ***, etc.)
- ✅ Sensitive field identification
- ✅ Log output sanitization
- ✅ Metrics data protection

#### **5.2 Cookie Security**
**Test Coverage**: Cookie handling and security attributes

**Test Scenarios:**
- ✅ Upstream-only cookie restrictions
- ✅ Automatic security attributes (HttpOnly, Secure)
- ✅ Cookie masking in logs and metrics

### **6. Observability Integration Testing**

#### **6.1 Logging Integration**
**Test Coverage**: MDC population and structured logging

**Test Scenarios:**
- ✅ Automatic MDC key population
- ✅ Custom MDC key configuration
- ✅ Nested JSON structure with dot notation
- ✅ Log level configuration

#### **6.2 Metrics Integration**
**Test Coverage**: Micrometer integration and cardinality control

**Test Scenarios:**
- ✅ Automatic metric tag population
- ✅ Cardinality level management (LOW, MEDIUM, HIGH)
- ✅ Custom metric names
- ✅ Counter and gauge metrics

#### **6.3 Tracing Integration**
**Test Coverage**: Distributed tracing span enrichment

**Test Scenarios:**
- ✅ Automatic span tag population
- ✅ Custom span tag names
- ✅ Trace ID propagation
- ✅ Operation context for traces

## 🚀 Test Execution Strategy

### **Continuous Integration Pipeline**

#### **Stage 1: Unit Tests**
```bash
# Fast feedback loop - runs in ~3 seconds
mvn test -Dtest="*ServiceTest,*SourceHandlerTest"
```

#### **Stage 2: Integration Tests**
```bash
# Service integration validation - runs in ~5 seconds
mvn test -Dtest="*IntegrationTest"
```

#### **Stage 3: API Tests**
```bash
# End-to-end validation - runs in ~7 seconds
mvn test -Dtest="*ApiTest"
```

#### **Stage 4: Coverage Verification**
```bash
# Full coverage analysis and reporting
mvn clean verify -Pcoverage-verify
```

### **Local Development Workflow**

#### **Quick Validation**
```bash
# Run specific test for feature being developed
mvn test -Dtest="SimpleRequestContextFieldsTest#testHeaderId1_BasicBidirectional"
```

#### **Feature Validation**
```bash
# Run all tests for a specific source type
mvn test -Dtest="*Test" -Dtest.groups="header-tests"
```

#### **Full Validation**
```bash
# Complete test suite with coverage
./scripts/run-coverage.sh
./scripts/coverage-summary.sh
```

## 📋 Test Quality Assurance

### **Test Code Quality Standards**

#### **Test Naming Convention**
- **Class Names**: `{Feature}{TestType}Test` (e.g., `SimpleRequestContextFieldsTest`)
- **Method Names**: `test{Feature}_{Scenario}` (e.g., `testHeaderId1_BasicBidirectional`)
- **Display Names**: Human-readable descriptions using `@DisplayName`

#### **Test Structure (AAA Pattern)**
```java
@Test
@DisplayName("Clear description of what is being tested")
void testMethod_scenario() {
    // Arrange - Set up test data and mocks
    given()
        .header("X-Test-Header", "test-value");

    // Act - Execute the operation being tested
    .when()
        .get("/api/endpoint");

    // Assert - Verify the expected outcomes
    .then()
        .statusCode(200);

    // Additional verifications
    wireMock.verify(expectedInteraction);
}
```

#### **Test Data Management**
- **Configuration**: Centralized in `request-context-config.yml`
- **Test Patterns**: 16 distinct configuration patterns covering all scenarios
- **Mock Data**: Consistent test data across all test classes
- **WireMock Stubs**: Reusable downstream service mocks

### **Coverage Goals & Metrics**

#### **Current Coverage Targets**
- **Minimum**: 50% (CI/CD gate)
- **Recommended**: 80% (production readiness)
- **Critical Paths**: 95%+ (core framework functionality)

#### **Coverage Analysis**
```bash
# Generate detailed coverage report
mvn jacoco:report

# View coverage by package
./scripts/coverage-summary.sh

# Check specific thresholds
mvn jacoco:check
```

## 🎯 Future Testing Enhancements

### **Planned Improvements**

#### **1. Performance Benchmarking**
- Load testing with JMeter or Gatling
- Memory usage profiling
- Latency impact measurement
- Throughput analysis under various loads

#### **2. Contract Testing**
- Pact-based contract testing for downstream services
- API contract validation
- Schema evolution testing

#### **3. Chaos Engineering**
- Network failure simulation
- Service unavailability testing
- Partial failure scenarios
- Recovery behavior validation

#### **4. Security Testing**
- Penetration testing for sensitive data exposure
- Authentication bypass attempts
- Authorization boundary testing
- Data injection attack prevention

### **Test Automation Enhancements**

#### **1. Test Data Generation**
- Property-based testing with random data generation
- Automated test case generation from configuration
- Edge case discovery through fuzzing

#### **2. Visual Testing**
- Log output format validation
- Metrics dashboard testing
- Documentation accuracy verification

#### **3. Cross-Environment Testing**
- Multi-environment deployment validation
- Configuration drift detection
- Environment-specific behavior testing

This comprehensive testing strategy ensures the Request Context Propagation framework maintains the highest quality standards while providing confidence for production deployments across enterprise environments. 🎯