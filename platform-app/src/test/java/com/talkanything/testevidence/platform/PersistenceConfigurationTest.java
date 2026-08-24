package com.talkanything.testevidence.platform;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class PersistenceConfigurationTest {
    @Autowired
    private DataSource dataSource;

    @Test
    void providesDataSourceForPlatformPersistence() {
        assertNotNull(dataSource);
    }
}
