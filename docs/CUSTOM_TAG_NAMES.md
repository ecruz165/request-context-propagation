# Custom Tag Names for Observability

## Overview

The Request Context Propagation framework allows you to define custom tag names for metrics, tracing, and logging. This enables you to control how values are grouped together in span structures and create logical namespaces for your observability data.

## Configuration

### Metrics Custom Tag Names

```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: "TOKEN"
          key: "sub"
      observability:
        metrics:
          enabled: true
          cardinality: LOW
          tagName: "principal.userId"  # Custom tag name instead of "userId"
```

### Tracing Custom Tag Names

```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: "TOKEN"
          key: "sub"
      observability:
        tracing:
          enabled: true
          tagName: "principal.userId"  # Custom span tag name
```

### Logging Custom MDC Keys

```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: "TOKEN"
          key: "sub"
      observability:
        logging:
          enabled: true
          mdcKey: "principal.userId"  # Custom MDC key
```

## Benefits

### 1. **Logical Grouping**
Group related fields under common namespaces:
- `principal.*` - User identity fields
- `org.*` - Organization/tenant fields  
- `request.*` - Request-specific fields
- `business.*` - Business context fields
- `tech.*` - Technical metadata fields

### 2. **Improved Observability**
- **Metrics**: Easier filtering and aggregation by namespace
- **Tracing**: Cleaner span tag organization
- **Logging**: Structured MDC keys for log analysis

### 3. **Dashboard Organization**
Create dashboards organized by logical domains:
```
Principal Dashboard: principal.userId, principal.role, principal.email
Organization Dashboard: org.tenantId, org.departmentId, org.region
Request Dashboard: request.id, request.correlationId, request.sessionId
```

### 4. **Alert Configuration**
Set up alerts based on grouped context:
```
Alert on high error rate for principal.role = "admin"
Alert on slow requests for org.tenantId = "premium-customer"
```

## Examples

### Example 1: User Identity Grouping

```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: "TOKEN"
          key: "sub"
      observability:
        metrics:
          enabled: true
          cardinality: LOW
          tagName: "principal.userId"
        tracing:
          enabled: true
          tagName: "principal.userId"
    
    userRole:
      upstream:
        inbound:
          source: "TOKEN"
          key: "role"
      observability:
        metrics:
          enabled: true
          cardinality: LOW
          tagName: "principal.role"
        tracing:
          enabled: true
          tagName: "principal.role"
```

**Result**: Span tags grouped as `principal.userId` and `principal.role`

### Example 2: Organization Context Grouping

```yaml
request-context:
  fields:
    tenantId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Tenant-ID"
      observability:
        metrics:
          enabled: true
          cardinality: LOW
          tagName: "org.tenantId"
        tracing:
          enabled: true
          tagName: "org.tenantId"
    
    departmentId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Department-ID"
      observability:
        metrics:
          enabled: true
          cardinality: MEDIUM
          tagName: "org.departmentId"
        tracing:
          enabled: true
          tagName: "org.departmentId"
```

**Result**: Span tags grouped as `org.tenantId` and `org.departmentId`

### Example 3: Request Context Grouping

```yaml
request-context:
  fields:
    requestId:
      upstream:
        inbound:
          source: "GENERATED"
          generator: "UUID"
      observability:
        metrics:
          enabled: true
          cardinality: HIGH
          tagName: "request.id"
        tracing:
          enabled: true
          tagName: "request.id"
        logging:
          enabled: true
          mdcKey: "request.id"
    
    correlationId:
      upstream:
        inbound:
          source: "HEADER"
          key: "X-Correlation-ID"
      observability:
        metrics:
          enabled: true
          cardinality: HIGH
          tagName: "request.correlationId"
        tracing:
          enabled: true
          tagName: "request.correlationId"
```

**Result**: Span tags grouped as `request.id` and `request.correlationId`

## Best Practices

### 1. **Use Consistent Namespaces**
- `principal.*` for user identity
- `org.*` for organizational context
- `request.*` for request-specific data
- `business.*` for business domain context
- `tech.*` for technical metadata

### 2. **Consider Cardinality**
- Use `LOW` cardinality for namespace identifiers
- Use `MEDIUM` cardinality for sub-categories
- Use `HIGH` cardinality for unique identifiers

### 3. **Align Across Observability Types**
Keep tag names consistent across metrics, tracing, and logging:
```yaml
tagName: "principal.userId"    # Metrics
tagName: "principal.userId"    # Tracing  
mdcKey: "principal.userId"     # Logging
```

### 4. **Use Descriptive Names**
Choose names that clearly indicate the purpose:
- ✅ `principal.userId` (clear namespace and purpose)
- ❌ `user` (ambiguous)
- ✅ `org.tenantId` (clear organizational context)
- ❌ `tenant` (unclear scope)

## Migration from Default Names

If you're migrating from default field names to custom tag names:

1. **Gradual Migration**: Add custom tag names alongside existing ones
2. **Update Dashboards**: Modify queries to use new tag names
3. **Update Alerts**: Change alert conditions to use new tag names
4. **Documentation**: Update team documentation with new naming conventions

## Troubleshooting

### Tag Names Not Appearing
- Verify `enabled: true` in the observability configuration
- Check that the field is being extracted successfully
- Ensure the tag name doesn't conflict with system tags

### Metrics Cardinality Issues
- Review cardinality levels for custom tag names
- Monitor metrics explosion with high-cardinality custom tags
- Consider using `NONE` cardinality for debugging fields

### Logging MDC Issues
- Verify MDC key configuration in logging framework
- Check log pattern includes custom MDC keys
- Ensure logging is enabled for the field
