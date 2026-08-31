package com.talkanything.testevidence.agent.dubbo;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboInstrumentationTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void capturesClientServerEvidenceForBothLegacyDubboPackages() throws Exception {
        CountDownLatch delivered = new CountDownLatch(4);
        List<EvidencePayload> captured = new CopyOnWriteArrayList<EvidencePayload>();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(8, 1, (events, diagnostics) -> {
            captured.addAll(events);
            for (EvidencePayload ignored : events) delivered.countDown();
        });
        DubboEvidenceRuntime.initialize("order-service", reporter);
        Class.forName("com.alibaba.dubbo.rpc.protocol.dubbo.DubboInvoker");
        Class.forName("com.alibaba.dubbo.rpc.filter.ContextFilter");
        Class.forName("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker");
        Class.forName("org.apache.dubbo.rpc.filter.ContextFilter");
        DubboInstrumentation.install(net.bytebuddy.agent.ByteBuddyAgent.install());
        TestContextHolder.enter("run", "case", "profile", 1);

        invokeVersion("com.alibaba.dubbo.rpc.protocol.dubbo.DubboInvoker",
                "com.alibaba.dubbo.rpc.filter.ContextFilter");
        invokeVersion("org.apache.dubbo.rpc.protocol.dubbo.DubboInvoker",
                "org.apache.dubbo.rpc.filter.ContextFilter");

        assertTrue(delivered.await(2, TimeUnit.SECONDS));
        assertEquals(4, captured.size());
        assertEquals(2, count(captured, "CLIENT"));
        assertEquals(2, count(captured, "SERVER"));
        reporter.close();
    }

    private static void invokeVersion(String invokerName, String filterName) throws Exception {
        Invocation invocation = new Invocation();
        Class<?> invokerType = Class.forName(invokerName);
        Object invoker = invokerType.getConstructor(Class.class).newInstance(EchoService.class);
        Method doInvoke = invokerType.getMethod("doInvoke", Object.class);
        doInvoke.invoke(invoker, invocation);
        Class<?> filterType = Class.forName(filterName);
        filterType.getMethod("invoke", Object.class, Object.class).invoke(filterType.newInstance(), invoker, invocation);
    }

    private static int count(List<EvidencePayload> events, String direction) {
        int count = 0;
        for (EvidencePayload event : events) if (event.toJson().contains("\"direction\":\"" + direction)) count++;
        return count;
    }

    public interface EchoService { }

    public static final class Invocation {
        private final java.util.Map<String, String> attachments = new java.util.HashMap<String, String>();

        public java.util.Map<String, String> getAttachments() { return attachments; }
        public String getMethodName() { return "echo"; }
    }
}
