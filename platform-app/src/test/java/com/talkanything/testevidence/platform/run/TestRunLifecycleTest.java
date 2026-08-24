package com.talkanything.testevidence.platform.run;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
@ActiveProfiles("test")
class TestRunLifecycleTest {
    @Autowired private TestCaseService testCaseService;
    @Autowired private CaptureProfileService profileService;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void browserRunDrainsBeforeManualCompletion() throws Exception {
        TestRun run = new TestRun(browserCase(), "{}");
        run.start();

        run.beginDraining(TestRunFinishReason.MANUAL);

        assertEquals(RunStatus.DRAINING, run.getStatus());
        assertNotNull(run.getDrainingAt());
        run.finishDraining();
        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
    }

    private TestCase browserCase() throws Exception {
        String definition = "{\"blackboxCorrelation\":{\"defaultTtlSeconds\":60,\"retentionSeconds\":300,"
                + "\"singleUse\":true,\"targetServices\":[\"lifecycle\"],\"matchGroups\":[{\"name\":\"browser\","
                + "\"matchers\":[{\"field\":\"orderNo\",\"locations\":[\"QUERY\"],\"match\":\"EXACT\"}]}]}}";
        CaptureProfile profile = profileService.create("lifecycle-profile-" + System.nanoTime(), 1, definition);
        return testCaseService.create(new TestCaseRequest("lifecycle browser", profile.getId(), TriggerType.BROWSER,
                objectMapper.readTree("{}"), 60, List.of()));
    }
}
