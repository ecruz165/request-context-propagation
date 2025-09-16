# Request Context Propagation

This project demonstrates an early extraction filter that runs **BEFORE** Spring Security to capture request context information, even for failed authentication attempts.

## Early Extraction Filter

The `EarlyExtractionFilter` is designed to:

- **Run before Spring Security filters** to ensure data capture even for authentication failures
- **Extract request metadata** like client IP, User-Agent, request ID, and timestamps
- **Parse JWT tokens** (structure only, without signature validation) for early logging
- **Store context data** in request attributes for later use
- **Handle errors gracefully** without breaking the request flow

### Key Features

1. **High Priority Ordering**: Runs with `Ordered.HIGHEST_PRECEDENCE + 10` to execute before Spring Security
2. **Client IP Detection**: Supports X-Forwarded-For, X-Real-IP headers with fallback to remote address
3. **JWT Structure Parsing**: Basic JWT token structure validation without signature verification
4. **Request Context Storage**: Stores extracted data in request attributes for downstream use
5. **Comprehensive Logging**: Configurable logging levels for debugging and monitoring

### Filter Registration

The filter is registered in two ways for maximum compatibility:

1. **Component Registration**: Automatic Spring component scanning with `@Component`
2. **Explicit Registration**: Manual `FilterRegistrationBean` configuration for precise ordering

## Test Coverage

This project uses JaCoCo for comprehensive test coverage reporting.

### Running Coverage Reports

#### Quick Start
```bash
# Run tests and generate coverage reports
./run-coverage.sh
```

#### Manual Commands
```bash
# Clean and run tests with coverage
./mvnw clean test

# Run integration tests and generate merged coverage
./mvnw verify

# Generate site reports (includes coverage)
./mvnw site
```

### Coverage Report Locations

After running tests, coverage reports are available at:

- **Unit Test Coverage**: `target/site/jacoco/index.html`
- **Integration Test Coverage**: `target/site/jacoco-it/index.html`
- **Merged Coverage**: `target/site/jacoco-merged/index.html`
- **Site Reports**: `target/site/index.html`

### Viewing Reports

Open the HTML reports in your browser:
```bash
# View unit test coverage
open target/site/jacoco/index.html

# View merged coverage (recommended)
open target/site/jacoco-merged/index.html
```

### Coverage Configuration

The JaCoCo plugin is configured with:

- **Minimum Coverage**: 50% instruction and class coverage
- **Excluded Classes**: Application main class and configuration classes
- **Report Formats**: HTML, XML, and CSV
- **Integration**: Unit tests, integration tests, and merged reports

### CI/CD Integration

The project includes GitHub Actions workflow for:

- ✅ Automated test execution
- 📊 Coverage report generation
- 📤 Coverage upload to Codecov
- 💬 PR coverage comments
- 📁 Artifact storage for reports

### Coverage Thresholds

Current coverage requirements:
- **Overall Coverage**: 50% minimum
- **Changed Files**: 60% minimum (for PRs)
- **Instruction Coverage**: 50% minimum
- **Class Coverage**: 50% minimum

To adjust thresholds, modify the JaCoCo plugin configuration in `pom.xml`.

## JSON Structured Logging

This project supports both traditional pattern-based logging and structured JSON logging using Logstash encoder.

### Logging Profiles

#### JSON Logs Profile
```bash
# Run with JSON structured logging
./run-with-json-logs.sh json-logs

# Or manually
./mvnw spring-boot:run -Dspring-boot.run.profiles=json-logs
```

#### Development Profile
```bash
# Run with debug logging
./run-with-json-logs.sh dev
```

#### Production Profile
```bash
# Run with production JSON logging
./run-with-json-logs.sh prod
```

### JSON Log Format

JSON logs include structured data with MDC context:

```json
{
  "@timestamp": "2024-01-15T10:30:45.123Z",
  "level": "INFO",
  "logger": "com.example.demo.filter.RequestContextExtractionFilter",
  "thread": "http-nio-8080-exec-1",
  "message": "Early extraction captured context",
  "mdc": {
    "request_id": "req-12345-abcdef",
    "user": "john.doe@example.com",
    "tenant": "tenant-123",
    "applicationId": "test-app",
    "clientId": "test-client"
  },
  "service": "request-context-propagation",
  "version": "0.0.1-SNAPSHOT"
}
```

### MDC Context Fields

The application automatically populates MDC with:

- `request_id` - Unique request identifier
- `user` - Authenticated user identifier
- `tenant` - Tenant/organization identifier
- `applicationId` - Application identifier from headers
- `clientId` - Client identifier from headers

### Log Configuration Files

- **Pattern Logging**: `application.yaml` logging section
- **JSON Logging**: `logback-spring.xml` with Logstash encoder
- **Profile-specific**: Different configurations per environment

### Log Output Locations

- **Console**: Structured JSON or pattern format
- **File**: `logs/application.log` (pattern) or `logs/application.json` (JSON)
- **Rotation**: Daily rotation with 30-day retention and 1GB size cap

### Testing JSON Logging

```bash
# Run logging tests
./mvnw test -Dtest=JsonLoggingTest

# View logs with different profiles
./run-with-json-logs.sh json-logs
```

### Security Configuration

The project includes a basic Spring Security configuration that:

- Allows public endpoints (`/public/**`, `/actuator/**`)
- Requires authentication for protected endpoints
- Configures JWT resource server (placeholder)
- Demonstrates filter ordering with Spring Security

## Usage Examples

### Public Endpoint (No Authentication Required)
```bash
curl -H "User-Agent: MyApp/1.0" \
     -H "X-Forwarded-For: 192.168.1.100" \
     http://localhost:8080/api/public/test
```

### Protected Endpoint (Authentication Required)
```bash
curl -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." \
     http://localhost:8080/api/protected/test
```

### Response Format
```json
{
  "message": "This is a public endpoint",
  "timestamp": 1703123456789,
  "earlyExtraction": {
    "requestId": "req-1703123456789-123",
    "clientIp": "192.168.1.100",
    "userAgent": "MyApp/1.0",
    "extractionTimestamp": 1703123456789
  }
}
```

## Testing

The project includes comprehensive tests:

- **Unit Tests**: `EarlyExtractionFilterTest` - Tests filter logic in isolation
- **Integration Tests**: `EarlyExtractionIntegrationTest` - Tests complete request flow

Run tests with:
```bash
./mvnw test
```

## Configuration

### Logging Levels

Configure logging in `application.properties`:
```properties
# Enable debug logging for the filter
logging.level.com.example.demo.filter.RequestContextExtractionFilter=DEBUG

# General application logging
logging.level.com.example.demo=INFO
```

### Security Settings

The security configuration can be customized in `SecurityConfig.java`:
- JWT decoder configuration
- Authorization rules
- Session management
- CSRF settings

## Production Considerations

1. **Sensitive Data**: Be careful with header logging in production
2. **Performance**: Consider the impact of extensive logging on performance
3. **JWT Parsing**: Add proper JWT library for claim extraction if needed
4. **Error Handling**: Monitor filter exceptions and their impact
5. **Security**: Ensure the filter doesn't bypass security controls

## Dependencies

- Spring Boot 3.5.5
- Spring Security
- Spring Web
- Micrometer Tracing
- Lombok
- JUnit 5
