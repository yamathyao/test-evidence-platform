package com.mysql.cj.jdbc;

public class StatementImpl {
    public boolean execute(String sql) { return true; }
    public int executeUpdate(String sql) { return 1; }
    public java.sql.ResultSet executeQuery(String sql) { return null; }
    public long executeLargeUpdate(String sql) { return 1L; }
    public boolean executeBatch() { return true; }
}