package com.talkanything.testevidence.sample.fulfillment;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.ResultSet;
import java.util.Locale;
import java.util.Optional;

@Repository
public class FulfillmentRepository {
    private static final String TABLE = "test_evidence_sample_fulfillments";
    private static final String CREATE = "CREATE TABLE IF NOT EXISTS " + TABLE
            + " (order_no VARCHAR(128) PRIMARY KEY, sku VARCHAR(64) NOT NULL, quantity INT NOT NULL,"
            + " note VARCHAR(256) NOT NULL, status VARCHAR(32) NOT NULL, fulfilled_at TIMESTAMP NOT NULL)";
    private static final String DELETE = "DELETE FROM " + TABLE + " WHERE order_no = ?";
    private static final String INSERT = "INSERT INTO " + TABLE
            + " (order_no, sku, quantity, note, status, fulfilled_at) VALUES (?, ?, ?, ?, ?, ?)";
    private static final String FIND = "SELECT order_no, sku, quantity, note, status, fulfilled_at FROM " + TABLE
            + " WHERE order_no = ?";

    private final JdbcTemplate jdbcTemplate;

    public FulfillmentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public FulfillmentRecord fulfill(FulfillmentRecord record) {
        ensureTable();
        jdbcTemplate.update(DELETE, record.orderNo());
        jdbcTemplate.update(INSERT, record.orderNo(), record.sku(), record.quantity(), record.note(), record.status(),
                Timestamp.from(record.fulfilledAt()));
        return record;
    }

    public void delete(String orderNo) {
        ensureTable();
        jdbcTemplate.update(DELETE, orderNo);
    }

    public Optional<FulfillmentRecord> find(String orderNo) {
        ensureTable();
        return jdbcTemplate.query(FIND, (resultSet, rowNumber) -> new FulfillmentRecord(
                resultSet.getString("order_no"), resultSet.getString("sku"), resultSet.getInt("quantity"),
                resultSet.getString("note"), resultSet.getString("status"),
                resultSet.getTimestamp("fulfilled_at").toInstant()), orderNo).stream().findFirst();
    }

    private void ensureTable() {
        jdbcTemplate.execute(CREATE);
        addColumnIfMissing("sku", "VARCHAR(64) NOT NULL DEFAULT ''");
        addColumnIfMissing("quantity", "INT NOT NULL DEFAULT 1");
        addColumnIfMissing("note", "VARCHAR(256) NOT NULL DEFAULT ''");
        addColumnIfMissing("fulfilled_at", "TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP");
    }

    private void addColumnIfMissing(String name, String definition) {
        if (!columnExists(name)) {
            jdbcTemplate.execute("ALTER TABLE " + TABLE + " ADD COLUMN " + name + " " + definition);
        }
    }

    private boolean columnExists(String name) {
        return Boolean.TRUE.equals(jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
            return containsColumn(connection, TABLE, name)
                    || containsColumn(connection, TABLE.toUpperCase(Locale.ROOT), name);
        }));
    }

    private boolean containsColumn(java.sql.Connection connection, String table, String columnName) throws java.sql.SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
            while (columns.next()) {
                if (columnName.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
