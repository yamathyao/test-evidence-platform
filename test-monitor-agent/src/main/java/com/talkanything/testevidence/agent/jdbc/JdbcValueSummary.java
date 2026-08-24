package com.talkanything.testevidence.agent.jdbc;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class JdbcValueSummary {
    private final int index;
    private final String setter;
    private final int valueLength;
    private final String sha256;

    private JdbcValueSummary(int index, String setter, int valueLength, String sha256) {
        this.index = index;
        this.setter = setter == null ? "" : setter;
        this.valueLength = valueLength;
        this.sha256 = sha256;
    }

    public static JdbcValueSummary of(int index, String setter, Object value) {
        if (value == null) return new JdbcValueSummary(index, setter, 0, "");
        String text = String.valueOf(value);
        return new JdbcValueSummary(index, setter, text.length(), digest(text));
    }

    public int index() { return index; }
    public String setter() { return setter; }
    public int valueLength() { return valueLength; }
    public String sha256() { return sha256; }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(16);
            for (int index = 0; index < 8; index++) result.append(String.format("%02x", bytes[index] & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
