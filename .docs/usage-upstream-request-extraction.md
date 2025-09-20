# Upstream Inbound Extraction Usage Guide

This guide covers how to configure and use upstream inbound extraction in the Request Context Propagation framework. Upstream inbound extraction captures values from incoming HTTP requests before they reach your application logic.

## Overview

Upstream inbound extraction runs **before Spring Security** and captures context values from incoming requests, making them available throughout the request lifecycle. This ensures context is captured even for failed authentication attempts.

## Basic Configuration

Configure upstream inbound extraction in your `request-context-config.yml`:

```yaml
fields:
  fieldName:
    upstream:
      inbound:
        source: "SOURCE_TYPE"
        key: "source_key"
```

## Supported Source Types

### 1. HEADER - Extract from HTTP Headers

```yaml
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
```

### 2. QUERY - Extract from Query Parameters

```yaml
clientVersion:
  upstream:
    inbound:
      source: "QUERY"
      key: "client_version"
      defaultValue: "1.0"
```

### 3. COOKIE - Extract from Cookies

```yaml
sessionId:
  upstream:
    inbound:
      source: "COOKIE"
      key: "JSESSIONID"
```

### 4. PATH - Extract from URL Path

```yaml
userId:
  upstream:
    inbound:
      source: "PATH"
      pattern: "/users/{userId}/profile"
```

### 5. BODY - Extract from Request Body (JSON)

```yaml
customerId:
  upstream:
    inbound:
      source: "BODY"
      key: "$.customer.id"  # JSONPath expression
```

### 6. CLAIM - Extract from JWT Claims

```yaml
userEmail:
  upstream:
    inbound:
      source: "CLAIM"
      key: "user.email"  # Supports dot notation for nested claims
```

### 7. SESSION - Extract from HTTP Session

```yaml
userId:
  upstream:
    inbound:
      source: "SESSION"
      key: "userId"
```



### 8. REMOVED - ATTRIBUTE Source Type

```yaml
# ATTRIBUTE source type removed - not reliably supported
# customData:
#   upstream:
#     inbound:
#       source: "ATTRIBUTE"  # ← REMOVED
#       key: "customAttribute"
#
# Use HEADER, CLAIM, or other reliable sources instead
```

## Advanced Features

### Default Values and Generation

```yaml
correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
      generateIfAbsent: true
      generator: "UUID"
      defaultValue: "unknown"
```

### Fallback Configuration

```yaml
apiKey:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-API-Key"
      fallback:
        source: "QUERY"
        key: "api_key"
```

### Validation and Transformation

```yaml
tenantId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Tenant-ID"
      required: true
      validationPattern: "^[a-zA-Z0-9-]+$"
      transformation: "UPPERCASE"
```

### Security Configuration

```yaml
authToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  security:
    sensitive: true
    masking: "*-8"  # Show last 8 characters
```

## Complete Example

```yaml
request-context:
  fields:
    # Basic header extraction
    requestId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Request-ID"
          generateIfAbsent: true
          generator: "UUID"
    
    # Query parameter with fallback
    clientVersion:
      upstream:
        inbound:
          source: "QUERY"
          key: "version"
          fallback:
            source: "HEADER"
            key: "X-Client-Version"
          defaultValue: "1.0"
    
    # Sensitive cookie extraction
    sessionToken:
      upstream:
        inbound:
          source: "COOKIE"
          key: "session_token"
          required: true
      security:
        sensitive: true
        masking: "*-4"
    
    # JSON body extraction
    userId:
      upstream:
        inbound:
          source: "BODY"
          key: "$.user.id"
          validationPattern: "^\\d+$"
    
    # JWT token claim
    userRole:
      upstream:
        inbound:
          source: "TOKEN"
          tokenType: "JWT"
          claimPath: "roles[0]"
```

## Key Benefits

- **Early Extraction**: Runs before Spring Security filters
- **Multiple Sources**: Support for headers, query params, cookies, path, and tokens
- **Note**: BODY source only supports downstream response extraction, not upstream request extraction
- **Fallback Support**: Try multiple sources for the same field
- **Auto-Generation**: Generate values when not present
- **Security**: Built-in masking for sensitive data
- **Validation**: Pattern-based validation with custom transformations

## Next Steps

- See [Framework Architecture](.docs/framework-architecture.md) for system overview
- Check [Testing Strategy](.docs/testing-strategy.md) for testing approaches
- Review [System-Specific Propagation](.docs/system-specific-propagation.md) for downstream usage
