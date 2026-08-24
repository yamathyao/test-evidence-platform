package com.talkanything.testevidence.platform.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import com.talkanything.testevidence.platform.shared.NotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CaptureProfileService {
    private final CaptureProfileRepository repository;
    private final ObjectMapper objectMapper;

    CaptureProfileService(CaptureProfileRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CaptureProfile create(String name, int version, String definitionJson) {
        JsonNode definition = parseJson(definitionJson);
        if (name == null || name.trim().isEmpty() || version < 1 || definition == null) {
            throw new IllegalArgumentException("Invalid capture profile");
        }
        if (definition.has("blackboxCorrelation")) BlackboxCorrelationDefinition.parse(definition);
        if (repository.existsByNameAndVersion(name, version)) {
            throw new ProfileConflictException("Capture profile version already exists");
        }
        return repository.save(new CaptureProfile(name.trim(), version, definitionJson));
    }

    public CaptureProfile find(UUID id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("Profile not found"));
    }

    @Transactional(readOnly = true)
    public List<CaptureProfile> list() {
        return repository.findTop50ByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Page<CaptureProfile> search(String query, Integer version, int page, int size) {
        return repository.findAll(specification(query, version), pageRequest(page, size));
    }

    @Transactional(readOnly = true)
    public List<CaptureProfile> options(String query, int limit) {
        return search(query, null, 0, limit).getContent();
    }

    private Specification<CaptureProfile> specification(String query, Integer version) {
        return (root, ignored, builder) -> {
            var predicate = builder.conjunction();
            if (query != null && !query.trim().isEmpty()) {
                predicate = builder.and(predicate, builder.like(builder.lower(root.get("name")),
                        "%" + query.trim().toLowerCase() + "%"));
            }
            if (version != null) predicate = builder.and(predicate, builder.equal(root.get("version"), version));
            return predicate;
        };
    }

    private PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid page request");
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
    }

    private JsonNode parseJson(String value) {
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException | NullPointerException exception) { return null; }
    }
}
