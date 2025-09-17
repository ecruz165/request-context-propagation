package com.example.demo.config.props;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.Ordered;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for Request Context Framework
 * Maps to application.yml under "request-context" prefix
 */
@ConfigurationProperties(prefix = "request-context")
@Data
@Validated
public class RequestContextProperties {

    /**
     * Field configurations mapped by field name
     */
    @Valid
    private Map<String, FieldConfiguration> fields = new HashMap<>();

    /**
     * Global source configuration for different source types
     */
    @Valid
    private SourceConfiguration sourceConfiguration = new SourceConfiguration();

    /**
     * Filter configuration for controlling filter behavior
     */
    @Valid
    private FilterConfig filterConfig = new FilterConfig();

    /**
     * Configuration for a single context field
     */
    @Data
    public static class FieldConfiguration {
        /**
         * Upstream configuration (incoming requests and outgoing responses)
         */
        @Valid
        private StreamConfig upstream;

        /**
         * Downstream configuration (outgoing calls and incoming responses)
         */
        @Valid
        private StreamConfig downstream;

        /**
         * Observability configuration for metrics and logging
         */
        @Valid
        private ObservabilityConfig observability;

        /**
         * Security configuration for sensitive data handling
         */
        @Valid
        private SecurityConfig security;

        /**
         * Field metadata
         */
        private FieldMetadata metadata;
    }

    /**
     * Stream configuration for upstream or downstream
     */
    @Data
    public static class StreamConfig {
        /**
         * Inbound configuration - how to extract values
         */
        @Valid
        private InboundConfig inbound;

        /**
         * Outbound configuration - how to enrich requests/responses
         */
        @Valid
        private OutboundConfig outbound;
    }

    /**
     * Inbound configuration for extracting values
     */
    @Data
    public static class InboundConfig {
        /**
         * Source type for extraction
         */
        @NotNull
        private SourceType source;

        /**
         * Key to extract from source
         */
        @NotNull
        private String key;

        /**
         * URL pattern for PATH source extraction
         */
        private String pattern;

        /**
         * Token type for TOKEN source (JWT, OAuth2, Basic, etc.)
         */
        private TokenType tokenType;

        /**
         * Path for nested claim extraction (e.g., "user.email")
         */
        private String claimPath;

        /**
         * Whether to generate value if absent
         */
        private boolean generateIfAbsent = false;

        /**
         * Generator type for auto-generation
         */
        private GeneratorType generator;

        /**
         * Fallback configuration if primary extraction fails
         */
        @Valid
        private InboundConfig fallback;

        /**
         * Transformation to apply after extraction
         */
        private TransformationType transformation;

        /**
         * Custom transformation expression (SpEL)
         */
        private String transformExpression;

        /**
         * Validation pattern (regex)
         */
        private String validationPattern;

        /**
         * Whether this field is required
         */
        private boolean required = false;

        /**
         * Default value to use if extraction fails or value is absent
         */
        private String defaultValue;
    }

    /**
     * Outbound configuration for enriching requests/responses
     */
    @Data
    public static class OutboundConfig {
        /**
         * How to enrich the request/response
         */
        @NotNull
        private EnrichmentType enrichAs;

        /**
         * Key to use for enrichment
         */
        @NotNull
        private String key;

        /**
         * How to represent the value
         */
        @NotNull
        private ValueType valueAs = ValueType.STRING;

        /**
         * Expression for EXPRESSION value type (SpEL)
         */
        private String value;

        /**
         * Whether to override existing values
         */
        private boolean override = true;

        /**
         * Conditional expression for enrichment (SpEL)
         */
        private String condition;
    }

    /**
     * Observability configuration
     */
    @Data
    public static class ObservabilityConfig {
        /**
         * Metrics configuration
         */
        @Valid
        private MetricsConfig metrics = new MetricsConfig();

        /**
         * Logging configuration
         */
        @Valid
        private LoggingConfig logging = new LoggingConfig();

        /**
         * Tracing configuration
         */
        @Valid
        private TracingConfig tracing = new TracingConfig();
    }

    /**
     * Metrics configuration
     */
    @Data
    public static class MetricsConfig {
        /**
         * Cardinality level for metrics tags
         */
        private CardinalityLevel cardinality = CardinalityLevel.NONE;

        /**
         * Whether to include in metrics
         */
        private boolean enabled = false;

        /**
         * Custom metric name
         */
        private String metricName;

        /**
         * Whether to record histogram
         */
        private boolean histogram = false;
    }

    /**
     * Logging configuration
     */
    @Data
    public static class LoggingConfig {
        /**
         * Whether to include in MDC
         */
        private boolean enabled = false;

        /**
         * MDC key (defaults to field name)
         */
        private String mdcKey;

        /**
         * Log level for this field
         */
        private LogLevel level = LogLevel.INFO;
    }

    /**
     * Tracing configuration
     */
    @Data
    public static class TracingConfig {
        /**
         * Whether to include in trace spans as tags
         */
        private boolean enabled = false;

        /**
         * Span tag name (defaults to field name)
         */
        private String tagName;
    }

    /**
     * Security configuration
     */
    @Data
    public static class SecurityConfig {
        /**
         * Whether this field contains sensitive data
         */
        private boolean sensitive = false;

        /**
         * Masking pattern for sensitive data
         */
        private String masking = "***";

        /**
         * Encryption configuration
         */
        private EncryptionConfig encryption;

        /**
         * Audit configuration
         */
        private AuditConfig audit;

        /**
         * PII classification
         */
        private PIILevel piiLevel = PIILevel.NONE;
    }

    /**
     * Encryption configuration
     */
    @Data
    public static class EncryptionConfig {
        /**
         * Whether to encrypt in logs
         */
        private boolean encryptInLogs = false;

        /**
         * Whether to encrypt in storage
         */
        private boolean encryptInStorage = false;

        /**
         * Encryption algorithm
         */
        private String algorithm = "AES";
    }

    /**
     * Audit configuration
     */
    @Data
    public static class AuditConfig {
        /**
         * Whether to audit access
         */
        private boolean auditAccess = false;

        /**
         * Whether to audit changes
         */
        private boolean auditChanges = false;

        /**
         * Audit event name
         */
        private String eventName;
    }

    /**
     * Field metadata
     */
    @Data
    public static class FieldMetadata {
        /**
         * Field description
         */
        private String description;

        /**
         * Field owner/team
         */
        private String owner;

        /**
         * Field version
         */
        private String version;

        /**
         * Whether field is deprecated
         */
        private boolean deprecated = false;

        /**
         * Deprecation message
         */
        private String deprecationMessage;
    }

    /**
     * Global source configuration
     */
    @Data
    public static class SourceConfiguration {
        /**
         * Token source configuration
         */
        @Valid
        private TokenConfig token = new TokenConfig();

        /**
         * Cookie source configuration
         */
        @Valid
        private CookieConfig cookie = new CookieConfig();

        /**
         * Session source configuration
         */
        @Valid
        private SessionConfig session = new SessionConfig();

        /**
         * Claim source configuration
         */
        @Valid
        private ClaimConfig claim = new ClaimConfig();

        /**
         * Header source configuration
         */
        @Valid
        private HeaderConfig header = new HeaderConfig();
    }

    /**
     * Token configuration
     */
    @Data
    public static class TokenConfig {
        /**
         * Header name containing the token
         */
        private String headerName = "Authorization";

        /**
         * Token prefix
         */
        private String prefix = "Bearer";

        /**
         * Default token type
         */
        private String type = "JWT";

        /**
         * JWT verification key
         */
        private String verificationKey;

        /**
         * JWT issuer
         */
        private String issuer;

        /**
         * JWT audience
         */
        private String audience;

        /**
         * Whether to validate token
         */
        private boolean validate = true;

        /**
         * Cache configuration for parsed tokens
         */
        private CacheConfig cache = new CacheConfig();
    }

    /**
     * Cookie configuration
     */
    @Data
    public static class CookieConfig {
        /**
         * Whether to only read httpOnly cookies
         */
        private boolean httpOnly = true;

        /**
         * Whether to only read secure cookies
         */
        private boolean secure = true;

        /**
         * Cookie domain
         */
        private String domain;

        /**
         * Cookie path
         */
        private String path = "/";

        /**
         * SameSite attribute
         */
        private SameSite sameSite = SameSite.LAX;
    }

    /**
     * Session configuration
     */
    @Data
    public static class SessionConfig {
        /**
         * Prefix for session attributes
         */
        private String attributePrefix = "context.";

        /**
         * Whether to create session if not exists
         */
        private boolean createIfAbsent = false;

        /**
         * Session timeout in seconds
         */
        private int timeoutSeconds = 1800;
    }

    /**
     * Claim configuration
     */
    @Data
    public static class ClaimConfig {
        /**
         * Separator for nested claims
         */
        private String nestedSeparator = ".";

        /**
         * Array index indicator
         */
        private String arrayIndex = "[]";

        /**
         * Whether to flatten claims
         */
        private boolean flatten = false;
    }

    /**
     * Header configuration
     */
    @Data
    public static class HeaderConfig {
        /**
         * Whether to normalize header names
         */
        private boolean normalizeNames = true;

        /**
         * Maximum header value length
         */
        private int maxValueLength = 8192;

        /**
         * Headers to exclude from extraction
         */
        private String[] excludeHeaders = {"Cookie", "Authorization"};
    }

    /**
     * Cache configuration
     */
    @Data
    public static class CacheConfig {
        /**
         * Whether caching is enabled
         */
        private boolean enabled = true;

        /**
         * Cache TTL in seconds
         */
        private int ttlSeconds = 300;

        /**
         * Maximum cache size
         */
        private int maxSize = 1000;
    }

    /**
     * Filter configuration
     */
    @Data
    public static class FilterConfig {
        /**
         * Whether filter runs before Spring Security
         */
        private boolean runBeforeSecurity = true;

        /**
         * Filter order
         */
        private int order = Ordered.HIGHEST_PRECEDENCE + 1;

        /**
         * Whether to extract unverified claims
         */
        private boolean extractUnverifiedClaims = false;

        /**
         * Whether to wait for authentication
         */
        private boolean waitForAuthentication = false;

        /**
         * URL patterns to include
         */
        private String[] includePatterns = {"/**"};

        /**
         * URL patterns to exclude
         */
        private String[] excludePatterns = {"/health", "/metrics", "/actuator/**"};

        /**
         * Whether to propagate to async threads
         */
        private boolean propagateToAsync = true;

        /**
         * Request attribute key for context storage
         */
        private String contextAttributeKey = "request.context";
    }

    // Enums

    public enum SourceType {
        HEADER,     // HTTP headers
        TOKEN,      // JWT or other tokens
        COOKIE,     // HTTP cookies
        QUERY,      // Query parameters
        PATH,       // Path variables
        SESSION,    // HTTP session
        ATTRIBUTE,  // Request attributes
        CLAIM,      // Direct JWT claim
        BODY,       // Request body (JSON path)
        FORM        // Form data
    }

    public enum EnrichmentType {
        HEADER,     // HTTP header
        QUERY,      // Query parameter
        COOKIE,     // Cookie
        PATH,       // Path parameter
        ATTRIBUTE,  // Request attribute
        BODY        // Request body
    }

    public enum ValueType {
        STRING,
        EXPRESSION,
        JSON_ARRAY,
        JSON_OBJECT,
        NUMBER,
        BOOLEAN,
        BASE64,
        URL_ENCODED
    }

    public enum CardinalityLevel {
        NONE,       // Not included in metrics
        LOW,        // < 10 unique values
        MEDIUM,     // 10-100 unique values
        HIGH        // > 100 unique values
    }

    public enum GeneratorType {
        UUID,
        ULID,
        TIMESTAMP,
        SEQUENCE,
        RANDOM,
        NANOID
    }

    public enum TokenType {
        JWT,
        OAUTH2,
        BASIC,
        BEARER,
        CUSTOM
    }

    public enum TransformationType {
        UPPERCASE,
        LOWERCASE,
        TRIM,
        BASE64_ENCODE,
        BASE64_DECODE,
        URL_ENCODE,
        URL_DECODE,
        HASH_SHA256,
        CUSTOM
    }

    public enum LogLevel {
        TRACE,
        DEBUG,
        INFO,
        WARN,
        ERROR
    }

    public enum PIILevel {
        NONE,
        LOW,        // Non-sensitive personal data
        MEDIUM,     // Personal identifiable information
        HIGH,       // Sensitive PII (SSN, credit cards)
        CRITICAL    // Healthcare, financial data
    }

    public enum SameSite {
        STRICT,
        LAX,
        NONE
    }
}