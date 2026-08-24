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
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class AssertionResultRepositoryTest {
    @Autowired private AssertionResultRepository repository;
    @Autowired private TestRunService runService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private CaptureProfileService profileService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HttpTriggerClient triggerClient;

    @Test
    void readsResultsInAssertionSequenceOrder() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));
        TestRun run = runService.start(httpCase());
        repository.save(new AssertionResult(run, 2, "HTTP_STATUS", AssertionResultStatus.PASSED,
                "{\"expected\":200}", "{\"actual\":200}", null));
        repository.save(new AssertionResult(run, 1, "HTTP_STATUS", AssertionResultStatus.FAILED,
                "{\"expected\":201}", "{\"actual\":200}", "Expected HTTP status 201 but was 200"));

        List<AssertionResult> results = repository.findByTestRunIdOrderBySequenceNoAsc(run.getId());

        assertEquals(List.of(1, 2), results.stream().map(AssertionResult::getSequenceNo).toList());
        assertEquals(AssertionResultStatus.FAILED, results.get(0).getStatus());
    }

    private TestCase httpCase() throws Exception {
        CaptureProfile profile = profileService.create("result-profile-" + System.nanoTime(), 1, "{}");
        return testCaseService.create(new TestCaseRequest("result case", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30, List.of()));
    }
}
