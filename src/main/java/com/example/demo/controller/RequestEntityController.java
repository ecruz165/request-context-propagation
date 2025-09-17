package com.example.demo.controller;

import com.example.demo.config.RequestContext;
import com.example.demo.service.RequestContextService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpMethod.*;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Controller demonstrating RequestEntity variations with RequestContext
 * RequestEntity provides access to HTTP method, URL, headers, and body
 */
@Slf4j
@RestController
@RequestMapping("/api/request-entity")
@RequiredArgsConstructor
public class RequestEntityController {
    private final RequestContextService requestContextService;

    @GetMapping("/test/unprotected/rest-entity/with-body/{pathId1}/{pathId2}")
    public ResponseEntity<Map<String, Object>> testRequestEntity(
            @PathVariable String pathId1,
            @PathVariable String pathId2,
            @RequestParam String queryParam1,
            @RequestParam String queryParam2,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        // the declarative config will populate response headers from context
        RequestContext context = requestContextService.getCurrentContext(request);

        // the declarative config will also populate MDC from context
        log.info("Processing request entity test. Context: {}", context.getAllValues());
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Request entity test successful");
        return ResponseEntity.ok(response);
    }

    // ============================================
    // PATH Variable Extraction Test Endpoints
    // ============================================

    @GetMapping("/path/single/{pathId1}")
    public ResponseEntity<Map<String, Object>> testSinglePathVariable(
            @PathVariable String pathId1,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedPathId1", context.get("pathId1"));
        response.put("providedPathId1", pathId1);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/path/multiple/{pathId1}/{pathId2}")
    public ResponseEntity<Map<String, Object>> testMultiplePathVariables(
            @PathVariable String pathId1,
            @PathVariable String pathId2,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedPathId1", context.get("pathId1"));
        response.put("extractedPathId2", context.get("pathId2"));
        response.put("providedPathId1", pathId1);
        response.put("providedPathId2", pathId2);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/path/with-body/{pathId1}")
    public ResponseEntity<Map<String, Object>> testPathWithBody(
            @PathVariable String pathId1,
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedPathId1", context.get("pathId1"));
        response.put("providedPathId1", pathId1);
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============================================
    // HEADER Source Extraction Test Endpoints
    // ============================================

    @GetMapping("/header/single")
    public ResponseEntity<Map<String, Object>> testSingleHeaderExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedHeaderId1", context.get("headerId1"));
        response.put("extractedHeaderId3", context.get("headerId3"));
        response.put("extractedHeaderId7", context.get("headerId7"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/header/multiple")
    public ResponseEntity<Map<String, Object>> testMultipleHeaderExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedHeaderId1", context.get("headerId1"));
        response.put("extractedHeaderId2", context.get("headerId2"));
        response.put("extractedHeaderId3", context.get("headerId3"));
        response.put("extractedHeaderId4", context.get("headerId4"));
        response.put("extractedHeaderId5", context.get("headerId5"));
        response.put("extractedHeaderId6", context.get("headerId6"));
        response.put("extractedHeaderId7", context.get("headerId7"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/header/with-body")
    public ResponseEntity<Map<String, Object>> testHeaderWithBody(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedHeaderId1", context.get("headerId1"));
        response.put("extractedHeaderId2", context.get("headerId2"));
        response.put("extractedSensitiveHeader", context.get("sensitiveHeader"));
        response.put("extractedEmailHeader", context.get("emailHeader"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/header/sensitive")
    public ResponseEntity<Map<String, Object>> testSensitiveHeaderExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedSensitiveHeader", context.get("sensitiveHeader"));
        response.put("extractedEmailHeader", context.get("emailHeader"));
        response.put("extractedCreditCardField", context.get("creditCardField"));
        response.put("extractedApiKeyField", context.get("apiKeyField"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/header/defaults")
    public ResponseEntity<Map<String, Object>> testHeaderDefaultValues(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedHeaderId3", context.get("headerId3")); // Has default: "default-header-3"
        response.put("extractedHeaderId7", context.get("headerId7")); // Has default: "anonymous-user"
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============================================
    // QUERY Source Extraction Test Endpoints
    // ============================================

    @GetMapping("/query/single")
    public ResponseEntity<Map<String, Object>> testSingleQueryExtraction(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/multiple")
    public ResponseEntity<Map<String, Object>> testMultipleQueryExtraction(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/query/with-body")
    public ResponseEntity<Map<String, Object>> testQueryWithBodyExtraction(
            @RequestBody Map<String, Object> requestBody,
            HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("requestBody", requestBody);
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/special")
    public ResponseEntity<Map<String, Object>> testQuerySpecialCharacters(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/encoded")
    public ResponseEntity<Map<String, Object>> testQueryUrlEncoded(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/defaults")
    public ResponseEntity<Map<String, Object>> testQueryDefaultValues(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/sensitive")
    public ResponseEntity<Map<String, Object>> testQuerySensitiveData(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/empty")
    public ResponseEntity<Map<String, Object>> testQueryEmptyValues(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/multiple-values")
    public ResponseEntity<Map<String, Object>> testQueryMultipleValues(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/timing")
    public ResponseEntity<Map<String, Object>> testQueryExtractionTiming(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("extractionPhase", "pre-authentication");
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/complex")
    public ResponseEntity<Map<String, Object>> testQueryComplexCombinations(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/query/types")
    public ResponseEntity<Map<String, Object>> testQueryVariousDataTypes(HttpServletRequest request) {
        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("contextFields", context.getAllValuesWithMasking());
        response.put("extractedValues", getQueryParameters(request));
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============================================
    // TOKEN Source Extraction Test Endpoints
    // ============================================

    @GetMapping("/token/single")
    public ResponseEntity<Map<String, Object>> testSingleTokenExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedJwtClaimId1", context.get("jwtClaimId1"));
        response.put("extractedJwtClaimId2", context.get("jwtClaimId2"));
        response.put("extractedJwtClaimId3", context.get("jwtClaimId3"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/token/multiple")
    public ResponseEntity<Map<String, Object>> testMultipleTokenExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedJwtClaimId1", context.get("jwtClaimId1"));
        response.put("extractedJwtClaimId2", context.get("jwtClaimId2"));
        response.put("extractedJwtClaimId3", context.get("jwtClaimId3"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/token/with-body")
    public ResponseEntity<Map<String, Object>> testTokenWithBody(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedJwtClaimId1", context.get("jwtClaimId1"));
        response.put("extractedJwtClaimId2", context.get("jwtClaimId2"));
        response.put("extractedJwtClaimId3", context.get("jwtClaimId3"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/token/defaults")
    public ResponseEntity<Map<String, Object>> testTokenDefaultValues(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedJwtClaimId1", context.get("jwtClaimId1"));
        response.put("extractedJwtClaimId2", context.get("jwtClaimId2")); // Has default: "anonymous"
        response.put("extractedJwtClaimId3", context.get("jwtClaimId3"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/token/claims")
    public ResponseEntity<Map<String, Object>> testTokenClaimsExtraction(
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedJwtClaimId1", context.get("jwtClaimId1"));
        response.put("extractedJwtClaimId2", context.get("jwtClaimId2"));
        response.put("extractedJwtClaimId3", context.get("jwtClaimId3"));
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    // ============================================
    // BODY Source Extraction Test Endpoints
    // ============================================

    @PostMapping("/body/single")
    public ResponseEntity<Map<String, Object>> testSingleBodyExtraction(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedBodyId1", context.get("bodyId1"));
        response.put("extractedBodyId2", context.get("bodyId2"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/body/multiple")
    public ResponseEntity<Map<String, Object>> testMultipleBodyExtraction(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedBodyId1", context.get("bodyId1"));
        response.put("extractedBodyId2", context.get("bodyId2"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/body/nested")
    public ResponseEntity<Map<String, Object>> testNestedBodyExtraction(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedBodyId1", context.get("bodyId1"));
        response.put("extractedBodyId2", context.get("bodyId2"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/body/defaults")
    public ResponseEntity<Map<String, Object>> testBodyDefaultValues(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedBodyId1", context.get("bodyId1"));
        response.put("extractedBodyId2", context.get("bodyId2"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/body/sensitive")
    public ResponseEntity<Map<String, Object>> testBodySensitiveData(
            @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        RequestContext context = requestContextService.getCurrentContext(request);

        Map<String, Object> response = new HashMap<>();
        response.put("extractedBodyId1", context.get("bodyId1"));
        response.put("extractedBodyId2", context.get("bodyId2"));
        response.put("requestBody", body);
        response.put("contextFields", context.getAllValues());
        response.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(response);
    }

    /**
     * Helper method to extract query parameters from the request
     */
    private Map<String, String> getQueryParameters(HttpServletRequest request) {
        Map<String, String> queryParams = new HashMap<>();

        if (request.getQueryString() != null) {
            String[] pairs = request.getQueryString().split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=", 2);
                if (keyValue.length == 2) {
                    try {
                        String key = java.net.URLDecoder.decode(keyValue[0], "UTF-8");
                        String value = java.net.URLDecoder.decode(keyValue[1], "UTF-8");
                        queryParams.put(key, value);
                    } catch (Exception e) {
                        // If decoding fails, use the raw values
                        queryParams.put(keyValue[0], keyValue[1]);
                    }
                } else if (keyValue.length == 1) {
                    // Handle parameters without values
                    queryParams.put(keyValue[0], "");
                }
            }
        }

        return queryParams;
    }
}
