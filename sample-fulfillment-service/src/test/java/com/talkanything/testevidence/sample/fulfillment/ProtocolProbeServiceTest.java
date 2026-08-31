package com.talkanything.testevidence.sample.fulfillment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolProbeServiceTest {
    private ProtocolProbeService service;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("protocol-probe")
                .build();
        service = new ProtocolProbeService(database);
    }

    @Test
    void executesFixedStatementAndEchoesOrderNumber() {
        ProtocolEchoResponse response = service.echo("matrix-order-1");

        assertEquals("fulfillment", response.service());
        assertEquals("matrix-order-1", response.orderNo());
        assertEquals(1, response.statementValue());
    }

    @Test
    void rejectsBlankOrderNumber() {
        assertThrows(IllegalArgumentException.class, () -> service.echo(" "));
    }
}