package com.talkanything.testevidence.platform.profile;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
class CaptureProfileServiceTest {
    @Autowired
    private CaptureProfileService service;

    @Test
    void createsAndFindsProfileVersion() {
        CaptureProfile profile = service.create("order-capture", 1, "{\"services\":[\"order-service\"]}");

        CaptureProfile found = service.find(profile.getId());

        assertEquals("order-capture", found.getName());
        assertEquals(1, found.getVersion());
    }

    @Test
    void rejectsDuplicateNameAndVersion() {
        service.create("inventory-capture", 1, "{}");

        assertThrows(ProfileConflictException.class,
                () -> service.create("inventory-capture", 1, "{}"));
    }

    @Test
    void rejectsInvalidBlackboxCorrelationConfiguration() {
        String invalidDefinition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":30}}";

        assertThrows(IllegalArgumentException.class,
                () -> service.create("invalid-blackbox-capture", 1, invalidDefinition));
    }
}
