# Request Context Propagation Framework - Product Requirements Document
==========================

## 📋 Executive Summary

The Request Context Propagation Framework is a declarative, configuration-driven solution for managing request context across distributed microservices. It automatically extracts, enriches, propagates, and monitors contextual information (user IDs, tenant IDs, correlation IDs, etc.) without requiring code changes.

### Key Business Value
- **Zero-code context management** - Configure via YAML, no development required
- **Enhanced observability** - Automatic span enrichment for DataDog/APM
- **Improved debugging** - Context preserved across service boundaries
- **Compliance ready** - Audit trail of all requests with full context
- **Multi-tenant support** - Built-in tenant isolation and tracking

## 🎯 Core Features & Testing

### 1. Context Extraction from Multiple Sources

#### 1.1 JWT Token Extraction
**Feature**: Extract context from JWT claims in Authorization header

```bash
# Generate a sample JWT with claims (for testing)
# Payload: {"sub":"user-123","tenant_id":"acme-corp","application_id":"mobile-app"}
JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJ1c2VyLTEyMyIsInRlbmFudF9pZCI6ImFjbWUtY29ycCIsImFwcGxpY2F0aW9uX2lkIjoibW9iaWxlLWFwcCIsImlhdCI6MTUxNjIzOTAyMn0.abc123"

# Test JWT extraction
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Authorization: Bearer ${JWT}" \
  -H "Accept: application/json" | jq

# Expected Response:
{
  "context": {
    "partyId": "user-123",
    "tenantId": "acme-corp",
    "applicationId": "mobile-app"
  }
}
```

#### 1.2 Header Extraction
**Feature**: Extract context from HTTP headers

```bash
# Test header extraction
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-456" \
  -H "X-Tenant-ID: tenant-789" \
  -H "X-Application-ID: web-app" \
  -H "X-Correlation-ID: corr-abc-123" \
  -H "Accept: application/json" | jq

# Test case-insensitive headers
curl -X GET http://localhost:8080/api/v1/profile \
  -H "x-party-id: party-456" \
  -H "X-TENANT-ID: tenant-789" | jq
```

#### 1.3 Cookie Extraction
**Feature**: Extract context from cookies

```bash
# Test cookie extraction
curl -X GET http://localhost:8080/api/v1/profile \
  -H "Cookie: sessionId=sess-123456; userId=user-789" \
  -H "Accept: application/json" | jq
```

#### 1.4 Query Parameter Extraction
**Feature**: Extract context from URL query parameters

```bash
# Test query parameter extraction
curl -X GET "http://localhost:8080/api/v1/profile?tenant=tenant-123&session=sess-456" \
  -H "Accept: application/json" | jq
```

#### 1.5 Path Variable Extraction
**Feature**: Extract context from URL path

```bash
# Test path variable extraction
# Assuming route: /api/v1/tenants/{tenantId}/users/{userId}
curl -X GET http://localhost:8080/api/v1/tenants/acme-corp/users/user-123 \
  -H "Accept: application/json" | jq
```

### 2. Context Transformation

#### 2.1 Built-in Transformations
**Feature**: Apply transformations to extracted values

```bash
# Test lowercase transformation (configured for tenantId)
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Tenant-ID: ACME-CORP" \
  -H "Accept: application/json" | jq

# Expected: tenantId should be "acme-corp"

# Test trim transformation
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-User-ID: '  user-123  '" \
  -H "Accept: application/json" | jq
```

### 3. Context Validation

#### 3.1 Required Fields Validation
**Feature**: Validate required context fields

```bash
# Test missing required field (applicationId is required)
curl -X POST http://localhost:8080/api/v1/transaction \
  -H "X-Party-ID: party-123" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}' | jq

# Expected Response (400 Bad Request):
{
  "error": "Missing required context",
  "missing": ["applicationId", "tenantId"]
}

# Test with all required fields
curl -X POST http://localhost:8080/api/v1/transaction \
  -H "X-Party-ID: party-123" \
  -H "X-Application-ID: mobile-app" \
  -H "X-Tenant-ID: acme-corp" \
  -H "Content-Type: application/json" \
  -d '{"amount": 100}' | jq

# Expected Response (200 OK):
{
  "transactionId": "txn-xxx",
  "status": "SUCCESS"
}
```

### 4. Context Propagation to Downstream Services

#### 4.1 Automatic Header Propagation
**Feature**: Context automatically propagated via WebClient

```bash
# Make a request that triggers downstream calls
curl -X GET http://localhost:8080/api/v1/aggregate \
  -H "X-Party-ID: party-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-Correlation-ID: corr-test-456" \
  -H "Accept: application/json" | jq

# Verify downstream service receives headers by checking logs or response
# Expected: Downstream service should receive X-Party-ID, X-Tenant-ID, X-Correlation-ID headers
```

#### 4.2 Correlation ID Generation
**Feature**: Auto-generate correlation ID if missing

```bash
# Request without correlation ID
curl -X GET http://localhost:8080/api/v1/aggregate \
  -H "X-Party-ID: party-123" \
  -v | jq

# Check response headers - should include generated correlation ID
# Expected Response Header: X-Correlation-ID: <generated-uuid>
```

### 5. Response Header Enrichment

#### 5.1 Echo Context in Response
**Feature**: Return context values in response headers

```bash
# Test response header enrichment
curl -X GET http://localhost:8080/api/v1/profile \
  -H "X-Party-ID: party-123" \
  -H "X-Correlation-ID: corr-789" \
  -v 2>&1 | grep "X-"

# Expected Response Headers:
< X-Correlation-ID: corr-789
< X-Request-ID: <generated-id>
< X-Party-ID: party-123
```

### 6. Observability Integration

#### 6.1 Debug Context Endpoint
**Feature**: Inspect current context and configuration

```bash
# Get current context (without sensitive data)
curl -X GET http://localhost:8080/debug/context \
  -H "X-Party-ID: party-123" \
  -H "X-Tenant-ID: acme-corp" \
  -H "X-API-Key: secret-key-123" | jq

# Expected Response:
{
  "allContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp"
    // Note: API key not shown (sensitive)
  },
  "configuredKeys": ["partyId", "tenantId", "applicationId", "apiKey"],
  "downstreamContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp"
  },
  "observabilityContext": {
    "party-id": "party-123",
    "tenant-id": "acme-corp"
  },
  "loggingContext": {
    "partyId": "party-123",
    "tenantId": "acme-corp"
  }
}

# Get context with sensitive data (for debugging)
curl -X GET "http://localhost:8080/debug/context?includeSensitive=true" \
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

## 📈 Success Metrics

| Metric | Target | Measurement Command |
|--------|--------|-------------------|
| Context Extraction Success Rate | >99.9% | `curl /actuator/metrics/context.extraction.success` |
| Propagation Latency | <1ms | `curl /actuator/metrics/context.propagation.duration` |
| Missing Context Errors | <0.1% | `curl /actuator/metrics/context.missing.count` |
| Downstream Propagation Rate | 100% | Check downstream service logs |

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

## 📋 Acceptance Criteria

- [ ] Context extraction works from all configured sources
- [ ] Required field validation prevents processing without mandatory context
- [ ] Context propagates to all downstream service calls
- [ ] Correlation ID is generated when missing
- [ ] Sensitive data is not logged
- [ ] Context appears in DataDog/APM traces
- [ ] Context is included in structured logs
- [ ] Performance impact is less than 1ms per request
- [ ] Context is preserved across async boundaries
- [ ] Failed requests still have context in error logs

## 🚀 Deployment Verification

```bash
# Production smoke test
curl -X GET https://api.production.com/health/context \
  -H "X-Party-ID: smoke-test" \
  -H "X-Correlation-ID: deploy-verification-$(date +%s)" \
  -v

# Verify in DataDog
# Search for: @correlation_id:"deploy-verification-*"
```