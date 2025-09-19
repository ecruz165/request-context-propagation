# Request Context Properties Reference

This document describes all configuration options supported by the RequestContextProperties class. These properties are mapped under the `request-context` prefix in your Spring application.yml or application.properties.

- Prefix: `request-context`
- Package: `com.example.demo.config.props.RequestContextProperties`

Contents
- Top-level properties
- Field configuration (per logical field)
  - Stream (upstream/downstream)
  - Inbound extraction
  - Outbound enrichment
  - Observability (metrics, logging, tracing)
  - Security (sensitive data, encryption, audit)
  - Field metadata
- Global source configuration
  - Token
  - Cookie
  - Session
  - Claim
  - Header
- Cache configuration
- Filter configuration
- Enum reference
- Example configuration

---

Top-level
- request-context.fields: Map of fieldName -> FieldConfiguration
- request-context.source-configuration: Global defaults for input sources
- request-context.filter-config: HTTP filter behavior

FieldConfiguration
Path: request-context.fields.<fieldName>
- upstream: StreamConfig (applies to incoming HTTP requests and outgoing HTTP responses in the same flow)
- downstream: StreamConfig (applies to outgoing client calls and their responses)
- observability: ObservabilityConfig
- security: SecurityConfig
- metadata: FieldMetadata

StreamConfig
- inbound: InboundConfig
- outbound: OutboundConfig

InboundConfig
Path: request-context.fields.<fieldName>.<upstream|downstream>.inbound
- source (SourceType, required): Where to extract from
  - HEADER, TOKEN, COOKIE, QUERY, PATH, SESSION, ATTRIBUTE, CLAIM, BODY, FORM
- key (String, required): Name/key to read (e.g., header name, cookie name, claim name)
- pattern (String, optional): URL pattern used when source=PATH
- token-type (TokenType, optional): When source=TOKEN, token type
  - JWT, OAUTH2, BASIC, BEARER, CUSTOM
- claim-path (String, optional): Nested path for claims (e.g., user.email)
- generate-if-absent (boolean, default=false): Generate a value when not found
- generator (GeneratorType, optional): How to generate
  - UUID, ULID, TIMESTAMP, SEQUENCE, RANDOM, NANOID
- fallback (InboundConfig, optional): Fallback extraction when primary fails
- transformation (TransformationType, optional): Apply transformation after extraction
  - UPPERCASE, LOWERCASE, TRIM, BASE64_ENCODE, BASE64_DECODE, URL_ENCODE, URL_DECODE, HASH_SHA256, CUSTOM
- transform-expression (String, optional): SpEL used when transformation=CUSTOM
- validation-pattern (String, optional): Regex to validate extracted value
- required (boolean, default=false): If true and missing/invalid, treat as error
- default-value (String, optional): Used when extraction fails or value is absent

OutboundConfig
Path: request-context.fields.<fieldName>.<upstream|downstream>.outbound
- enrich-as (EnrichmentType, required): How to write/enrich
  - HEADER, QUERY, COOKIE, PATH, ATTRIBUTE, BODY
- key (String, required): Name/key to write (e.g., header name)
- value-as (ValueType, default=STRING): Format of the value
  - STRING, EXPRESSION, JSON_ARRAY, JSON_OBJECT, NUMBER, BOOLEAN, BASE64, URL_ENCODED
- value (String, optional): Value expression when value-as=EXPRESSION (SpEL)
- override (boolean, default=true): Whether to replace existing values
- condition (String, optional): SpEL condition to decide whether to enrich

ObservabilityConfig
Path: request-context.fields.<fieldName>.observability
- metrics: MetricsConfig
- logging: LoggingConfig
- tracing: TracingConfig

MetricsConfig
Path: request-context.fields.<fieldName>.observability.metrics
- cardinality (CardinalityLevel, default=NONE): Tag cardinality budget
  - NONE, LOW, MEDIUM, HIGH
- enabled (boolean, default=false): Include as metric tag / emit metrics
- tag-name (String, optional): Custom tag name (defaults to field name)
- metric-name (String, optional): Custom metric name
- histogram (boolean, default=false): Record histogram for numeric values

LoggingConfig
Path: request-context.fields.<fieldName>.observability.logging
- enabled (boolean, default=false): Put into MDC/log entries
- mdc-key (String, optional): Custom MDC key (defaults to field name)
- level (LogLevel, default=INFO): Minimum level to log field
  - TRACE, DEBUG, INFO, WARN, ERROR
- use-nested-json (boolean, default=true): Dot keys become nested objects in JSON logs

TracingConfig
Path: request-context.fields.<fieldName>.observability.tracing
- enabled (boolean, default=false): Add as span tag
- tag-name (String, optional): Custom span tag name (defaults to field name)
- use-nested-tags (boolean, default=true): Dot keys become nested tag structures

SecurityConfig
Path: request-context.fields.<fieldName>.security
- sensitive (boolean, default=false): Marks value as sensitive
- masking (String, default="***"): Mask pattern for logs/prints
- encryption: EncryptionConfig
- audit: AuditConfig
- pii-level (PIILevel, default=NONE): PII classification
  - NONE, LOW, MEDIUM, HIGH

EncryptionConfig
Path: request-context.fields.<fieldName>.security.encryption
- encrypt-in-logs (boolean, default=false): Encrypt when written to logs
- encrypt-in-storage (boolean, default=false): Encrypt when stored
- algorithm (String, default=AES): Encryption algorithm

AuditConfig
Path: request-context.fields.<fieldName>.security.audit
- audit-access (boolean, default=false): Audit read/access events
- audit-changes (boolean, default=false): Audit mutation events
- event-name (String, optional): Custom audit event name

FieldMetadata
Path: request-context.fields.<fieldName>.metadata
- description (String, optional): Human-friendly description
- owner (String, optional): Owning team/contact
- version (String, optional): Field version/contract
- deprecated (boolean, default=false): Marks field as deprecated
- deprecation-message (String, optional): Message with migration guidance

Global SourceConfiguration
Path: request-context.source-configuration
- token: TokenConfig
- cookie: CookieConfig
- session: SessionConfig
- claim: ClaimConfig
- header: HeaderConfig

TokenConfig
Path: request-context.source-configuration.token
- header-name (String, default=Authorization): Header that carries token
- prefix (String, default=Bearer): Token prefix
- type (String, default=JWT): Default token type string
- verification-key (String, optional): JWT verification key
- issuer (String, optional): Expected JWT issuer
- audience (String, optional): Expected JWT audience
- validate (boolean, default=true): Validate token when extracting
- cache: CacheConfig (for parsed tokens)

CookieConfig
Path: request-context.source-configuration.cookie
- http-only (boolean, default=true): Only read HttpOnly cookies
- secure (boolean, default=true): Only read Secure cookies
- domain (String, optional): Cookie domain restriction
- path (String, default=/): Cookie path restriction
- same-site (SameSite, default=LAX): SameSite attribute
  - LAX, STRICT, NONE

SessionConfig
Path: request-context.source-configuration.session
- attribute-prefix (String, default="context."): Prefix for session attributes
- create-if-absent (boolean, default=false): Create session if missing
- timeout-seconds (int, default=1800): Session timeout

ClaimConfig
Path: request-context.source-configuration.claim
- nested-separator (String, default=.): Separator for nested claims
- array-index (String, default=[]): Marker used for array segment
- flatten (boolean, default=false): Flatten nested claims

HeaderConfig
Path: request-context.source-configuration.header
- normalize-names (boolean, default=true): Normalize header names
- max-value-length (int, default=8192): Truncate or limit header values
- exclude-headers (String[], default=["Cookie","Authorization"]): Headers to ignore

CacheConfig
Path: request-context.source-configuration.token.cache (also reusable elsewhere)
- enabled (boolean, default=true)
- ttl-seconds (int, default=300)
- max-size (int, default=1000)

FilterConfig
Path: request-context.filter-config
- run-before-security (boolean, default=true): Register filter before Spring Security chain
- order (int, default=Ordered.HIGHEST_PRECEDENCE + 1): Filter order
- extract-unverified-claims (boolean, default=false): Allow unverified claims
- wait-for-authentication (boolean, default=false): Defer until authentication available
- include-patterns (String[], default=["/**"]): URL patterns to include
- exclude-patterns (String[], default=["/health","/metrics","/actuator/**"]): URL patterns to skip
- propagate-to-async (boolean, default=true): Propagate context to async threads
- context-attribute-key (String, default="request.context"): Request attribute for context storage

Enum Reference
- SourceType: HEADER, TOKEN, COOKIE, QUERY, PATH, SESSION, ATTRIBUTE, CLAIM, BODY, FORM
- EnrichmentType: HEADER, QUERY, COOKIE, PATH, ATTRIBUTE, BODY
- ValueType: STRING, EXPRESSION, JSON_ARRAY, JSON_OBJECT, NUMBER, BOOLEAN, BASE64, URL_ENCODED
- CardinalityLevel: NONE, LOW, MEDIUM, HIGH
- GeneratorType: UUID, ULID, TIMESTAMP, SEQUENCE, RANDOM, NANOID
- TokenType: JWT, OAUTH2, BASIC, BEARER, CUSTOM
- TransformationType: UPPERCASE, LOWERCASE, TRIM, BASE64_ENCODE, BASE64_DECODE, URL_ENCODE, URL_DECODE, HASH_SHA256, CUSTOM
- LogLevel: TRACE, DEBUG, INFO, WARN, ERROR
- PIILevel: NONE, LOW, MEDIUM, HIGH
- SameSite: LAX, STRICT, NONE

Example YAML
request-context:
  filter-config:
    run-before-security: true
    include-patterns: ["/**"]
    exclude-patterns: ["/health","/metrics","/actuator/**"]

  source-configuration:
    token:
      header-name: Authorization
      prefix: Bearer
      type: JWT
      validate: true
      cache:
        enabled: true
        ttl-seconds: 300
        max-size: 1000
    cookie:
      http-only: true
      secure: true
      same-site: LAX
    session:
      attribute-prefix: "context."
      timeout-seconds: 1800
    claim:
      nested-separator: "."
      array-index: "[]"
      flatten: false
    header:
      normalize-names: true
      max-value-length: 8192

  fields:
    correlationId:
      upstream:
        inbound:
          source: HEADER
          key: X-Correlation-Id
          generate-if-absent: true
          generator: UUID
          required: false
        outbound:
          enrich-as: HEADER
          key: X-Correlation-Id
          value-as: STRING
          override: true
      observability:
        logging:
          enabled: true
          mdc-key: correlationId
          level: INFO
        tracing:
          enabled: true
          tag-name: correlation.id
          use-nested-tags: true
        metrics:
          enabled: true
          cardinality: LOW
          tag-name: correlation_id
      security:
        sensitive: false
        pii-level: NONE
      metadata:
        description: "Request correlation identifier"
        owner: "platform-team"
        version: "1.0"

Notes
- All properties use the `request-context` prefix.
- Property names shown in kebab-case reflect Spring Boot relaxed binding for YAML/properties.
- Defaults listed are derived from code; adjust as needed for your environment.
