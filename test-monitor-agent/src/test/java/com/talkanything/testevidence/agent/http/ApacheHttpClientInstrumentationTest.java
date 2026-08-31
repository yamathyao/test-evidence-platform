package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import org.apache.http.HttpVersion;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.BasicHttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ApacheHttpClientInstrumentationTest {
    @AfterEach
    void clearContext() { TestContextHolder.clear(); }

    @Test
    void injectsHeadersAndCompletesApacheHttpClient4Request() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        BasicHttpRequest request = new BasicHttpRequest("GET", "/orders");

        HttpClientExecutionAdvice.ExecutionState state = HttpClientExecutionAdvice.enter(new Object[] {request});
        HttpClientExecutionAdvice.exit(state, new BasicHttpResponse(HttpVersion.HTTP_1_1, 201, "Created"), null);

        assertNotNull(request.getFirstHeader("X-Test-Run-Id"));
        assertEquals("run-1", request.getFirstHeader("X-Test-Run-Id").getValue());
    }

    @Test
    void injectsHeadersIntoApacheHttpClient5Request() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        org.apache.hc.client5.http.classic.methods.HttpGet request =
                new org.apache.hc.client5.http.classic.methods.HttpGet("/orders");

        HttpClientExecutionAdvice.enter(new Object[] {request});

        assertNotNull(request.getFirstHeader("X-Test-Run-Id"));
        assertEquals("run-1", request.getFirstHeader("X-Test-Run-Id").getValue());
    }
}
