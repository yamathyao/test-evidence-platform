package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.talkanything.testevidence.autoconfigure.MysqlAssertionAutoConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MysqlAssertionDataSourceConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MysqlAssertionAutoConfiguration.class));

    @Test
    void doesNotCreateDataSourceWithoutCompleteCredentials() {
        contextRunner.run(context -> assertFalse(context.containsBean("mysqlAssertionDataSource")));
    }

    @Test
    void doesNotCreateDataSourceWithBlankCredentials() {
        contextRunner.withPropertyValues(
                "test-evidence.assertion-mysql.url=jdbc:h2:mem:mysql_assertion",
                "test-evidence.assertion-mysql.username=sa",
                "test-evidence.assertion-mysql.password= ")
                .run(context -> assertFalse(context.containsBean("mysqlAssertionDataSource")));
    }

    @Test
    void createsReadOnlyBoundedDataSourceWithCompleteCredentials() {
        contextRunner.withPropertyValues(
                "test-evidence.assertion-mysql.url=jdbc:h2:mem:mysql_assertion",
                "test-evidence.assertion-mysql.username=sa",
                "test-evidence.assertion-mysql.password=password")
                .run(context -> {
                    HikariDataSource dataSource = context.getBean("mysqlAssertionDataSource", HikariDataSource.class);
                    assertEquals("jdbc:h2:mem:mysql_assertion", dataSource.getJdbcUrl());
                    assertTrue(dataSource.isReadOnly());
                    assertEquals(2, dataSource.getMaximumPoolSize());
                    assertEquals(2_000L, dataSource.getConnectionTimeout());
                });
    }

    @Test
    void createsJdbcQueryClientWhenAssertionDataSourceIsConfigured() {
        contextRunner.withPropertyValues(
                        "test-evidence.assertion-mysql.url=jdbc:h2:mem:mysql_assertion_client",
                        "test-evidence.assertion-mysql.username=sa",
                        "test-evidence.assertion-mysql.password=password")
                .run(context -> assertTrue(context.containsBean("jdbcMysqlScalarQueryClient")));
    }
}
