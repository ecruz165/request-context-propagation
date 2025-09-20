# Observability Usage Guide

This guide covers how to configure and use the comprehensive observability features in the Request Context Propagation framework.

## Overview

The framework provides three pillars of observability:
- **Logging** - Structured JSON logging with MDC integration
- **Metrics** - Micrometer integration with configurable cardinality
- **Tracing** - Distributed tracing with span enrichment

All context fields can be configured for observability with fine-grained control over what data appears where.

## Table of Contents

1. [Logging Integration](#logging-integration)
2. [Metrics Integration](#metrics-integration)
3. [Tracing Integration](#tracing-integration)
4. [Structured JSON Logging](#structured-json-logging)
5. [Cardinality Management](#cardinality-management)
6. [Security and Masking](#security-and-masking)
7. [Best Practices](#best-practices)
8. [Common Patterns](#common-patterns)
9. [Configuration Examples](#configuration-examples)

## Logging Integration

The framework integrates with SLF4J MDC for structured logging with automatic field inclusion.

### Basic Logging Configuration

```yaml
# Simple logging enablement
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
  observability:
    includeInLogs: true  # Adds to MDC with field name as key
```

### Custom MDC Keys

```yaml
# Custom MDC key for better log organization
userContext:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
  observability:
    logging:
      mdcKey: "user.id"  # Custom MDC key
      includeInLogs: true
```

### Nested JSON Structure

```yaml
# Intelligent grouping with dot notation
principalId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
  observability:
    logging:
      mdcKey: "principal.partyId"  # Groups under 'principal' in JSON

applicationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Application-ID"
  observability:
    logging:
      mdcKey: "principal.applicationId"  # Also groups under 'principal'
```

### Log Level Configuration

```yaml
# Custom log levels for specific fields
debugInfo:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Debug-Info"
  observability:
    logging:
      level: DEBUG  # Only appears in DEBUG logs
      mdcKey: "debug.info"
```

## Metrics Integration

Integration with Micrometer for metrics collection with context tags.

### Basic Metrics Configuration

```yaml
# Simple metrics enablement
responseTime:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Response-Time"
  observability:
    includeInMetrics: true
    metricName: "downstream_response_time_ms"
```

### Custom Metric Names

```yaml
# Custom metric names and cardinality
serviceHealth:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Health-Status"
  observability:
    metrics:
      includeInMetrics: true
      metricName: "service_health_status"
      cardinality: LOW  # Controls tag inclusion
```

### Counter Metrics

```yaml
# API request counting
apiRequests:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Application-ID"
  observability:
    metrics:
      includeInMetrics: true
      metricName: "api_requests_total"
      cardinality: MEDIUM
```

## Tracing Integration

Distributed tracing integration with automatic span enrichment.

### Basic Tracing Configuration

```yaml
# Simple tracing enablement
correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
  observability:
    includeInTracing: true
    tracingKey: "correlation.id"  # Span tag name
```

### Custom Span Tags

```yaml
# Custom span tag names
userSegment:
  upstream:
    inbound:
      source: "CLAIM"
      key: "segment"
  observability:
    tracing:
      tagName: "user.segment"  # Custom span tag
      includeInTracing: true
```

### Operation Context

```yaml
# Add operation context to traces
apiHandler:
  observability:
    tracing:
      tagName: "api.handler"
      includeInTracing: true
  # Framework automatically provides handler method info
```

## Structured JSON Logging

The framework provides comprehensive JSON logging with multiple profiles.

### Logging Profiles

- **`dev/development/local`** - Console logging with pattern format
- **`json/json-dev`** - JSON console and file logging for development
- **`prod/production`** - JSON console and async file logging for production
- **`test`** - Minimal console logging for testing
- **Default** - JSON logging when no profile is active

### JSON Structure Example

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "logger": "com.example.demo.controller.UserController",
  "message": "Processing user request",
  "thread": "http-nio-8080-exec-1",
  "service": "user-service",
  "version": "1.0.0",
  "environment": "production",
  "context": {
    "traceId": "abc123def456",
    "spanId": "789ghi012jkl",
    "requestId": "req-12345",
    "principal": {
      "partyId": "user-67890",
      "applicationId": "mobile-app"
    },
    "api": {
      "handler": "UserController/getUser"
    },
    "downstream": {
      "serviceVersion": "2.1.0",
      "responseTime": "150ms"
    },
    "contextFieldCount": 12
  }
}
```

### Custom JSON Provider

The framework includes `RequestContextJsonProvider` for intelligent field grouping:

```yaml
# Fields with dot notation automatically group
principal.partyId: "user-123"
principal.applicationId: "web-app"
api.handler: "UserController/getUser"
downstream.serviceVersion: "1.2.3"
```

Results in nested JSON:
```json
{
  "context": {
    "principal": {
      "partyId": "user-123",
      "applicationId": "web-app"
    },
    "api": {
      "handler": "UserController/getUser"
    },
    "downstream": {
      "serviceVersion": "1.2.3"
    }
  }
}
```

## Cardinality Management

Control metric cardinality to prevent metric explosion.

### Cardinality Levels

```yaml
# LOW cardinality - essential tags only
applicationId:
  observability:
    metrics:
      cardinality: LOW  # Service, application, basic context

# MEDIUM cardinality - moderate detail
userId:
  observability:
    metrics:
      cardinality: MEDIUM  # Adds user context, API endpoints

# HIGH cardinality - detailed tags
requestPath:
  observability:
    metrics:
      cardinality: HIGH  # All available context
```

### Cardinality Guidelines

- **LOW**: Service identification, application context
- **MEDIUM**: User context, API operations, basic business context
- **HIGH**: Detailed request context, debugging information

## Security and Masking

Protect sensitive data in observability outputs.

### Sensitive Field Masking

```yaml
authToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "auth.token"
  security:
    sensitive: true
    masking: "*-8"  # Show last 8 characters
```

### Security Warnings

```yaml
securityWarnings:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Security-Warning"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "security.warnings"
  security:
    sensitive: true
    masking: "***"  # Completely masked
```

## Best Practices

### 1. Use Appropriate Cardinality

```yaml
# Good - LOW cardinality for high-volume metrics
serviceRequests:
  observability:
    metrics:
      cardinality: LOW

# Avoid - HIGH cardinality for high-volume metrics
userSpecificMetric:
  observability:
    metrics:
      cardinality: HIGH  # Only for low-volume, detailed metrics
```

### 2. Organize with Dot Notation

```yaml
# Good - logical grouping
userContext:
  observability:
    logging:
      mdcKey: "user.id"

apiContext:
  observability:
    logging:
      mdcKey: "api.handler"

# Avoid - flat structure
userContext:
  observability:
    logging:
      mdcKey: "userId"
```

### 3. Choose Meaningful Names

```yaml
# Good - descriptive names
downstreamLatency:
  observability:
    metrics:
      metricName: "downstream_response_time_ms"

# Avoid - generic names
timing:
  observability:
    metrics:
      metricName: "timing"
```

### 4. Protect Sensitive Data

```yaml
# Always mark sensitive fields
personalData:
  observability:
    includeInLogs: true
  security:
    sensitive: true
    masking: "***"
```

## Common Patterns

### Pattern 1: Request Tracking

```yaml
# Complete request tracking setup
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
      generateIfAbsent: true
      generator: "UUID"
  observability:
    includeInLogs: true
    includeInTracing: true
    tracingKey: "request.id"

correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
      generateIfAbsent: true
      generator: "UUID"
  observability:
    includeInLogs: true
    includeInTracing: true
    tracingKey: "correlation.id"
```

### Pattern 2: Performance Monitoring

```yaml
# Comprehensive performance tracking
responseTime:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Response-Time"
  observability:
    includeInMetrics: true
    metricName: "downstream_response_time_ms"
    logging:
      mdcKey: "performance.responseTime"

cacheStatus:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Cache-Status"
  observability:
    includeInMetrics: true
    metricName: "cache_hit_rate"
    logging:
      mdcKey: "performance.cacheStatus"
```

### Pattern 3: User Context

```yaml
# User context for observability
userId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
  observability:
    includeInLogs: true
    includeInMetrics: true
    logging:
      mdcKey: "user.id"
    metrics:
      cardinality: MEDIUM

userRole:
  upstream:
    inbound:
      source: "CLAIM"
      key: "roles[0]"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "user.role"
```

### Pattern 4: Service Health

```yaml
# Service health monitoring
serviceHealth:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Health-Status"
  observability:
    includeInMetrics: true
    includeInLogs: true
    metricName: "service_health_status"
    logging:
      mdcKey: "service.health"

errorRate:
  downstream:
    inbound:
      source: "HEADER"
      key: "X-Error-Rate"
  observability:
    includeInMetrics: true
    metricName: "service_error_rate"
    metrics:
      cardinality: LOW
```

## Configuration Examples

### Complete Observability Setup

```yaml
request-context:
  fields:
    # Request tracking
    requestId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Request-ID"
          generateIfAbsent: true
          generator: "UUID"
      observability:
        includeInLogs: true
        includeInTracing: true
        tracingKey: "request.id"

    # User context
    userId:
      upstream:
        inbound:
          source: "CLAIM"
          key: "sub"
      observability:
        includeInLogs: true
        includeInMetrics: true
        logging:
          mdcKey: "user.id"
        metrics:
          cardinality: MEDIUM

    # Performance monitoring
    apiLatency:
      downstream:
        inbound:
          source: "HEADER"
          key: "X-Response-Time"
      observability:
        includeInMetrics: true
        includeInLogs: true
        metricName: "api_response_time_ms"
        logging:
          mdcKey: "performance.latency"

    # Framework-provided fields
    apiHandler:
      observability:
        includeInLogs: true
        includeInTracing: true
        logging:
          mdcKey: "api.handler"
        tracing:
          tagName: "api.handler"
```

## Next Steps

- [Upstream Inbound Extraction](usage-upstream-inbound-extraction.md)
- [Downstream Response Extraction](usage-downstream-response.md)
- [Upstream Response Enrichment](usage-upstream-response-enrichment.md)
- [Overview of Source Types](overview-of-source-types.md)
