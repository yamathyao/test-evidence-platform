package com.talkanything.testevidence.agent;

import java.lang.instrument.Instrumentation;
import com.talkanything.testevidence.agent.dubbo.DubboEvidenceRuntime;
import com.talkanything.testevidence.agent.feign.FeignInstrumentation;
import com.talkanything.testevidence.agent.blackbox.BlackboxCorrelationRuntime;
import com.talkanything.testevidence.agent.dubbo.DubboInstrumentation;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import com.talkanything.testevidence.agent.http.ApacheHttpClientInstrumentation;
import com.talkanything.testevidence.agent.http.HttpClientEvidenceRuntime;
import com.talkanything.testevidence.agent.http.HttpEvidenceRuntime;
import com.talkanything.testevidence.agent.http.OkHttpInstrumentation;
import com.talkanything.testevidence.agent.http.WebClientInstrumentation;
import com.talkanything.testevidence.agent.http.HttpInstrumentation;
import com.talkanything.testevidence.agent.jdbc.JdbcEvidenceRuntime;
import com.talkanything.testevidence.agent.jdbc.MySqlJdbcInstrumentation;

public final class TestMonitorAgent {
    private TestMonitorAgent() {
    }

    public static void premain(String arguments, Instrumentation instrumentation) {
        AgentConfiguration configuration = AgentConfiguration.parse(arguments);
        System.setProperty("test.monitor.agent.service", configuration.serviceName());
        System.setProperty("test.monitor.agent.dubbo.evidence.enabled", "true");
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(1000, 20,
                AsyncEvidenceReporter.httpSink(configuration.collectorUrl(), configuration.token()));
        HttpEvidenceRuntime.initialize(configuration.serviceName(), reporter);
        HttpClientEvidenceRuntime.initialize(configuration.serviceName(), reporter);
        JdbcEvidenceRuntime.initialize(configuration.serviceName(), reporter, configuration.jdbcIgnoreSqlPrefixes(),
                configuration.jdbcIgnoreSqlRegexes());
        DubboEvidenceRuntime.initialize(configuration.serviceName(), reporter);
        BlackboxCorrelationRuntime.initialize(configuration.collectorUrl(), configuration.serviceName(), configuration.token());
        HttpInstrumentation.install(instrumentation);
        ApacheHttpClientInstrumentation.install(instrumentation);
        OkHttpInstrumentation.install(instrumentation);
        FeignInstrumentation.install(instrumentation);
        WebClientInstrumentation.install(instrumentation);
        DubboInstrumentation.install(instrumentation);
        MySqlJdbcInstrumentation.install(instrumentation);
    }
}
