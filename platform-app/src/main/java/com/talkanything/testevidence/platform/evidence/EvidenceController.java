package com.talkanything.testevidence.platform.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import com.talkanything.testevidence.platform.run.TestRunService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class EvidenceController {
    private final String agentToken;
    private final EvidenceIngestionService ingestionService;
    private final EvidenceEventRepository repository;
    private final HttpPayloadEvidenceRepository payloadRepository;
    private final ProtocolPayloadEvidenceRepository protocolPayloadRepository;
    private final TestRunService runService;

    EvidenceController(@Value("${test-evidence.agent-token}") String agentToken,
                       EvidenceIngestionService ingestionService, EvidenceEventRepository repository,
                       HttpPayloadEvidenceRepository payloadRepository, ProtocolPayloadEvidenceRepository protocolPayloadRepository,
                       TestRunService runService) {
        this.agentToken = agentToken;
        this.ingestionService = ingestionService;
        this.repository = repository; this.payloadRepository = payloadRepository; this.protocolPayloadRepository = protocolPayloadRepository;
        this.runService = runService;
    }

    @PostMapping("/internal/v1/evidence/batches")
    @ResponseStatus(HttpStatus.ACCEPTED)
    BatchResponse ingest(@RequestHeader(value = "X-Test-Agent-Token", required = false) String token,
                         @RequestBody EvidenceBatch batch) {
        if (!agentToken.equals(token)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized agent");
        return new BatchResponse(ingestionService.ingest(batch.events(), batch.diagnostics()));
    }

    @GetMapping("/api/runs/{id}/trace")
    TraceResponse trace(@PathVariable("id") UUID id) {
        runService.find(id);
        return new TraceResponse(new TraceTreeAssembler().assemble(repository.findByTestRunIdOrderByEventTimeAscIdAsc(id)));
    }
    @GetMapping("/api/runs/{id}/trace/http-payloads/{spanId}")
    HttpPayloadResponse payload(@PathVariable("id") UUID id, @PathVariable("spanId") String spanId) {
        EvidenceEvent event = repository.findByTestRunIdAndSpanId(id, spanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        PayloadSource source = localPayload(event).or(() -> childServerPayload(id, event))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        HttpPayloadEvidence payload = source.payload;
        return new HttpPayloadResponse(payload.getRequestContentType(), payload.getResponseContentType(), payload.getRequestStatus(),
                payload.getResponseStatus(), payload.getRequestBody(), payload.getResponseBody(), payload.isRequestTruncated(),
                payload.isResponseTruncated(), source.spanId);
    }

    @GetMapping("/api/runs/{id}/trace/payloads/{spanId}")
    ProtocolPayloadResponse protocolPayload(@PathVariable("id") UUID id, @PathVariable("spanId") String spanId) {
        EvidenceEvent event = repository.findByTestRunIdAndSpanId(id, spanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        var protocol = protocolPayloadRepository.findByEvidenceEvent_Id(event.getId());
        if (protocol.isPresent()) return response(event.getProtocol(), protocol.get(), event.getSpanId());
        PayloadSource source = localPayload(event).or(() -> childServerPayload(id, event))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payload not found"));
        HttpPayloadEvidence payload = source.payload;
        return new ProtocolPayloadResponse(event.getProtocol(), payload.getRequestContentType(), payload.getResponseContentType(),
                payload.getRequestStatus(), payload.getResponseStatus(), payload.getRequestBody(), payload.getResponseBody(),
                payload.isRequestTruncated(), payload.isResponseTruncated(), source.spanId);
    }

    private ProtocolPayloadResponse response(String protocol, ProtocolPayloadEvidence payload, String sourceSpanId) {
        return new ProtocolPayloadResponse(protocol, payload.getRequestContentType(), payload.getResponseContentType(),
                payload.getRequestStatus(), payload.getResponseStatus(), payload.getRequestBody(), payload.getResponseBody(),
                payload.isRequestTruncated(), payload.isResponseTruncated(), sourceSpanId);
    }

    private java.util.Optional<PayloadSource> localPayload(EvidenceEvent event) {
        return payloadRepository.findByEvidenceEvent_Id(event.getId()).map(payload -> new PayloadSource(payload, event.getSpanId()));
    }

    private java.util.Optional<PayloadSource> childServerPayload(UUID runId, EvidenceEvent event) {
        if (!"HTTP".equals(event.getProtocol()) || !"CLIENT".equals(event.getDirection())) return java.util.Optional.empty();
        return repository.findByTestRunIdAndTraceIdAndParentSpanIdAndProtocolAndDirectionOrderByEventTimeAscIdAsc(
                        runId, event.getTraceId(), event.getSpanId(), "HTTP", "SERVER")
                .stream().map(this::localPayload).filter(java.util.Optional::isPresent).map(java.util.Optional::get).findFirst();
    }

    record EvidenceBatch(List<EvidenceRequest> events, List<AgentDiagnosticRequest> diagnostics) { }
    record AgentDiagnosticRequest(UUID testRunId, UUID profileId, int profileVersion, String serviceName,
                                  long droppedEvidenceCount, long deliveryFailureCount, String lastFailure) { }
    record EvidenceRequest(UUID testRunId, UUID profileId, int profileVersion, String traceId, String spanId,
                           String parentSpanId, String serviceName, String protocol, String direction,
                           String httpMethod, String target, Integer statusCode, Instant eventTime,
                           long durationMillis, String errorSummary, String jdbcOperation, String sqlTemplate,
                           JsonNode jdbcParameters, HttpPayloadRequest httpPayload, ProtocolPayloadRequest protocolPayload) {
        EvidenceRequest(UUID testRunId, UUID profileId, int profileVersion, String traceId, String spanId,
                        String parentSpanId, String serviceName, String protocol, String direction, String httpMethod,
                        String target, Integer statusCode, Instant eventTime, long durationMillis, String errorSummary,
                        String jdbcOperation, String sqlTemplate, JsonNode jdbcParameters) {
            this(testRunId, profileId, profileVersion, traceId, spanId, parentSpanId, serviceName, protocol, direction,
                    httpMethod, target, statusCode, eventTime, durationMillis, errorSummary, jdbcOperation, sqlTemplate, jdbcParameters,
                    null, null);
        }
    }
    record HttpPayloadRequest(String requestContentType, String responseContentType, String requestStatus, String responseStatus,
                              String requestBody, String responseBody, boolean requestTruncated, boolean responseTruncated) { }
    record HttpPayloadResponse(String requestContentType, String responseContentType, String requestStatus, String responseStatus,
                               String requestBody, String responseBody, boolean requestTruncated, boolean responseTruncated,
                               String sourceSpanId) { }
    record ProtocolPayloadRequest(String requestContentType, String responseContentType, String requestStatus, String responseStatus,
                                  String requestBody, String responseBody, boolean requestTruncated, boolean responseTruncated) { }
    record ProtocolPayloadResponse(String protocol, String requestContentType, String responseContentType, String requestStatus,
                                   String responseStatus, String requestBody, String responseBody, boolean requestTruncated,
                                   boolean responseTruncated, String sourceSpanId) { }
    private record PayloadSource(HttpPayloadEvidence payload, String spanId) { }
    record BatchResponse(int accepted) { }
    record TraceResponse(List<TraceTreeAssembler.TraceNode> roots) { }
}
