package com.alibaba.druid.pool;

public final class DruidPooledPreparedStatement {
    private final com.mysql.cj.jdbc.ClientPreparedStatement delegate;
    private final String sql;

    public DruidPooledPreparedStatement(String sql) {
        this.sql = sql;
        this.delegate = new com.mysql.cj.jdbc.ClientPreparedStatement(sql);
    }

    public String getPreparedSql() { return sql; }
    public void setString(int index, String value) { delegate.setString(index, value); }
    public int executeUpdate() { return delegate.executeUpdate(); }
}
