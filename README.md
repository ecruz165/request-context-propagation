# Request Context Propagation Framework

A **Spring Boot Starter** module for declarative, configuration-driven request context management across distributed services.

## 🚀 Quick Start

### 1. Add Dependency
```xml
<dependency>
    <groupId>com.example</groupId>
    <artifactId>request-context-propagation</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Enable Framework
```java
@SpringBootApplication
@EnableRequestContextV2
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### 3. Configure Context Fields
```yaml
request-context:
  fields:
    userId:
      upstream:
        inbound:
          source: HEADER
          key: X-User-ID
      downstream:
        outbound:
          enrichAs: HEADER
          key: X-User-ID
      observability:
        logging:
          mdcKey: "user.id"
```

That's it! The framework automatically extracts, propagates, and logs your context fields.

## 🎯 Key Features

- **📥 Multi-Source Extraction** - Headers, cookies, query params, JWT claims, path variables, JSON bodies
- **🔄 Automatic Propagation** - Context flows seamlessly to downstream services via WebClient
- **📊 Built-in Observability** - MDC logging, metrics tagging, distributed tracing
- **⚡ Async/Reactive Ready** - Full support for WebFlux, @Async, and reactive streams
- **🔒 Security First** - Runs before Spring Security, sensitive data masking, cookie security
- **⚙️ Zero Code Required** - Pure YAML configuration, no code changes needed

## 📖 Documentation

### Framework Architecture
- [**Complete Architecture Overview**](.docs/framework-architecture.md) - Comprehensive framework design and components
- [**Request Flow Diagrams**](.docs/framework-request-flow.md) - Detailed sequence diagrams of request processing
- [**Source Types Reference**](.docs/framework-source-types.md) - All supported source types and capabilities
- [**System-Specific Propagation**](.docs/framework-system-specific-propagation.md) - Configure field propagation per external system
- [**Context Provided Values**](.docs/framework-context-provided-values.md) - Framework-generated fields (requestId, apiHandler, etc.)

### Usage Guides
- [**Accessing Context in Business Logic**](.docs/usage-accessing-context-in-business-logic.md) - Complete guide with WebClient integration examples
- [**Upstream Request Extraction**](.docs/usage-upstream-request-extraction.md) - Extracting context from incoming requests
- [**Upstream Response Enrichment**](.docs/usage-upstream-response-enrichment.md) - Adding context to HTTP responses
- [**Downstream Request Enrichment**](.docs/usage-downstream-request-enrichment.md) - Propagating context to downstream services
- [**Downstream Response Extraction**](.docs/usage-downstream-response-extraction.md) - Capturing data from downstream responses
- [**Observability Integration**](.docs/usage-observability.md) - Logging, metrics, and tracing with context

### Testing & Analysis
- [**Comprehensive Testing Strategy**](.docs/framework-test-strategy.md) - Multi-layered testing approach
- [**Test Coverage Analysis**](.docs/test-coverage-analysis.md) - Coverage metrics and gap analysis
- [**Response Body Buffering**](.docs/framework-downstream-response-body-source-buffering.md) - Technical details on response capture

## 💻 Common Usage Examples

### Access Context in Controllers
```java
@Autowired
private RequestContextService contextService;

@GetMapping("/api/users")
public List<User> getUsers() {
    String tenantId = contextService.getField("tenantId");
    String userId = contextService.getField("userId");

    return userService.getUsersForTenant(tenantId);
}
```

### WebClient with Automatic Context Propagation
```java
@Autowired
private RequestContextWebClientBuilder contextAwareBuilder;

public Mono<Response> callDownstream() {
    return contextAwareBuilder.createForSystem("user-service")
        .baseUrl("http://user-service")
        .build()
        .get()
        .uri("/api/data")
        .retrieve()
        .bodyToMono(Response.class);
}
```

### Add Custom Context Fields
```java
@PostMapping("/orders")
public Order createOrder(@RequestBody OrderRequest request) {
    // Add business-specific context
    contextService.addCustomField("orderId", order.getId());
    contextService.addCustomField("orderType", request.getType());

    return orderService.process(request);
}
```

## 🏗️ Architecture Highlights

### Unified Source Handler Pattern
All source types implement a single interface with four standardized operations:
- Extract from upstream requests
- Enrich upstream responses
- Enrich downstream requests
- Extract from downstream responses

### HttpServletRequest Storage
Uses request attributes instead of ThreadLocal for:
- Async/reactive compatibility
- WebClient integration
- Thread pool safety
- Spring request scope lifecycle

### Filter Chain Architecture
1. **RequestContextFilter** (Order: -100) - Pre-authentication extraction
2. **Spring Security** - Authentication/authorization
3. **RequestContextInterceptor** - Post-auth enrichment
4. **WebClient Filters** - Downstream propagation

## 🧪 Testing

```bash
# Run all tests
./mvnw clean test

# Run specific test
./mvnw test -Dtest=SimpleRequestContextFieldsTest

# Generate coverage report
./mvnw clean test jacoco:report
# Report: target/site/jacoco/index.html
```

## 🛠️ Development

```bash
# Run with dev profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Run with JSON logging
./mvnw spring-boot:run -Dspring-boot.run.profiles=json

# Build without tests
./mvnw clean package -DskipTests
```

## 📊 Configuration Reference

```yaml
request-context:
  fields:
    fieldName:
      upstream:
        inbound:
          source: HEADER|COOKIE|QUERY|CLAIM|BODY|PATH
          key: "source-key"
          defaultValue: "optional-default"
          required: true|false
        outbound:
          enrichAs: HEADER|COOKIE|QUERY
          key: "target-key"
      downstream:
        outbound:
          enrichAs: HEADER|QUERY
          key: "downstream-key"
        inbound:
          source: HEADER|BODY
          key: "response-key"
      observability:
        logging:
          mdcKey: "mdc.key.name"
        metrics:
          tagCardinality: LOW|MEDIUM|HIGH
      security:
        sensitive: true|false
        masking: "***"
```

## 📝 License

[Your License Here]

## 🤝 Contributing

[Contributing Guidelines]

## 📞 Support

[Support Information]