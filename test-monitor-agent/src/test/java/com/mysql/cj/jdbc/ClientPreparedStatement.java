package com.mysql.cj.jdbc;

public class ClientPreparedStatement {
    private final String sql;

    public ClientPreparedStatement(String sql) {
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
