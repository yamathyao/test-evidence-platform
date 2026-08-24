package com.talkanything.testevidence.platform.run;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("test-evidence.assertion-mysql")
public record MysqlAssertionProperties(String url, String username, String password) { }
