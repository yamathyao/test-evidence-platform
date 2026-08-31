package com.talkanything.testevidence.sample.fulfillment;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Service
public class ProtocolProbeService {
    private final DataSource dataSource;

    public ProtocolProbeService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ProtocolEchoResponse echo(String orderNo) {
        validateOrderNo(orderNo);
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT 1")) {
            if (!resultSet.next()) {
                throw new IllegalStateException("Protocol probe returned no row");
            }
            return new ProtocolEchoResponse("fulfillment", orderNo, resultSet.getInt(1));
        } catch (java.sql.SQLException exception) {
            throw new IllegalStateException("Protocol probe query failed", exception);
        }
    }

    private void validateOrderNo(String orderNo) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > 128) {
            throw new IllegalArgumentException("Invalid order number");
        }
    }
}