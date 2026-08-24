package com.talkanything.testevidence.autoconfigure;

import com.talkanything.testevidence.platform.run.JdbcMysqlScalarQueryClient;
import com.talkanything.testevidence.platform.run.MysqlAssertionProperties;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;

@AutoConfiguration(after = DataSourceAutoConfiguration.class)
@EnableConfigurationProperties(MysqlAssertionProperties.class)
@Conditional(MysqlAssertionAutoConfiguration.MysqlAssertionConfigurationComplete.class)
public class MysqlAssertionAutoConfiguration {
    @Bean(name = "mysqlAssertionDataSource")
    HikariDataSource mysqlAssertionDataSource(MysqlAssertionProperties properties) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(properties.url());
        dataSource.setUsername(properties.username());
        dataSource.setPassword(properties.password());
        dataSource.setReadOnly(true);
        dataSource.setMaximumPoolSize(2);
        dataSource.setConnectionTimeout(2_000L);
        dataSource.setValidationTimeout(1_000L);
        return dataSource;
    }

    @Bean
    JdbcMysqlScalarQueryClient jdbcMysqlScalarQueryClient(
            @Qualifier("mysqlAssertionDataSource") DataSource dataSource) {
        return new JdbcMysqlScalarQueryClient(dataSource);
    }

    static final class MysqlAssertionConfigurationComplete implements Condition {
        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return hasText(context, "test-evidence.assertion-mysql.url")
                    && hasText(context, "test-evidence.assertion-mysql.username")
                    && hasText(context, "test-evidence.assertion-mysql.password");
        }

        private boolean hasText(ConditionContext context, String name) {
            String value = context.getEnvironment().getProperty(name);
            return value != null && !value.trim().isEmpty();
        }
    }
}
