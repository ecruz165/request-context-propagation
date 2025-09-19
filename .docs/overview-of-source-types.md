# Request Context Propagation Framework - Source Type Support Matrix

## 📊 Source Type Capabilities Grid

| Source Type        | Extraction | Upstream Response | Downstream Request | Downstream Response | Observability   | Notes                                                |
|--------------------|------------|-------------------|--------------------|---------------------|-----------------|------------------------------------------------------|
| **HEADER**         | ✅          | ✅                 | ✅                  | ✅                   | ✅               | Full bidirectional support                           |
| **COOKIE**         | ✅          | ✅                 | ❌                  | ❌                   | ✅               | Upstream-only (security best practice)               |
| **QUERY**          | ✅          | ❌                 | ✅                  | ❌                   | ✅               | Request-only (query params don't exist in responses) |
| **CLAIM**          | ✅          | ❌                 | ❌                  | ❌                   | ✅               | JWT claims from Spring Security context (extract-only) |
| **SESSION**        | ✅          | ❌                 | ❌                  | ❌                   | ✅               | Server-side session storage (extract-only)          |

| **PATH**           | ✅          | ❌                 | ❌                  | ❌                   | ✅               | URL path variables (request-only)                    |
| **BODY**           | ✅          | ❌                 | ❌                  | ❌                   | ✅               | JSON body extraction (request-only)                  |
| **FORM**           | ✅          | ❌                 | ❌                  | ❌                   | ✅               | Form data extraction (request-only)                  |
| **ATTRIBUTE**      | ✅          | ❌                 | ❌                  | ❌                   | ✅               | Request attributes (request-only)                    |
| **Context Fields** | ✅          | ✅                 | ✅                  | ❌                   | ✅               | Framework-provided (e.g., apiHandler)                |

## 🔍 Detailed Source Type Descriptions

### **HEADER** - HTTP Headers
- **Extraction**: ✅ From incoming request headers
- **Upstream Response**: ✅ Add to response headers
- **Downstream Request**: ✅ Propagate to downstream services
- **Downstream Response**: ✅ Extract from downstream responses
- **Use Cases**: User IDs, tenant IDs, correlation IDs, API keys
- **Example**: `X-User-ID`, `X-Tenant-ID`, `X-Request-ID`

### **COOKIE** - HTTP Cookies
- **Extraction**: ✅ From incoming request cookies
- **Upstream Response**: ✅ Set cookies in responses (security best practice)
- **Downstream Propagation**: ❌ Not propagated to downstream services (security)
- **Use Cases**: Session IDs, user preferences, client state
- **Security**: Cookies are upstream-only to prevent security issues
- **Example**: `sessionId`, `userPreferences`

### **QUERY** - Query Parameters
- **Extraction**: ✅ From URL query parameters
- **Downstream Request**: ✅ Add as query parameters
- **Limitations**: ❌ No response propagation (queries don't exist in responses)
- **Use Cases**: API versions, format preferences, pagination
- **Example**: `?version=v1&format=json`

### **CLAIM** - JWT Claims
- **Extraction**: ✅ From Spring Security JWT context
- **Propagation**: ❌ Extract-only (claims are authentication data)
- **Features**: Supports nested claims with dot notation
- **Use Cases**: User roles, permissions, nested user data
- **Example**: `user.email`, `roles`, `permissions`

### **SESSION** - HTTP Session
- **Extraction**: ✅ From HttpSession attributes
- **Propagation**: ❌ Extract-only (interface limitation - requires HttpServletRequest)
- **Use Cases**: Server-side user state, shopping cart, preferences
- **Example**: `userId`, `cartId`, `preferences`



### **PATH** - URL Path Variables
- **Extraction**: ✅ From URL path segments
- **Limitations**: ❌ Request-only (no propagation)
- **Use Cases**: Resource IDs, tenant identifiers in URLs
- **Example**: `/users/{userId}/tenants/{tenantId}`

### **BODY** - Request Body
- **Extraction**: ✅ From JSON request body using JSONPath
- **Limitations**: ❌ Request-only (no propagation)
- **Features**: Supports nested JSON extraction
- **Use Cases**: User data from POST/PUT requests
- **Example**: `$.user.id`, `$.metadata.tenantId`

### **FORM** - Form Data
- **Extraction**: ✅ From form-encoded request data
- **Limitations**: ❌ Request-only (no propagation)
- **Use Cases**: Traditional form submissions
- **Example**: `username`, `tenantId` from form fields

### **ATTRIBUTE** - Request Attributes
- **Extraction**: ✅ From HttpServletRequest attributes
- **Limitations**: ❌ Request-only (no propagation)
- **Use Cases**: Data set by filters, interceptors
- **Example**: Custom attributes set by security filters

### **Context Fields** - Framework-Provided
- **Generation**: ✅ Automatically generated by framework
- **Propagation**: ✅ Can be propagated as headers
- **Features**: No extraction needed, always available
- **Use Cases**: Handler method info, computed values
- **Example**: `apiHandler: "UserController/getUser"`

## 🎯 Enrichment Type Support

| Enrichment Type | Description | Supported Sources |
|-----------------|-------------|-------------------|
| **HEADER** | Add to HTTP headers | All propagatable sources |
| **QUERY** | Add to query parameters | All propagatable sources |
| **COOKIE** | Add to cookies | ✅ Upstream responses only (security) |
| **SESSION** | Add to session | ❌ Not supported (stateless design) |

## 🔧 Configuration Examples

### **Bidirectional Header Propagation**
```yaml
requestId:
  upstream:
    inbound:
      source: HEADER
      key: X-Request-ID
    outbound:
      enrichAs: HEADER
      key: X-Request-ID
  downstream:
    outbound:
      enrichAs: HEADER
      key: X-Request-ID
```

### **Upstream-Only Cookie Extraction**
```yaml
sessionId:
  upstream:
    inbound:
      source: COOKIE
      key: JSESSIONID
  # No downstream propagation for security
```

### **Context Field with Propagation**
```yaml
apiHandler:
  upstream:
    outbound:
      enrichAs: HEADER
      key: X-API-Handler
  downstream:
    outbound:
      enrichAs: HEADER
      key: X-API-Handler
  observability:
    logging:
      mdcKey: "api.handler"
  # Framework automatically provides value
```

## 🚀 Framework Features

### **Universal Observability**
All source types support:
- ✅ **Logging**: MDC integration with configurable keys
- ✅ **Metrics**: Configurable cardinality levels (LOW/MEDIUM/HIGH)
- ✅ **Tracing**: Span tag integration with custom tag names

### **Security Features**
- ✅ **Sensitive Data Masking**: Configurable per field
- ✅ **Required Field Validation**: Fail fast for missing required data
- ✅ **Default Values**: Fallback values for optional fields
- ✅ **Cookie Security**: Upstream-only to prevent propagation

### **Performance Optimizations**
- ✅ **Early Extraction**: Pre-authentication phase for non-auth sources
- ✅ **Lazy Evaluation**: Only extract configured fields
- ✅ **Caching**: Request-scoped context storage
- ✅ **Async Support**: HttpServletRequest storage for thread safety