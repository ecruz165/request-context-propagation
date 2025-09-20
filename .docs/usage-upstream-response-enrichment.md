# Upstream Response Enrichment Usage Guide

This guide covers how to enrich upstream HTTP responses (responses sent back to clients) with context fields in the Request Context Propagation framework.

## Overview

The framework supports enriching upstream responses using three enrichment types:
- **HEADER** - Add HTTP headers to client responses
- **COOKIE** - Add cookies to client responses
- **QUERY** - Not supported (query parameters don't exist in responses)

Upstream response enrichment allows you to send context data back to clients, including data captured from downstream services.

## Table of Contents

1. [Header Response Enrichment](#header-response-enrichment)
2. [Cookie Response Enrichment](#cookie-response-enrichment)
3. [Framework-Provided Fields](#framework-provided-fields)
4. [Downstream Data Forwarding](#downstream-data-forwarding)
5. [Cross-Format Enrichment](#cross-format-enrichment)
6. [Security and Masking](#security-and-masking)
7. [Best Practices](#best-practices)
8. [Common Patterns](#common-patterns)
9. [Troubleshooting](#troubleshooting)

## Header Response Enrichment

Add HTTP headers to responses sent back to clients.

### Basic Header Enrichment

```yaml
# Extract from request and send back in response
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
  observability:
    includeInLogs: true
```

### Header Enrichment with Different Keys

```yaml
# Extract from one header, send back with different name
correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Response-Correlation-ID"  # Different response header name
  observability:
    includeInLogs: true
```

### Bidirectional Header Flow

```yaml
# Extract, propagate downstream, and send back to client
traceId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Trace-ID"
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Trace-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Trace-ID"
  observability:
    includeInTracing: true
    tracingKey: "trace.id"
```

## Cookie Response Enrichment

Add cookies to responses sent back to clients.

### Basic Cookie Enrichment

```yaml
# Extract from request cookie and set response cookie
sessionId:
  upstream:
    inbound:
      source: "COOKIE"
      key: "JSESSIONID"
    outbound:
      enrichAs: "COOKIE"
      key: "JSESSIONID"
  security:
    sensitive: true
    masking: "*-8"
```

### User Preference Cookies

```yaml
# Set user preferences in response cookies
userPreference:
  upstream:
    inbound:
      source: "COOKIE"
      key: "user-pref"
      defaultValue: "default-theme"
    outbound:
      enrichAs: "COOKIE"
      key: "user-pref"
  observability:
    includeInLogs: true
```

## Framework-Provided Fields

Enrich responses with framework-computed values.

### API Handler Information

```yaml
# Send API handler information to client
apiHandler:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Handler"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "api.handler"
  # No upstream.inbound needed - framework provides value
```

### Processing Time

```yaml
# Send combined processing time to client
combinedProcessingTime:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Combined-Processing-Time"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "combined.processing.time"
  # Framework calculates total processing time
```

## Downstream Data Forwarding

Forward data captured from downstream services back to clients.

### Downstream Service Version

```yaml
# Capture downstream service version and forward to client
downstreamServiceVersionPublic:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Service-Version"
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Downstream-Service-Version"  # Sent to client
  observability:
    includeInLogs: true
```

### Downstream User Data

```yaml
# Capture user service version and forward to client
downstreamUserServiceVersion:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-User-Service-Version"
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Downstream-User-Version"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.user.version"
```

### Rate Limiting Information

```yaml
# Forward rate limit information to client
rateLimitRemaining:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Client-ID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-ID"
  downstream:
    inbound:
      source: "HEADER"
      key: "X-RateLimit-Remaining"
  # Rate limit info automatically forwarded to client
  observability:
    includeInMetrics: true
    metricName: "rate_limit_remaining"
```

## Cross-Format Enrichment

Enrich responses using data from different source formats.

### Query to Header

```yaml
# Extract from query parameter, send as response header
clientVersion:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
      defaultValue: "1.0"
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"
  observability:
    includeInLogs: true
```

### Claim to Header

```yaml
# Extract from JWT claim, send as response header
userId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
    outbound:
      enrichAs: "HEADER"
      key: "X-User-ID"
  observability:
    includeInLogs: true
```



## Security and Masking

Protect sensitive data when enriching responses.

### Sensitive Header Masking

```yaml
authToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
    outbound:
      enrichAs: "HEADER"
      key: "X-Auth-Status"  # Don't echo back the actual token
  security:
    sensitive: true
    masking: "***"
  observability:
    includeInLogs: true
```

### Secure Cookie Settings

```yaml
sessionToken:
  upstream:
    inbound:
      source: "COOKIE"
      key: "session-token"
    outbound:
      enrichAs: "COOKIE"
      key: "session-token"
  security:
    sensitive: true
    masking: "*-8"
  # Framework automatically sets HttpOnly and Secure flags
```

## Best Practices

### 1. Be Selective About Response Data

```yaml
# Good - send useful client information
requestId:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"

# Avoid - sending internal processing details
internalProcessingDetails:
  # Don't add upstream.outbound for internal data
  observability:
    includeInLogs: true
```

### 2. Use Meaningful Header Names

```yaml
# Good - clear, descriptive names
downstreamServiceVersion:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Downstream-Service-Version"

# Avoid - generic or unclear names
data:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Data"
```

### 3. Protect Sensitive Information

```yaml
# Always mark sensitive fields
userAuth:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Auth-Status"  # Status, not the actual token
  security:
    sensitive: true
    masking: "***"
```

### 4. Consider Client Needs

```yaml
# Send data that clients actually need
apiVersion:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Version"

processingTime:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Processing-Time-Ms"
```

## Common Patterns

### Pattern 1: Request Tracking

```yaml
# Enable client-side request tracking
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

correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"
```

### Pattern 2: API Metadata

```yaml
# Provide API metadata to clients
apiHandler:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Handler"

apiVersion:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Version"
```

### Pattern 3: Performance Information

```yaml
# Send performance metrics to clients
processingTime:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Processing-Time-Ms"

cacheStatus:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Cache-Status"
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Cache-Status"
```

### Pattern 4: User Context

```yaml
# Provide user context information
userId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
    outbound:
      enrichAs: "HEADER"
      key: "X-User-ID"

userRole:
  upstream:
    inbound:
      source: "CLAIM"
      key: "roles[0]"
    outbound:
      enrichAs: "HEADER"
      key: "X-User-Role"
```

## Troubleshooting

### Common Issues

1. **Headers Not Appearing in Response**
   - Check `upstream.outbound` configuration
   - Verify `enrichAs: "HEADER"` is set
   - Ensure field has a value in context

2. **Cookies Not Set**
   - Check `enrichAs: "COOKIE"` configuration
   - Verify cookie security settings
   - Check browser cookie policies

3. **Sensitive Data Exposed**
   - Add `security.sensitive: true`
   - Configure appropriate masking
   - Review response headers in browser dev tools

4. **Framework Fields Not Working**
   - Ensure no `upstream.inbound` for framework fields
   - Check field is in framework-provided list
   - Verify observability configuration

### Debug Configuration

```yaml
# Enable debug response headers
debugInfo:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Debug"
    outbound:
      enrichAs: "HEADER"
      key: "X-Debug-Response"
  observability:
    includeInLogs: true
```

### Response Validation

Check response headers in:
- Browser Developer Tools (Network tab)
- curl commands: `curl -I http://your-api/endpoint`
- Integration tests with response header assertions

### Integration Points

The framework automatically enriches responses through:
- `RequestContextInterceptor` - For controller responses
- `ResponseBodyAdvice` - For response body processing
- Spring MVC response processing pipeline

## Next Steps

- [Downstream Request Propagation](usage-downstream-request.md)
- [Downstream Response Extraction](usage-downstream-response.md)
- [Upstream Inbound Extraction](usage-upstream-inbound-extraction.md)
- [Overview of Source Types](overview-of-source-types.md)
