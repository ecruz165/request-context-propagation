# Request Context Configuration Patterns Guide

Based on your `application-test.yml`, here are the various configuration patterns supported by your request context propagation system:

## 🔄 **Pattern 1: Basic Bidirectional Propagation**
```yaml
headerId1:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-HEADER-ID-1"
    outbound:
      enrichAs: "HEADER"
      key: "X-HEADER-ID-1"
```
- **Extract** from upstream requests
- **Propagate** to downstream requests
- **Use case**: Standard context propagation

## 🎯 **Pattern 2: With Default Values**
```yaml
headerId3:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-HEADER-ID-3"
      defaultValue: "default-header-3"
    outbound:
      enrichAs: "HEADER"
      key: "X-HEADER-ID-3"
```
- **Fallback** when source value is missing
- **Ensures** field always has a value
- **Use case**: Required fields with sensible defaults

## 📥 **Pattern 3: Extract-Only (No Propagation)**
```yaml
emailHeader:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-User-Email"
      required: false
  # No outbound section = stays local
  security:
    sensitive: true
    masking: "***@***.***"
```
- **Extract** for local processing only
- **No downstream** propagation
- **Use case**: Sensitive data that shouldn't leave the service

## 🔒 **Pattern 4: Sensitive with Custom Masking**
```yaml
sensitiveHeader:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Sensitive-Data"
    outbound:
      enrichAs: "HEADER"
      key: "X-Sensitive-Data"
  security:
    sensitive: true
    masking: "*-4"  # Show last 4 characters
```
- **Propagates** but masked in logs
- **Custom masking** patterns
- **Use case**: API keys, tokens that need forwarding

## 🍪 **Pattern 5: Cookie Sources**
```yaml
cookieId1:
  upstream:
    inbound:
      source: "COOKIE"
      key: "session-id"
    outbound:
      enrichAs: "COOKIE"
      key: "session-id"
```
- **Extract** from HTTP cookies
- **Propagate** as cookies
- **Use case**: Session management, user preferences

## ❓ **Pattern 6: Query Parameters**
```yaml
queryId1:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
    outbound:
      enrichAs: "QUERY"
      key: "version"
```
- **Extract** from URL query parameters
- **Propagate** as query parameters
- **Use case**: API versioning, feature flags

## 🗄️ **Pattern 7: Session Attributes**
```yaml
sessionId:
  upstream:
    inbound:
      source: "SESSION"
      key: "user.session.id"
    outbound:
      enrichAs: "SESSION"
      key: "user.session.id"
  security:
    sensitive: true
```
- **Server-side** session storage
- **Secure** context persistence
- **Use case**: User state, shopping carts

## 🎫 **Pattern 8: JWT Claims (Extract-Only)**
```yaml
userId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
      defaultValue: "anonymous"
  security:
    sensitive: true
```
- **Extract** from JWT tokens
- **No propagation** (claims are input-only)
- **Use case**: User identity, permissions

## 🆔 **Pattern 9: Generated Values**
```yaml
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
```
- **Auto-generate** if missing
- **Ensures** tracing continuity
- **Use case**: Request IDs, correlation IDs

## 🔗 **Pattern 10: Fallback Chains**
```yaml
tenantId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Tenant-ID"
      fallback:
        source: "QUERY"
        key: "tenant"
        fallback:
          source: "CLAIM"
          key: "tenant"
          defaultValue: "default-tenant"
```
- **Multiple sources** in priority order
- **Graceful degradation**
- **Use case**: Multi-channel tenant identification

## 🔽 **Pattern 11: Downstream Response Extraction**
```yaml
downstreamServiceVersion:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"  # Required for correlation
      generateIfAbsent: true
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Service-Version"  # Extract from response
```
- **Requires upstream context** for correlation
- **Monitors downstream** service responses
- **Use case**: Service monitoring, SLA tracking

## ⚠️ **Important Rule: All Configs Need Upstream Context**
**Every field configuration MUST have upstream.inbound** because:
- Downstream extraction needs **request correlation**
- Context fields need **lifecycle tracking**
- Metrics need **request attribution**
- No "downstream-only" configurations are valid

## 🔄 **Pattern 12: Cross-Type Enrichment**
```yaml
apiKey:
  upstream:
    inbound:
      source: "HEADER"      # Extract from header
      key: "X-API-Key"
    outbound:
      enrichAs: "QUERY"     # Propagate as query param
      key: "api_key"
```
- **Different** source and enrichment types
- **Protocol adaptation**
- **Use case**: API gateway transformations

## 📊 **Pattern 13: Observability Fields**
```yaml
applicationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Application-ID"
  observability:
    includeInMetrics: true
    metricName: "application_requests"
```
- **Enhanced** observability
- **Metrics, logging, tracing**
- **Use case**: Monitoring, debugging

## 🔧 **Source-Specific Configurations**
```yaml
sourceConfiguration:
  header:
    normalizeNames: true
    caseSensitive: false
  cookie:
    normalizeNames: true
  session:
    createIfMissing: false
  claim:
    validateSignature: true
```

## 🛡️ **Security Masking Patterns**
- `"***"` - Complete masking
- `"*-4"` - Show last 4 characters
- `"***@***.***"` - Email pattern
- `"****-****-****-*-4"` - Credit card pattern

## 🎛️ **Transformation Types**
- `"UPPERCASE"` - Convert to uppercase
- `"LOWERCASE"` - Convert to lowercase
- `"EXPRESSION"` - Custom expression with `${value}`

This comprehensive configuration system supports all common request context propagation patterns while maintaining security and observability!
