package com.sgmart.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Guest order tokens. The browser keeps the token; the database keeps only its SHA-256 hash,
 * so a database leak can't be used to open or change guest orders.
 */
public final class TrackingTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TrackingTokens() {
    }

    /** 32 URL-safe characters (192 random bits). */
    public static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    /** Constant-time compare, so response timing can't reveal how much of a token was right. */
    public static boolean matches(String token, String expectedHash) {
        if (token == null || token.isBlank() || expectedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(token.trim()).getBytes(StandardCharsets.US_ASCII),
                expectedHash.getBytes(StandardCharsets.US_ASCII));
    }
}
