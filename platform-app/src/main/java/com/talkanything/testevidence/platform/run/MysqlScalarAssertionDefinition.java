package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

public record MysqlScalarAssertionDefinition(String sql, List<Parameter> parameters, JsonNode expected,
                                             int waitSeconds) {
    public record Parameter(String source, String jsonPath, String headerName) { }
}
