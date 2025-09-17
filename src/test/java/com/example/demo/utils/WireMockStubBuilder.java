package com.example.demo.utils;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class WireMockStubBuilder {

    private final WireMockServer server;
    private MappingBuilder mappingBuilder;

    public WireMockStubBuilder(WireMockServer server) {
        this.server = server;
    }

    public static WireMockStubBuilder create(WireMockServer server) {
        return new WireMockStubBuilder(server);
    }

    public WireMockStubBuilder get(String url) {
        this.mappingBuilder = com.github.tomakehurst.wiremock.client.WireMock.get(urlEqualTo(url));
        return this;
    }

    public WireMockStubBuilder post(String url) {
        this.mappingBuilder = com.github.tomakehurst.wiremock.client.WireMock.post(urlEqualTo(url));
        return this;
    }

    public WireMockStubBuilder put(String url) {
        this.mappingBuilder = com.github.tomakehurst.wiremock.client.WireMock.put(urlEqualTo(url));
        return this;
    }

    public WireMockStubBuilder delete(String url) {
        this.mappingBuilder = com.github.tomakehurst.wiremock.client.WireMock.delete(urlEqualTo(url));
        return this;
    }

    public WireMockStubBuilder withHeader(String name, String value) {
        this.mappingBuilder.withHeader(name, equalTo(value));
        return this;
    }

    public WireMockStubBuilder withHeaders(Map<String, String> headers) {
        headers.forEach(this::withHeader);
        return this;
    }

    public WireMockStubBuilder withQueryParam(String name, String value) {
        this.mappingBuilder.withQueryParam(name, equalTo(value));
        return this;
    }

    public WireMockStubBuilder withRequestBody(String body) {
        this.mappingBuilder.withRequestBody(equalToJson(body));
        return this;
    }

    public WireMockStubBuilder willReturn(int status) {
        return willReturn(status, "");
    }

    public WireMockStubBuilder willReturn(int status, String body) {
        ResponseDefinitionBuilder response = aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json");

        if (body != null && !body.isEmpty()) {
            response.withBody(body);
        }

        this.mappingBuilder.willReturn(response);
        return this;
    }

    public WireMockStubBuilder willReturnJson(Object responseObject) {
        // Simple JSON conversion - in real scenarios you might want to use ObjectMapper
        String json;
        if (responseObject instanceof String) {
            json = (String) responseObject;
        } else {
            json = "{}"; // Placeholder - you'd use Jackson here
        }

        this.mappingBuilder.willReturn(
            aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(json)
        );
        return this;
    }

    public WireMockStubBuilder willReturnWithDelay(int status, String body, int delayMs) {
        this.mappingBuilder.willReturn(
            aResponse()
                .withStatus(status)
                .withHeader("Content-Type", "application/json")
                .withBody(body)
                .withFixedDelay(delayMs)
        );
        return this;
    }

    public StubMapping stub() {
        StubMapping stubMapping = server.stubFor(mappingBuilder);
        this.mappingBuilder = null; // Reset for next use
        return stubMapping;
    }

    // Convenience methods for common scenarios
    public StubMapping stubSuccess(String url, String responseBody) {
        return get(url).willReturn(200, responseBody).stub();
    }

    public StubMapping stubError(String url, int errorCode) {
        return get(url).willReturn(errorCode).stub();
    }

    public StubMapping stubTimeout(String url, int delayMs) {
        return get(url).willReturnWithDelay(200, "{}", delayMs).stub();
    }

    // Verification helpers
    public static void verifyRequestMade(WireMockServer server, String method, String url) {
        RequestPatternBuilder pattern = switch (method.toUpperCase()) {
            case "GET" -> getRequestedFor(urlEqualTo(url));
            case "POST" -> postRequestedFor(urlEqualTo(url));
            case "PUT" -> putRequestedFor(urlEqualTo(url));
            case "DELETE" -> deleteRequestedFor(urlEqualTo(url));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        server.verify(pattern);
    }

    public static void verifyRequestWithHeader(WireMockServer server, String method, String url, String header, String value) {
        RequestPatternBuilder pattern = switch (method.toUpperCase()) {
            case "GET" -> getRequestedFor(urlEqualTo(url));
            case "POST" -> postRequestedFor(urlEqualTo(url));
            case "PUT" -> putRequestedFor(urlEqualTo(url));
            case "DELETE" -> deleteRequestedFor(urlEqualTo(url));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        pattern.withHeader(header, equalTo(value));
        server.verify(pattern);
    }

    public static void verifyNoRequests(WireMockServer server, String method, String url) {
        RequestPatternBuilder pattern = switch (method.toUpperCase()) {
            case "GET" -> getRequestedFor(urlEqualTo(url));
            case "POST" -> postRequestedFor(urlEqualTo(url));
            case "PUT" -> putRequestedFor(urlEqualTo(url));
            case "DELETE" -> deleteRequestedFor(urlEqualTo(url));
            default -> throw new IllegalArgumentException("Unsupported method: " + method);
        };
        server.verify(0, pattern);
    }
}