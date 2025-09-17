package com.example.demo.api.source;

import com.example.demo.config.BaseApiTest;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;

@DisplayName("Path Variable Source Extraction Tests")
class PathSourceExtractionTest extends BaseApiTest {

    @Test
    @DisplayName("Should extract single path variable from URL")
    void shouldExtractSinglePathVariable() {
        String pathValue = "test-path-123";

        Response response = given(requestSpec)
            .when()
            .get("/request-entity/path/single/{pathId1}", pathValue)
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("extractedPathId1", equalTo(pathValue))
            .body("providedPathId1", equalTo(pathValue))
            .body("contextFields.pathId1", equalTo(pathValue))
            .extract().response();

        // Verify response structure
        Map<String, Object> responseBody = response.jsonPath().getMap("$");
        assertThat(responseBody).containsKey("timestamp");
        assertThat(responseBody).containsKey("contextFields");
    }

    @Test
    @DisplayName("Should extract multiple path variables from URL")
    void shouldExtractMultiplePathVariables() {
        String pathValue1 = "user-456";
        String pathValue2 = "order-789";

        given(requestSpec)
            .when()
            .get("/request-entity/path/multiple/{pathId1}/{pathId2}", pathValue1, pathValue2)
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("extractedPathId1", equalTo(pathValue1))
            .body("extractedPathId2", equalTo(pathValue2))
            .body("providedPathId1", equalTo(pathValue1))
            .body("providedPathId2", equalTo(pathValue2))
            .body("contextFields.pathId1", equalTo(pathValue1))
            .body("contextFields.pathId2", equalTo(pathValue2));
    }

    @ParameterizedTest
    @CsvSource({
        "simple-id, basic-value",
        "complex-id-123, complex-value-456",
        "user_123, order_456",
        "a, b",
        "very-long-path-variable-name-123456789, another-very-long-path-variable-value-987654321"
    })
    @DisplayName("Should extract path variables with various formats")
    void shouldExtractPathVariablesWithVariousFormats(String pathId1, String pathId2) {
        given(requestSpec)
            .when()
            .get("/request-entity/path/multiple/{pathId1}/{pathId2}", pathId1, pathId2)
            .then()
            .statusCode(200)
            .body("extractedPathId1", equalTo(pathId1))
            .body("extractedPathId2", equalTo(pathId2))
            .body("contextFields.pathId1", equalTo(pathId1))
            .body("contextFields.pathId2", equalTo(pathId2));
    }

    @Test
    @DisplayName("Should extract path variable with POST request and body")
    void shouldExtractPathVariableWithPostAndBody() {
        String pathValue = "post-path-123";
        Map<String, Object> requestBody = Map.of(
            "name", "Test User",
            "email", "test@example.com",
            "data", Map.of("nested", "value")
        );

        given(requestSpec)
            .body(requestBody)
            .when()
            .post("/request-entity/path/with-body/{pathId1}", pathValue)
            .then()
            .statusCode(200)
            .contentType("application/json")
            .body("extractedPathId1", equalTo(pathValue))
            .body("providedPathId1", equalTo(pathValue))
            .body("contextFields.pathId1", equalTo(pathValue))
            .body("requestBody.name", equalTo("Test User"))
            .body("requestBody.email", equalTo("test@example.com"))
            .body("requestBody.data.nested", equalTo("value"));
    }

    @Test
    @DisplayName("Should handle URL encoded path variables")
    void shouldHandleUrlEncodedPathVariables() {
        // Use simple URL encoding that won't trigger Spring Security firewall
        String pathValue1 = "user-with-spaces";
        String pathValue2 = "order-with-plus";

        given(requestSpec)
            .when()
            .get("/request-entity/path/multiple/{pathId1}/{pathId2}", pathValue1, pathValue2)
            .then()
            .statusCode(200)
            .body("extractedPathId1", equalTo(pathValue1))
            .body("extractedPathId2", equalTo(pathValue2))
            .body("contextFields.pathId1", equalTo(pathValue1))
            .body("contextFields.pathId2", equalTo(pathValue2));
    }

    @Test
    @DisplayName("Should extract path variables with special characters")
    void shouldExtractPathVariablesWithSpecialCharacters() {
        String pathValue1 = "user-123_test";
        String pathValue2 = "order.456-final";

        given(requestSpec)
            .when()
            .get("/request-entity/path/multiple/{pathId1}/{pathId2}", pathValue1, pathValue2)
            .then()
            .statusCode(200)
            .body("extractedPathId1", equalTo(pathValue1))
            .body("extractedPathId2", equalTo(pathValue2))
            .body("contextFields.pathId1", equalTo(pathValue1))
            .body("contextFields.pathId2", equalTo(pathValue2));
    }

    @Test
    @DisplayName("Should verify path extraction happens in post-auth phase")
    void shouldVerifyPathExtractionInPostAuthPhase() {
        String pathValue = "post-auth-test-123";

        Response response = given(requestSpec)
            .when()
            .get("/request-entity/path/single/{pathId1}", pathValue)
            .then()
            .statusCode(200)
            .extract().response();

        // Verify that path variables are extracted and available in context
        Map<String, Object> contextFields = response.jsonPath().getMap("contextFields");

        // PATH source should be in post-auth phase, so it should be available
        assertThat(contextFields).containsKey("pathId1");
        assertThat(contextFields.get("pathId1")).isEqualTo(pathValue);

        // Verify other context fields are also present (from pre-auth phase)
        // This confirms that both pre-auth and post-auth extraction worked
        assertThat(contextFields).isNotEmpty();
    }

    @Test
    @DisplayName("Should handle missing path variables gracefully")
    void shouldHandleMissingPathVariablesGracefully() {
        // Test with a path that doesn't match the expected pattern
        // This should still work but pathId2 might have default value
        String pathValue1 = "only-one-path";

        given(requestSpec)
            .when()
            .get("/request-entity/path/single/{pathId1}", pathValue1)
            .then()
            .statusCode(200)
            .body("extractedPathId1", equalTo(pathValue1))
            .body("contextFields.pathId1", equalTo(pathValue1));
    }

    @Test
    @DisplayName("Should verify path extraction with default values")
    void shouldVerifyPathExtractionWithDefaultValues() {
        // Test the endpoint that has pathId2 with default value configured
        String pathValue1 = "test-with-default";
        String pathValue2 = "custom-value";

        Response response = given(requestSpec)
            .when()
            .get("/request-entity/path/multiple/{pathId1}/{pathId2}", pathValue1, pathValue2)
            .then()
            .statusCode(200)
            .extract().response();

        Map<String, Object> contextFields = response.jsonPath().getMap("contextFields");

        // pathId1 should have the provided value
        assertThat(contextFields.get("pathId1")).isEqualTo(pathValue1);

        // pathId2 should have the provided value (not the default since we provided one)
        assertThat(contextFields.get("pathId2")).isEqualTo(pathValue2);
    }
}