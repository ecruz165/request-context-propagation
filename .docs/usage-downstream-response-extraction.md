# Downstream Response Extraction Usage Guide

This guide covers how to extract and capture data from downstream service responses in the Request Context Propagation framework.

## Overview

The framework supports extracting context fields from downstream service responses using two source types:
- **HEADER** - Extract from HTTP response headers
- **BODY** - Extract from JSON response bodies using JSONPath

## Table of Contents

1. [Header Response Extraction](#header-response-extraction)
2. [Body Response Extraction](#body-response-extraction)
3. [Response Body Buffering](#response-body-buffering)
4. [Observability Integration](#observability-integration)
5. [Best Practices](#best-practices)
6. [Common Patterns](#common-patterns)
7. [Troubleshooting](#troubleshooting)

## Header Response Extraction

Extract values from downstream HTTP response headers for monitoring, correlation, and observability.

### Basic Header Extraction

```yaml
# Extract service version from downstream response header
downstreamServiceVersion:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Service-Version"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.service.version"
```

### Header Extraction with Upstream Context

```yaml
# Extract response time with request correlation
downstreamResponseTime:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Response-Time"
  observability:
    includeInMetrics: true
    metricName: "downstream_response_time_ms"
```

### Header Extraction with Client Response

```yaml
# Extract and forward to client
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

## Body Response Extraction

Extract specific fields or entire JSON responses from downstream service response bodies using JSONPath expressions.

### Basic JSONPath Extraction

```yaml
# Extract user email from nested JSON response
userProfileEmail:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.profile.email"  # JSONPath expression
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.user.email"
```

### Complex JSONPath Patterns

```yaml
# Extract first result ID from array
firstResultId:
  downstream:
    inbound:
      source: "BODY"
      key: "$.results[0].id"
  observability:
    includeInLogs: true

# Extract nested object
userPreferences:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.preferences"
  observability:
    includeInLogs: true

# Extract with filtering (advanced JSONPath)
activeUsersCount:
  downstream:
    inbound:
      source: "BODY"
      key: "$.users[?(@.status == 'active')].length()"
  observability:
    includeInMetrics: true
    metricName: "active_users_count"
```

### Entire Response Extraction

```yaml
# Extract complete JSON response
fullApiResponse:
  downstream:
    inbound:
      source: "BODY"
      key: "$"  # Root - entire response
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.full.response"
  security:
    sensitive: true
    masking: "*-50"  # Show last 50 characters for debugging
```

### Default Values and Error Handling

```yaml
# Extract with fallback
serviceVersion:
  downstream:
    inbound:
      source: "BODY"
      key: "$.metadata.version"
      defaultValue: "unknown"
  observability:
    includeInLogs: true

# Required field with validation
criticalData:
  downstream:
    inbound:
      source: "BODY"
      key: "$.critical.field"
      required: true  # Will log warning if missing
  observability:
    includeInLogs: true
```

## Response Body Buffering

The framework automatically handles response body buffering when BODY extraction is configured.

### How It Works

1. **Automatic Detection**: Framework detects when BODY source fields are configured
2. **Conditional Buffering**: Only buffers responses when BODY extraction is needed
3. **Multiple Reads**: Allows both framework extraction and application consumption
4. **Memory Efficient**: Buffers only when necessary

### Configuration

No additional configuration required - buffering is automatic:

```yaml
# This configuration automatically enables response buffering
userEmail:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.email"
```

### Performance Considerations

- Buffering adds memory overhead for response content
- Only enabled when BODY extraction fields are configured
- Consider response size when extracting entire responses (`key: "$"`)

## Observability Integration

Downstream response data integrates seamlessly with observability systems.

### Logging Integration

```yaml
downstreamMetrics:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Processing-Time"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.processing.time"
      # Automatically added to MDC for structured logging
```

### Metrics Integration

```yaml
responseLatency:
  downstream:
    inbound:
      source: "BODY"
      key: "$.metadata.processingTimeMs"
  observability:
    includeInMetrics: true
    metricName: "downstream_processing_time"
    # Automatically exposed as metric
```

### Tracing Integration

```yaml
downstreamTraceData:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Trace-Context"
  observability:
    includeInTracing: true
    tracingKey: "downstream.trace.context"
    # Automatically added to trace spans
```

## Best Practices

### 1. Use Specific JSONPath Expressions

```yaml
# Good - specific extraction
userEmail:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.email"

# Avoid - extracting large responses unless needed
fullResponse:
  downstream:
    inbound:
      source: "BODY"
      key: "$"  # Only use when you need the entire response
```

### 2. Provide Default Values

```yaml
# Always provide defaults for optional fields
serviceVersion:
  downstream:
    inbound:
      source: "BODY"
      key: "$.version"
      defaultValue: "unknown"
```

### 3. Use Appropriate Security Settings

```yaml
# Mark sensitive data appropriately
userPersonalData:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.personalInfo"
  security:
    sensitive: true
    masking: "***"
```

### 4. Optimize for Performance

```yaml
# Extract only what you need
userId:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.id"  # Not "$.user" (entire user object)
```

## Common Patterns

### Pattern 1: Service Health Monitoring

```yaml
serviceHealth:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Service-Name"
      defaultValue: "unknown-service"
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Health-Status"
  observability:
    includeInMetrics: true
    metricName: "service_health_status"
```

### Pattern 2: Error Tracking

```yaml
downstreamErrorCode:
  downstream:
    inbound:
      source: "BODY"
      key: "$.error.code"
      required: false
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.error.code"
```

### Pattern 3: Performance Monitoring

```yaml
apiLatency:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Response-Time"
  observability:
    includeInMetrics: true
    metricName: "api_response_time_ms"
```

### Pattern 4: User Context Enrichment

```yaml
userRole:
  downstream:
    inbound:
      source: "BODY"
      key: "$.user.role"
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-User-Role"  # Forward to client
  observability:
    includeInLogs: true
```

## Troubleshooting

### Common Issues

1. **Empty Response Body**
   - Check if downstream service returns content
   - Verify Content-Type is application/json for BODY extraction

2. **JSONPath Not Working**
   - Validate JSONPath syntax: `$.field.subfield`
   - Test JSONPath expressions with sample JSON

3. **Response Body Already Consumed**
   - Framework handles this automatically with buffering
   - Ensure BODY fields are properly configured

4. **Missing Header Values**
   - Check if downstream service sets the expected headers
   - Verify header key names (case-sensitive)

### Debug Configuration

```yaml
# Enable debug logging for troubleshooting
downstreamDebug:
  downstream:
    inbound:
      source: "BODY"
      key: "$.debug.info"
      required: false  # Won't fail if missing
  observability:
    includeInLogs: true
    logging:
      mdcKey: "downstream.debug"
```

### Validation

Use the integration tests as examples:
- `BodyDownstreamExtractionIntegrationTest` - BODY extraction examples
- `ResponseBodyBufferingIntegrationTest` - Buffering validation
- `SimpleRequestContextFieldsTest` - Header extraction examples

## Next Steps

- [Upstream Inbound Extraction](usage-upstream-inbound-extraction.md)
- [Upstream to Downstream Propagation](usage-upstream-to-downstream-propagation.md)
- [Overview of Source Types](overview-of-source-types.md)
