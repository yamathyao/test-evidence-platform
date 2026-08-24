package com.talkanything.testevidence.agent.jdbc;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import com.talkanything.testevidence.agent.evidence.EvidencePayload;
import com.talkanything.testevidence.agent.http.AsyncEvidenceReporter;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlJdbcInstrumentationTest {
    @AfterEach
    void clearContext() {
        TestContextHolder.clear();
    }

    @Test
    void capturesBothSupportedMysqlPreparedStatementVersions() throws Exception {
        CountDownLatch delivered = new CountDownLatch(4);
        AtomicInteger jdbcEvents = new AtomicInteger();
        AsyncEvidenceReporter reporter = new AsyncEvidenceReporter(8, 1, events -> countJdbcEvents(events, jdbcEvents, delivered));
        JdbcEvidenceRuntime.initialize("order-service", reporter);
        Class.forName("com.mysql.jdbc.PreparedStatement");
        Class.forName("com.mysql.cj.jdbc.ClientPreparedStatement");
        Class.forName("com.zaxxer.hikari.pool.HikariProxyPreparedStatement");
        Class.forName("com.alibaba.druid.pool.DruidPooledPreparedStatement");
        TransformListener listener = new TransformListener();
        MySqlJdbcInstrumentation.install(ByteBuddyAgent.install(), listener);
        TestContextHolder.enter("run", "case", "profile", 1);

        execute("com.mysql.jdbc.PreparedStatement");
        execute("com.mysql.cj.jdbc.ClientPreparedStatement");
        execute("com.zaxxer.hikari.pool.HikariProxyPreparedStatement");
        execute("com.alibaba.druid.pool.DruidPooledPreparedStatement");

        assertTrue(listener.transformed.contains("com.mysql.jdbc.PreparedStatement"), listener.error);
        assertTrue(listener.transformed.contains("com.mysql.cj.jdbc.ClientPreparedStatement"), listener.error);
        assertTrue(listener.transformed.contains("com.zaxxer.hikari.pool.HikariProxyPreparedStatement"), listener.error);
        assertTrue(listener.transformed.contains("com.alibaba.druid.pool.DruidPooledPreparedStatement"), listener.error);
        assertTrue(delivered.await(2, TimeUnit.SECONDS), listener.error);
        assertEquals(4, jdbcEvents.get());
        reporter.close();
    }

    private static void execute(String className) throws Exception {
        Class<?> type = Class.forName(className);
        Object statement = type.getConstructor(String.class).newInstance("UPDATE orders SET status = ?");
        Method setString = type.getMethod("setString", int.class, String.class);
        setString.invoke(statement, Integer.valueOf(1), "PAID");
        type.getMethod("executeUpdate").invoke(statement);
    }

    private static void countJdbcEvents(List<EvidencePayload> events, AtomicInteger count, CountDownLatch delivered) {
        for (EvidencePayload event : events) {
            if (event.toJson().contains("\"protocol\":\"JDBC\"")) {
                count.incrementAndGet();
                delivered.countDown();
            }
        }
    }

    private static final class TransformListener extends AgentBuilder.Listener.Adapter {
        private volatile String error = "";
        private final Set<String> transformed = ConcurrentHashMap.newKeySet();

        @Override
        public void onError(String typeName, ClassLoader loader, JavaModule module, boolean loaded, Throwable throwable) {
            if (isJdbcStatementType(typeName)) error = throwable.toString();
        }

        @Override
        public void onTransformation(TypeDescription type, ClassLoader loader, JavaModule module,
                                     boolean loaded, DynamicType dynamicType) {
            if (isJdbcStatementType(type.getName())) transformed.add(type.getName());
        }

        private static boolean isJdbcStatementType(String typeName) {
            return typeName.startsWith("com.mysql.") || typeName.startsWith("com.zaxxer.hikari.")
                    || typeName.startsWith("com.alibaba.druid.");
        }
    }
}
