package com.example.demo.api.source;

import com.example.demo.config.BaseApiTest;
import io.restassured.RestAssured;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Integration tests for QUERY source extraction functionality.
 * Tests the framework's ability to extract values from query parameters
 * in the pre-authentication phase.
 */
@DisplayName("Query Source Extraction Tests")
class QuerySourceExtractionTest extends BaseApiTest {

    @Test
    @DisplayName("Should extract single query parameter")
    void shouldExtractSingleQueryParameter() {
        given()
                .queryParam("queryId1", "single-query-123")
                .when()
                .get("/request-entity/query/single")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("single-query-123"))
                .body("contextFields.queryId2", equalTo("default-query")) // Default value
                .body("contextFields.queryId3", nullValue()) // No default, no value provided
                .body("extractedValues.queryId1", equalTo("single-query-123"));
    }

    @Test
    @DisplayName("Should extract multiple query parameters")
    void shouldExtractMultipleQueryParameters() {
        given()
                .queryParam("queryId1", "multi-query-456")
                .queryParam("queryId2", "custom-query-value")
                .queryParam("queryId3", "third-query-789")
                .queryParam("search", "test-search")
                .queryParam("filter", "active")
                .when()
                .get("/request-entity/query/multiple")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("multi-query-456"))
                .body("contextFields.queryId2", equalTo("custom-query-value"))
                .body("contextFields.queryId3", equalTo("third-query-789"))
                .body("contextFields.queryId4", equalTo("test-search"))
                .body("contextFields.queryId5", equalTo("active"))
                .body("extractedValues.queryId1", equalTo("multi-query-456"))
                .body("extractedValues.queryId2", equalTo("custom-query-value"))
                .body("extractedValues.queryId3", equalTo("third-query-789"))
                .body("extractedValues.search", equalTo("test-search"))
                .body("extractedValues.filter", equalTo("active"));
    }

    @Test
    @DisplayName("Should handle POST requests with query parameters")
    void shouldHandlePostRequestsWithQueryParameters() {
        given()
                .queryParam("queryId1", "post-query-test")
                .queryParam("search", "post-search")
                .contentType("application/json")
                .body("{\"data\": \"test-data\"}")
                .when()
                .post("/request-entity/query/with-body")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("post-query-test"))
                .body("contextFields.queryId4", equalTo("post-search"))
                .body("requestBody.data", equalTo("test-data"));
    }

    @Test
    @DisplayName("Should handle special characters in query parameters")
    void shouldHandleSpecialCharactersInQueryParameters() {
        given()
                .queryParam("queryId1", "special-chars-!@#$%^&*()")
                .queryParam("search", "test with spaces")
                .queryParam("filter", "value,with,commas")
                .when()
                .get("/request-entity/query/special")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("special-chars-!@#$%^&*()"))
                .body("contextFields.queryId4", equalTo("test with spaces"))
                .body("contextFields.queryId5", equalTo("value,with,commas"));
    }

    @Test
    @DisplayName("Should handle URL encoded query parameters")
    void shouldHandleUrlEncodedQueryParameters() {
        given()
                .queryParam("queryId1", "url%20encoded%20value")
                .queryParam("search", "test%2Bplus%2Bsigns")
                .when()
                .get("/request-entity/query/encoded")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("url encoded value"))
                .body("contextFields.queryId4", equalTo("test+plus+signs"));
    }

    @Test
    @DisplayName("Should use default values when query parameters are missing")
    void shouldUseDefaultValuesWhenQueryParametersAreMissing() {
        given()
                .when()
                .get("/request-entity/query/defaults")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", nullValue()) // No default value
                .body("contextFields.queryId2", equalTo("default-query")) // Has default
                .body("contextFields.queryId3", nullValue()) // No default value
                .body("contextFields.queryId5", equalTo("all")); // Has default value
    }

    @Test
    @DisplayName("Should handle sensitive query parameters")
    void shouldHandleSensitiveQueryParameters() {
        given()
                .queryParam("queryId1", "normal-query-value")
                .queryParam("sensitive-param", "secret-api-key-12345")
                .when()
                .get("/request-entity/query/sensitive")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("normal-query-value"))
                .body("contextFields.queryId6", equalTo("***")) // Masked sensitive value
                .body("extractedValues.queryId1", equalTo("normal-query-value"))
                .body("extractedValues.'sensitive-param'", equalTo("secret-api-key-12345")); // Raw value in response
    }

    @Test
    @DisplayName("Should handle empty query parameter values")
    void shouldHandleEmptyQueryParameterValues() {
        given()
                .queryParam("queryId1", "")
                .queryParam("queryId2", "")
                .queryParam("search", "")
                .when()
                .get("/request-entity/query/empty")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo(""))
                .body("contextFields.queryId2", equalTo("")) // Empty overrides default
                .body("contextFields.queryId4", equalTo(""));
    }

    @Test
    @DisplayName("Should handle multiple values for same query parameter")
    void shouldHandleMultipleValuesForSameQueryParameter() {
        given()
                .queryParam("queryId1", "first-value")
                .queryParam("queryId1", "second-value")
                .queryParam("search", "search1")
                .queryParam("search", "search2")
                .when()
                .get("/request-entity/query/multiple-values")
                .then()
                .statusCode(200)
                // Should get the first value when multiple values are provided
                .body("contextFields.queryId1", equalTo("first-value"))
                .body("contextFields.queryId4", equalTo("search1"));
    }

    @Test
    @DisplayName("Should demonstrate pre-authentication extraction timing")
    void shouldDemonstratePreAuthenticationExtractionTiming() {
        given()
                .queryParam("queryId1", "pre-auth-query-test")
                .header("X-HEADER-ID-1", "test-header-value")
                .when()
                .get("/request-entity/query/timing")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("pre-auth-query-test"))
                .body("contextFields.headerId1", equalTo("test-header-value"))
                .body("extractionPhase", equalTo("pre-authentication"));
    }

    @Test
    @DisplayName("Should handle complex query parameter combinations")
    void shouldHandleComplexQueryParameterCombinations() {
        given()
                .queryParam("queryId1", "complex-test-123")
                .queryParam("queryId2", "override-default")
                .queryParam("queryId3", "third-param")
                .queryParam("search", "complex search terms")
                .queryParam("filter", "status:active,type:user")
                .queryParam("sensitive-param", "sensitive-data-456")
                .when()
                .get("/request-entity/query/complex")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("complex-test-123"))
                .body("contextFields.queryId2", equalTo("override-default"))
                .body("contextFields.queryId3", equalTo("third-param"))
                .body("contextFields.queryId4", equalTo("complex search terms"))
                .body("contextFields.queryId5", equalTo("status:active,type:user"))
                .body("contextFields.queryId6", equalTo("***")) // Masked
                .body("extractedValues.queryId1", equalTo("complex-test-123"))
                .body("extractedValues.queryId2", equalTo("override-default"))
                .body("extractedValues.queryId3", equalTo("third-param"))
                .body("extractedValues.search", equalTo("complex search terms"))
                .body("extractedValues.filter", equalTo("status:active,type:user"))
                .body("extractedValues.'sensitive-param'", equalTo("sensitive-data-456"));
    }

    @Test
    @DisplayName("Should handle query parameters with various data types")
    void shouldHandleQueryParametersWithVariousDataTypes() {
        given()
                .queryParam("queryId1", "12345") // Number as string
                .queryParam("search", "true") // Boolean as string
                .queryParam("filter", "98.5") // Float as string
                .when()
                .get("/request-entity/query/types")
                .then()
                .statusCode(200)
                .body("contextFields.queryId1", equalTo("12345"))
                .body("contextFields.queryId4", equalTo("true"))
                .body("contextFields.queryId5", equalTo("98.5"));
    }
}
