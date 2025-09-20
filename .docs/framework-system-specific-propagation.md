# System-Specific Field Propagation

## Overview

The Request Context Propagation framework now supports **system-specific field propagation** using the `extSysIds` configuration. This allows you to control which external systems receive specific context fields, rather than propagating all fields to all WebClients.

## Default Behavior

**By default, all configured fields are propagated to ALL WebClients** (existing behavior).

## New Feature: extSysIds

You can now specify which external systems should receive specific fields using the `extSysIds` property in the downstream outbound configuration.

### Configuration Examples

#### 1. Global Field (Default Behavior)
```yaml
request-context:
  fields:
    globalApiKey:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-API-Key"
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-API-Key"
          # No extSysIds specified = propagate to ALL systems
```

#### 2. System-Specific Field
```yaml
request-context:
  fields:
    userServiceToken:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-User-Token"
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-User-Token"
          extSysIds: ["user-service"]  # Only propagate to user-service
```

#### 3. Multi-System Field
```yaml
request-context:
  fields:
    profilePaymentAuth:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Profile-Payment-Auth"
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-Profile-Payment-Auth"
          extSysIds: ["profile-service", "payment-service"]  # Only these two systems
```

## Usage with WebClient

### Creating System-Specific WebClients

```java
@Service
public class MyService {
    
    @Autowired
    private RequestContextWebClientBuilder webClientBuilder;
    
    public void makeSystemSpecificCalls() {
        // Create WebClients with specific extSysIds
        WebClient userServiceClient = webClientBuilder.createForSystem("user-service")
                .baseUrl("https://user-api.company.com")
                .build();
        
        WebClient profileServiceClient = webClientBuilder.createForSystem("profile-service")
                .baseUrl("https://profile-api.company.com")
                .build();
        
        WebClient paymentServiceClient = webClientBuilder.createForSystem("payment-service")
                .baseUrl("https://payment-api.company.com")
                .build();
        
        // Each WebClient will only receive fields configured for its extSysId
        // - userServiceClient gets: globalApiKey + userServiceToken
        // - profileServiceClient gets: globalApiKey + profilePaymentAuth
        // - paymentServiceClient gets: globalApiKey + profilePaymentAuth
    }
}
```

## Propagation Logic

### Field Propagation Rules

1. **No `extSysIds` specified** (null or empty list):
   - Field is propagated to **ALL WebClients** (default behavior)

2. **`extSysIds` specified** with system list:
   - Field is **ONLY** propagated to WebClients with matching `extSysId`

### Example Scenario

Given this configuration:
```yaml
request-context:
  fields:
    # Global field - goes to all systems
    apiKey:
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-API-Key"
          # No extSysIds = all systems
    
    # User-specific field
    userToken:
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-User-Token"
          extSysIds: ["user-service"]
    
    # Profile/Payment field
    profileAuth:
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-Profile-Auth"
          extSysIds: ["profile-service", "payment-service"]
```

**Propagation Results:**

| WebClient System | Receives Headers |
|------------------|------------------|
| `user-service` | `X-API-Key`, `X-User-Token` |
| `profile-service` | `X-API-Key`, `X-Profile-Auth` |
| `payment-service` | `X-API-Key`, `X-Profile-Auth` |
| `notification-service` | `X-API-Key` only |

## Benefits

1. **Security**: Sensitive tokens only go to systems that need them
2. **Performance**: Reduced header overhead for systems that don't need specific fields
3. **Compliance**: Better control over data flow between systems
4. **Flexibility**: Mix global and system-specific fields as needed

## Backward Compatibility

This feature is **100% backward compatible**:
- Existing configurations without `extSysIds` continue to work exactly as before
- All fields without `extSysIds` are propagated to all systems (current behavior)
- No changes required to existing code

## Testing

The feature includes comprehensive unit tests demonstrating:
- Default propagation behavior (all systems)
- System-specific propagation
- Multi-system propagation
- Configuration validation

See `SystemSpecificPropagationUnitTest.java` for examples.
