package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.evidence.AgentDeliveryDiagnostic;
import com.talkanything.testevidence.platform.evidence.AgentDeliveryDiagnosticRepository;
import com.talkanything.testevidence.platform.shared.PageResponse;
import java.util.UUID;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api")
class TestRunController {
    private final TestCaseService testCaseService;
    private final TestRunService runService;
    private final AgentDeliveryDiagnosticRepository diagnosticRepository;
    private final ObjectMapper objectMapper;
    TestRunController(TestCaseService testCaseService, TestRunService runService,
                      AgentDeliveryDiagnosticRepository diagnosticRepository, ObjectMapper objectMapper) {
        this.testCaseService = testCaseService; this.runService = runService;
        this.diagnosticRepository = diagnosticRepository; this.objectMapper = objectMapper;
    }
    @PostMapping("/test-cases/{id}/runs") @ResponseStatus(HttpStatus.CREATED)
    RunResponse start(@PathVariable("id") UUID id) { return response(runService.start(testCaseService.find(id))); }
    @PostMapping("/test-cases/{id}/blackbox-runs") @ResponseStatus(HttpStatus.CREATED)
    RunResponse startBlackbox(@PathVariable("id") UUID id, @RequestBody BlackboxRunRequest request) {
        return response(runService.startBlackbox(testCaseService.find(id), values(request), request.ttlSeconds()));
    }
    @PostMapping("/runs/{id}/complete")
    RunResponse complete(@PathVariable("id") UUID id) { return response(runService.complete(id)); }
    @GetMapping("/runs") List<RunListResponse> list() { return runService.list().stream().map(this::listResponse).toList(); }
    @GetMapping("/runs/page")
    PageResponse<RunListResponse> page(@RequestParam(value = "query", required = false) String query,
                                       @RequestParam(value = "status", required = false) RunStatus status,
                                       @RequestParam(value = "triggerType", required = false) TriggerType triggerType,
                                       @RequestParam(value = "startedFrom", required = false) LocalDate startedFrom,
                                       @RequestParam(value = "startedTo", required = false) LocalDate startedTo,
                                       @RequestParam(value = "page", defaultValue = "0") int page,
                                       @RequestParam(value = "size", defaultValue = "20") int size) {
        return PageResponse.from(runService.search(query, status, triggerType, startedFrom, startedTo, page, size), this::listResponse);
    }
    @GetMapping("/runs/{id}") RunResponse find(@PathVariable("id") UUID id) { return response(runService.find(id)); }
    private RunResponse response(TestRun run) {
        List<AssertionResultResponse> results = runService.results(run.getId()).stream().map(item -> new AssertionResultResponse(
                item.getSequenceNo(), item.getAssertionType(), item.getStatus(), json(item.getExpectedJson()),
                json(item.getActualJson()), item.getFailureReason())).toList();
        JsonNode snapshot = run.getCaseSnapshotJson() == null ? null : json(run.getCaseSnapshotJson());
        return new RunResponse(run.getId(), run.getStatus(), run.getFailureReason(), run.getStartedAt(), run.getDrainingAt(),
                run.getFinishedAt(), run.getFinishReason(), runService.remainingCaptureSeconds(run),
                runService.rootTraceCount(run.getId()), diagnostics(run.getId()), results, snapshot != null, snapshot);
    }
    private EvidenceDeliveryDiagnostics diagnostics(UUID runId) {
        List<AgentDeliveryDiagnostic> agents = diagnosticRepository.findByTestRunIdOrderByServiceNameAsc(runId);
        long dropped = agents.stream().mapToLong(AgentDeliveryDiagnostic::getDroppedEvidenceCount).sum();
        long failures = agents.stream().mapToLong(AgentDeliveryDiagnostic::getDeliveryFailureCount).sum();
        List<AgentDeliveryDiagnosticResponse> values = agents.stream().map(item -> new AgentDeliveryDiagnosticResponse(
                item.getServiceName(), item.getDroppedEvidenceCount(), item.getDeliveryFailureCount(),
                item.getLastFailure(), item.getUpdatedAt())).toList();
        return new EvidenceDeliveryDiagnostics(dropped, failures, values);
    }
    private RunListResponse listResponse(TestRun run) {
        return new RunListResponse(run.getId(), run.getTestCase().getId(), run.getTestCase().getName(), run.getStatus(),
                run.getStartedAt(), run.getDrainingAt(), run.getFinishedAt(), run.getFinishReason(),
                runService.remainingCaptureSeconds(run), runService.rootTraceCount(run.getId()), run.getFailureReason());
    }
    private JsonNode json(String value) {
        try { return objectMapper.readTree(value); } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private Map<String, String> values(BlackboxRunRequest request) {
        if (request == null || request.correlationData() == null) throw new IllegalArgumentException("Invalid blackbox run");
        return request.correlationData();
    }
    record BlackboxRunRequest(Map<String, String> correlationData, Integer ttlSeconds) { }
    record RunResponse(UUID id, RunStatus status, String failureReason, Instant startedAt, Instant drainingAt,
                       Instant finishedAt, TestRunFinishReason finishReason, long remainingCaptureSeconds,
                       long rootTraceCount, EvidenceDeliveryDiagnostics evidenceDeliveryDiagnostics,
                       List<AssertionResultResponse> assertionResults, boolean snapshotAvailable, JsonNode caseSnapshot) { }
    record EvidenceDeliveryDiagnostics(long droppedEvidenceCount, long deliveryFailureCount,
                                      List<AgentDeliveryDiagnosticResponse> agents) { }
    record AgentDeliveryDiagnosticResponse(String serviceName, long droppedEvidenceCount, long deliveryFailureCount,
                                          String lastFailure, Instant updatedAt) { }
    record RunListResponse(UUID id, UUID testCaseId, String testCaseName, RunStatus status, Instant startedAt,
                           Instant drainingAt, Instant finishedAt, TestRunFinishReason finishReason,
                           long remainingCaptureSeconds, long rootTraceCount, String failureReason) { }
    record AssertionResultResponse(int sequenceNo, String type, AssertionResultStatus status, JsonNode expected,
                                   JsonNode actual, String failureReason) { }
}
