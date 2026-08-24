package com.talkanything.testevidence.platform.run;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

public class JdbcMysqlScalarQueryClient implements MysqlScalarQueryClient {
    private static final String INVALID_SHAPE = "MYSQL_SCALAR query did not return exactly one row and one column";
    private final DataSource dataSource;

    public JdbcMysqlScalarQueryClient(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ScalarQueryResult query(String sql, List<Object> parameters, MysqlScalarValueType expectedType)
            throws SQLException {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setQueryTimeout(2);
            for (int index = 0; index < parameters.size(); index++) statement.setObject(index + 1, parameters.get(index));
            try (ResultSet resultSet = statement.executeQuery()) {
                ResultSetMetaData metadata = resultSet.getMetaData();
                if (metadata.getColumnCount() != 1 || !resultSet.next()) throw invalidShape();
                Object value = value(resultSet, expectedType);
                if (resultSet.next()) throw invalidShape();
                return ScalarQueryResult.from(value, expectedType);
            }
        }
    }

    private Object value(ResultSet resultSet, MysqlScalarValueType type) throws SQLException {
        return switch (type) {
            case STRING -> resultSet.getString(1);
            case NUMBER -> resultSet.getBigDecimal(1);
            case BOOLEAN -> booleanValue(resultSet);
            case NULL -> resultSet.getObject(1);
        };
    }

    private Boolean booleanValue(ResultSet resultSet) throws SQLException {
        boolean value = resultSet.getBoolean(1);
        return resultSet.wasNull() ? null : value;
    }

    private MysqlScalarQueryException invalidShape() {
        return new MysqlScalarQueryException(INVALID_SHAPE);
    }
}
