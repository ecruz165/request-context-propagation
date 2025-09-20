# Upstream to Downstream Propagation Usage Guide

This guide covers how to configure and use upstream to downstream propagation in the Request Context Propagation framework. This feature automatically forwards context values from incoming requests to outgoing downstream service calls.

> **Note**: For complete source type capabilities, see [Overview of Source Types](overview-of-source-types.md)

## Overview

Upstream to downstream propagation takes values extracted from incoming HTTP requests and automatically includes them in outgoing calls to downstream services. This ensures context flows seamlessly through your service architecture.

## Flow Pattern

```
Incoming Request → Extract Values → Store in Context → Propagate to Downstream Services
```

## Supported Propagation Patterns

Only certain source types support downstream propagation. See the [Source Type Support Matrix](overview-of-source-types.md#-source-type-capabilities-grid) for complete details.

### Propagatable Sources:
- **HEADER** - Full bidirectional support
- **QUERY** - Request-only (no response propagation)
- **Context Fields** - Framework-provided values

### Extract-Only Sources (No Downstream Propagation):
- **COOKIE** - Upstream-only for security
- **CLAIM** - JWT claims (extract-only)
- **SESSION** - Server-side session (extract-only)
- **PATH** - URL path variables (request-only)
- **BODY** - Request body (request-only)
- **FORM** - Form data (request-only)
- **ATTRIBUTE** - Request attributes (request-only)

## Basic Configuration

Configure both upstream extraction and downstream propagation:

```yaml
fields:
  fieldName:
    upstream:
      inbound:
        source: "HEADER"
        key: "X-Field-Name"
    downstream:
      outbound:
        enrichAs: "HEADER"
        key: "X-Field-Name"
```

## Enrichment Types

### 1. HEADER - Propagate as HTTP Headers

```yaml
requestId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Request-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
```

### 2. QUERY - Propagate as Query Parameters

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
```

## System-Specific Propagation

Control which downstream systems receive specific fields:

```yaml
# Global field - goes to all systems
correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"
      # No extSysIds = propagates to all systems

# System-specific field
userToken:
  upstream:
    inbound:
      source: "HEADER"
      key: "Authorization"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "Authorization"
      extSysIds: ["user-service", "profile-service"]  # Only these systems
```

## WebClient Integration

### Automatic Propagation

The framework automatically propagates context when using the configured WebClient:

```java
@Service
public class UserService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public Mono<User> getUser(String userId) {
        WebClient client = webClientBuilder.build();
        
        return client.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(User.class);
        // Context automatically propagated to downstream call
    }
}
```

### System-Specific WebClients

Create WebClients for specific downstream systems:

```java
@Service
public class IntegrationService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public Mono<UserProfile> getUserProfile(String userId) {
        // Only fields configured for "profile-service" will be propagated
        WebClient profileClient = webClientBuilder.forSystem("profile-service").build();
        
        return profileClient.get()
                .uri("/profiles/{id}", userId)
                .retrieve()
                .bodyToMono(UserProfile.class);
    }
}
```

## Advanced Features

### Conditional Propagation

```yaml
debugInfo:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Debug-Mode"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Debug-Mode"
      condition: "#{context.get('environment') == 'development'}"
```

### Value Transformation

```yaml
clientVersion:
  upstream:
    inbound:
      source: "QUERY"
      key: "version"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Client-Version"
      valueAs: "EXPRESSION"
      value: "#{context.get('clientVersion').toUpperCase()}"
```

### Security and Masking

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
```

## Complete Example

```yaml
request-context:
  fields:
    # Basic HEADER propagation - same source and target
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

    # Cross-format propagation - header to query
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

    # QUERY parameter propagation
    clientVersion:
      upstream:
        inbound:
          source: "QUERY"
          key: "version"
      downstream:
        outbound:
          enrichAs: "QUERY"
          key: "version"

    # System-specific propagation
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

    # Context field propagation (framework-provided)
    apiHandler:
      upstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-API-Handler"
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-API-Handler"
      # No upstream.inbound needed - framework provides value

    # Extract-only examples (no downstream propagation)
    sessionId:
      upstream:
        inbound:
          source: "COOKIE"
          key: "JSESSIONID"
      # No downstream config - cookies don't propagate for security

    userRole:
      upstream:
        inbound:
          source: "CLAIM"
          key: "roles[0]"
      # No downstream config - claims are extract-only
```

## Usage in Code

```java
@RestController
public class ApiController {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    @GetMapping("/user/{id}")
    public Mono<UserResponse> getUser(@PathVariable String id) {
        // All configured fields automatically propagated
        WebClient client = webClientBuilder.build();
        
        return client.get()
                .uri("http://user-service/users/{id}", id)
                .retrieve()
                .bodyToMono(UserResponse.class);
    }
    
    @GetMapping("/profile/{id}")
    public Mono<ProfileResponse> getProfile(@PathVariable String id) {
        // Only profile-service specific fields propagated
        WebClient profileClient = webClientBuilder.forSystem("profile-service").build();
        
        return profileClient.get()
                .uri("http://profile-service/profiles/{id}", id)
                .retrieve()
                .bodyToMono(ProfileResponse.class);
    }
}
```

## Key Benefits

- **Automatic Propagation**: No manual header management required
- **Selective Propagation**: Only supported source types propagate (see [Source Type Matrix](overview-of-source-types.md#-source-type-capabilities-grid))
- **Cross-Format Support**: Convert between headers and query params
- **System-Specific Control**: Fine-grained control over which systems receive which fields
- **Security**: Built-in masking for sensitive data in logs
- **Extract-Only Sources**: Secure handling of cookies, claims, and session data

## Limitations

- **COOKIE**: No downstream propagation for security reasons
- **CLAIM/SESSION**: Extract-only sources (authentication/session data)
- **PATH/BODY/FORM/ATTRIBUTE**: Request-only sources (no propagation support)
- **Response Enrichment**: Only HEADER supports response enrichment

## Next Steps

- See [Overview of Source Types](overview-of-source-types.md) for complete capabilities matrix
- Check [Upstream Inbound Extraction](usage-upstream-inbound-extraction.md) for extraction setup
- Review [System-Specific Propagation](system-specific-propagation.md) for advanced targeting
- See [Framework Architecture](framework-architecture.md) for system overview
