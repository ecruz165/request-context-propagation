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

### Unit Tests with Mockito
- **BodySourceHandlerTest** - JSONPath extraction logic with comprehensive edge cases
- **RequestContextServiceProgrammaticTest** - Service API validation and programmatic access
- **RequestContextWebClientCaptureFilterTest** - WebClient filter behavior testing

### API Tests with RestAssured and WireMock
- **SimpleRequestContextFieldsTest** - Complete validation of all 16 configuration patterns from Source Type Support Matrix
- **ConcurrentContextAccessTest** - Multi-threading scenarios and HttpServletRequest storage limitations
- **ConcurrentZipBlockUpstreamTest** - Reactive concurrent operations with context propagation
- **DeferredResultReactiveContextTest** - Asynchronous DeferredResult endpoint testing
- **SystemSpecificPropagationUnitTest** - extSysIds filtering and system-specific propagation

### Integration Tests
- **BodyDownstreamExtractionIntegrationTest** - JSONPath downstream response extraction
- **ResponseBodyBufferingIntegrationTest** - Response body consumption handling
- **ContextAwareWebClientBuilderTest** - WebClient context propagation integration


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
- **Total Test Classes**: 20 Java test files
- **Test Categories**: 5 distinct testing approaches (API, Unit, Integration, Performance, Security)
- **Current Coverage**: 54.4% instruction coverage, 54.8% line coverage, 67.6% method coverage
- **Coverage Target**: 50% minimum (configured), targeting 80%+ for production
- **Test Execution Time**: ~10 seconds for full suite
- **JaCoCo Integration**: Comprehensive coverage reporting with exclusions

#### **Test Package Structure**
```
src/test/java/com/example/demo/
├── api/                          # API-level integration tests (6 classes)
│   ├── SimpleRequestContextFieldsTest.java      # 16 configuration patterns
│   ├── ConcurrentContextAccessTest.java         # Multi-threading scenarios
│   ├── ConcurrentZipBlockUpstreamTest.java      # Reactive concurrent operations
│   ├── ContextAwareWebClientBuilderTest.java    # WebClient integration
│   ├── DeferredResultReactiveContextTest.java   # Async DeferredResult testing
│   └── SystemSpecificPropagationUnitTest.java   # extSysIds filtering
├── integration/                  # Service integration tests (2 classes)
│   ├── BodyDownstreamExtractionIntegrationTest.java  # JSONPath extraction
│   └── ResponseBodyBufferingIntegrationTest.java     # Response buffering
├── service/                      # Service-level unit tests (3 classes)
│   ├── RequestContextServiceProgrammaticTest.java    # Programmatic API
│   ├── ReactiveTestService.java                      # Reactive service helper
│   └── source/BodySourceHandlerTest.java             # Source handler logic
├── filter/                       # Filter-level tests (1 class)
│   └── RequestContextWebClientCaptureFilterTest.java # WebClient filters
├── config/                       # Test infrastructure (3 classes)
│   ├── BaseApiTest.java          # Common test setup with WireMock
│   ├── TestSecurityConfig.java   # Security configuration for tests
│   └── TestWebClientConfig.java  # WebClient test configuration
├── controller/                   # Test controllers (2 classes)
│   ├── TestController.java       # Main test endpoints
│   └── ProtectedController.java  # Security-protected endpoints
├── examples/                     # Integration examples (1 class)
│   └── WebClientBuilderIntegrationExample.java   # Usage examples
├── utils/                        # Test utilities (1 class)
│   └── JwtTestHelper.java         # JWT generation for testing
├── DemoApplication.java          # Test application entry point
└── TestApplicationTests.java     # Basic Spring Boot test
```

### **Testing Approach by Layer**

#### **1. API Tests (End-to-End Validation)**
**Purpose**: Validate complete request/response flows through HTTP endpoints
**Tools**: REST Assured, WireMock, Spring Boot Test
**Coverage**: All 16 configuration patterns from `request-context-config.yml`

**Key Test Classes:**
- `SimpleRequestContextFieldsTest` - Core functionality validation (16 configuration patterns)
- `ConcurrentContextAccessTest` - Multi-threading and HttpServletRequest storage limitations
- `ConcurrentZipBlockUpstreamTest` - Reactive operations testing
- `DeferredResultReactiveContextTest` - Asynchronous DeferredResult endpoint testing
- `SystemSpecificPropagationUnitTest` - extSysIds filtering validation
- `ContextAwareWebClientBuilderTest` - WebClient integration testing

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

**Current Coverage Metrics (Latest):**
- **Instruction Coverage**: 54.4% (4067/7473)
- **Branch Coverage**: 40.7% (363/892)
- **Line Coverage**: 54.8% (1004/1833)
- **Method Coverage**: 67.6% (225/333)

**Coverage by Package:**
- `com.example.demo.autoconfigure`: 100.0% (fully covered)
- `com.example.demo.config.props`: 90.8% (excellent coverage)
- `com.example.demo.service.source`: 60.4% (good coverage)
- `com.example.demo.observability`: 59.7% (good coverage)
- `com.example.demo.filter`: 56.6% (fair coverage)
- `com.example.demo.service`: 50.1% (meets minimum)
- `com.example.demo.config`: 48.5% (below target)
- `com.example.demo.logging`: 42.5% (needs improvement)

**Coverage Exclusions:**
- `**/DemoApplication.class` - Main application class
- `**/config/**/*Config.class` - Configuration classes
- Auto-generated classes and DTOs

### **Test Execution Commands**
```bash
# Run all tests with coverage
./mvnw clean test jacoco:report

# Run with coverage verification
./mvnw clean verify -Pcoverage-verify

# Run specific test categories
./mvnw test -Dtest="*ApiTest" jacoco:report          # API tests only
./mvnw test -Dtest="*IntegrationTest" jacoco:report  # Integration tests
./mvnw test -Dtest="*ServiceTest" jacoco:report      # Service layer tests
./mvnw test -Dtest="*SourceHandlerTest" jacoco:report # Source handler tests

# Generate and view coverage reports
./mvnw jacoco:report
open target/site/jacoco/index.html

# Enhanced test execution scripts
./.scripts/run-coverage.sh        # Complete test suite with coverage
./.scripts/coverage-summary.sh    # Detailed coverage analysis
./.scripts/view-coverage.sh       # Open coverage reports in browser

# Run specific test classes
./mvnw test -Dtest="SimpleRequestContextFieldsTest"
./mvnw test -Dtest="BodySourceHandlerTest"
./mvnw test -Dtest="ConcurrentContextAccessTest"
```

## 🧪 Current Test Implementation Details

### **Actual Test Classes and Their Coverage**

#### **API Test Layer (6 Classes)**

**1. SimpleRequestContextFieldsTest**
- **Purpose**: Validates all 16 configuration patterns from `request-context-config.yml`
- **Test Methods**: 16 test methods covering each configuration pattern
- **Key Features**: REST Assured + WireMock integration, header/cookie/query/claim/path testing
- **Coverage**: Complete end-to-end validation of source type matrix

**2. ConcurrentContextAccessTest**
- **Purpose**: Demonstrates multi-threading limitations with HttpServletRequest storage
- **Test Methods**: TRUE parallel thread execution, race condition detection
- **Key Features**: ExecutorService, CountDownLatch synchronization, AtomicInteger counters
- **Coverage**: Thread safety validation and context isolation testing

**3. ConcurrentZipBlockUpstreamTest**
- **Purpose**: Reactive concurrent operations with context propagation
- **Test Methods**: Zip and block operations with multiple downstream services
- **Key Features**: WebClient reactive streams, concurrent downstream calls
- **Coverage**: Reactive context preservation and aggregation

**4. DeferredResultReactiveContextTest**
- **Purpose**: Asynchronous DeferredResult endpoint testing
- **Test Methods**: Async processing with context propagation
- **Key Features**: DeferredResult, CompletableFuture, async context handling
- **Coverage**: Asynchronous request processing validation

**5. SystemSpecificPropagationUnitTest**
- **Purpose**: extSysIds filtering and system-specific propagation
- **Test Methods**: Targeted propagation based on system identifiers
- **Key Features**: extSysIds configuration, selective downstream propagation
- **Coverage**: System-specific context filtering

**6. ContextAwareWebClientBuilderTest**
- **Purpose**: WebClient integration and context propagation
- **Test Methods**: WebClient builder configuration and filter integration
- **Key Features**: Custom WebClient configuration, filter chain testing
- **Coverage**: WebClient context propagation validation

#### **Unit Test Layer (3 Classes)**

**1. BodySourceHandlerTest**
- **Purpose**: JSONPath extraction logic with comprehensive edge cases
- **Test Methods**: 15+ test methods covering all JSONPath scenarios
- **Key Features**: Mockito mocking, JSONPath validation, type conversion
- **Coverage**: Complete source handler logic validation

**2. RequestContextServiceProgrammaticTest**
- **Purpose**: Service API validation and programmatic access
- **Test Methods**: Field access, configuration methods, context management
- **Key Features**: Spring Boot Test, MockHttpServletRequest, service proxy testing
- **Coverage**: Programmatic API validation

**3. RequestContextWebClientCaptureFilterTest**
- **Purpose**: WebClient filter behavior testing
- **Test Methods**: Filter chain execution, response capture, context extraction
- **Key Features**: Filter testing, WebClient integration
- **Coverage**: Filter behavior validation

#### **Integration Test Layer (2 Classes)**

**1. BodyDownstreamExtractionIntegrationTest**
- **Purpose**: JSONPath downstream response extraction
- **Test Methods**: End-to-end JSONPath extraction from real responses
- **Key Features**: Integration testing, real HTTP calls, JSONPath validation
- **Coverage**: Downstream extraction integration

**2. ResponseBodyBufferingIntegrationTest**
- **Purpose**: Response body consumption handling
- **Test Methods**: Multiple response body reads, buffering validation
- **Key Features**: Response body buffering, consumption conflict prevention
- **Coverage**: Response handling integration

#### **Test Infrastructure (8 Classes)**

**1. BaseApiTest**
- **Purpose**: Common test setup with WireMock server
- **Features**: WireMock server lifecycle, port 8089 configuration, test profiles
- **Usage**: Extended by all API test classes for consistent setup

**2. TestController**
- **Purpose**: Main test endpoints for API testing
- **Features**: Multiple endpoint types (sync, async, reactive), context field exposure
- **Endpoints**: `/api/test/downstream`, `/api/test/reactive`, `/api/test/deferred`

**3. ProtectedController**
- **Purpose**: Security-protected endpoints for authentication testing
- **Features**: JWT-protected endpoints, claim extraction testing
- **Security**: Spring Security integration with JWT authentication

**4. TestSecurityConfig**
- **Purpose**: Security configuration for tests
- **Features**: JWT decoder configuration, test security setup
- **Integration**: Spring Security test configuration

**5. TestWebClientConfig**
- **Purpose**: WebClient test configuration
- **Features**: Custom WebClient beans, filter configuration
- **Integration**: Context-aware WebClient setup

**6. JwtTestHelper**
- **Purpose**: JWT generation for testing
- **Features**: JWT token builder, custom claims, test key management
- **Usage**: Used by claim extraction tests

**7. ReactiveTestService**
- **Purpose**: Reactive service helper for testing
- **Features**: Reactive operations, context propagation testing
- **Integration**: Used by reactive test scenarios

**8. WebClientBuilderIntegrationExample**
- **Purpose**: Integration examples and usage patterns
- **Features**: Real-world usage examples, integration patterns
- **Documentation**: Living documentation through code examples

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
- ✅ JSONPath extraction from downstream responses (`$.data.userId`)
- ✅ Complex JSON structure navigation with nested objects
- ✅ Error handling for invalid JSON and malformed JSONPath
- ✅ Response body buffering to prevent consumption conflicts
- ✅ Multiple JSONPath expressions in single response
- ✅ Type conversion (numbers, booleans to strings)
- ✅ Complex object extraction returning JSON strings
- ✅ Array element extraction with index notation
- ✅ Null value handling and missing path scenarios
- ✅ Empty response body and non-JSON response handling

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
- ✅ Multiple threads accessing context simultaneously (demonstrates HttpServletRequest limitations)
- ✅ Parallel execution with thread pools (context not propagated across threads)
- ✅ Context isolation between different request threads
- ✅ Race condition detection with CountDownLatch synchronization
- ✅ HttpServletRequest storage limitations vs ThreadLocal behavior
- ✅ TRUE parallel thread execution with context access attempts
- ✅ Concurrent context access race conditions with multiple iterations

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
./.scripts/run-coverage.sh
./.scripts/coverage-summary.sh
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
./.scripts/coverage-summary.sh

# Check specific thresholds
mvn jacoco:check
```
