package com.talkanything.testevidence.agent.feign;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FeignInstrumentationTest {
    @AfterEach
    void clearContext() { TestContextHolder.clear(); }

    @Test
    void injectsHeadersAndCompletesSynchronousFeignCall() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        Map<String, java.util.Collection<String>> headers = new LinkedHashMap<String, java.util.Collection<String>>();
        Request request = Request.create(Request.HttpMethod.GET, "http://inventory/items",
                Collections.unmodifiableMap(headers), null, StandardCharsets.UTF_8);

        FeignExecutionAdvice.ExecutionState state = FeignExecutionAdvice.enter(new Object[] {request});
        FeignExecutionAdvice.exit(state, Response.builder().status(201).reason("Created")
                .request(request).headers(Collections.<String, java.util.Collection<String>>emptyMap()).build(), null);

        assertNotNull(request.headers().get("X-Test-Run-Id"));
        assertEquals("run-1", request.headers().get("X-Test-Run-Id").iterator().next());
    }
}
