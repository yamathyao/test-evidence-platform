package com.talkanything.testevidence.agent.jdbc;

import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

final class JdbcSqlClassifier {
    private JdbcSqlClassifier() { }

    static String operation(String sql, String fallback) {
        String normalized = normalize(sql);
        if (normalized.startsWith("SELECT ")) return "SELECT";
        if (normalized.startsWith("INSERT ")) return "INSERT";
        if (normalized.startsWith("UPDATE ")) return "UPDATE";
        if (normalized.startsWith("DELETE ")) return "DELETE";
        return fallback;
    }

    static boolean isConnectorMetadataQuery(String sql) {
        String normalized = normalize(sql);
        return normalized.startsWith("SELECT TABLE_SCHEMA, NULL, TABLE_NAME, COLUMN_NAME")
                && normalized.contains("FROM INFORMATION_SCHEMA.COLUMNS");
    }

    static boolean shouldIgnore(String sql, List<String> prefixes, List<String> regexes) {
        return filter(prefixes, regexes).shouldIgnore(sql);
    }

    static Filter filter(List<String> prefixes, List<String> regexes) {
        return new Filter(prefixes, regexes);
    }

    private static String normalize(String sql) {
        return sql == null ? "" : sql.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    static final class Filter {
        private final List<String> normalizedPrefixes;
        private final List<Pattern> patterns;

        Filter(List<String> prefixes, List<String> regexes) {
            this.normalizedPrefixes = normalizePrefixes(prefixes);
            this.patterns = compilePatterns(regexes);
        }

        boolean shouldIgnore(String sql) {
            if (isConnectorMetadataQuery(sql)) return true;
            String normalizedSql = normalize(sql);
            for (String prefix : normalizedPrefixes) {
                if (normalizedSql.startsWith(prefix)) return true;
            }
            String rawSql = sql == null ? "" : sql;
            for (Pattern pattern : patterns) {
                if (pattern.matcher(rawSql).matches()) return true;
            }
            return false;
        }

        private static List<String> normalizePrefixes(List<String> prefixes) {
            if (prefixes == null || prefixes.isEmpty()) return Collections.emptyList();
            List<String> values = new ArrayList<String>();
            for (String prefix : prefixes) {
                String normalized = normalize(prefix);
                if (!normalized.isEmpty()) values.add(normalized);
            }
            return Collections.unmodifiableList(values);
        }

        private static List<Pattern> compilePatterns(List<String> regexes) {
            if (regexes == null || regexes.isEmpty()) return Collections.emptyList();
            List<Pattern> values = new ArrayList<Pattern>();
            for (String regex : regexes) {
                try {
                    values.add(Pattern.compile(regex));
                } catch (PatternSyntaxException ignored) { }
            }
            return Collections.unmodifiableList(values);
        }
    }
}
