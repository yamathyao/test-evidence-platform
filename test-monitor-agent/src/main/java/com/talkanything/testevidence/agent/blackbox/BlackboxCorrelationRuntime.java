package com.talkanything.testevidence.agent.blackbox;

import com.talkanything.testevidence.agent.http.AgentDiagnostics;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public final class BlackboxCorrelationRuntime {
    private static volatile BlackboxCorrelationRuntime current;
    private final String collectorUrl;
    private final String serviceName;
    private final String token;
    private final BlackboxValueMatcher matcher = new BlackboxValueMatcher();
    private volatile List<BlackboxRule> rules = Collections.emptyList();

    public BlackboxCorrelationRuntime(String collectorUrl, String serviceName, String token) {
        this.collectorUrl = collectorUrl;
        this.serviceName = serviceName;
        this.token = token;
    }

    public static void initialize(String collectorUrl, String serviceName, String token) {
        BlackboxCorrelationRuntime runtime = new BlackboxCorrelationRuntime(collectorUrl, serviceName, token);
        current = runtime;
        runtime.start();
    }

    public static BlackboxCorrelationRuntime current() { return current; }

    public BlackboxRule match(Map<String, String> values) {
        List<BlackboxRule> snapshot = rules;
        for (int index = 0; index < snapshot.size(); index++) {
            BlackboxRule rule = snapshot.get(index);
            if (matcher.match(rule, values)) return rule;
        }
        return null;
    }

    public BlackboxRule matchRequest(BlackboxRequestValues values) {
        List<BlackboxRule> snapshot = rules;
        for (int index = 0; index < snapshot.size(); index++) {
            BlackboxRule rule = snapshot.get(index);
            if (matcher.matchRequest(rule, values)) return rule;
        }
        return null;
    }

    public boolean hasPayloadCaptureJsonBodyRule() {
        List<BlackboxRule> snapshot = rules;
        for (int index = 0; index < snapshot.size(); index++) {
            BlackboxRule rule = snapshot.get(index);
            if (!rule.payloadCaptureEnabled() || rule.expired(System.currentTimeMillis())) continue;
            for (BlackboxRule.Matcher matcher : rule.matchers()) {
                if (matcher.locations().contains("JSON_BODY")) return true;
            }
        }
        return false;
    }

    public void refreshNow() {
        try {
            List<BlackboxRule> refreshed = BlackboxRuleCodec.decode(fetch());
            rules = refreshed;
            AgentDiagnostics.log("blackbox.rule-refresh.service=" + serviceName + " count=" + refreshed.size());
        } catch (Exception exception) {
            AgentDiagnostics.log("blackbox.rule-refresh.failure=" + exception.getClass().getSimpleName()
                    + " service=" + serviceName + " message=" + exception.getMessage());
        }
    }

    private void start() {
        Thread thread = new Thread(new Runnable() {
            @Override public void run() {
                while (!Thread.currentThread().isInterrupted()) {
                    refreshNow();
                    try { Thread.sleep(5000L); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
                }
            }
        }, "test-blackbox-rule-refresh");
        thread.setDaemon(true);
        thread.start();
    }

    private String fetch() throws Exception {
        String query = URLEncoder.encode(serviceName, "UTF-8");
        HttpURLConnection connection = (HttpURLConnection) new URL(collectorUrl
                + "/internal/v1/blackbox-correlation-rules?service=" + query).openConnection();
        connection.setConnectTimeout(1000);
        connection.setReadTimeout(1000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("X-Test-Agent-Token", token);
        try {
            int status = connection.getResponseCode();
            if (status != 200) throw new IllegalStateException("Rule endpoint rejected request: " + status);
            return read(connection.getInputStream());
        } finally {
            connection.disconnect();
        }
    }

    private String read(InputStream input) throws Exception {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            input.close();
        }
    }
}
