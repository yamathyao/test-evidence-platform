package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.profile.BlackboxCorrelationDefinition;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlackboxCorrelationService {
    private final BlackboxCorrelationRuleRepository repository;
    private final ObjectMapper objectMapper;

    BlackboxCorrelationService(BlackboxCorrelationRuleRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void create(TestRun run, BlackboxCorrelationDefinition definition, String definitionJson,
                       Map<String, String> values, Integer ttlSeconds) {
        int ttl = ttl(definition, ttlSeconds);
        if (!definition.accepts(values)) throw new IllegalArgumentException("Invalid blackbox correlation data");
        repository.save(new BlackboxCorrelationRule(run, json(definition.targetServices()), definitionJson, json(values),
                Instant.now().plusSeconds(ttl)));
    }

    @Transactional(readOnly = true)
    public String activeProtocol(String service) {
        if (blank(service)) throw new IllegalArgumentException("Invalid service");
        List<String> lines = new ArrayList<String>();
        for (BlackboxCorrelationRule rule : repository.findByStatusAndExpiresAtAfter(BlackboxCorrelationRuleStatus.ACTIVE, Instant.now())) {
            if (targets(rule, service)) lines.addAll(lines(rule));
        }
        return String.join("\n", lines);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveRule(UUID runId) {
        if (runId == null) throw new IllegalArgumentException("Invalid run");
        return !repository.findByTestRun_IdAndStatus(runId, BlackboxCorrelationRuleStatus.ACTIVE).isEmpty();
    }

    @Transactional
    public void bind(UUID runId) {
        List<BlackboxCorrelationRule> active = repository.findByTestRun_IdAndStatus(runId, BlackboxCorrelationRuleStatus.ACTIVE);
        if (active.isEmpty()) return;
        BlackboxCorrelationDefinition definition = definition(active.get(0));
        Instant now = Instant.now();
        repository.bindActive(runId, BlackboxCorrelationRuleStatus.ACTIVE, BlackboxCorrelationRuleStatus.BOUND, now,
                now.plusSeconds(definition.retentionSeconds()));
    }

    @Transactional
    public void discardActive(UUID runId) {
        repository.deleteByTestRun_IdAndStatus(runId, BlackboxCorrelationRuleStatus.ACTIVE);
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional
    public void cleanExpired() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    private int ttl(BlackboxCorrelationDefinition definition, Integer requested) {
        if (requested == null) return definition.defaultTtlSeconds();
        if (requested < 60 || requested > 3600) {
            throw new IllegalArgumentException("Invalid blackbox correlation ttl");
        }
        return requested;
    }

    private boolean targets(BlackboxCorrelationRule rule, String service) {
        try {
            JsonNode services = objectMapper.readTree(rule.getTargetServicesJson());
            if (services.isArray() && services.isEmpty()) return true;
            for (JsonNode item : services) if ("*".equals(item.asText()) || service.equals(item.asText())) return true;
            return false;
        } catch (Exception exception) {
            return false;
        }
    }

    private List<String> lines(BlackboxCorrelationRule rule) {
        try {
            JsonNode correlation = objectMapper.readTree(rule.getDefinitionJson()).path("blackboxCorrelation");
            JsonNode values = objectMapper.readTree(rule.getValueJson());
            List<String> result = new ArrayList<String>();
            for (JsonNode group : correlation.path("matchGroups")) {
                for (JsonNode matcher : group.path("matchers")) result.add(line(rule, group, matcher, values));
            }
            return result;
        } catch (Exception exception) {
            return List.of();
        }
    }

    private String line(BlackboxCorrelationRule rule, JsonNode group, JsonNode matcher, JsonNode values) {
        return String.join("\t", encoded(rule.getId().toString()), encoded(rule.getTestRunId().toString()),
                encoded(rule.getTestCaseId().toString()), encoded(rule.getProfileId().toString()),
                encoded(String.valueOf(rule.getProfileVersion())), encoded(String.valueOf(rule.getExpiresAt().toEpochMilli())),
                encoded(group.path("name").asText()), encoded(matcher.path("field").asText()),
                encoded(matcher.path("locations").toString()), encoded(values.path(matcher.path("field").asText()).asText()),
                encoded(String.valueOf(rule.isHttpPayloadCaptureEnabled())));
    }

    private BlackboxCorrelationDefinition definition(BlackboxCorrelationRule rule) {
        try { return BlackboxCorrelationDefinition.parse(objectMapper.readTree(rule.getDefinitionJson())); }
        catch (Exception exception) { throw new IllegalStateException("Invalid blackbox correlation rule"); }
    }
    private String json(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private String encoded(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
}
