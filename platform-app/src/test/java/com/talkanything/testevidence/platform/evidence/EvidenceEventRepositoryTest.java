package com.talkanything.testevidence.platform.evidence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.talkanything.testevidence.platform.run.HttpTriggerClient;
import com.talkanything.testevidence.platform.run.HttpTriggerResponse;
import com.talkanything.testevidence.platform.run.TestRun;
import com.talkanything.testevidence.platform.run.TestRunService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class EvidenceEventRepositoryTest {
    @Autowired private EvidenceEventRepository repository;
    @Autowired private CaptureProfileService profileService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private TestRunService runService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void returnsOnlyEventsForRequestedRunInStableTimeOrder() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun firstRun = runService.start(createHttpCase("first"));
        TestRun secondRun = runService.start(createHttpCase("second"));
        Instant earlier = Instant.parse("2026-08-13T01:00:00Z");

        repository.save(new EvidenceEvent(firstRun, firstRun.getProfileId(), 1, "trace-1", "span-late", "",
                "order-service", "HTTP", "SERVER", "POST", "/orders", 201, earlier.plusSeconds(1), 3, "", null, null, null));
        repository.save(new EvidenceEvent(secondRun, secondRun.getProfileId(), 1, "trace-2", "span-other", "",
                "stock-service", "HTTP", "SERVER", "POST", "/stock", 200, earlier, 2, "", null, null, null));
        repository.save(new EvidenceEvent(firstRun, firstRun.getProfileId(), 1, "trace-1", "span-early", "",
                "order-service", "HTTP", "SERVER", "POST", "/orders", 200, earlier, 1, "", null, null, null));

        List<EvidenceEvent> events = repository.findByTestRunIdOrderByEventTimeAscIdAsc(firstRun.getId());

        assertEquals(2, events.size());
        assertEquals("span-early", events.get(0).getSpanId());
        assertEquals("span-late", events.get(1).getSpanId());
    }

    private TestCase createHttpCase(String suffix) throws Exception {
        CaptureProfile profile = profileService.create("evidence-profile-" + suffix + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest("evidence case", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/evidence\"}"), 30, List.of()));
    }
}
