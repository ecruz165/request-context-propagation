# Framework-Provided Context Values

The Request Context Propagation framework automatically provides several context values without requiring explicit configuration. These values are computed at runtime and can be used for observability, debugging, and operational insights.

## 📋 Overview

Framework-provided values fall into three categories:

1. **🔧 Auto-Generated Values** - Values generated when missing (UUIDs, timestamps, etc.)
2. **🎯 Context-Computed Values** - Values derived from Spring MVC context (API handler info)
3. **📊 Runtime Metadata** - Values computed during request processing (field counts, etc.)

## 🔧 Auto-Generated Values

### Request ID Generation

The framework can automatically generate request IDs when they're missing from incoming requests:

```yaml
request-context:
  fields:
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

**Available Generators:**
- `UUID` - Standard UUID format (default)
- `ULID` - Universally Unique Lexicographically Sortable Identifier
- `TIMESTAMP` - Current timestamp in milliseconds
- `SEQUENCE` - Auto-incrementing sequence number
- `RANDOM` - Random long value
- `NANOID` - Compact URL-safe unique ID

### Correlation ID Generation

```yaml
correlationId:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Correlation-ID"
      generateIfAbsent: true
      generator: "ULID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Correlation-ID"
```

### Trace ID Generation

```yaml
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
  observability:
    includeInTracing: true
    tracingKey: "trace.id"
```

## 🎯 Context-Computed Values

### API Handler Information

The framework automatically provides information about the Spring MVC handler method:

```yaml
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
    logging:
      mdcKey: "api.handler"
    tracing:
      tagName: "api.handler"
```

**Value Format:** `ClassName/methodName`

**Examples:**
- `UserController/getUserById`
- `OrderController/createOrder`
- `PaymentController/processPayment`

**Use Cases:**
- **Debugging**: Identify which endpoint processed a request
- **Monitoring**: Track performance by endpoint
- **Logging**: Include handler info in structured logs
- **Tracing**: Add endpoint context to distributed traces

### Configuration Requirements

For context-computed values, the field must have:
1. **Observability configuration** (logging, metrics, or tracing)
2. **No inbound source** (framework provides the value)

```yaml
# ✅ Correct - Framework will provide apiHandler value
apiHandler:
  observability:
    logging:
      mdcKey: "api.handler"

# ❌ Incorrect - Has inbound source, framework won't override
apiHandler:
  upstream:
    inbound:
      source: "HEADER"
      key: "X-Handler"
  observability:
    logging:
      mdcKey: "api.handler"
```

## 📊 Runtime Metadata

### Context Field Count

The framework automatically tracks the number of context fields:

```yaml
contextFieldCount:
  observability:
    logging:
      mdcKey: "context.field.count"
    metrics:
      cardinality: LOW
```

**Use Cases:**
- **Performance monitoring**: Track context size impact
- **Debugging**: Verify expected number of fields
- **Capacity planning**: Monitor context growth over time

## 🔄 Programmatic Context Values

### Custom Computed Fields

Add runtime-computed values programmatically:

```java
@Service
public class BusinessLogicService {
    
    @Autowired
    private RequestContextService contextService;
    
    public void processOrder(Order order) {
        // Add computed business values
        contextService.addCustomField("orderValue", 
            String.valueOf(order.getTotalAmount()));
        contextService.addCustomField("customerTier", 
            order.getCustomer().getTier());
        contextService.addCustomField("processingRegion", 
            determineRegion(order));
    }
}
```

### Expression-Based Values

Use expressions to compute values from existing context:

```yaml
fullUserContext:
  upstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-User-Context"
      valueAs: "EXPRESSION"
      value: "${userId}:${userRole}:${tenantId}"
```

## 🎛️ Configuration Patterns

### Pattern 1: Request Tracking

```yaml
request-context:
  fields:
    # Auto-generate request ID if missing
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
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-Request-ID"
      observability:
        includeInLogs: true
        includeInTracing: true
        tracingKey: "request.id"

    # Framework-provided API handler
    apiHandler:
      upstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-API-Handler"
      observability:
        logging:
          mdcKey: "api.handler"
        tracing:
          tagName: "api.handler"
```

### Pattern 2: Operational Metadata

```yaml
request-context:
  fields:
    # Framework-provided field count
    contextFieldCount:
      observability:
        logging:
          mdcKey: "context.field.count"
        metrics:
          cardinality: LOW
          metricName: "context_field_count"

    # Auto-generated correlation ID
    correlationId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Correlation-ID"
          generateIfAbsent: true
          generator: "ULID"
        outbound:
          enrichAs: "HEADER"
          key: "X-Correlation-ID"
      downstream:
        outbound:
          enrichAs: "HEADER"
          key: "X-Correlation-ID"
```

### Pattern 3: Performance Monitoring

```yaml
request-context:
  fields:
    # Processing timestamp
    processingStart:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Processing-Start"
          generateIfAbsent: true
          generator: "TIMESTAMP"
      observability:
        includeInMetrics: true
        metricName: "request_start_time"

    # API handler for performance tracking
    apiHandler:
      observability:
        includeInMetrics: true
        metrics:
          cardinality: MEDIUM
          tagName: "endpoint"
```

## 🚀 Best Practices

### 1. Use Appropriate Generators

```yaml
# ✅ Good - UUID for request IDs
requestId:
  upstream:
    inbound:
      generateIfAbsent: true
      generator: "UUID"

# ✅ Good - ULID for sortable IDs
correlationId:
  upstream:
    inbound:
      generateIfAbsent: true
      generator: "ULID"

# ✅ Good - TIMESTAMP for timing
processingStart:
  upstream:
    inbound:
      generateIfAbsent: true
      generator: "TIMESTAMP"
```

### 2. Include in Observability

```yaml
# ✅ Always include framework values in observability
apiHandler:
  observability:
    includeInLogs: true
    includeInTracing: true
    logging:
      mdcKey: "api.handler"
    tracing:
      tagName: "api.handler"
```

### 3. Propagate Important Values

```yaml
# ✅ Propagate generated IDs downstream
requestId:
  upstream:
    inbound:
      generateIfAbsent: true
      generator: "UUID"
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
  downstream:
    outbound:
      enrichAs: "HEADER"
      key: "X-Request-ID"
```

## 📈 Monitoring and Debugging

### Log Output Example

```json
{
  "@timestamp": "2024-01-15T10:30:00.000Z",
  "level": "INFO",
  "message": "Processing user request",
  "context": {
    "request.id": "550e8400-e29b-41d4-a716-446655440000",
    "api.handler": "UserController/getUserById",
    "context.field.count": 15,
    "user.id": "user123",
    "correlation.id": "01ARZ3NDEKTSV4RRFFQ69G5FAV"
  }
}
```

### Metrics Example

```
# Request count by endpoint
http_requests_total{endpoint="UserController/getUserById"} 1250
http_requests_total{endpoint="OrderController/createOrder"} 890

# Context field count distribution
context_field_count{quantile="0.5"} 12
context_field_count{quantile="0.95"} 18
context_field_count{quantile="0.99"} 22
```

## 🔍 Troubleshooting

### Common Issues

1. **apiHandler not appearing**: Ensure field has observability config but no inbound source
2. **Generated values not working**: Check `generateIfAbsent: true` is set
3. **Custom fields not in logs**: Verify field is configured in YAML with observability settings

### Debug Configuration

```yaml
# Enable debug logging for framework values
logging:
  level:
    com.example.demo.service.RequestContextService: DEBUG
```

Framework-provided values enhance observability and operational insights while reducing configuration overhead. Use them to build robust, traceable microservices architectures! 🎯
