package com.example.demo.service;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.config.props.RequestContextProperties.ClaimConfig;
import com.example.demo.config.props.RequestContextProperties.CookieConfig;
import com.example.demo.config.props.RequestContextProperties.FieldConfiguration;
import com.example.demo.config.props.RequestContextProperties.GeneratorType;
import com.example.demo.config.props.RequestContextProperties.InboundConfig;
import com.example.demo.config.props.RequestContextProperties.SessionConfig;
import com.example.demo.config.props.RequestContextProperties.TokenConfig;
import com.example.demo.config.props.RequestContextProperties.TransformationType;
import com.example.demo.util.CachedBodyHttpServletRequest;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerMapping;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
/**
 * Extractor for extracting context values from HTTP requests based on configuration.
 * This class handles the low-level extraction logic for different source types.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RequestContextExtractor {

    private final RequestContextProperties properties;
    private final AtomicLong sequenceGenerator = new AtomicLong(0);

    /**
     * Extracts a value for a field from the HTTP request
     * @param fieldName The field name to extract
     * @param request The HTTP request
     * @return The extracted value or null if not found
     */
    public String extractValue(String fieldName, HttpServletRequest request) {
        FieldConfiguration fieldConfig = properties.getFields().get(fieldName);
        if (fieldConfig == null) {
            log.debug("No configuration found for field: {}", fieldName);
            return null;
        }

        if (fieldConfig.getUpstream() == null || fieldConfig.getUpstream().getInbound() == null) {
            log.debug("No upstream inbound configuration for field: {}", fieldName);
            return null;
        }

        InboundConfig inboundConfig = fieldConfig.getUpstream().getInbound();
        return extractFromSource(inboundConfig, request, fieldName);
    }

    /**
     * Extracts value from the configured source with fallback support
     */
    private String extractFromSource(InboundConfig config, HttpServletRequest request, String fieldName) {
        String value = null;

        try {
            // Extract based on source type
            switch (config.getSource()) {
                case HEADER:
                    value = extractFromHeader(request, config);
                    break;

                case QUERY:
                    value = extractFromQuery(request, config);
                    break;

                case COOKIE:
                    value = extractFromCookie(request, config);
                    break;

                case PATH:
                    value = extractFromPath(request, config);
                    break;

                case SESSION:
                    value = extractFromSession(request, config);
                    break;

                case ATTRIBUTE:
                    value = extractFromAttribute(request, config);
                    break;

                case TOKEN:
                    value = extractFromToken(request, config);
                    break;

                case CLAIM:
                    value = extractFromClaim(config);
                    break;

                case BODY:
                    value = extractFromBody(request, config);
                    break;

                case FORM:
                    value = extractFromForm(request, config);
                    break;

                default:
                    log.warn("Unsupported source type: {} for field: {}", config.getSource(), fieldName);
            }

            // Try fallback if primary source returned null
            if (value == null && config.getFallback() != null) {
                log.debug("Primary source {} returned null for field {}, trying fallback",
                        config.getSource(), fieldName);
                value = extractFromSource(config.getFallback(), request, fieldName);
            }

            // Use default value if still null
            if (value == null && config.getDefaultValue() != null) {
                log.debug("Using default value for field {}: {}", fieldName, config.getDefaultValue());
                value = config.getDefaultValue();
            }

            // Generate if absent and configured
            if (value == null && config.isGenerateIfAbsent()) {
                value = generateValue(config.getGenerator());
                log.debug("Generated value for field {}: {}", fieldName, value);
            }

            // Apply transformation if configured
            if (value != null && config.getTransformation() != null) {
                value = applyTransformation(value, config.getTransformation(), config.getTransformExpression());
            }

            // Validate if pattern is configured
            if (value != null && config.getValidationPattern() != null) {
                if (!value.matches(config.getValidationPattern())) {
                    log.warn("Value '{}' for field {} does not match validation pattern: {}",
                            value, fieldName, config.getValidationPattern());
                    if (config.isRequired()) {
                        throw new IllegalArgumentException(
                                "Required field " + fieldName + " validation failed");
                    }
                    return null;
                }
            }

            // Check required constraint
            if (value == null && config.isRequired()) {
                throw new IllegalArgumentException("Required field " + fieldName + " is missing");
            }

        } catch (Exception e) {
            log.error("Error extracting field {}: {}", fieldName, e.getMessage());
            if (config.isRequired()) {
                throw new RuntimeException("Failed to extract required field: " + fieldName, e);
            }
        }

        return value;
    }

    /**
     * Extract from HTTP header
     */
    private String extractFromHeader(HttpServletRequest request, InboundConfig config) {
        String value = request.getHeader(config.getKey());
        if (value != null && properties.getSourceConfiguration().getHeader().isNormalizeNames()) {
            value = value.trim();
        }
        return value;
    }

    /**
     * Extract from query parameter
     */
    private String extractFromQuery(HttpServletRequest request, InboundConfig config) {
        String value = request.getParameter(config.getKey());
        if (value != null) {
            try {
                // Decode URL-encoded value
                value = URLDecoder.decode(value, StandardCharsets.UTF_8);
            } catch (Exception e) {
                log.debug("Failed to URL decode query parameter: {}", e.getMessage());
            }
        }
        return value;
    }

    /**
     * Extract from cookie
     */
    private String extractFromCookie(HttpServletRequest request, InboundConfig config) {
        if (request.getCookies() != null) {
            CookieConfig cookieConfig = properties.getSourceConfiguration().getCookie();

            for (Cookie cookie : request.getCookies()) {
                if (cookie.getName().equals(config.getKey())) {
                    // Check security constraints
                    if (cookieConfig.isHttpOnly() && !cookie.isHttpOnly()) {
                        log.debug("Cookie {} is not httpOnly, skipping", config.getKey());
                        continue;
                    }
                    if (cookieConfig.isSecure() && !cookie.getSecure()) {
                        log.debug("Cookie {} is not secure, skipping", config.getKey());
                        continue;
                    }
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    /**
     * Extract from path variable
     */
    @SuppressWarnings("unchecked")
    private String extractFromPath(HttpServletRequest request, InboundConfig config) {
        Map<String, String> pathVariables = (Map<String, String>) request.getAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

        if (pathVariables != null) {
            return pathVariables.get(config.getKey());
        }

        // Fallback to pattern matching if configured
        if (config.getPattern() != null) {
            String path = request.getRequestURI();
            // Simple pattern extraction (could use more sophisticated matching)
            return extractFromPattern(path, config.getPattern(), config.getKey());
        }

        return null;
    }

    /**
     * Extract from HTTP session
     */
    private String extractFromSession(HttpServletRequest request, InboundConfig config) {
        SessionConfig sessionConfig = properties.getSourceConfiguration().getSession();
        HttpSession session = request.getSession(sessionConfig.isCreateIfAbsent());

        if (session != null) {
            String key = sessionConfig.getAttributePrefix() + config.getKey();
            Object value = session.getAttribute(key);
            return value != null ? value.toString() : null;
        }
        return null;
    }

    /**
     * Extract from request attribute
     */
    private String extractFromAttribute(HttpServletRequest request, InboundConfig config) {
        Object value = request.getAttribute(config.getKey());
        return value != null ? value.toString() : null;
    }

    /**
     * Extract from token (JWT)
     */
    private String extractFromToken(HttpServletRequest request, InboundConfig config) {
        TokenConfig tokenConfig = properties.getSourceConfiguration().getToken();
        String authHeader = request.getHeader(tokenConfig.getHeaderName());

        if (authHeader != null && authHeader.startsWith(tokenConfig.getPrefix())) {
            String token = authHeader.substring(tokenConfig.getPrefix().length()).trim();

            // If already authenticated by Spring Security, use that
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken) {
                Jwt jwt = ((JwtAuthenticationToken) auth).getToken();
                return extractClaimFromJwt(jwt, config.getKey(), config.getClaimPath());
            }

            // Otherwise, parse token manually (if validation is disabled)
            if (!tokenConfig.isValidate()) {
                return extractUnverifiedClaim(token, config.getKey());
            }
        }
        return null;
    }

    /**
     * Extract from authenticated JWT claim
     */
    private String extractFromClaim(InboundConfig config) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth != null && auth.isAuthenticated()) {
            if (auth instanceof JwtAuthenticationToken) {
                Jwt jwt = ((JwtAuthenticationToken) auth).getToken();
                return extractClaimFromJwt(jwt, config.getKey(), config.getClaimPath());
            }
            // Add support for other authentication types as needed
        }
        return null;
    }

    /**
     * Extract claim from JWT
     */
    private String extractClaimFromJwt(Jwt jwt, String claimKey, String claimPath) {
        Map<String, Object> claims = jwt.getClaims();

        // Use claim path if specified
        if (StringUtils.hasText(claimPath)) {
            return extractNestedValue(claims, claimPath);
        }

        // Otherwise use claim key directly
        Object claim = claims.get(claimKey);
        return claim != null ? claim.toString() : null;
    }

    /**
     * Extract nested value from map using dot notation
     */
    private String extractNestedValue(Map<String, Object> map, String path) {
        if (path == null || map == null) {
            return null;
        }

        ClaimConfig claimConfig = properties.getSourceConfiguration().getClaim();
        String separator = claimConfig.getNestedSeparator();
        String[] parts = path.split("\\" + separator);

        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else if (current instanceof List && part.contains(claimConfig.getArrayIndex())) {
                // Handle array access
                String indexStr = part.replace(claimConfig.getArrayIndex(), "");
                try {
                    int index = Integer.parseInt(indexStr);
                    current = ((List<?>) current).get(index);
                } catch (Exception e) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current != null ? current.toString() : null;
    }

    /**
     * Extract unverified claim from JWT (for non-sensitive data only)
     */
    private String extractUnverifiedClaim(String token, String claimKey) {
        try {
            // Parse JWT without verification
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
                // Simple JSON parsing (could use Jackson for complex cases)
                return extractSimpleJsonValue(payload, claimKey);
            }
        } catch (Exception e) {
            log.debug("Failed to extract unverified claim: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract from request body using JSONPath expressions
     */
    private String extractFromBody(HttpServletRequest request, InboundConfig config) {
        try {
            String contentType = request.getContentType();
            if (contentType == null || !contentType.contains("application/json")) {
                log.debug("Body extraction skipped - content type is not JSON: {}", contentType);
                return null;
            }

            // Get the request body from cached wrapper
            String requestBody = getRequestBody(request);
            if (requestBody == null || requestBody.trim().isEmpty()) {
                log.debug("Body extraction skipped - request body is empty");
                return null;
            }

            // Extract value using JSONPath
            String jsonPath = config.getKey();
            if (jsonPath == null || jsonPath.trim().isEmpty()) {
                log.debug("Body extraction skipped - JSONPath key is empty");
                return null;
            }

            try {
                Object value = JsonPath.read(requestBody, jsonPath);
                if (value != null) {
                    String extractedValue = value.toString();
                    log.debug("Extracted value from body using JSONPath '{}': {}", jsonPath, extractedValue);
                    return extractedValue;
                }
            } catch (PathNotFoundException e) {
                log.debug("JSONPath '{}' not found in request body", jsonPath);
            } catch (Exception e) {
                log.warn("Error extracting value from body using JSONPath '{}': {}", jsonPath, e.getMessage());
            }

        } catch (Exception e) {
            log.warn("Error reading request body for extraction: {}", e.getMessage());
        }

        return null;
    }

    /**
     * Get the request body as a string, using cached body if available
     */
    private String getRequestBody(HttpServletRequest request) {
        // If this is a cached body request, use the cached body
        if (request instanceof CachedBodyHttpServletRequest) {
            return ((CachedBodyHttpServletRequest) request).getCachedBody();
        }

        // Fallback to reading directly (may cause issues with Spring's body processing)
        try {
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = request.getReader();

            String line;
            while ((line = bufferedReader.readLine()) != null) {
                stringBuilder.append(line);
            }

            return stringBuilder.toString();
        } catch (IOException e) {
            log.warn("Error reading request body: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract from form data
     */
    private String extractFromForm(HttpServletRequest request, InboundConfig config) {
        if ("POST".equalsIgnoreCase(request.getMethod()) &&
                request.getContentType() != null &&
                request.getContentType().contains("application/x-www-form-urlencoded")) {
            return request.getParameter(config.getKey());
        }
        return null;
    }

    /**
     * Generate value based on generator type - package-private for reuse
     */
    String generateValue(GeneratorType generator) {
        if (generator == null) {
            generator = GeneratorType.UUID;
        }

        switch (generator) {
            case UUID:
                return UUID.randomUUID().toString();

            case ULID:
                return generateULID();

            case TIMESTAMP:
                return String.valueOf(System.currentTimeMillis());

            case SEQUENCE:
                return String.valueOf(sequenceGenerator.incrementAndGet());

            case RANDOM:
                return String.valueOf(ThreadLocalRandom.current().nextLong());

            case NANOID:
                return generateNanoId();

            default:
                return UUID.randomUUID().toString();
        }
    }

    /**
     * Apply transformation to value - package-private for reuse
     */
    public String applyTransformation(String value, TransformationType transformation, String expression) {
        if (value == null) {
            return null;
        }

        try {
            switch (transformation) {
                case UPPERCASE:
                    return value.toUpperCase();

                case LOWERCASE:
                    return value.toLowerCase();

                case TRIM:
                    return value.trim();

                case BASE64_ENCODE:
                    return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));

                case BASE64_DECODE:
                    return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);

                case URL_ENCODE:
                    return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);

                case URL_DECODE:
                    return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8);

                case HASH_SHA256:
                    return hashSHA256(value);

                case CUSTOM:
                    return applyCustomTransformation(value, expression);

                default:
                    return value;
            }
        } catch (Exception e) {
            log.error("Error applying transformation {}: {}", transformation, e.getMessage());
            return value;
        }
    }

    /**
     * Generate ULID
     */
    private String generateULID() {
        // Simple ULID implementation
        long timestamp = System.currentTimeMillis();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        return timestamp + random;
    }

    /**
     * Generate Nano ID
     */
    private String generateNanoId() {
        // Simple NanoID implementation
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        StringBuilder id = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            id.append(alphabet.charAt(ThreadLocalRandom.current().nextInt(alphabet.length())));
        }
        return id.toString();
    }

    /**
     * Hash value using SHA-256 - package-private for reuse
     */
    public String hashSHA256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("SHA-256 hashing failed", e);
            return value;
        }
    }

    /**
     * Apply custom transformation using expression
     */
    private String applyCustomTransformation(String value, String expression) {
        // Simple implementation - could integrate SpEL for complex transformations
        if (expression != null && expression.contains("substring")) {
            // Example: substring(0,5)
            try {
                String params = expression.substring(expression.indexOf('(') + 1, expression.indexOf(')'));
                String[] indices = params.split(",");
                int start = Integer.parseInt(indices[0].trim());
                int end = indices.length > 1 ? Integer.parseInt(indices[1].trim()) : value.length();
                return value.substring(start, Math.min(end, value.length()));
            } catch (Exception e) {
                log.debug("Custom transformation failed: {}", e.getMessage());
            }
        }
        return value;
    }

    /**
     * Extract value from pattern
     */
    private String extractFromPattern(String path, String pattern, String key) {
        // Simple pattern matching - could use more sophisticated regex
        // Example: /api/v1/users/{userId}/orders -> extract userId
        String placeholder = "{" + key + "}";
        int index = pattern.indexOf(placeholder);
        if (index >= 0) {
            String prefix = pattern.substring(0, index);
            String suffix = pattern.substring(index + placeholder.length());

            if (path.startsWith(prefix)) {
                String remaining = path.substring(prefix.length());
                if (suffix.isEmpty()) {
                    return remaining;
                }
                int suffixIndex = remaining.indexOf(suffix);
                if (suffixIndex > 0) {
                    return remaining.substring(0, suffixIndex);
                }
            }
        }
        return null;
    }

    /**
     * Simple JSON value extraction
     */
    private String extractSimpleJsonValue(String json, String key) {
        // Very simple JSON parsing for demonstration
        // Use Jackson for production
        String searchKey = "\"" + key + "\":";
        int index = json.indexOf(searchKey);
        if (index >= 0) {
            int start = index + searchKey.length();
            int end = json.indexOf(",", start);
            if (end < 0) {
                end = json.indexOf("}", start);
            }
            if (end > start) {
                String value = json.substring(start, end).trim();
                // Remove quotes if present
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    return value.substring(1, value.length() - 1);
                }
                return value;
            }
        }
        return null;
    }
}