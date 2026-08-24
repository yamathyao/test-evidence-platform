package com.talkanything.testevidence.agent.blackbox;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlackboxRuleCodecTest {
    @Test
    void decodesBase64UrlProtocolIntoMatcherGroup() {
        String lines = line("rule", "run", "case", "profile", "1", "4102444800000", "order", "orderNo", "[\"QUERY\"]", "ORD-1")
                + "\n" + line("rule", "run", "case", "profile", "1", "4102444800000", "order", "tenantId", "[\"HEADER\"]", "tenant-a");

        List<BlackboxRule> rules = BlackboxRuleCodec.decode(lines);

        assertEquals(1, rules.size());
        assertEquals(2, rules.get(0).matchers().size());
        assertEquals("run", rules.get(0).runId());
    }

    @Test
    void decodesPayloadCaptureFlagAndDefaultsLegacyRulesToDisabled() {
        List<BlackboxRule> enabled = BlackboxRuleCodec.decode(
                line("rule", "run", "case", "profile", "1", "4102444800000", "order", "orderNo", "[\"QUERY\"]", "ORD-1", "true"));
        List<BlackboxRule> legacy = BlackboxRuleCodec.decode(
                line("legacy", "run", "case", "profile", "1", "4102444800000", "order", "orderNo", "[\"QUERY\"]", "ORD-1"));

        assertEquals(true, enabled.get(0).payloadCaptureEnabled());
        assertEquals(false, legacy.get(0).payloadCaptureEnabled());
    }

    private String line(String... values) {
        StringBuilder line = new StringBuilder();
        for (int index = 0; index < values.length; index++) {
            if (index > 0) line.append('\t');
            line.append(Base64.getUrlEncoder().withoutPadding().encodeToString(values[index].getBytes(StandardCharsets.UTF_8)));
        }
        return line.toString();
    }
}
