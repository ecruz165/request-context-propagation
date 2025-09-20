# Comprehensive Request Context Field Tests

I've created a complete test suite that covers **every field** defined in your `request-context-config.yml` with comprehensive assertions for:

## 🧪 **Test Coverage Created:**

### **1. RequestContextFieldsTest.java** - Main Test Suite
- **32 test methods** covering all configuration patterns
- **4 types of assertions** per test:
  - ✅ **Result assertions** - Verify field values in response
  - ✅ **Log assertions** - Verify expected log content and masking
  - ✅ **WireMock verification** - Verify downstream propagation
  - ✅ **Actuator metrics** - Verify metrics collection

### **2. Test Controllers Created:**

#### **TestController.java** - Synchronous Controller
- `/api/test/downstream` - Makes downstream calls, tests propagation
- `/api/test/simple` - No downstream calls, tests extract-only fields
- Returns all context fields for verification

#### **ProtectedController.java** - JWT Token Source Tests
- `/api/protected/user-info` - **Single protected endpoint** for JWT claims
- Requires authentication to access JWT token sources
- Tests JWT claim extraction patterns

### **3. Supporting Infrastructure:**

#### **JwtTestHelper.java** - JWT Token Creation
- Builder pattern for creating test JWT tokens
- Supports custom claims for comprehensive JWT testing
- Used for testing JWT claim extraction patterns

#### **BaseApiTest.java** - Test Infrastructure
- WireMock server setup for downstream service mocking
- Common test configuration and profiles
- Shared setup/teardown logic

#### **RequestContextService.java** - Context Access
- Service for accessing request context field values
- Integration point with actual context storage
- Used by controllers to return field values

## 📋 **All Configuration Patterns Tested:**

### **Pattern 1: Basic Bidirectional Propagation**
- `headerId1`, `headerId2` - Extract + propagate downstream

### **Pattern 2: With Default Values**
- `headerId3` - Custom value vs default value scenarios
- `headerId7` - Extract-only with default

### **Pattern 3: Extract-Only (No Propagation)**
- `emailHeader` - Sensitive field, no downstream propagation

### **Pattern 4: Sensitive with Custom Masking**
- `sensitiveHeader` - Propagates but masked in logs (*-4 pattern)

### **Pattern 5: Cookie Sources**
- `cookieId1`, `cookieId2` - Cookie extraction and propagation

### **Pattern 6: Query Parameters**
- `queryId1`, `queryId2` - Query param propagation
- `queryId5` - Extract-only query parameter

### **Pattern 7: Session Attributes**
- `sessionId` - Session attribute with masking

### **Pattern 8: JWT Claims (Extract-Only) - PROTECTED ENDPOINT**
- `userId`, `userEmail`, `userRole` - JWT claim extraction
- **Only protected endpoint** for token source tests
- Tests default values when claims missing

### **Pattern 9: Generated Values**
- `requestId` - UUID generation when missing vs provided value

### **Pattern 10: Fallback Chains**
- `tenantId` - Header → Query → JWT Claim → Default fallback

### **Pattern 11: Cross-Type Enrichment**
- `apiKey` - Header → Query param propagation
- `clientVersion` - Query → Header propagation

### **Pattern 12: Downstream Response Extraction**
- `downstreamServiceVersion`, `downstreamResponseStatus`
- `rateLimitRemaining`, `downstreamResponseTime`, `downstreamErrorCode`

### **Pattern 13: Bidirectional with Downstream**
- `correlationId` - Full lifecycle tracking

### **Pattern 14: Observability Fields**
- `applicationId`, `clientId`, `traceId` - Metrics, logging, tracing

### **Pattern 15: Downstream Monitoring with Context**
- `serviceHealth`, `cacheStatus`, `dbQueryTime`
- `featureFlags`, `securityWarnings`

## 🔍 **Test Assertions Per Field:**

### **1. Result Assertions**
```java
.body("contextFields.headerId1", equalTo("test-header-1"))
```

### **2. Log Assertions**
```java
assertLogContains("headerId1=test-header-1");
assertLogContains("sensitiveHeader=*3456");  // Masked
```

### **3. WireMock Downstream Verification**
```java
wireMock.verify(getRequestedFor(urlPathEqualTo("/downstream/service"))
    .withHeader("X-HEADER-ID-1", equalTo("test-header-1")));
```

### **4. Actuator Metrics Assertions**
```java
assertMetricExists("application_requests", "application", "my-app-v1");
assertMetricExists("downstream_service_version", "version", "2.1.0");
```

## 🎯 **Key Features:**

- **Complete Coverage**: Every field in your config is tested
- **Single Protected Endpoint**: Only `/api/protected/user-info` for JWT tests
- **Comprehensive Assertions**: 4 types of verification per field
- **Real Scenarios**: Tests both success and fallback cases
- **Security Testing**: Verifies masking patterns work correctly
- **Downstream Mocking**: WireMock simulates downstream services
- **Metrics Verification**: Confirms observability integration

This test suite provides **complete validation** that your request context propagation system works correctly for all 30+ fields across all configuration patterns!

## 🚀 **Running the Tests:**

```bash
# Run all field tests
./mvnw test -Dtest=RequestContextFieldsTest

# Run specific pattern tests
./mvnw test -Dtest=RequestContextFieldsTest#testHeaderId1_BasicBidirectional
./mvnw test -Dtest=RequestContextFieldsTest#testJwtClaims_ProtectedEndpoint
```
