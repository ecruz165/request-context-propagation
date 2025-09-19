package com.example.demo.config;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for API tests providing common test infrastructure
 * - WireMock server for downstream service mocking
 * - Test profiles and configuration
 * - Common setup and teardown
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestWebClientConfig.class, TestSecurityConfig.class})
public abstract class BaseApiTest {

    protected WireMockServer wireMock;

    @BeforeEach
    void setUpWireMock() {
        // Start WireMock server on port 8089 (matches downstream service URL)
        wireMock = new WireMockServer(WireMockConfiguration.options()
                .port(8089)
                .usingFilesUnderDirectory("src/test/resources"));
        wireMock.start();
    }

    @AfterEach
    void tearDownWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }
}
