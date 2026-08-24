package com.talkanything.testevidence.sample.fulfillment;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillmentRepositoryTest {
    private JdbcTemplate jdbc;
    private FulfillmentRepository repository;

    @BeforeEach
    void setUp() {
        EmbeddedDatabase database = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .setName("fulfillment-repository")
                .build();
        jdbc = new JdbcTemplate(database);
        repository = new FulfillmentRepository(jdbc);
    }

    @Test
    void writesAndReadsDetailedFulfillmentInFixedTable() {
        FulfillmentRecord expected = new FulfillmentRecord(
                "P20-ORDER-1", "SKU-COFFEE", 2, "blackbox", "FULFILLED",
                java.time.Instant.parse("2026-08-19T15:30:00Z"));

        assertEquals(expected, repository.fulfill(expected));
        assertEquals(expected, repository.find("P20-ORDER-1").orElseThrow());
    }

    @Test
    void deletesExistingFulfillment() {
        repository.fulfill(new FulfillmentRecord(
                "P20-ORDER-2", "SKU-TEA", 1, "", "FULFILLED",
                java.time.Instant.parse("2026-08-19T15:30:00Z")));

        repository.delete("P20-ORDER-2");

        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM test_evidence_sample_fulfillments WHERE order_no = ?",
                Integer.class, "P20-ORDER-2"));
    }

    @Test
    void returnsEmptyWhenFulfillmentDoesNotExist() {
        assertTrue(repository.find("P20-UNKNOWN").isEmpty());
    }

    @Test
    void upgradesTheLegacyTwoColumnSampleTableBeforeWriting() {
        jdbc.execute("DROP TABLE test_evidence_sample_fulfillments");
        jdbc.execute("CREATE TABLE test_evidence_sample_fulfillments "
                + "(order_no VARCHAR(128) PRIMARY KEY, status VARCHAR(32) NOT NULL)");
        FulfillmentRecord record = new FulfillmentRecord(
                "P20-UPGRADE-1", "SKU-COOKIE", 3, "legacy table", "FULFILLED",
                java.time.Instant.parse("2026-08-19T15:30:00Z"));

        repository.fulfill(record);

        assertEquals("SKU-COOKIE", jdbc.queryForObject(
                "SELECT sku FROM test_evidence_sample_fulfillments WHERE order_no = ?",
                String.class, "P20-UPGRADE-1"));
    }
}
