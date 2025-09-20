# Accessing Request Context in Business Logic - Usage Guide

## 📋 Table of Contents
- [Overview](#overview)
- [Quick Start](#quick-start)
- [Access Patterns](#access-patterns)
- [Common Scenarios](#common-scenarios)
- [Advanced Usage](#advanced-usage)
- [Best Practices](#best-practices)
- [Troubleshooting](#troubleshooting)

## Overview

The Request Context Propagation Framework provides multiple ways to access context data within your business logic. Context is stored in `HttpServletRequest` attributes (not ThreadLocal) to ensure compatibility with async operations, reactive streams, and WebClient calls.

### Key Classes
- **`RequestContext`** - Core context storage object (accessed only through service)
- **`RequestContextService`** - **Primary and only** service for context operations
- **`RequestContextHolder`** - Spring's holder for request attributes

## Quick Start

### Method 1: Service Injection (Recommended)
```java
import com.example.demo.service.RequestContextService;

@Service
public class BusinessService {

    @Autowired
    private RequestContextService contextService;

    public void processOrder(Order order) {
        // Access specific fields
        String userId = contextService.getField("userId");
        String requestId = contextService.getField("requestId");

        // Get all fields
        Map<String, String> allContext = contextService.getAllFields();

        log.info("Processing order {} for user {} (request: {})",
                 order.getId(), userId, requestId);
    }
}
```

## Access Patterns

### 1. Synchronous Controller Access

```java
@RestController
@RequestMapping("/api/v1")
public class SyncController {

    @Autowired
    private RequestContextService contextService;

    @GetMapping("/products")
    public List<Product> getProducts() {
        // Get context with null safety
        Optional<RequestContext> context = contextService.getCurrentContext();

        return context.map(ctx -> {
            String tenantId = ctx.get("tenantId");
            String userId = ctx.get("userId");

            // Use context for business logic
            return productService.getProductsForTenant(tenantId, userId);
        }).orElse(Collections.emptyList());
    }

    @PostMapping("/orders")
    public Order createOrder(@RequestBody OrderRequest request) {
        // Direct field access
        String userId = contextService.getField("userId");
        String clientId = contextService.getField("clientId");

        // Add computed field to context
        contextService.addCustomField("orderType", determineOrderType(request));

        return orderService.create(request, userId, clientId);
    }
}
```

### 2. Async/Reactive Controller Access

```java
@RestController
public class ReactiveController {

    @Autowired
    private RequestContextService contextService;

    @GetMapping("/async/data")
    public Mono<DataResponse> getAsyncData() {
        // Context is preserved across async boundaries
        String requestId = contextService.getField("requestId");

        return webClient.get()
            .uri("/external/api")
            .retrieve()
            .bodyToMono(DataResponse.class)
            .doOnNext(response -> {
                // Context still accessible in reactive chain
                String userId = contextService.getField("userId");
                log.info("Received async response for user {} (request: {})",
                         userId, requestId);
            });
    }

    @GetMapping("/deferred/result")
    public DeferredResult<ResponseEntity<?>> getDeferredResult() {
        DeferredResult<ResponseEntity<?>> result = new DeferredResult<>();

        // Capture context before async processing
        String userId = contextService.getField("userId");

        CompletableFuture.runAsync(() -> {
            // Context still accessible in async thread
            String tenantId = contextService.getField("tenantId");

            ResponseEntity<?> response = processAsync(userId, tenantId);
            result.setResult(response);
        });

        return result;
    }
}
```

### 3. Service Layer Access

```java
@Service
@Slf4j
public class OrderService {

    @Autowired
    private RequestContextService contextService;

    @Transactional
    public Order processOrder(OrderRequest request) {
        // Get required context
        RequestContext context = contextService.getCurrentContextRequired();

        // Extract multiple fields
        String userId = context.get("userId");
        String tenantId = context.get("tenantId");
        String requestId = context.get("requestId");

        // Business validation using context
        validateUserPermissions(userId, tenantId, request);

        // Create order with context
        Order order = Order.builder()
            .userId(userId)
            .tenantId(tenantId)
            .requestId(requestId)
            .items(request.getItems())
            .build();

        // Add business-specific context
        contextService.addCustomField("orderId", order.getId());
        contextService.addCustomField("orderStatus", order.getStatus().name());

        return orderRepository.save(order);
    }

    public void auditOrderAccess(String orderId) {
        // Safe context access with defaults
        String userId = Optional.ofNullable(contextService.getField("userId"))
            .orElse("anonymous");
        String requestId = Optional.ofNullable(contextService.getField("requestId"))
            .orElse("unknown");

        auditService.log(AuditEvent.builder()
            .action("ORDER_ACCESS")
            .orderId(orderId)
            .userId(userId)
            .requestId(requestId)
            .timestamp(Instant.now())
            .build());
    }
}
```

### 4. Repository Layer Access

```java
@Repository
public class TenantAwareRepository {

    @Autowired
    private RequestContextService contextService;

    @PersistenceContext
    private EntityManager entityManager;

    public List<Customer> findCustomers() {
        // Apply tenant filtering automatically
        String tenantId = contextService.getField("tenantId");

        if (tenantId == null) {
            throw new IllegalStateException("Tenant ID is required");
        }

        return entityManager.createQuery(
            "SELECT c FROM Customer c WHERE c.tenantId = :tenantId",
            Customer.class)
            .setParameter("tenantId", tenantId)
            .getResultList();
    }

    @PrePersist
    public void prePersist(BaseEntity entity) {
        // Automatically set audit fields from context
        entity.setCreatedBy(contextService.getField("userId"));
        entity.setTenantId(contextService.getField("tenantId"));
        entity.setRequestId(contextService.getField("requestId"));
    }
}
```

## Common Scenarios

### Scenario 1: Multi-Tenant Data Isolation

```java
@Component
public class TenantContextInterceptor {

    @Autowired
    private RequestContextService contextService;

    @EventListener
    public void handleDataAccess(DataAccessEvent event) {
        String tenantId = contextService.getField("tenantId");

        if (tenantId == null) {
            throw new TenantRequiredException("Tenant context is required");
        }

        // Apply tenant filter to all queries
        event.addFilter("tenant_id", tenantId);
    }
}
```

### Scenario 2: Distributed Tracing

```java
@Service
public class TracingService {

    @Autowired
    private RequestContextService contextService;

    public void traceOperation(String operation) {
        Map<String, String> traceContext = new HashMap<>();
        traceContext.put("traceId", contextService.getField("traceId"));
        traceContext.put("spanId", contextService.getField("spanId"));
        traceContext.put("userId", contextService.getField("userId"));
        traceContext.put("operation", operation);

        // Send to tracing system
        tracingClient.send(traceContext);
    }
}
```

### Scenario 3: Conditional Business Logic

```java
@Service
public class PricingService {

    @Autowired
    private RequestContextService contextService;

    public BigDecimal calculatePrice(Product product) {
        String customerType = contextService.getField("customerType");
        String region = contextService.getField("region");
        String currency = contextService.getField("currency");

        BigDecimal basePrice = product.getPrice();

        // Apply customer-specific discounts
        if ("PREMIUM".equals(customerType)) {
            basePrice = basePrice.multiply(new BigDecimal("0.9")); // 10% discount
        }

        // Apply regional pricing
        if ("EU".equals(region)) {
            basePrice = convertToEuro(basePrice, currency);
        }

        return basePrice;
    }
}
```

### Scenario 4: Security and Authorization

```java
@Component
public class SecurityContextEnricher {

    @Autowired
    private RequestContextService contextService;

    public boolean hasPermission(String resource, String action) {
        String userId = contextService.getField("userId");
        String roles = contextService.getField("roles");
        String tenantId = contextService.getField("tenantId");

        // Check tenant-specific permissions
        return permissionService.checkAccess(
            userId,
            roles,
            tenantId,
            resource,
            action
        );
    }

    @PreAuthorize("@securityContextEnricher.hasPermission(#resource, 'READ')")
    public void securedMethod(String resource) {
        // Method secured with context-aware authorization
    }
}
```

## Advanced Usage

### Programmatic Field Management

```java
@Service
public class ContextManagementService {

    @Autowired
    private RequestContextService contextService;

    public void enrichContextWithBusinessData(BusinessEvent event) {
        // Add multiple custom fields
        contextService.addCustomField("eventType", event.getType());
        contextService.addCustomField("eventId", event.getId());
        contextService.addCustomField("eventTimestamp",
            event.getTimestamp().toString());

        // Update existing field
        contextService.setField("lastActivity", Instant.now().toString());

        // Check field existence
        if (contextService.hasField("sessionId")) {
            String sessionId = contextService.getField("sessionId");
            sessionService.updateActivity(sessionId);
        }

        // Remove temporary field
        contextService.removeField("tempData");
    }

    public void performContextAwareOperation() {
        // Get all configured fields
        Set<String> configuredFields = contextService.getConfiguredFieldNames();

        // Get all current context values
        Map<String, String> allFields = contextService.getAllFields();

        // Get specific field configuration
        FieldConfiguration config =
            contextService.getFieldConfiguration("userId");

        if (config != null && config.getUpstream() != null) {
            // Field is configured for upstream extraction
            processUpstreamField(config);
        }
    }
}
```

### Context in Scheduled Tasks

```java
@Component
public class ScheduledTaskWithContext {

    @Autowired
    private RequestContextService contextService;

    @Scheduled(fixedDelay = 60000)
    public void processQueuedItems() {
        List<QueuedItem> items = queueRepository.findPending();

        for (QueuedItem item : items) {
            // Restore context from queued item
            MockHttpServletRequest request = new MockHttpServletRequest();
            RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(request));

            RequestContext context = new RequestContext();
            context.put("userId", item.getUserId());
            context.put("tenantId", item.getTenantId());
            context.put("requestId", item.getOriginalRequestId());

            RequestContext.setInRequest(request, context);

            try {
                // Process with restored context
                processItem(item);
            } finally {
                // Clean up context
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }
}
```

### Context in WebClient Calls - Using RequestContextWebClientBuilder

```java
@Service
public class DownstreamService {

    @Autowired
    private RequestContextService contextService;

    @Autowired
    private RequestContextWebClientBuilder contextAwareBuilder;

    /**
     * Basic usage - Context is automatically propagated
     */
    public Mono<Response> callDownstreamBasic() {
        // Create a WebClient with automatic context propagation
        WebClient webClient = contextAwareBuilder.create()
            .baseUrl("http://downstream-service")
            .build();

        return webClient.get()
            .uri("/api/endpoint")
            .retrieve()
            .bodyToMono(Response.class)
            .doOnSuccess(response -> {
                // Context still accessible in reactive chain
                String requestId = contextService.getField("requestId");
                log.info("Downstream call completed for request: {}", requestId);
            });
    }

    /**
     * System-specific propagation - Only propagates fields for specific system
     */
    public Mono<Response> callSpecificSystem(String systemId) {
        // Create WebClient for specific system with filtered field propagation
        WebClient webClient = contextAwareBuilder.createForSystem(systemId)
            .baseUrl("http://system-" + systemId)
            .defaultHeader("X-Client-System", "my-service")
            .build();

        return webClient.get()
            .uri("/api/data")
            .retrieve()
            .bodyToMono(Response.class);
    }

    /**
     * Customized builder with additional configuration
     */
    public Mono<Response> callWithCustomConfiguration() {
        WebClient webClient = contextAwareBuilder.createWithCustomization(builder ->
            builder.baseUrl("http://custom-service")
                   .defaultHeader("Accept", "application/json")
                   .codecs(configurer -> configurer
                       .defaultCodecs()
                       .maxInMemorySize(10 * 1024 * 1024)) // 10MB
        ).build();

        return webClient.post()
            .uri("/api/process")
            .bodyValue(createRequest())
            .retrieve()
            .bodyToMono(Response.class);
    }

    /**
     * Clone existing builder and enhance with context
     */
    public Mono<Response> cloneAndEnhanceBuilder(WebClient.Builder existingBuilder) {
        // Clone an existing builder and add context propagation
        WebClient.Builder enhancedBuilder = contextAwareBuilder.cloneAndEnhance(existingBuilder)
            .defaultHeader("X-Enhanced", "true");

        return enhancedBuilder.build()
            .get()
            .uri("/api/enhanced")
            .retrieve()
            .bodyToMono(Response.class);
    }

    /**
     * Selective filter application
     */
    public Mono<Response> callWithSelectiveFilters() {
        // Create WebClient with only specific filters
        WebClient webClient = contextAwareBuilder.createWithSelectiveFilters(
            true,  // enablePropagation - propagate context downstream
            false, // enableCapture - don't capture response data
            true   // enableLogging - enable context-aware logging
        ).baseUrl("http://selective-service")
         .build();

        return webClient.get()
            .uri("/api/filtered")
            .retrieve()
            .bodyToMono(Response.class);
    }

    /**
     * Using Reactor Context for full context propagation
     */
    public Mono<Response> callWithReactorContext() {
        return RequestContextWebClientBuilder.executeWithReactorContext(
            "downstream-system",
            "http://downstream-service",
            webClient -> webClient.get()
                .uri("/api/reactive")
                .retrieve()
                .bodyToMono(Response.class)
                .flatMap(response ->
                    // Access context from Reactor Context
                    RequestContextWebClientBuilder.getContextFromReactorContext()
                        .map(context -> {
                            log.info("Processing response for user: {}",
                                context.get("userId"));
                            return response;
                        })
                ),
            contextService,
            contextAwareBuilder
        );
    }

    /**
     * Access specific field from Reactor Context
     */
    public Mono<String> getDataWithContextField() {
        WebClient webClient = contextAwareBuilder.create()
            .baseUrl("http://data-service")
            .build();

        return webClient.get()
            .uri("/api/user-data")
            .retrieve()
            .bodyToMono(String.class)
            .flatMap(data ->
                // Get specific field from Reactor Context
                RequestContextWebClientBuilder.getFieldFromReactorContext("userId")
                    .map(userId -> data + " for user: " + userId)
            );
    }

    /**
     * Propagate upstream values back through reactive chain
     */
    public Response callWithUpstreamPropagation() {
        WebClient webClient = contextAwareBuilder.createForSystem("upstream-system")
            .baseUrl("http://upstream-service")
            .build();

        Mono<Response> responseMono = webClient.post()
            .uri("/api/process")
            .bodyValue(createRequest())
            .retrieve()
            .bodyToMono(Response.class)
            .doOnNext(response -> {
                // Add computed field that should be propagated upstream
                contextService.addCustomField("processedId", response.getId());
                contextService.addCustomField("processStatus", response.getStatus());
            });

        // Block with automatic upstream value propagation
        return RequestContextWebClientBuilder.blockWithUpstreamPropagation(
            responseMono,
            contextService
        );
    }

    /**
     * Multiple WebClient calls with shared context
     */
    public Mono<CombinedResponse> callMultipleServicesWithContext() {
        // All WebClients share the same context automatically
        WebClient userService = contextAwareBuilder.createForSystem("user-service")
            .baseUrl("http://user-service")
            .build();

        WebClient orderService = contextAwareBuilder.createForSystem("order-service")
            .baseUrl("http://order-service")
            .build();

        Mono<User> userMono = userService.get()
            .uri("/api/user/current")
            .retrieve()
            .bodyToMono(User.class);

        Mono<List<Order>> ordersMono = orderService.get()
            .uri("/api/orders")
            .retrieve()
            .bodyToMono(new ParameterizedTypeReference<List<Order>>() {});

        // Combine results with context preserved
        return Mono.zip(userMono, ordersMono)
            .map(tuple -> new CombinedResponse(tuple.getT1(), tuple.getT2()))
            .doOnSuccess(combined -> {
                // Context is still available after zip
                String requestId = contextService.getField("requestId");
                log.info("Combined response ready for request: {}", requestId);
            });
    }
}
```

## Best Practices

### 1. Null Safety
Always check for null when accessing context fields:

```java
// Good - Safe access
String userId = Optional.ofNullable(contextService.getField("userId"))
    .orElseThrow(() -> new RequiredFieldException("userId is required"));

// Good - With default
String region = Optional.ofNullable(contextService.getField("region"))
    .orElse("DEFAULT");

// Bad - May cause NPE
String tenantId = contextService.getField("tenantId").toUpperCase();
```

### 2. Field Naming Conventions
Use consistent field names across your application:

```java
// Recommended field names
- userId, tenantId, requestId, traceId
- customerType, region, currency
- sessionId, correlationId
- apiVersion, clientId
```

### 3. Context Validation
Validate required context early in the request:

```java
@Component
public class ContextValidationInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                           HttpServletResponse response,
                           Object handler) {
        RequestContext context = contextService.getCurrentContext()
            .orElseThrow(() -> new MissingContextException());

        // Validate required fields
        List<String> requiredFields = Arrays.asList("userId", "tenantId");
        for (String field : requiredFields) {
            if (context.get(field) == null) {
                throw new RequiredFieldException(field + " is required");
            }
        }

        return true;
    }
}
```

### 4. Performance Considerations
Cache frequently accessed fields:

```java
@Service
public class PerformantService {

    @Autowired
    private RequestContextService contextService;

    public void processLargeDataset(List<Item> items) {
        // Cache context values outside loop
        String userId = contextService.getField("userId");
        String tenantId = contextService.getField("tenantId");

        // Use cached values in loop
        for (Item item : items) {
            processItem(item, userId, tenantId);
        }
    }
}
```

### 5. Testing with Context

```java
@SpringBootTest
class ServiceTest {

    @Autowired
    private RequestContextService contextService;

    @Autowired
    private BusinessService businessService;

    @BeforeEach
    void setupContext() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));

        RequestContext context = new RequestContext();
        context.put("userId", "test-user");
        context.put("tenantId", "test-tenant");
        context.put("requestId", "test-request");

        RequestContext.setInRequest(request, context);
    }

    @Test
    void testBusinessLogicWithContext() {
        // Context is available in service
        Result result = businessService.process();

        assertThat(result.getUserId()).isEqualTo("test-user");
        assertThat(result.getTenantId()).isEqualTo("test-tenant");
    }

    @AfterEach
    void cleanupContext() {
        RequestContextHolder.resetRequestAttributes();
    }
}
```

## Troubleshooting

### Issue: Context is null in async operations

**Solution**: Context is stored in HttpServletRequest, which is preserved across async boundaries:

```java
// Context is automatically preserved
@Async
public CompletableFuture<Result> asyncMethod() {
    // This works - context is available
    String userId = contextService.getField("userId");
    return CompletableFuture.completedFuture(new Result(userId));
}
```

### Issue: Context lost in new threads

**Solution**: For manual thread creation, pass context explicitly:

```java
public void processInNewThread() {
    // Capture context
    Map<String, String> contextData = contextService.getAllFields();

    new Thread(() -> {
        // Restore context in new thread
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(
            new ServletRequestAttributes(request));

        RequestContext newContext = new RequestContext();
        contextData.forEach(newContext::put);
        RequestContext.setInRequest(request, newContext);

        // Now context is available
        doWork();
    }).start();
}
```

### Issue: Context not available in static methods

**Solution**: RequestContext no longer provides static access methods. Use dependency injection instead:

```java
// Bad - Static access no longer available
// Optional<RequestContext> context = RequestContext.getCurrentContext();

// Good - Use dependency injection
@Component
public class UtilityClass {

    @Autowired
    private RequestContextService contextService;

    public void processWithContext() {
        Optional<RequestContext> context = contextService.getCurrentContext();
        context.ifPresent(ctx -> {
            String userId = ctx.get("userId");
            // Process with context
        });
    }
}
```

### Issue: Debugging context values

**Solution**: Use context summary for debugging:

```java
@Component
public class ContextDebugger {

    @Autowired
    private RequestContextService contextService;

    public void debugContext() {
        Optional<RequestContext> context = contextService.getCurrentContext();

        context.ifPresent(ctx -> {
            // Get formatted summary
            String summary = contextService.getContextSummary(ctx);
            log.debug("Current context: {}", summary);

            // Get all values including masked
            Map<String, String> all = ctx.getAllValues();
            Map<String, String> masked = ctx.getAllMaskedValues();

            log.debug("Raw values: {}", all);
            log.debug("Masked values: {}", masked);
        });
    }
}
```

## Summary

The Request Context Propagation Framework provides flexible and powerful ways to access context throughout your application:

1. **Service Injection** - `RequestContextService` for all context access
3. **Automatic Propagation** - Context flows through async/reactive operations
4. **WebClient Integration** - Context automatically propagates to downstream services
5. **Thread Safety** - HttpServletRequest storage ensures thread-safe access

Choose the access pattern that best fits your use case, always handle null values safely, and follow the naming conventions for consistency across your application.