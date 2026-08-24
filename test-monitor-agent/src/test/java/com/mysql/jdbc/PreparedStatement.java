package com.mysql.jdbc;

public class PreparedStatement {
    private final String sql;

    public PreparedStatement(String sql) {
        this.sql = sql;
    }

    public String getOriginalSql() {
        return sql;
    }

    public void setString(int index, String value) { }

    public int executeUpdate() {
        return 1;
    }
}
