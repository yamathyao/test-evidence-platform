package com.talkanything.testevidence.platform.casefile;

import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.talkanything.testevidence.platform.run.MysqlScalarAssertionDefinitionParser;
import com.talkanything.testevidence.platform.evidence.HttpPayloadEvidenceRepository;
import com.talkanything.testevidence.platform.evidence.ProtocolPayloadEvidenceRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@Service
public class TestCaseService {
    private final TestCaseRepository repository;
    private final CaptureProfileService profileService;
    private final MysqlScalarAssertionDefinitionParser mysqlScalarDefinitionParser;
    private final HttpPayloadEvidenceRepository payloadRepository;
    private final ProtocolPayloadEvidenceRepository protocolPayloadRepository;

    TestCaseService(TestCaseRepository repository, CaptureProfileService profileService,
                    MysqlScalarAssertionDefinitionParser mysqlScalarDefinitionParser, HttpPayloadEvidenceRepository payloadRepository,
                    ProtocolPayloadEvidenceRepository protocolPayloadRepository) {
        this.repository = repository; this.profileService = profileService;
        this.mysqlScalarDefinitionParser = mysqlScalarDefinitionParser;
        this.payloadRepository = payloadRepository;
        this.protocolPayloadRepository = protocolPayloadRepository;
    }

    @Transactional
    public TestCase create(TestCaseRequest request) {
        validate(request);
        CaptureProfile profile = profileService.find(request.profileId());
        TestCase testCase = new TestCase(request.name().trim(), profile, request.triggerType(),
                request.triggerConfig().toString(), request.timeoutSeconds(), request.httpPayloadCaptureEnabled());
        for (TestCaseRequest.AssertionRequest assertion : request.assertions()) {
            testCase.addAssertion(assertion.sequenceNo(), assertion.type(), assertion.definition().toString());
        }
        return repository.save(testCase);
    }

    @Transactional(readOnly = true)
    public TestCase find(UUID id) {
        return repository.findDetailedById(id).orElseThrow(() -> new TestCaseNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public List<TestCase> list() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<TestCase> search(String query, TriggerType triggerType, Boolean completed, UUID profileId, int page, int size) {
        return repository.findAll(specification(query, triggerType, completed, profileId), pageRequest(page, size));
    }

    @Transactional(readOnly = true)
    public List<TestCase> options(String query, int limit) {
        return search(query, null, false, null, 0, limit).getContent();
    }

    @Transactional
    public TestCase update(UUID id, TestCaseRequest request) {
        validate(request);
        TestCase testCase = find(id);
        CaptureProfile profile = profileService.find(request.profileId());
        java.util.List<TestCase.AssertionRequestData> assertions = request.assertions().stream()
                .map(item -> new TestCase.AssertionRequestData(item.sequenceNo(), item.type(), item.definition().toString())).toList();
        testCase.replace(request.name().trim(), profile, request.triggerType(), request.triggerConfig().toString(),
                request.timeoutSeconds(), request.httpPayloadCaptureEnabled(), assertions);
        return testCase;
    }

    @Transactional
    public TestCase complete(UUID id) {
        TestCase testCase = find(id);
        java.time.Instant now = java.time.Instant.now();
        testCase.complete(now);
        for (var payload : payloadRepository.findByEvidenceEvent_TestRun_TestCase_IdAndExpiresAtAfter(id, now)) {
            payload.shortenExpiry(now.plusSeconds(7L * 86400L));
        }
        for (var payload : protocolPayloadRepository.findByEvidenceEvent_TestRun_TestCase_IdAndExpiresAtAfter(id, now)) {
            payload.shortenExpiry(now.plusSeconds(7L * 86400L));
        }
        return testCase;
    }

    @Transactional
    public TestCase reopen(UUID id) {
        TestCase testCase = find(id);
        testCase.reopen();
        return testCase;
    }

    private void validate(TestCaseRequest request) {
        if (request == null || blank(request.name()) || request.profileId() == null || request.triggerType() == null
                || request.triggerConfig() == null || request.assertions() == null || invalidTimeout(request)) {
            throw new IllegalArgumentException("Invalid test case");
        }
        Set<Integer> sequences = new HashSet<Integer>();
        for (TestCaseRequest.AssertionRequest item : request.assertions()) {
            if (item == null || item.sequenceNo() < 1 || blank(item.type()) || item.definition() == null
                    || !sequences.add(item.sequenceNo())) throw new IllegalArgumentException("Invalid assertions");
            if ("MYSQL_SCALAR".equals(item.type())) validateMysqlScalar(request, item);
        }
    }
    private boolean invalidTimeout(TestCaseRequest request) {
        int minimum = request.triggerType() == TriggerType.BROWSER ? 60 : 1;
        int maximum = request.triggerType() == TriggerType.BROWSER ? 3600 : 300;
        return request.timeoutSeconds() < minimum || request.timeoutSeconds() > maximum;
    }
    private void validateMysqlScalar(TestCaseRequest request, TestCaseRequest.AssertionRequest assertion) {
        if (request.triggerType() != TriggerType.HTTP) throw new IllegalArgumentException("MYSQL_SCALAR requires HTTP trigger");
        mysqlScalarDefinitionParser.parse(assertion.definition());
    }
    private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

    private Specification<TestCase> specification(String query, TriggerType triggerType, Boolean completed, UUID profileId) {
        return (root, ignored, builder) -> {
            var predicate = builder.conjunction();
            if (!blank(query)) predicate = builder.and(predicate, builder.like(builder.lower(root.get("name")),
                    "%" + query.trim().toLowerCase() + "%"));
            if (triggerType != null) predicate = builder.and(predicate, builder.equal(root.get("triggerType"), triggerType));
            if (completed != null) predicate = builder.and(predicate, completed
                    ? builder.isNotNull(root.get("completedAt")) : builder.isNull(root.get("completedAt")));
            if (profileId != null) predicate = builder.and(predicate, builder.equal(root.get("profile").get("id"), profileId));
            return predicate;
        };
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id")));
    }
}
