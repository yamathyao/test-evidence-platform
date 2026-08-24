package com.talkanything.testevidence.platform.evidence;

import com.talkanything.testevidence.platform.run.TestRun;
import com.talkanything.testevidence.platform.run.TestRunService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class EvidenceIngestionService {
    private final EvidenceEventRepository repository;
    private final HttpPayloadEvidenceRepository payloadRepository;
    private final TestRunService runService;

    EvidenceIngestionService(EvidenceEventRepository repository, HttpPayloadEvidenceRepository payloadRepository,
                             TestRunService runService) {
        this.repository = repository; this.payloadRepository = payloadRepository;
        this.runService = runService;
    }

    @Transactional
    int ingest(List<EvidenceController.EvidenceRequest> events) {
        if (events == null || events.isEmpty() || events.size() > 100) {
            throw new IllegalArgumentException("Evidence batch must contain 1 to 100 events");
        }
        for (EvidenceController.EvidenceRequest event : events) {
            validate(event);
            TestRun run = runService.find(event.testRunId());
            if (!run.getProfileId().equals(event.profileId()) || run.getProfileVersion() != event.profileVersion()) {
                throw new IllegalStateException("Evidence profile does not match test run");
            }
            EvidenceEvent saved = repository.save(new EvidenceEvent(run, event.profileId(), event.profileVersion(), event.traceId(),
                    event.spanId(), event.parentSpanId(), event.serviceName(), event.protocol(), event.direction(),
                    event.httpMethod(), event.target(), event.statusCode(), event.eventTime(),
                    event.durationMillis(), event.errorSummary(), event.jdbcOperation(), event.sqlTemplate(),
                    event.jdbcParameters()));
            if (event.httpPayload() != null) payloadRepository.save(new HttpPayloadEvidence(saved, event.httpPayload(),
                    payloadExpiry(run, Instant.now())));
        }
        return events.size();
    }

    private void validate(EvidenceController.EvidenceRequest event) {
        if (event == null || event.testRunId() == null || event.profileId() == null || event.profileVersion() < 1
                || blank(event.traceId()) || blank(event.spanId()) || blank(event.serviceName()) || blank(event.protocol())
                || blank(event.direction()) || blank(event.target()) || event.eventTime() == null || event.durationMillis() < 0
                || tooLong(event.traceId(), 64) || tooLong(event.spanId(), 64) || tooLong(event.parentSpanId(), 64)
                || tooLong(event.serviceName(), 160) || tooLong(event.protocol(), 32) || tooLong(event.direction(), 32)
                || tooLong(event.httpMethod(), 16) || tooLong(event.target(), 1000) || tooLong(event.errorSummary(), 1000)) {
            throw new IllegalArgumentException("Invalid evidence event");
        }
        if ("JDBC".equals(event.protocol()) && (blank(event.jdbcOperation()) || event.sqlTemplate() == null
                || event.jdbcParameters() == null || !event.jdbcParameters().isArray()
                || tooLong(event.jdbcOperation(), 16) || tooLong(event.sqlTemplate(), 4000)
                || event.jdbcParameters().toString().length() > 8000)) {
            throw new IllegalArgumentException("Invalid JDBC evidence event");
        }
        if (event.httpPayload() != null && (!"HTTP".equals(event.protocol()) || invalidPayload(event.httpPayload()))) {
            throw new IllegalArgumentException("Invalid HTTP payload evidence");
        }
    }
    private boolean invalidPayload(EvidenceController.HttpPayloadRequest payload) {
        return blank(payload.requestStatus()) || blank(payload.responseStatus()) || utf8(payload.requestBody()) > 65_536
                || utf8(payload.responseBody()) > 65_536 || utf8(payload.requestBody()) + utf8(payload.responseBody()) > 131_072;
    }
    private int utf8(String value) { return value == null ? 0 : value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length; }
    private Instant payloadExpiry(TestRun run, Instant now) {
        Instant completedAt = run.getTestCaseCompletedAt();
        return completedAt == null ? now.plusSeconds(30L * 86400L) : completedAt.plusSeconds(7L * 86400L);
    }

    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }
    private boolean tooLong(String value, int limit) { return value != null && value.length() > limit; }
}
