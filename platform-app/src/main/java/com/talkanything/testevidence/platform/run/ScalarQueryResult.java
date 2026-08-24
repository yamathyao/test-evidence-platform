package com.talkanything.testevidence.platform.run;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public record ScalarQueryResult(Object value, MysqlScalarValueType type, int length, String sha256) {
    public static ScalarQueryResult from(Object value, MysqlScalarValueType type) {
        String text = value == null ? "null" : String.valueOf(value);
        int length = value == null ? 0 : text.length();
        return new ScalarQueryResult(value, type, length, sha256(text));
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) result.append(String.format("%02x", digest[index]));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
