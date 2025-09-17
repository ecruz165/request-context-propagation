package com.example.demo.utils;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.util.Date;
import java.util.Map;
import java.util.UUID;

public class JwtTestHelper {

    private static final String SECRET = "test-secret-key-for-jwt-signing-must-be-at-least-256-bits";

    public static String generateJwt() {
        return generateJwt(Map.of());
    }

    public static String generateJwt(Map<String, Object> customClaims) {
        return generateJwt("test-user", "USER", customClaims);
    }

    public static String generateJwt(String subject, String role, Map<String, Object> customClaims) {
        try {
            // Build JWT claims
            JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder()
                .subject(subject)
                .issuer("test-issuer")
                .audience("test-audience")
                .jwtID(UUID.randomUUID().toString())
                .issueTime(new Date())
                .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1 hour
                .claim("scope", "read write")
                .claim("role", role)
                .claim("tenant_id", "test-tenant")
                .claim("user_id", "user-123")
                .claim("email", subject + "@example.com");

            // Add custom claims
            customClaims.forEach(claimsBuilder::claim);

            // Add nested claims for testing
            claimsBuilder.claim("user", Map.of(
                "id", "user-123",
                "name", subject,
                "department", "Engineering",
                "permissions", new String[]{"READ", "WRITE"}
            ));

            claimsBuilder.claim("context", Map.of(
                "request_id", UUID.randomUUID().toString(),
                "session_id", "session-" + System.currentTimeMillis(),
                "environment", "test"
            ));

            JWTClaimsSet claimsSet = claimsBuilder.build();

            // Create signed JWT
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256)
                .type(JOSEObjectType.JWT)
                .build();

            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            JWSSigner signer = new MACSigner(SECRET.getBytes());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT", e);
        }
    }

    public static String generateExpiredJwt() {
        try {
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("test-issuer")
                .issueTime(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .expirationTime(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);
            JWSSigner signer = new MACSigner(SECRET.getBytes());
            signedJWT.sign(signer);

            return signedJWT.serialize();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate expired JWT", e);
        }
    }

    public static String generateMalformedJwt() {
        return "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.invalid.signature";
    }

    public static String generateUnsignedJwt() {
        try {
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("test-user")
                .issuer("test-issuer")
                .issueTime(new Date())
                .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.HS256).build();
            SignedJWT signedJWT = new SignedJWT(header, claimsSet);

            // Return without signing
            return signedJWT.serialize().split("\\.")[0] + "." + 
                   signedJWT.serialize().split("\\.")[1] + ".";
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate unsigned JWT", e);
        }
    }

    public static String generateJwtWithDeepClaims() {
        Map<String, Object> deepClaims = Map.of(
            "level1", Map.of(
                "level2", Map.of(
                    "level3", Map.of(
                        "value", "deep-value",
                        "array", new String[]{"item1", "item2", "item3"}
                    )
                )
            ),
            "metadata", Map.of(
                "timestamp", System.currentTimeMillis(),
                "version", "1.0.0",
                "flags", Map.of(
                    "feature_a", true,
                    "feature_b", false
                )
            )
        );

        return generateJwt(deepClaims);
    }
}