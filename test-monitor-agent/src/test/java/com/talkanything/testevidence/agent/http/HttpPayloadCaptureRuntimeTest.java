package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpPayloadCaptureRuntimeTest {
    @Test
    void capturesEnabledJsonPayloadAndTruncatesAt64KiB() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.start(context(true), "application/json", null);
        runtime.request("{\"id\":1}".getBytes(StandardCharsets.UTF_8), 0, 8);
        runtime.response("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), 0, 11);

        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/json", null);

        assertEquals("{\"id\":1}", payload.requestBody());
        assertEquals("{\"ok\":true}", payload.responseBody());
        assertEquals("CAPTURED", payload.requestStatus());
        assertEquals("CAPTURED", payload.responseStatus());

        runtime.start(context(true), "application/json", null);
        runtime.request(new byte[65_537], 0, 65_537);
        payload = runtime.finish("application/json", null);
        assertTrue(payload.requestTruncated());
    }

    @Test
    void doesNotStoreDisabledOrUnsupportedBodies() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.start(context(false), "application/json", null);
        runtime.request("ignored".getBytes(StandardCharsets.UTF_8), 0, 7);
        assertEquals(null, runtime.finish("application/json", null));

        runtime.start(context(true), "multipart/form-data", null);
        runtime.request("ignored".getBytes(StandardCharsets.UTF_8), 0, 7);
        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/gzip", "gzip");
        assertEquals("UNSUPPORTED", payload.requestStatus());
        assertEquals("UNSUPPORTED", payload.responseStatus());
    }

    @Test
    void marksBodylessGetRequestAsNotApplicable() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.start(context(true), "GET", null, null);
        runtime.response("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), 0, 11);

        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/json", null);

        assertEquals("NOT_APPLICABLE", payload.requestStatus());
        assertEquals(null, payload.requestBody());
        assertEquals("CAPTURED", payload.responseStatus());
    }

    @Test
    void startsOnlyOnceForTheCurrentRequest() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.start(context(true), "application/json", null);
        runtime.request("before".getBytes(StandardCharsets.UTF_8), 0, 6);
        runtime.start(context(true), "application/json", null);
        runtime.response("after".getBytes(StandardCharsets.UTF_8), 0, 5);

        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/json", null);

        assertEquals("before", payload.requestBody());
        assertEquals("after", payload.responseBody());
    }

    @Test
    void promotesDeferredRequestBytesAfterBlackboxContextIsEstablished() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.startDeferred("application/json", null);
        runtime.request("{\"orderNo\":\"BLACKBOX-1\"}".getBytes(StandardCharsets.UTF_8), 0, 24);
        runtime.start(context(true), null, null);
        runtime.response("{\"processed\":true}".getBytes(StandardCharsets.UTF_8), 0, 18);

        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/json", null);

        assertEquals("{\"orderNo\":\"BLACKBOX-1\"}", payload.requestBody());
        assertEquals("{\"processed\":true}", payload.responseBody());
    }

    @Test
    void retainsInheritedPayloadWhenNoBlackboxBodyMatchExists() {
        HttpPayloadCaptureRuntime runtime = new HttpPayloadCaptureRuntime();
        runtime.start(context(true), "application/json", null);
        runtime.request("{\"id\":1}".getBytes(StandardCharsets.UTF_8), 0, 8);
        runtime.activate(null);
        runtime.response("{\"ok\":true}".getBytes(StandardCharsets.UTF_8), 0, 11);

        HttpPayloadCaptureRuntime.HttpPayload payload = runtime.finish("application/json", null);

        assertEquals("{\"id\":1}", payload.requestBody());
        assertEquals("{\"ok\":true}", payload.responseBody());
    }

    private TestContext context(boolean enabled) {
        return new TestContext("run", "case", "profile", 1, "trace", "span", "", enabled);
    }
}
