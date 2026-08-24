package com.talkanything.testevidence.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.Assertion;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "TEST_PLATFORM_DB_URL", matches = ".+")
class PostgresqlJsonbRoundTripTest {
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private EntityManager entityManager;

    @Test
    void persistsAndReadsProfileCaseAndAssertionJsonb() throws Exception {
        String profileJson = "{\"routes\":[\"/orders\"],\"mask\":[\"phone\"]}";
        String triggerJson = "{\"method\":\"POST\",\"url\":\"http://example.test/orders\"}";
        String assertionJson = "{\"expected\":201}";
        CaptureProfile profile = profileService.create("postgres-jsonb-" + System.nanoTime(), 1, profileJson);
        TestCase created = testCaseService.create(new TestCaseRequest("postgres jsonb", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree(triggerJson), 30, List.of(new TestCaseRequest.AssertionRequest(1,
                "HTTP_STATUS", objectMapper.readTree(assertionJson)))));

        entityManager.flush();
        entityManager.clear();
        TestCase reloaded = testCaseService.find(created.getId());
        Assertion assertion = reloaded.getAssertions().get(0);

        assertEquals(objectMapper.readTree(profileJson), objectMapper.readTree(reloaded.getProfile().getDefinitionJson()));
        assertEquals(objectMapper.readTree(triggerJson), objectMapper.readTree(reloaded.getTriggerConfigJson()));
        assertEquals(objectMapper.readTree(assertionJson), objectMapper.readTree(assertion.getDefinitionJson()));
    }
}
