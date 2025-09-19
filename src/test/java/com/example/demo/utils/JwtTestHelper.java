package com.example.demo.utils;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper utility for creating JWT tokens in tests
 * Supports creating tokens with custom claims for testing JWT claim extraction
 */
@Component
public class JwtTestHelper {

    private final byte[] secretKey;

    public JwtTestHelper() {
        // Generate a test key for JWT signing (256-bit key for HS256)
        this.secretKey = "test-secret-key-for-jwt-signing-must-be-256-bits".getBytes();
    }

    /**
     * Create a new JWT token builder
     */
    public JwtTokenBuilder createJwtToken() {
        return new JwtTokenBuilder(secretKey);
    }

    /**
     * Builder class for creating JWT tokens with custom claims
     */
    public static class JwtTokenBuilder {
        private final byte[] secretKey;
        private String subject;
        private final Map<String, Object> claims = new HashMap<>();
        private Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS);

        public JwtTokenBuilder(byte[] secretKey) {
            this.secretKey = secretKey;
        }

        /**
         * Set the subject (sub) claim
         */
        public JwtTokenBuilder withSubject(String subject) {
            this.subject = subject;
            return this;
        }

        /**
         * Add a custom claim
         */
        public JwtTokenBuilder withClaim(String key, Object value) {
            this.claims.put(key, value);
            return this;
        }

        /**
         * Set expiration time
         */
        public JwtTokenBuilder withExpiration(Instant expiration) {
            this.expiration = expiration;
            return this;
        }

        /**
         * Build the JWT token
         */
        public String build() {
            try {
                // Create JWT claims set
                JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                        .subject(subject)
                        .issueTime(Date.from(Instant.now()))
                        .expirationTime(Date.from(expiration));

                // Add custom claims
                for (Map.Entry<String, Object> entry : claims.entrySet()) {
                    claimsBuilder.claim(entry.getKey(), entry.getValue());
                }

                JWTClaimsSet claimsSet = claimsBuilder.build();

                // Create signed JWT
                SignedJWT signedJWT = new SignedJWT(
                        new JWSHeader(JWSAlgorithm.HS256),
                        claimsSet
                );

                // Sign the JWT
                JWSSigner signer = new MACSigner(secretKey);
                signedJWT.sign(signer);

                return signedJWT.serialize();
            } catch (JOSEException e) {
                throw new RuntimeException("Failed to create JWT token", e);
            }
        }
    }

    /**
     * Get the secret key used for signing (for verification in tests)
     */
    public byte[] getSecretKey() {
        return secretKey;
    }
}
