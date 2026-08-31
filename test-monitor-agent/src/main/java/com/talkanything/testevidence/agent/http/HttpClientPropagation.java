package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContext;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class HttpClientPropagation {
    private HttpClientPropagation() { }

    public static void inject(Object requestOrHeaders, TestContext context) {
        if (requestOrHeaders == null || context == null) return;
        Object headers = headers(requestOrHeaders);
        for (Map.Entry<String, String> entry : headers(context).entrySet()) {
            putIfMissing(headers, entry.getKey(), entry.getValue());
        }
    }

    public static Object copyWithHeaders(Object request, Map<String, String> headers) {
        if (request == null || headers == null || headers.isEmpty()) return request;
        try {
            Object builder = request.getClass().getMethod("newBuilder").invoke(request);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                if (!hasHeader(request, entry.getKey())) {
                    builder = builder.getClass().getMethod("header", String.class, String.class)
                            .invoke(builder, entry.getKey(), entry.getValue());
                }
            }
            return builder.getClass().getMethod("build").invoke(builder);
        } catch (Throwable ignored) {
            return request;
        }
    }

    public static Map<String, String> headers(TestContext context) {
        Map<String, String> values = new LinkedHashMap<String, String>();
        if (context == null) return values;
        values.put("X-Test-Run-Id", context.runId());
        values.put("X-Test-Case-Id", context.caseId());
        values.put("X-Test-Profile-Id", context.profileId());
        values.put("X-Test-Profile-Version", Integer.toString(context.profileVersion()));
        values.put("X-Test-Trace-Id", context.traceId());
        values.put("X-Test-Parent-Span-Id", context.spanId());
        if (context.payloadCaptureEnabled()) values.put("X-Test-Capture-Http-Payload", "true");
        return values;
    }

    private static Object headers(Object requestOrHeaders) {
        try {
            Object value = requestOrHeaders.getClass().getMethod("getHeaders").invoke(requestOrHeaders);
            return value == null || value.getClass().isArray() ? requestOrHeaders : value;
        } catch (Throwable ignored) {
            return requestOrHeaders;
        }
    }

    private static void putIfMissing(Object headers, String name, String value) {
        if (headers instanceof Map) {
            Map values = (Map) headers;
            if (!values.containsKey(name)) values.put(name, Collections.singletonList(value));
            return;
        }
        try {
            Method getFirst = headers.getClass().getMethod("getFirst", String.class);
            if (getFirst.invoke(headers, name) == null) {
                headers.getClass().getMethod("set", String.class, String.class).invoke(headers, name, value);
            }
        } catch (Throwable ignored) {
            try {
                Object header = headers.getClass().getMethod("getFirstHeader", String.class).invoke(headers, name);
                if (header == null) setHeader(headers, name, value);
            } catch (Throwable suppressed) { }
        }
    }

    private static void setHeader(Object headers, String name, String value) throws Exception {
        try {
            headers.getClass().getMethod("setHeader", String.class, String.class).invoke(headers, name, value);
        } catch (NoSuchMethodException ignored) {
            headers.getClass().getMethod("setHeader", String.class, Object.class).invoke(headers, name, value);
        }
    }

    private static boolean hasHeader(Object request, String name) {
        try {
            Object value = request.getClass().getMethod("header", String.class).invoke(request, name);
            return value != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
