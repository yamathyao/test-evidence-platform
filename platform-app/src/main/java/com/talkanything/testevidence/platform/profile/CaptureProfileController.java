package com.talkanything.testevidence.platform.profile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.shared.PageResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/capture-profiles")
class CaptureProfileController {
    private final CaptureProfileService service;
    private final ObjectMapper objectMapper;
    CaptureProfileController(CaptureProfileService service, ObjectMapper objectMapper) { this.service = service; this.objectMapper = objectMapper; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    CaptureProfileResponse create(@RequestBody CaptureProfileRequest request) { return response(service.create(request.name(), request.version(), request.definition().toString())); }
    @GetMapping List<CaptureProfileListResponse> list() { return service.list().stream().map(this::listResponse).toList(); }
    @GetMapping("/page")
    PageResponse<CaptureProfileListResponse> page(@RequestParam(value = "query", required = false) String query,
                                                   @RequestParam(value = "version", required = false) Integer version,
                                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        return PageResponse.from(service.search(query, version, page, size), this::listResponse);
    }
    @GetMapping("/options")
    List<CaptureProfileOptionResponse> options(@RequestParam(value = "query", required = false) String query,
                                                @RequestParam(value = "limit", defaultValue = "50") int limit) {
        return service.options(query, limit).stream().map(this::optionResponse).toList();
    }
    @GetMapping("/{id}") CaptureProfileResponse find(@PathVariable("id") UUID id) { return response(service.find(id)); }
    private CaptureProfileResponse response(CaptureProfile profile) { return new CaptureProfileResponse(profile.getId(), profile.getName(), profile.getVersion(), json(profile.getDefinitionJson())); }
    private CaptureProfileListResponse listResponse(CaptureProfile profile) { return new CaptureProfileListResponse(profile.getId(), profile.getName(), profile.getVersion(), profile.getCreatedAt()); }
    private CaptureProfileOptionResponse optionResponse(CaptureProfile profile) { return new CaptureProfileOptionResponse(profile.getId(), profile.getName(), profile.getVersion()); }
    private JsonNode json(String value) { try { return objectMapper.readTree(value); } catch (Exception exception) { throw new IllegalStateException(exception); } }
    record CaptureProfileRequest(String name, int version, JsonNode definition) { }
    record CaptureProfileResponse(UUID id, String name, int version, JsonNode definition) { }
    record CaptureProfileListResponse(UUID id, String name, int version, Instant createdAt) { }
    record CaptureProfileOptionResponse(UUID id, String name, int version) { }
}
