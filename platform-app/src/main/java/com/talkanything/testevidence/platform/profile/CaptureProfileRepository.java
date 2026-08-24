package com.talkanything.testevidence.platform.profile;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

interface CaptureProfileRepository extends JpaRepository<CaptureProfile, UUID>, JpaSpecificationExecutor<CaptureProfile> {
    boolean existsByNameAndVersion(String name, int version);
    List<CaptureProfile> findTop50ByOrderByCreatedAtDesc();
}
