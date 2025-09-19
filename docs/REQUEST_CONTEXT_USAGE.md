## Usage Examples: Request Context Propagation Framework

This guide shows how to configure, access, and propagate request context across inbound and outbound calls in this Spring MVC app.

### 1) Configure fields (application.yaml)
<augment_code_snippet mode="EXCERPT">
````yaml
request-context:
  fields:
    userId:
      upstream: { inbound: { source: CLAIM, key: sub } }
      downstream: { outbound: { enrichAs: HEADER, key: X-User-Id } }
      security: { sensitive: true, masking: "***" }
````
</augment_code_snippet>

### 2) WebClient with propagation + logging filters
<augment_code_snippet mode="EXCERPT">
````java
@Bean
WebClient webClient(RequestContextWebClientPropagationFilter p,
                    RequestContextWebClientLoggingFilter l) {
  return WebClient.builder().filter(l.createFilter()).filter(p.createFilter()).build();
}
````
</augment_code_snippet>

### 3) Access context in a controller/service
<augment_code_snippet mode="EXCERPT">
````java
@Autowired RequestContextService ctxService;
@GetMapping("/whoami")
public Map<String,String> who(HttpServletRequest req){
  RequestContext ctx = ctxService.getCurrentContext(req);
  return Map.of("userId", ctx.getMaskedOrOriginal("userId"));
}
````
</augment_code_snippet>

### 4) Outbound call automatically propagates headers
<augment_code_snippet mode="EXCERPT">
````java
@Autowired WebClient webClient;
public Mono<Map<String,Object>> fetch(){
  return webClient.get().uri("/downstream").retrieve().bodyToMono(Map.class);
}
````
</augment_code_snippet>

What happens:
- RequestContextFilter initializes context pre-security (requestId, headers, claims, etc.)
- RequestContextService enriches/updates context and MDC for logs
- WebClient filters add headers (propagation) and structured logs (masked values)

### 5) Declarative propagation rules
- Configure per-field outbound method: HEADER, QUERY, COOKIE, ATTRIBUTE
- Sensitive fields are masked in logs/traces via MaskingHelper
- MDC keys set via `fields.<name>.observability.logging.mdcKey`

### 6) Testing tips (API-first)
<augment_code_snippet mode="EXCERPT">
````java
// Using WireMock: assert header X-User-Id is present
stubFor(get(urlEqualTo("/downstream"))
  .withHeader("X-User-Id", matching(".+"))
  .willReturn(aResponse().withStatus(200)));
````
</augment_code_snippet>

### 7) Troubleshooting
- No headers propagated? Ensure RequestContextFilter runs and WebClient has both filters.
- Missing values? Verify `request-context.fields` config and source mapping.
- Unmasked logs? Check `security.sensitive` and use `getMaskedOrOriginal()` when logging.

