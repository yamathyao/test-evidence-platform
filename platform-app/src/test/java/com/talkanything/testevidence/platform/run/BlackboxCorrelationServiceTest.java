package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import java.util.List;
import java.util.Map;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class BlackboxCorrelationServiceTest {
    @Autowired private BlackboxCorrelationService service;
    @Autowired private BlackboxCorrelationRuleRepository repository;
    @Autowired private TestRunService runService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private CaptureProfileService profileService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void distributesOnlyToTargetServiceAndRemainsActiveDuringCollection() throws Exception {
        TestRun run = runService.startBlackbox(browserCase(), Map.of("orderNo", "ORD-1", "tenantId", "tenant-a"), null);

        String protocol = service.activeProtocol("order-service");

        assertFalse(protocol.isEmpty());
        assertFalse(protocol.contains("ORD-1"));
        assertTrue(service.activeProtocol("inventory-service").isEmpty());
        assertFalse(repository.findByTestRun_IdAndStatus(run.getId(), BlackboxCorrelationRuleStatus.ACTIVE).isEmpty());
        assertFalse(service.activeProtocol("order-service").isEmpty());
    }

    @Test
    void distributesAnUnscopedRuleToMultipleAgents() throws Exception {
        TestRun run = runService.startBlackbox(browserCaseWithoutServiceScope(),
                Map.of("orderNo", "ORD-2", "tenantId", "tenant-a"), null);

        assertFalse(service.activeProtocol("order").isEmpty());
        assertFalse(service.activeProtocol("fullfill").isEmpty());
        assertFalse(repository.findByTestRun_IdAndStatus(run.getId(), BlackboxCorrelationRuleStatus.ACTIVE).isEmpty());
    }

    @Test
    void removesExpiredActiveRule() throws Exception {
        TestRun run = runService.startBlackbox(browserCase(), Map.of("orderNo", "ORD-4", "tenantId", "tenant-a"), null);
        BlackboxCorrelationRule rule = activeRule(run);
        jdbcTemplate.update("update blackbox_correlation_rule set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), rule.getId());

        service.cleanExpired();

        assertTrue(repository.findById(rule.getId()).isEmpty());
    }

    @Test
    void retainsBoundRuleUntilRetentionDeadlineThenRemovesIt() throws Exception {
        TestRun run = runService.startBlackbox(browserCase(), Map.of("orderNo", "ORD-5", "tenantId", "tenant-a"), null);
        BlackboxCorrelationRule active = activeRule(run);
        service.bind(run.getId());
        assertEquals(BlackboxCorrelationRuleStatus.BOUND,
                repository.findById(active.getId()).orElseThrow().getStatus());

        jdbcTemplate.update("update blackbox_correlation_rule set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().plusSeconds(60)), active.getId());
        service.cleanExpired();
        assertTrue(repository.findById(active.getId()).isPresent());

        jdbcTemplate.update("update blackbox_correlation_rule set expires_at = ? where id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)), active.getId());
        service.cleanExpired();
        assertTrue(repository.findById(active.getId()).isEmpty());
    }
    @Test
    void keepsRuleActiveForTheWholeBrowserCaptureWindow() throws Exception {
        CaptureProfile profile = profileService.create("blackbox-window-" + System.nanoTime(), 1,
                definition().replace("\"defaultTtlSeconds\":600", "\"defaultTtlSeconds\":60"));
        TestCase testCase = testCaseService.create(new TestCaseRequest("long browser window", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 1800, List.of()));

        TestRun run = runService.startBlackbox(testCase, Map.of("orderNo", "ORD-3", "tenantId", "tenant-a"), null);
        BlackboxCorrelationRule rule = repository.findByTestRun_IdAndStatus(run.getId(), BlackboxCorrelationRuleStatus.ACTIVE).get(0);

        assertFalse(rule.getExpiresAt().isBefore(run.getStartedAt().plus(Duration.ofSeconds(1799))));
    }

    private BlackboxCorrelationRule activeRule(TestRun run) {
        List<BlackboxCorrelationRule> rules = repository.findByTestRun_IdAndStatus(run.getId(), BlackboxCorrelationRuleStatus.ACTIVE);
        assertEquals(1, rules.size());
        return rules.get(0);
    }
    private TestCase browserCase() throws Exception {
        CaptureProfile profile = profileService.create("blackbox-service-" + System.nanoTime(), 1, definition());
        return testCaseService.create(new TestCaseRequest("blackbox service", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
    }

    private TestCase browserCaseWithoutServiceScope() throws Exception {
        CaptureProfile profile = profileService.create("blackbox-unscoped-" + System.nanoTime(), 1,
                definition().replace("\"singleUse\":true,\"targetServices\":[\"order-service\"],", ""));
        return testCaseService.create(new TestCaseRequest("blackbox unscoped", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
    }

    private String definition() {
        return "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":600,\"retentionSeconds\":3600,\"singleUse\":true,"
                + "\"targetServices\":[\"order-service\"],\"matchGroups\":[{\"name\":\"order\",\"matchers\":["
                + "{\"field\":\"orderNo\",\"locations\":[\"QUERY\"],\"match\":\"EXACT\"},"
                + "{\"field\":\"tenantId\",\"locations\":[\"HEADER\"],\"match\":\"EXACT\"}]}]}}";
    }
}
