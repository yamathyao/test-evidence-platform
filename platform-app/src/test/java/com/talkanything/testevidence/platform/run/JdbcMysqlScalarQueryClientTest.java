package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JdbcMysqlScalarQueryClientTest {
    private final DataSource dataSource = dataSource();
    private final JdbcMysqlScalarQueryClient client = new JdbcMysqlScalarQueryClient(dataSource);

    @BeforeEach
    void setUp() throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS scalar_sample");
            statement.execute("CREATE TABLE scalar_sample (id VARCHAR(20), text_value VARCHAR(30), numeric_value DECIMAL(10,2), boolean_value BOOLEAN)");
            statement.execute("INSERT INTO scalar_sample VALUES ('item-1', 'PAID', 12.50, TRUE)");
            statement.execute("INSERT INTO scalar_sample VALUES ('item-2', NULL, 7.00, FALSE)");
        }
    }

    @Test
    void bindsParametersAndReadsExpectedJdbcTypes() throws Exception {
        ScalarQueryResult text = client.query("SELECT text_value FROM scalar_sample WHERE id = ?", List.of("item-1"),
                MysqlScalarValueType.STRING);
        ScalarQueryResult number = client.query("SELECT numeric_value FROM scalar_sample WHERE id = ?", List.of("item-1"),
                MysqlScalarValueType.NUMBER);
        ScalarQueryResult bool = client.query("SELECT boolean_value FROM scalar_sample WHERE id = ?", List.of("item-1"),
                MysqlScalarValueType.BOOLEAN);
        ScalarQueryResult nullValue = client.query("SELECT text_value FROM scalar_sample WHERE id = ?", List.of("item-2"),
                MysqlScalarValueType.NULL);

        assertEquals("PAID", text.value());
        assertEquals(new BigDecimal("12.50"), number.value());
        assertEquals(true, bool.value());
        assertEquals(null, nullValue.value());
    }

    @Test
    void rejectsResultsThatAreNotExactlyOneRowAndOneColumn() {
        assertShapeFailure("SELECT text_value FROM scalar_sample WHERE id = 'missing'");
        assertShapeFailure("SELECT text_value FROM scalar_sample");
        assertShapeFailure("SELECT id, text_value FROM scalar_sample WHERE id = 'item-1'");
    }

    private void assertShapeFailure(String sql) {
        MysqlScalarQueryException exception = assertThrows(MysqlScalarQueryException.class,
                () -> client.query(sql, List.of(), MysqlScalarValueType.STRING));
        assertEquals("MYSQL_SCALAR query did not return exactly one row and one column", exception.getMessage());
    }

    private static DataSource dataSource() {
        JdbcDataSource value = new JdbcDataSource();
        value.setURL("jdbc:h2:mem:mysql_scalar_client;DB_CLOSE_DELAY=-1");
        value.setUser("sa");
        return value;
    }
}
