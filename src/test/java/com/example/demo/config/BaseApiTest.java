package com.example.demo.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.filter.log.LogDetail;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "request-context.enabled=true",
    "downstream.service.url=http://localhost:8089",
    "logging.level.com.example.demo=DEBUG",
    "spring.jackson.serialization.indent-output=true"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class BaseApiTest {

    @LocalServerPort
    protected int port;

    protected static WireMockServer wireMockServer;
    protected RequestSpecification requestSpec;

    @BeforeAll
    public void setupClass() {
        // Initialize WireMock server
        if (wireMockServer == null) {
            wireMockServer = new WireMockServer(
                wireMockConfig()
                    .port(8089)
                    .usingFilesUnderDirectory("src/test/resources/wiremock")
            );
            wireMockServer.start();
        }
    }

    @BeforeEach
    public void setup() {
        // Configure REST Assured
        RestAssured.port = port;
        RestAssured.basePath = "/api";
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails(LogDetail.ALL);

        // Build base request specification
        requestSpec = new RequestSpecBuilder()
            .setPort(port)
            .setBasePath("/api")
            .setContentType(ContentType.JSON)
            .setAccept(ContentType.JSON)
            .addHeader("X-HEADER-ID-1", generateRequestId())
            .addHeader("X-HEADER-ID-2", generateCorrelationId())
            .log(LogDetail.ALL)
            .build();

        // Reset WireMock state
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.resetAll();
        }
    }

    @AfterEach
    public void tearDown() {
        // Clean up after each test
        RestAssured.reset();
    }

    @AfterAll
    public void tearDownClass() {
        // Stop WireMock server
        if (wireMockServer != null && wireMockServer.isRunning()) {
            wireMockServer.stop();
            wireMockServer = null;
        }
    }

    protected String generateRequestId() {
        return "test-request-" + System.currentTimeMillis();
    }

    protected String generateCorrelationId() {
        return "test-correlation-" + System.nanoTime();
    }

    protected WireMockServer getWireMockServer() {
        return wireMockServer;
    }
}