package com.talkanything.testevidence.platform.run;

import com.talkanything.testevidence.platform.casefile.TestCase;
import com.talkanything.testevidence.platform.casefile.TestCaseRequest;
import com.talkanything.testevidence.platform.casefile.TestCaseService;
import com.talkanything.testevidence.platform.casefile.TriggerType;
import com.talkanything.testevidence.platform.profile.CaptureProfile;
import com.talkanything.testevidence.platform.profile.CaptureProfileService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
class TestRunServiceTest {
    @Autowired private TestRunService runService;
    @Autowired private TestCaseService testCaseService;
    @Autowired private CaptureProfileService profileService;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private HttpTriggerClient triggerClient;
    @MockBean private MysqlScalarQueryClient mysqlScalarQueryClient;
    @Autowired private AssertionResultRepository assertionResultRepository;

    @Test
    void invokesHttpTriggerOutsideThePlatformTransaction() throws Exception {
        AtomicBoolean transactionActiveDuringTrigger = new AtomicBoolean();
        when(triggerClient.execute(any(), any())).thenAnswer(invocation -> {
            transactionActiveDuringTrigger.set(TransactionSynchronizationManager.isActualTransactionActive());
            return new HttpTriggerResponse(200, "");
        });

        runService.start(httpCase());

        assertEquals(false, transactionActiveDuringTrigger.get());
    }

    @Test
    void marksRunSucceededForSuccessfulHttpTrigger() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(200, ""));

        TestRun run = runService.start(httpCase());

        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
    }

    @Test
    void marksRunFailedForHttpFailure() throws Exception {
        when(triggerClient.execute(any(), any())).thenThrow(new IOException("connection refused"));

        TestRun run = runService.start(httpCase());

        assertEquals(RunStatus.FAILED, run.getStatus());
    }

    @Test
    void persistsPassedHttpStatusAssertion() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));

        TestRun run = runService.start(httpCase("{\"expected\":201}"));

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(AssertionResultStatus.PASSED, results.get(0).getStatus());
        assertEquals("{\"actual\":201}", results.get(0).getActualJson());
    }

    @Test
    void marksRunFailedWhenHttpStatusAssertionDoesNotMatch() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));

        TestRun run = runService.start(httpCase("{\"expected\":200}"));

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals(AssertionResultStatus.FAILED, results.get(0).getStatus());
        assertEquals("Expected HTTP status 200 but was 201", results.get(0).getFailureReason());
    }

    @Test
    void persistsPassedHttpJsonPathAssertion() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(orderResponse());

        TestRun run = runService.start(httpCase("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-001\"}"));

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(AssertionResultStatus.PASSED, results.get(0).getStatus());
        assertEquals("{\"jsonPath\":\"$.data.orderNo\",\"actual\":\"ORD-001\"}", results.get(0).getActualJson());
    }

    @Test
    void marksRunFailedWhenHttpJsonPathAssertionDoesNotMatch() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(orderResponse());

        TestRun run = runService.start(httpCase("HTTP_JSON_PATH",
                "{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-002\"}"));

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals(AssertionResultStatus.FAILED, results.get(0).getStatus());
        assertEquals("Expected JSONPath $.data.orderNo value \"ORD-002\" but was \"ORD-001\"",
                results.get(0).getFailureReason());
    }

    @Test
    void persistsAllAssertionResultsInSequenceOrder() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(new HttpTriggerResponse(201, ""));
        CaptureProfile profile = profileService.create("ordered-profile-" + System.nanoTime(), 1, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("ordered case", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"GET\",\"url\":\"http://localhost/orders\"}"), 30, List.of(
                new TestCaseRequest.AssertionRequest(2, "HTTP_STATUS", objectMapper.readTree("{\"expected\":200}")),
                new TestCaseRequest.AssertionRequest(1, "HTTP_STATUS", objectMapper.readTree("{\"expected\":201}")))));

        TestRun run = runService.start(testCase);

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.FAILED, run.getStatus());
        assertEquals(List.of(1, 2), results.stream().map(AssertionResult::getSequenceNo).toList());
        assertEquals(List.of(AssertionResultStatus.PASSED, AssertionResultStatus.FAILED),
                results.stream().map(AssertionResult::getStatus).toList());
    }

    @Test
    void persistsHttpAndMysqlAssertionsInSequenceOrder() throws Exception {
        when(triggerClient.execute(any(), any())).thenReturn(orderResponse());
        when(mysqlScalarQueryClient.query(anyString(), anyList(), eq(MysqlScalarValueType.STRING)))
                .thenReturn(ScalarQueryResult.from("PAID", MysqlScalarValueType.STRING));
        CaptureProfile profile = profileService.create("mysql-run-profile-" + System.nanoTime(), 1, "{}");
        TestCase testCase = testCaseService.create(new TestCaseRequest("mysql run", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"POST\",\"url\":\"http://localhost/orders\",\"body\":\"{\\\"orderNo\\\":\\\"order-1\\\"}\"}"),
                30, List.of(
                new TestCaseRequest.AssertionRequest(3, "MYSQL_SCALAR", objectMapper.readTree("""
                        {"sql":"SELECT status FROM orders WHERE order_no = ?","parameters":[{"source":"REQUEST_JSON_PATH","jsonPath":"$.orderNo"}],"expected":"PAID"}
                        """)),
                new TestCaseRequest.AssertionRequest(1, "HTTP_STATUS", objectMapper.readTree("{\"expected\":201}")),
                new TestCaseRequest.AssertionRequest(2, "HTTP_JSON_PATH", objectMapper.readTree("{\"jsonPath\":\"$.data.orderNo\",\"expected\":\"ORD-001\"}")))));

        TestRun run = runService.start(testCase);

        List<AssertionResult> results = assertionResultRepository.findByTestRunIdOrderBySequenceNoAsc(run.getId());
        assertEquals(RunStatus.SUCCEEDED, run.getStatus());
        assertEquals(List.of(1, 2, 3), results.stream().map(AssertionResult::getSequenceNo).toList());
        assertEquals(List.of(AssertionResultStatus.PASSED, AssertionResultStatus.PASSED, AssertionResultStatus.PASSED),
                results.stream().map(AssertionResult::getStatus).toList());
    }

    private TestCase httpCase() throws Exception {
        return httpCase(null);
    }

    private TestCase httpCase(String assertionDefinition) throws Exception {
        return httpCase("HTTP_STATUS", assertionDefinition);
    }

    private TestCase httpCase(String assertionType, String assertionDefinition) throws Exception {
        CaptureProfile profile = profileService.create("run-profile-" + System.nanoTime(), 1, "{}");
        List<TestCaseRequest.AssertionRequest> assertions = assertionDefinition == null ? List.of() : List.of(
                new TestCaseRequest.AssertionRequest(1, assertionType, objectMapper.readTree(assertionDefinition)));
        TestCaseRequest request = new TestCaseRequest("run case", profile.getId(), TriggerType.HTTP,
                objectMapper.readTree("{\"method\":\"POST\",\"url\":\"http://localhost/orders\"}"), 30, assertions);
        return testCaseService.create(request);
    }

    private HttpTriggerResponse orderResponse() {
        return new HttpTriggerResponse(201, "{\"data\":{\"orderNo\":\"ORD-001\"}}");
    }
}
