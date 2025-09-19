package com.example.demo.api;

import com.example.demo.config.props.RequestContextProperties;
import com.example.demo.service.RequestContextEnricher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for system-specific field propagation using extSysIds configuration.
 * <p>
 * This test demonstrates the new feature where fields can be configured
 * to only propagate to specific external systems (extSysIds) rather than
 * all WebClients by default.
 */
@SpringBootTest
@ActiveProfiles("test")
public class SystemSpecificPropagationUnitTest {

    @Test
    public void testPropagationDataSystemFiltering() {
        // Test the PropagationData.shouldPropagateToSystem method

        // Case 1: No extSysIds specified (null) - should propagate to all systems
        RequestContextEnricher.PropagationData globalField =
                new RequestContextEnricher.PropagationData(
                        RequestContextProperties.EnrichmentType.HEADER,
                        "X-API-Key",
                        "global-api-key-123",
                        false,
                        "***",
                        null  // No extSysIds = propagate to all
                );

        assertThat(globalField.shouldPropagateToSystem("user-service")).isTrue();
        assertThat(globalField.shouldPropagateToSystem("profile-service")).isTrue();
        assertThat(globalField.shouldPropagateToSystem("payment-service")).isTrue();
        assertThat(globalField.shouldPropagateToSystem("any-system")).isTrue();

        // Case 2: Empty extSysIds list - should propagate to all systems
        RequestContextEnricher.PropagationData emptyListField =
                new RequestContextEnricher.PropagationData(
                        RequestContextProperties.EnrichmentType.HEADER,
                        "X-Global-Token",
                        "global-token-456",
                        false,
                        "***",
                        java.util.Collections.emptyList()  // Empty list = propagate to all
                );

        assertThat(emptyListField.shouldPropagateToSystem("user-service")).isTrue();
        assertThat(emptyListField.shouldPropagateToSystem("profile-service")).isTrue();
        assertThat(emptyListField.shouldPropagateToSystem("payment-service")).isTrue();

        // Case 3: Specific extSysIds - should only propagate to specified systems
        RequestContextEnricher.PropagationData userOnlyField =
                new RequestContextEnricher.PropagationData(
                        RequestContextProperties.EnrichmentType.HEADER,
                        "X-User-Token",
                        "user-token-789",
                        false,
                        "***",
                        java.util.List.of("user-service")  // Only user-service
                );

        assertThat(userOnlyField.shouldPropagateToSystem("user-service")).isTrue();
        assertThat(userOnlyField.shouldPropagateToSystem("profile-service")).isFalse();
        assertThat(userOnlyField.shouldPropagateToSystem("payment-service")).isFalse();
        assertThat(userOnlyField.shouldPropagateToSystem("notification-service")).isFalse();

        // Case 4: Multiple specific extSysIds
        RequestContextEnricher.PropagationData multiSystemField =
                new RequestContextEnricher.PropagationData(
                        RequestContextProperties.EnrichmentType.HEADER,
                        "X-Multi-System-Auth",
                        "multi-auth-abc",
                        false,
                        "***",
                        java.util.List.of("user-service", "profile-service", "notification-service")
                );

        assertThat(multiSystemField.shouldPropagateToSystem("user-service")).isTrue();
        assertThat(multiSystemField.shouldPropagateToSystem("profile-service")).isTrue();
        assertThat(multiSystemField.shouldPropagateToSystem("notification-service")).isTrue();
        assertThat(multiSystemField.shouldPropagateToSystem("payment-service")).isFalse();
        assertThat(multiSystemField.shouldPropagateToSystem("unknown-service")).isFalse();
    }

    @Test
    public void testPropagationDataToString() {
        // Test that toString includes extSysIds information
        RequestContextEnricher.PropagationData systemSpecificField =
                new RequestContextEnricher.PropagationData(
                        RequestContextProperties.EnrichmentType.HEADER,
                        "X-System-Specific",
                        "test-value",
                        false,
                        "***",
                        java.util.List.of("user-service", "profile-service")
                );

        String toString = systemSpecificField.toString();
        assertThat(toString).contains("extSysIds=[user-service, profile-service]");
        assertThat(toString).contains("type=HEADER");
        assertThat(toString).contains("key='X-System-Specific'");
        assertThat(toString).contains("value='test-value'");
    }

    @Test
    public void testConfigurationStructure() {
        // Test that the configuration structure supports extSysIds
        RequestContextProperties.OutboundConfig outboundConfig =
                new RequestContextProperties.OutboundConfig();

        outboundConfig.setEnrichAs(RequestContextProperties.EnrichmentType.HEADER);
        outboundConfig.setKey("X-Test-Header");
        outboundConfig.setExtSysIds(java.util.List.of("system1", "system2"));

        assertThat(outboundConfig.getExtSysIds()).isNotNull();
        assertThat(outboundConfig.getExtSysIds()).hasSize(2);
        assertThat(outboundConfig.getExtSysIds()).contains("system1", "system2");
    }
}
