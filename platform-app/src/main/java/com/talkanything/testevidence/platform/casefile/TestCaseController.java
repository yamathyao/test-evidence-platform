package com.talkanything.testevidence.platform.casefile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.shared.PageResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/test-cases")
class TestCaseController {
    private final TestCaseService service;
    private final ObjectMapper objectMapper;
    TestCaseController(TestCaseService service, ObjectMapper objectMapper) { this.service = service; this.objectMapper = objectMapper; }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    TestCaseResponse create(@RequestBody TestCaseRequest request) { return response(service.create(request)); }
    @GetMapping List<TestCaseResponse> list() { return service.list().stream().map(this::response).toList(); }
    @GetMapping("/page")
    PageResponse<TestCaseListResponse> page(@RequestParam(value = "query", required = false) String query,
                                             @RequestParam(value = "triggerType", required = false) TriggerType triggerType,
                                             @RequestParam(value = "completed", required = false) Boolean completed,
                                             @RequestParam(value = "profileId", required = false) UUID profileId,
                                             @RequestParam(value = "page", defaultValue = "0") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        return PageResponse.from(service.search(query, triggerType, completed, profileId, page, size), this::listResponse);
    }
    @GetMapping("/options")
    List<TestCaseOptionResponse> options(@RequestParam(value = "query", required = false) String query,
                                         @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.options(query, limit).stream().map(this::optionResponse).toList();
    }
    @PutMapping("/{id}") TestCaseResponse update(@PathVariable("id") UUID id, @RequestBody TestCaseRequest request) { return response(service.update(id, request)); }
    @GetMapping("/{id}") TestCaseResponse find(@PathVariable("id") UUID id) { return response(service.find(id)); }
    @PostMapping("/{id}/complete") TestCaseResponse complete(@PathVariable("id") UUID id) { return response(service.complete(id)); }
    @PostMapping("/{id}/reopen") TestCaseResponse reopen(@PathVariable("id") UUID id) { return response(service.reopen(id)); }

    private TestCaseResponse response(TestCase value) {
        List<AssertionResponse> assertions = value.getAssertions().stream().map(item -> new AssertionResponse(
                item.getSequenceNo(), item.getAssertionType(), json(item.getDefinitionJson()))).toList();
        return new TestCaseResponse(value.getId(), value.getName(), value.getProfile().getId(), value.getTriggerType(),
                json(value.getTriggerConfigJson()), value.getTimeoutSeconds(), value.isHttpPayloadCaptureEnabled(),
                value.getCompletedAt(), assertions);
    }
    private JsonNode json(String raw) { try { return objectMapper.readTree(raw); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    private TestCaseListResponse listResponse(TestCase value) {
        return new TestCaseListResponse(value.getId(), value.getName(), value.getProfile().getId(), value.getTriggerType(),
                value.getTimeoutSeconds(), value.isHttpPayloadCaptureEnabled(), value.getCompletedAt());
    }
    private TestCaseOptionResponse optionResponse(TestCase value) {
        return new TestCaseOptionResponse(value.getId(), value.getName(), value.getTriggerType());
    }

    record TestCaseResponse(UUID id, String name, UUID profileId, TriggerType triggerType, JsonNode triggerConfig,
                            int timeoutSeconds, boolean httpPayloadCaptureEnabled, java.time.Instant completedAt,
                            List<AssertionResponse> assertions) { }
    record AssertionResponse(int sequenceNo, String type, JsonNode definition) { }
    record TestCaseListResponse(UUID id, String name, UUID profileId, TriggerType triggerType, int timeoutSeconds,
                                boolean httpPayloadCaptureEnabled, java.time.Instant completedAt) { }
    record TestCaseOptionResponse(UUID id, String name, TriggerType triggerType) { }
}
