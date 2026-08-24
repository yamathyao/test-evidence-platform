package com.talkanything.testevidence.agent.blackbox;

import com.talkanything.testevidence.agent.context.TestContext;
import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.http.AgentDiagnostics;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;

public final class BlackboxRequestState {
    private static final int MAX_DEPTH = 5;
    private static final int MAX_SCALARS = 100;
    private static final ThreadLocal<BlackboxRequestValues> CURRENT = new ThreadLocal<BlackboxRequestValues>();

    private BlackboxRequestState() { }

    public static TestContext begin(Object request) {
        BlackboxRequestValues values = requestValues(request);
        CURRENT.set(values);
        return match(values);
    }

    public static TestContext matchBody(Object body) {
        BlackboxRequestValues values = CURRENT.get();
        if (values == null || body == null || body instanceof String) return null;
        collect(body, values, 0, new Counter(), new IdentityHashMap<Object, Boolean>());
        return match(values);
    }

    public static boolean mayMatchPayloadJsonBody() {
        BlackboxCorrelationRuntime runtime = BlackboxCorrelationRuntime.current();
        return runtime != null && runtime.hasPayloadCaptureJsonBodyRule();
    }

    public static BlackboxRequestValues bodyValues(Object body) {
        BlackboxRequestValues values = new BlackboxRequestValues();
        if (body != null && !(body instanceof String)) {
            collect(body, values, 0, new Counter(), new IdentityHashMap<Object, Boolean>());
        }
        return values;
    }

    public static void clear() { CURRENT.remove(); }

    private static TestContext match(BlackboxRequestValues values) {
        if (TestContextHolder.currentOrNull() != null) return TestContextHolder.currentOrNull();
        BlackboxCorrelationRuntime runtime = BlackboxCorrelationRuntime.current();
        BlackboxRule rule = runtime == null ? null : runtime.matchRequest(values);
        if (rule == null) return null;
        AgentDiagnostics.log("blackbox.request-match.run=" + rule.runId() + " service-rule=" + rule.ruleId());
        return TestContextHolder.enter(rule.runId(), rule.caseId(), rule.profileId(), rule.profileVersion(), rule.payloadCaptureEnabled());
    }

    private static BlackboxRequestValues requestValues(Object request) {
        BlackboxRequestValues values = new BlackboxRequestValues();
        String uri = invoke(request, "getRequestURI");
        String[] segments = uri.split("/");
        for (int index = 0; index < segments.length; index++) values.addPathValue(decoded(segments[index]));
        query(values, invoke(request, "getQueryString"));
        headers(values, request);
        form(values, request);
        return values;
    }

    private static void query(BlackboxRequestValues values, String source) {
        if (source == null || source.isEmpty()) return;
        String[] pairs = source.split("&");
        for (int index = 0; index < pairs.length; index++) {
            String[] pair = pairs[index].split("=", 2);
            if (pair.length == 2) values.put("QUERY", decoded(pair[0]), decoded(pair[1]));
        }
    }

    @SuppressWarnings("unchecked")
    private static void headers(BlackboxRequestValues values, Object request) {
        try {
            Object names = request.getClass().getMethod("getHeaderNames").invoke(request);
            if (!(names instanceof Enumeration)) return;
            Enumeration<String> enumeration = (Enumeration<String>) names;
            while (enumeration.hasMoreElements()) {
                String name = enumeration.nextElement();
                values.put("HEADER", name, invokeHeader(request, name));
            }
        } catch (Throwable ignored) { }
    }

    @SuppressWarnings("unchecked")
    private static void form(BlackboxRequestValues values, Object request) {
        try {
            Object source = request.getClass().getMethod("getParameterMap").invoke(request);
            if (!(source instanceof Map)) return;
            for (Map.Entry<Object, Object> entry : ((Map<Object, Object>) source).entrySet()) {
                Object value = entry.getValue();
                if (value instanceof String[] && ((String[]) value).length > 0) {
                    values.put("FORM", String.valueOf(entry.getKey()), ((String[]) value)[0]);
                }
            }
        } catch (Throwable ignored) { }
    }

    private static void collect(Object value, BlackboxRequestValues values, int depth, Counter counter,
                                IdentityHashMap<Object, Boolean> visited) {
        if (value == null || depth > MAX_DEPTH || counter.value >= MAX_SCALARS || scalar(value)) return;
        if (visited.put(value, Boolean.TRUE) != null) return;
        if (value instanceof Map) collectMap((Map<?, ?>) value, values, depth, counter, visited);
        else if (value instanceof Iterable) collectIterable((Iterable<?>) value, values, depth, counter, visited);
        else collectBean(value, values, depth, counter, visited);
    }

    private static void collectMap(Map<?, ?> map, BlackboxRequestValues values, int depth, Counter counter,
                                   IdentityHashMap<Object, Boolean> visited) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            collectField(String.valueOf(entry.getKey()), entry.getValue(), values, depth, counter, visited);
        }
    }

    private static void collectIterable(Iterable<?> items, BlackboxRequestValues values, int depth, Counter counter,
                                        IdentityHashMap<Object, Boolean> visited) {
        Iterator<?> iterator = items.iterator();
        while (iterator.hasNext() && counter.value < MAX_SCALARS) collect(iterator.next(), values, depth + 1, counter, visited);
    }

    private static void collectBean(Object bean, BlackboxRequestValues values, int depth, Counter counter,
                                    IdentityHashMap<Object, Boolean> visited) {
        Method[] methods = bean.getClass().getMethods();
        for (int index = 0; index < methods.length && counter.value < MAX_SCALARS; index++) {
            Method method = methods[index];
            String field = field(method);
            if (field == null) continue;
            try { collectField(field, method.invoke(bean), values, depth, counter, visited); }
            catch (Throwable ignored) { }
        }
    }

    private static void collectField(String field, Object value, BlackboxRequestValues values, int depth,
                                     Counter counter, IdentityHashMap<Object, Boolean> visited) {
        if (scalar(value)) {
            values.put("JSON_BODY", field, String.valueOf(value));
            counter.value++;
        } else collect(value, values, depth + 1, counter, visited);
    }

    private static boolean scalar(Object value) {
        return value instanceof CharSequence || value instanceof Number || value instanceof Boolean
                || value instanceof Character || value instanceof Enum;
    }

    private static String field(Method method) {
        if (method.getParameterTypes().length != 0 || method.getReturnType() == Void.TYPE || method.getName().equals("getClass")) return null;
        String name = method.getName();
        if (name.startsWith("get") && name.length() > 3) return lower(name.substring(3));
        if (name.startsWith("is") && name.length() > 2) return lower(name.substring(2));
        return hasField(method.getDeclaringClass(), name) ? name : null;
    }

    private static boolean hasField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                current.getDeclaredField(name);
                return true;
            } catch (NoSuchFieldException ignored) { }
        }
        return false;
    }

    private static String lower(String value) { return Character.toLowerCase(value.charAt(0)) + value.substring(1); }
    private static String invoke(Object target, String method) { try { Object value = target.getClass().getMethod(method).invoke(target); return value == null ? "" : value.toString(); } catch (Throwable ignored) { return ""; } }
    private static String invokeHeader(Object target, String name) { try { Object value = target.getClass().getMethod("getHeader", String.class).invoke(target, name); return value == null ? "" : value.toString(); } catch (Throwable ignored) { return ""; } }
    private static String decoded(String value) { try { return URLDecoder.decode(value, "UTF-8"); } catch (Exception ignored) { return value; } }
    private static final class Counter { private int value; }
}
