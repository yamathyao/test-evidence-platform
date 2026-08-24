package com.talkanything.testevidence.agent.jdbc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class JdbcStatementState {
    private final Map<Integer, JdbcValueSummary> parameters = new TreeMap<Integer, JdbcValueSummary>();

    void record(String setter, int index, Object value) {
        parameters.put(Integer.valueOf(index), JdbcValueSummary.of(index, setter, value));
    }

    List<JdbcValueSummary> parameters() {
        return new ArrayList<JdbcValueSummary>(parameters.values());
    }
}
