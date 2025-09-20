# Downstream Request Propagation Usage Guide

This guide covers how to propagate context fields to downstream service requests in the Request Context Propagation framework.

## Overview

The framework supports propagating context fields to downstream service requests using three enrichment types:
- **HEADER** - Add HTTP headers to downstream requests
- **QUERY** - Add query parameters to downstream requests  
- **COOKIE** - Add cookies to upstream responses (not downstream requests)

## Table of Contents

1. [Header Propagation](#header-propagation)
2. [Query Parameter Propagation](#query-parameter-propagation)
3. [System-Specific Propagation](#system-specific-propagation)
4. [WebClient Integration](#webclient-integration)
5. [Cross-Format Propagation](#cross-format-propagation)
6. [Security and Masking](#security-and-masking)
7. [Best Practices](#best-practices)
8. [Common Patterns](#common-patterns)
9. [Troubleshooting](#troubleshooting)

## Header Propagation

Propagate context fields as HTTP headers to downstream services.

### Basic Header Propagation

```yaml
# Extract from upstream header and propagate to downstream header
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
      generateIfAbsent: true
      generator: "UUID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
  observability:
    includeInLogs: true
```

### Header Propagation with Different Keys

```yaml
# Extract from one header, propagate with different name
clientVersion:
  upstream:
    inbound:
      source: "HEADER"
      key: "User-Agent"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"
  observability:
    includeInLogs: true
```

### Framework-Provided Header Propagation

```yaml
# Propagate framework-computed values
apiHandler:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Handler"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Handler"
  observability:
    includeInLogs: true
  # No upstream.inbound needed - framework provides value
```

## Query Parameter Propagation

Propagate context fields as query parameters to downstream services.

### Basic Query Propagation

```yaml
# Extract from query parameter and propagate to downstream query
version:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "version"
  observability:
    includeInLogs: true
```

### Query Propagation with Default Values

```yaml
# Query parameter with fallback value
format:
  upstream:
    inbound:
      source: "QUERY"
      key: "format"
      defaultValue: "json"
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "format"
  observability:
    includeInLogs: true
```

## System-Specific Propagation

Control which downstream systems receive specific fields using `extSysIds`.

### Global Propagation (Default)

```yaml
# Propagates to ALL downstream systems
globalApiKey:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-API-Key"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-API-Key"
      # No extSysIds = propagates to all systems
  observability:
    includeInLogs: true
```

### Single System Propagation

```yaml
# Propagates only to user-service
userToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-User-Token"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-User-Token"
      extSysIds: ["user-service"]  # Only this system
  observability:
    includeInLogs: true
```

### Multi-System Propagation

```yaml
# Propagates to specific systems only
profilePaymentAuth:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Profile-Payment-Auth"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Profile-Payment-Auth"
      extSysIds: ["profile-service", "payment-service"]
  security:
    sensitive: true
    masking: "*-8"
```

## WebClient Integration

The framework automatically integrates with Spring WebClient for downstream propagation.

### Basic WebClient Usage

```java
@Service
public class MyService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public Mono<String> callDownstreamService() {
        WebClient webClient = webClientBuilder.build();
        
        return webClient.get()
                .uri("http://downstream-service/api/data")
                .retrieve()
                .bodyToMono(String.class);
        // Context headers automatically added
    }
}
```

### System-Specific WebClient

```java
@Service
public class UserService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public Mono<User> getUserData(String userId) {
        // Create WebClient for specific system
        WebClient webClient = webClientBuilder
                .forSystem("user-service")  // Only user-service fields propagated
                .build();
        
        return webClient.get()
                .uri("http://user-service/users/{id}", userId)
                .retrieve()
                .bodyToMono(User.class);
    }
}
```

### Multiple System Calls

```java
@Service
public class AggregatorService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public Mono<CombinedData> getCombinedData(String id) {
        WebClient userClient = webClientBuilder
                .forSystem("user-service")
                .build();
                
        WebClient profileClient = webClientBuilder
                .forSystem("profile-service")
                .build();
        
        Mono<User> userMono = userClient.get()
                .uri("http://user-service/users/{id}", id)
                .retrieve()
                .bodyToMono(User.class);
                
        Mono<Profile> profileMono = profileClient.get()
                .uri("http://profile-service/profiles/{id}", id)
                .retrieve()
                .bodyToMono(Profile.class);
        
        return Mono.zip(userMono, profileMono)
                .map(tuple -> new CombinedData(tuple.getT1(), tuple.getT2()));
    }
}
```

## Cross-Format Propagation

Propagate fields between different formats (header to query, query to header, etc.).

### Header to Query Propagation

```yaml
# Extract from header, propagate as query parameter
apiKey:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-API-Key"
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "api_key"
  security:
    sensitive: true
    masking: "*-4"
```

### Query to Header Propagation

```yaml
# Extract from query, propagate as header
clientVersion:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"
  observability:
    includeInLogs: true
```

### Claim to Header Propagation

```yaml
# Extract from JWT claim, propagate as header
userId:
  upstream:
    inbound:
      source: "CLAIM"
      key: "sub"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-User-ID"
  observability:
    includeInLogs: true
```

## Security and Masking

Protect sensitive data during propagation with proper security settings.

### Sensitive Data Masking

```yaml
authToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "Authorization"
  security:
    sensitive: true
    masking: "*-8"  # Mask all but last 8 characters in logs
  observability:
    includeInLogs: true
```

### API Key Protection

```yaml
apiKey:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-API-Key"
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "api_key"
      extSysIds: ["external-api"]  # Limit propagation
  security:
    sensitive: true
    masking: "*-4"
```

## Best Practices

### 1. Use System-Specific Propagation

```yaml
# Good - limit sensitive data to specific systems
userAuth:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "Authorization"
      extSysIds: ["user-service", "profile-service"]

# Avoid - sending sensitive data to all systems
# (omit extSysIds only for truly global fields)
```

### 2. Choose Appropriate Enrichment Types

```yaml
# Headers for metadata and authentication
correlationId:
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"

# Query parameters for API parameters
version:
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "version"
```

### 3. Implement Proper Security

```yaml
# Always mark sensitive fields
sensitiveData:
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Sensitive-Data"
  security:
    sensitive: true
    masking: "***"
```

### 4. Use Meaningful Key Names

```yaml
# Good - clear, descriptive names
clientVersion:
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"

# Avoid - generic or unclear names
data:
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Data"
```

## Common Patterns

### Pattern 1: Request Correlation

```yaml
# Ensure request traceability across services
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
      generateIfAbsent: true
      generator: "UUID"
  downstream:
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
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"
```

### Pattern 2: Authentication Propagation

```yaml
# Forward authentication context
userToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "Authorization"
      extSysIds: ["user-service", "profile-service"]
  security:
    sensitive: true
    masking: "*-8"
```

### Pattern 3: Client Context

```yaml
# Propagate client information
clientVersion:
  upstream:
    inbound:
      source: "HEADER"
      key: "User-Agent"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"

clientId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Client-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-ID"
```

### Pattern 4: API Versioning

```yaml
# Propagate API version information
apiVersion:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
      defaultValue: "v1"
  downstream:
    outbound:
      enrichAs: "QUERY"
      key: "version"
```

## Troubleshooting

### Common Issues

1. **Headers Not Propagating**
   - Check `enrichAs: "HEADER"` configuration
   - Verify WebClient is created with RequestContextWebClientBuilder
   - Ensure field has a value in the context

2. **System-Specific Propagation Not Working**
   - Verify `extSysIds` array syntax
   - Check WebClient is created with `.forSystem("system-name")`
   - Ensure system name matches exactly

3. **Query Parameters Not Added**
   - Check `enrichAs: "QUERY"` configuration
   - Verify URL encoding for special characters
   - Check for URL length limits

4. **Sensitive Data Exposed**
   - Add `security.sensitive: true`
   - Configure appropriate masking pattern
   - Review log output for leaks

### Debug Configuration

```yaml
# Enable debug logging
debugField:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Debug"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Debug"
  observability:
    includeInLogs: true
    logging:
      mdcKey: "debug.field"
```

### Validation

Check the integration tests for working examples:
- `SimpleRequestContextFieldsTest` - Basic propagation patterns
- `ConcurrentZipBlockUpstreamTest` - System-specific propagation
- `RequestContextWebClientPropagationFilterTest` - WebClient integration

## Next Steps

- [Downstream Response Extraction](usage-downstream-response.md)
- [Upstream Inbound Extraction](usage-upstream-inbound-extraction.md)
- [System-Specific Propagation](system-specific-propagation.md)
- [Overview of Source Types](overview-of-source-types.md)
