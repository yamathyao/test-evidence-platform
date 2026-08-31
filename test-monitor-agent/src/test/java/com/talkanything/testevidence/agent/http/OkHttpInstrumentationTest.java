package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class OkHttpInstrumentationTest {
    @AfterEach
    void clearContext() { TestContextHolder.clear(); }

    @Test
    void copiesRequestWithMissingTestHeadersAndCompletesSyncCall() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        Request original = new Request.Builder().url("http://inventory/items")
                .header("X-Test-Trace-Id", "caller-trace").build();

        Request copied = (Request) OkHttpNewCallAdvice.copy(original);
        OkHttpExecutionAdvice.ExecutionState state = OkHttpExecutionAdvice.enter(copied);
        Response response = new Response.Builder().request(copied).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").build();
        OkHttpExecutionAdvice.exit(state, response, null);

        assertNotSame(original, copied);
        assertEquals("run-1", copied.header("X-Test-Run-Id"));
        assertEquals("caller-trace", copied.header("X-Test-Trace-Id"));
    }
}