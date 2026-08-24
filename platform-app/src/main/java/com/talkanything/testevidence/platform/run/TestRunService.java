package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.casefile.Assertion;
import com.talkanything.testevidence.platform.evidence.EvidenceEventRepository;
import com.talkanything.testevidence.platform.profile.BlackboxCorrelationDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import com.talkanything.testevidence.platform.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class TestRunService {
    private final TestRunRepository repository;
    private final HttpTriggerClient triggerClient;
    private final ObjectMapper objectMapper;
    private final AssertionResultRepository assertionResultRepository;
    private final HttpAssertionEvaluator assertionEvaluator;
    private final MysqlScalarAssertionEvaluator mysqlScalarAssertionEvaluator;
    private final BlackboxCorrelationService blackboxCorrelationService;
    private final EvidenceEventRepository evidenceEventRepository;
    private final int drainPeriodSeconds;
    private final TransactionTemplate transactionTemplate;

    TestRunService(TestRunRepository repository, HttpTriggerClient triggerClient, ObjectMapper objectMapper,
                   AssertionResultRepository assertionResultRepository, HttpAssertionEvaluator assertionEvaluator,
                   MysqlScalarAssertionEvaluator mysqlScalarAssertionEvaluator,
                   BlackboxCorrelationService blackboxCorrelationService, EvidenceEventRepository evidenceEventRepository,
                   @Value("${test-evidence.blackbox-drain-period-seconds:10}") int drainPeriodSeconds,
                   PlatformTransactionManager transactionManager) {
        this.repository = repository; this.triggerClient = triggerClient; this.objectMapper = objectMapper;
        this.assertionResultRepository = assertionResultRepository; this.assertionEvaluator = assertionEvaluator;
        this.mysqlScalarAssertionEvaluator = mysqlScalarAssertionEvaluator;
        this.blackboxCorrelationService = blackboxCorrelationService;
        this.evidenceEventRepository = evidenceEventRepository; this.drainPeriodSeconds = drainPeriodSeconds;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public TestRun startBlackbox(TestCase testCase, Map<String, String> values, Integer ttlSeconds) {
        if (testCase.getTriggerType() != TriggerType.BROWSER) {
            throw new IllegalStateException("Blackbox run requires BROWSER trigger");
        }
        BlackboxCorrelationDefinition definition = BlackboxCorrelationDefinition.parse(config(testCase.getProfile().getDefinitionJson()));
        TestRun run = repository.save(new TestRun(testCase, caseSnapshot(testCase).toString()));
        run.start();
        int ruleTtlSeconds = ttlSeconds == null ? testCase.getTimeoutSeconds() : ttlSeconds;
        blackboxCorrelationService.create(run, definition, testCase.getProfile().getDefinitionJson(), values, ruleTtlSeconds);
        return run;
    }

    @Transactional
    public TestRun complete(UUID id) {
        TestRun run = find(id);
        if (run.getTestCase().getTriggerType() != TriggerType.BROWSER) {
            throw new IllegalStateException("Blackbox completion requires BROWSER trigger");
        }
        if (run.getStatus() == RunStatus.RUNNING) beginDraining(run, TestRunFinishReason.MANUAL);
        return run;
    }

    public TestRun start(TestCase testCase) {
        if (testCase.getTriggerType() != TriggerType.HTTP) throw new IllegalStateException("Only HTTP trigger is available in P1");
        HttpRunStart started = transactionTemplate.execute(status -> prepareHttpRun(testCase));
        try {
            HttpTriggerResponse response = triggerClient.execute(started.config(), started.context());
            return transactionTemplate.execute(status -> settleHttpRun(started.runId(), response));
        } catch (Exception exception) {
            return transactionTemplate.execute(status -> failHttpRun(started.runId(), exception));
        }
    }

    public TestRun find(UUID id) { return repository.findById(id).orElseThrow(() -> new NotFoundException("Run not found")); }
    public List<AssertionResult> results(UUID runId) { return assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(runId); }
    public long rootTraceCount(UUID runId) { return evidenceEventRepository.countDistinctTraceIdsByTestRunId(runId); }

    public long remainingCaptureSeconds(TestRun run) {
        if (run.getTestCase().getTriggerType() != TriggerType.BROWSER || run.getStatus() != RunStatus.RUNNING) return 0;
        long deadline = run.getStartedAt().plusSeconds(run.getTestCase().getTimeoutSeconds()).getEpochSecond();
        return Math.max(0, deadline - Instant.now().getEpochSecond());
    }

    @Transactional(readOnly = true)
    public List<TestRun> list() {
        return repository.findTop50ByOrderByStartedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<TestRun> search(String query, RunStatus status, TriggerType triggerType,
                                LocalDate startedFrom, LocalDate startedTo, int page, int size) {
        return repository.findAll(specification(query, status, triggerType, startedFrom, startedTo), pageRequest(page, size));
    }

    @Transactional
    public void settleBlackboxRuns(Instant now) {
        if (now == null) throw new IllegalArgumentException("Settlement time is required");
        for (TestRun run : repository.findByStatusAndTestCase_TriggerType(RunStatus.RUNNING, TriggerType.BROWSER)) {
            Instant deadline = run.getStartedAt().plusSeconds(run.getTestCase().getTimeoutSeconds());
            if (!now.isBefore(deadline)) beginDraining(run, TestRunFinishReason.TIMEOUT, now);
        }
        for (TestRun run : repository.findByStatusAndTestCase_TriggerType(RunStatus.DRAINING, TriggerType.BROWSER)) {
            if (!now.isBefore(run.getDrainingAt().plusSeconds(drainPeriodSeconds))) run.finishDraining();
        }
    }

    private void settle(TestRun run, List<Assertion> assertions, HttpTriggerResponse response) {
        if (assertions.isEmpty()) {
            if (response.statusCode() >= 200 && response.statusCode() < 400) run.succeed();
            else run.fail("HTTP status " + response.statusCode());
            return;
        }
        List<AssertionResult> results = new ArrayList<AssertionResult>();
        for (Assertion assertion : assertions) {
            AssertionEvaluation evaluation = "MYSQL_SCALAR".equals(assertion.getAssertionType())
                    ? mysqlScalarAssertionEvaluator.evaluate(assertion, config(run.getTestCase()))
                    : assertionEvaluator.evaluate(assertion, response);
            results.add(new AssertionResult(run, assertion.getSequenceNo(), assertion.getAssertionType(), evaluation.status(),
                    evaluation.expectedJson(), evaluation.actualJson(), evaluation.failureReason()));
        }
        assertionResultRepository.saveAll(results);
        AssertionResult failed = results.stream().filter(item -> item.getStatus() == AssertionResultStatus.FAILED)
                .findFirst().orElse(null);
        if (failed == null) run.succeed(); else run.fail(failed.getFailureReason());
    }
    private void beginDraining(TestRun run, TestRunFinishReason reason) {
        blackboxCorrelationService.discardActive(run.getId());
        run.beginDraining(reason);
    }
    private void beginDraining(TestRun run, TestRunFinishReason reason, Instant at) {
        blackboxCorrelationService.discardActive(run.getId());
        run.beginDraining(reason, at);
    }
    private HttpRunStart prepareHttpRun(TestCase testCase) {
        TestRun run = repository.save(new TestRun(testCase, caseSnapshot(testCase).toString()));
        run.start();
        HttpTriggerClient.TriggerContext context = new HttpTriggerClient.TriggerContext(run.getId(), testCase.getId(),
                testCase.getProfile().getId(), testCase.getProfile().getVersion(), testCase.getTimeoutSeconds(),
                testCase.isHttpPayloadCaptureEnabled());
        return new HttpRunStart(run.getId(), config(testCase), context);
    }
    private TestRun settleHttpRun(UUID runId, HttpTriggerResponse response) {
        TestRun run = find(runId);
        settle(run, run.getTestCase().getAssertions(), response);
        return run;
    }
    private TestRun failHttpRun(UUID runId, Exception exception) {
        TestRun run = find(runId);
        run.fail(limit(exception.getMessage()));
        return run;
    }
    private JsonNode config(TestCase testCase) { return config(testCase.getTriggerConfigJson()); }
    private JsonNode config(String json) {
        try { return objectMapper.readTree(json); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private ObjectNode caseSnapshot(TestCase testCase) {
        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("name", testCase.getName());
        snapshot.put("triggerType", testCase.getTriggerType().name());
        snapshot.put("timeoutSeconds", testCase.getTimeoutSeconds());
        snapshot.put("payloadCaptureEnabled", testCase.isHttpPayloadCaptureEnabled());
        snapshot.put("completed", testCase.getCompletedAt() != null);
        ObjectNode profile = snapshot.putObject("profile");
        profile.put("id", testCase.getProfile().getId().toString());
        profile.put("version", testCase.getProfile().getVersion());
        addTriggerSnapshot(snapshot.putObject("trigger"), testCase);
        ArrayNode assertions = snapshot.putArray("assertions");
        for (Assertion assertion : testCase.getAssertions()) {
            ObjectNode item = assertions.addObject();
            item.put("sequenceNo", assertion.getSequenceNo());
            item.put("type", assertion.getAssertionType());
        }
        return snapshot;
    }

    private void addTriggerSnapshot(ObjectNode snapshot, TestCase testCase) {
        if (testCase.getTriggerType() != TriggerType.HTTP) return;
        JsonNode config = config(testCase);
        addText(snapshot, "method", config.path("method"));
        addText(snapshot, "url", config.path("url"));
        ArrayNode headerNames = snapshot.putArray("headerNames");
        config.path("headers").fieldNames().forEachRemaining(headerNames::add);
    }

    private void addText(ObjectNode target, String field, JsonNode value) {
        if (value.isTextual()) target.put(field, value.asText());
    }
    private String limit(String message) { return message == null ? "Trigger failed" : message.substring(0, Math.min(1000, message.length())); }
    private record HttpRunStart(UUID runId, JsonNode config, HttpTriggerClient.TriggerContext context) { }

    private Specification<TestRun> specification(String query, RunStatus status, TriggerType triggerType,
                                                  LocalDate startedFrom, LocalDate startedTo) {
        return (root, ignored, builder) -> {
            var predicate = builder.conjunction();
            var testCase = root.join("testCase");
            if (query != null && !query.trim().isEmpty()) {
                String value = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicate = builder.and(predicate, builder.or(
                        builder.like(builder.lower(testCase.get("name")), value),
                        builder.like(builder.lower(root.get("id").as(String.class)), value)));
            }
            if (status != null) predicate = builder.and(predicate, builder.equal(root.get("status"), status));
            if (triggerType != null) predicate = builder.and(predicate, builder.equal(testCase.get("triggerType"), triggerType));
            if (startedFrom != null) predicate = builder.and(predicate, builder.greaterThanOrEqualTo(root.get("startedAt"), startOfDay(startedFrom)));
            if (startedTo != null) predicate = builder.and(predicate, builder.lessThan(root.get("startedAt"), startOfDay(startedTo.plusDays(1))));
            return predicate;
        };
    }

    private Instant startOfDay(LocalDate value) { return value.atStartOfDay(ZoneId.of("Asia/Shanghai")).toInstant(); }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("startedAt").nullsLast(), Sort.Order.desc("id")));
    }
}
