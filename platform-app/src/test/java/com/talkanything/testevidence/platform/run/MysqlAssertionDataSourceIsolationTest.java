package com.talkanything.testevidence.platform.run;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.talkanything.testevidence.platform.PlatformApplication;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = PlatformApplication.class,
        properties = {
                "spring.datasource.url=jdbc:h2:mem:platform_primary",
                "spring.datasource.username=sa",
                "spring.datasource.password=",
                "spring.flyway.enabled=false",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "TEST_EVIDENCE_AGENT_TOKEN=isolation-test-token",
                "test-evidence.assertion-mysql.url=jdbc:h2:mem:assertion_secondary",
                "test-evidence.assertion-mysql.username=sa",
                "test-evidence.assertion-mysql.password=password"
        })
class MysqlAssertionDataSourceIsolationTest {
    @Autowired private DataSource dataSource;
    @Autowired @Qualifier("mysqlAssertionDataSource") private HikariDataSource mysqlAssertionDataSource;

    @Test
    void keepsPlatformDataSourceAsDefaultWhenMysqlAssertionsAreConfigured() {
        HikariDataSource platformDataSource = (HikariDataSource) dataSource;

        assertEquals("jdbc:h2:mem:platform_primary", platformDataSource.getJdbcUrl());
        assertEquals("jdbc:h2:mem:assertion_secondary", mysqlAssertionDataSource.getJdbcUrl());
    }
}
