package com.talkanything.testevidence.agent.dubbo;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DubboPayloadCaptureTest {
    @Test
    void capturesArgumentsJavaBeanAndRecordResultAsJson() throws Exception {
        DubboPayloadCapture.Captured arguments = DubboPayloadCapture.arguments(new Object[] { "ORD-1" });
        DubboPayloadCapture.Captured result = DubboPayloadCapture.result(new Reply("OK"));
        Object recordReply = recordReply("REC-1");
        assertRecordFixture(recordReply);
        DubboPayloadCapture.Captured record = DubboPayloadCapture.result(recordReply);

        assertEquals("application/json", arguments.contentType());
        assertEquals("CAPTURED", arguments.status());
        assertEquals("[\"ORD-1\"]", arguments.body());
        assertFalse(arguments.truncated());
        assertEquals("{\"status\":\"OK\"}", result.body());
        assertEquals("{\"status\":\"REC-1\"}", record.body());
    }

    @Test
    void marksCyclicAndOversizedValuesAsTruncated() {
        Node node = new Node();
        node.self = node;
        DubboPayloadCapture.Captured cycle = DubboPayloadCapture.arguments(new Object[] { node });
        DubboPayloadCapture.Captured oversized = DubboPayloadCapture.result(repeat('x', 65_537));

        assertEquals("TRUNCATED", cycle.status());
        assertTrue(cycle.truncated());
        assertEquals("TRUNCATED", oversized.status());
        assertTrue(oversized.truncated());
        assertTrue(oversized.body().getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 65_536);
        assertDoesNotThrow(() -> new JsonParser(cycle.body()).parse());
        assertDoesNotThrow(() -> new JsonParser(oversized.body()).parse());
    }

    @Test
    void marksFailedOrInaccessibleGetterAsUnavailableWithoutThrowing() throws Exception {
        DubboPayloadCapture.Captured captured = assertDoesNotThrow(
                () -> DubboPayloadCapture.result(new BrokenReply()));
        DubboPayloadCapture.Captured inaccessible = assertDoesNotThrow(
                () -> DubboPayloadCapture.result(inaccessibleReply()));

        assertEquals("UNAVAILABLE", captured.status());
        assertFalse(captured.truncated());
        assertEquals(null, captured.body());
        assertEquals("UNAVAILABLE", inaccessible.status());
        assertFalse(inaccessible.truncated());
        assertEquals(null, inaccessible.body());
    }

    private static String repeat(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int index = 0; index < count; index++) result.append(value);
        return result.toString();
    }

    private static Object recordReply(String status) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path directory = Files.createTempDirectory("dubbo-record-");
        Path source = Files.createDirectories(directory.resolve("fixture")).resolve("RecordReply.java");
        Files.write(source, Collections.singletonList("package fixture; public record RecordReply(String status) {}"), StandardCharsets.UTF_8);
        assertEquals(0, compiler.run(null, null, null, "-d", directory.toString(), source.toString()));
        URLClassLoader loader = new URLClassLoader(new URL[] { directory.toUri().toURL() });
        Class<?> type = Class.forName("fixture.RecordReply", true, loader);
        return type.getConstructor(String.class).newInstance(status);
    }

    private static Object inaccessibleReply() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        Path directory = Files.createTempDirectory("dubbo-inaccessible-");
        Path source = Files.createDirectories(directory.resolve("fixture")).resolve("InaccessibleFactory.java");
        Files.write(source, Collections.singletonList("package fixture; public class InaccessibleFactory {"
                + " public static Object create() { return new InaccessibleReply(); } }"
                + " class InaccessibleReply { public String getStatus() { return \"hidden\"; } }"), StandardCharsets.UTF_8);
        assertEquals(0, compiler.run(null, null, null, "-d", directory.toString(), source.toString()));
        URLClassLoader loader = new URLClassLoader(new URL[] { directory.toUri().toURL() });
        Class<?> factory = Class.forName("fixture.InaccessibleFactory", true, loader);
        return factory.getMethod("create").invoke(null);
    }

    private static void assertRecordFixture(Object value) throws Exception {
        assertEquals(Boolean.TRUE, Class.class.getMethod("isRecord").invoke(value.getClass()));
        Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(value.getClass());
        assertEquals("status", components[0].getClass().getMethod("getName").invoke(components[0]));
        Object accessor = components[0].getClass().getMethod("getAccessor").invoke(components[0]);
        assertEquals("REC-1", ((java.lang.reflect.Method) accessor).invoke(value));
    }

    public static final class Reply {
        private final String status;

        public Reply(String status) {
            this.status = status;
        }

        public String getStatus() {
            return status;
        }
    }

    public static final class Node {
        public Node self;

        public Node getSelf() {
            return self;
        }
    }

    public static final class BrokenReply {
        public String getStatus() {
            throw new IllegalStateException("unavailable");
        }
    }

    private static final class JsonParser {
        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text;
        }

        private void parse() {
            value();
            if (index != text.length()) throw new IllegalArgumentException("trailing content");
        }

        private void value() {
            if (index >= text.length()) throw new IllegalArgumentException("missing value");
            char token = text.charAt(index);
            if (token == '{') object();
            else if (token == '[') array();
            else if (token == '"') string();
            else if (text.startsWith("null", index)) index += 4;
            else if (text.startsWith("true", index)) index += 4;
            else if (text.startsWith("false", index)) index += 5;
            else number();
        }

        private void object() {
            index++;
            if (accept('}')) return;
            while (true) {
                string();
                require(':');
                value();
                if (accept('}')) return;
                require(',');
            }
        }

        private void array() {
            index++;
            if (accept(']')) return;
            while (true) {
                value();
                if (accept(']')) return;
                require(',');
            }
        }

        private void string() {
            require('"');
            while (index < text.length() && text.charAt(index) != '"') {
                if (text.charAt(index++) == '\\') index++;
            }
            require('"');
        }

        private void number() {
            int start = index;
            while (index < text.length() && "-+.0123456789eE".indexOf(text.charAt(index)) >= 0) index++;
            if (start == index) throw new IllegalArgumentException("invalid value");
        }

        private boolean accept(char token) {
            if (index >= text.length() || text.charAt(index) != token) return false;
            index++;
            return true;
        }

        private void require(char token) {
            if (!accept(token)) throw new IllegalArgumentException("expected " + token);
        }
    }
}
