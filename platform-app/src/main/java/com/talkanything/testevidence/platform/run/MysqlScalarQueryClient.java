package com.talkanything.testevidence.platform.run;

import java.sql.SQLException;
import java.util.List;

public interface MysqlScalarQueryClient {
    ScalarQueryResult query(String sql, List<Object> parameters, MysqlScalarValueType expectedType) throws SQLException;
}
