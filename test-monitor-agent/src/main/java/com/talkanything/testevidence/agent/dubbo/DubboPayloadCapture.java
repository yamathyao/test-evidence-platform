package com.talkanything.testevidence.agent.dubbo;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
public final class DubboPayloadCapture {
    private static final int MAX_BYTES = 65_536;
    private static final int MAX_DEPTH = 32;
    private static final int MAX_ELEMENTS = 1_024;
    private static final int MAX_CONTAINER_ELEMENTS = 256;
    private DubboPayloadCapture() {
    }
    public static Captured arguments(Object[] arguments) {
        return capture(arguments);
    }
    public static Captured result(Object result) {
        return capture(result);
    }

    public static Captured resultValue(Object result) {
        if (result == null) return unavailable();
        try { return result(result.getClass().getMethod("getValue").invoke(result)); }
        catch (Throwable ignored) { return unavailable(); }
    }

    public static Captured unavailable() {
        return new Captured("application/json", "UNAVAILABLE", null, false);
    }
    private static Captured capture(Object value) {
        Writer writer = new Writer();
        try {
            new Formatter(writer).write(value, 0);
        } catch (Throwable ignored) {
            writer.unavailable = true;
        }
        if (writer.unavailable) return unavailable();
        String status = writer.truncated ? "TRUNCATED" : "CAPTURED";
        return new Captured("application/json", status, writer.truncated ? "null" : writer.output.toString(), writer.truncated);
    }
    public static final class Captured {
        private final String contentType;
        private final String status;
        private final String body;
        private final boolean truncated;
        private Captured(String contentType, String status, String body, boolean truncated) {
            this.contentType = contentType;
            this.status = status;
            this.body = body;
            this.truncated = truncated;
        }
        public String contentType() { return contentType; }
        public String status() { return status; }
        public String body() { return body; }
        public boolean truncated() { return truncated; }
    }
    private static final class Formatter {
        private final Writer writer;
        private final IdentityHashMap<Object, Boolean> active = new IdentityHashMap<Object, Boolean>();
        private int elements;
        private Formatter(Writer writer) {
            this.writer = writer;
        }
        private void write(Object value, int depth) {
            if (writer.finished()) return;
            if (!takeElement() || depth > MAX_DEPTH) {
                writer.truncated = true;
                writer.append("null");
                return;
            }
            if (value == null) {
                writer.append("null");
            } else if (writeScalar(value)) {
                return;
            } else if (active.put(value, Boolean.TRUE) != null) {
                writer.truncated = true;
                writer.append("null");
            } else {
                try {
                    writeStructured(value, depth);
                } catch (Throwable ignored) {
                    writer.unavailable = true;
                } finally {
                    active.remove(value);
                }
            }
        }
        private boolean writeScalar(Object value) {
            Class<?> type = value.getClass();
            if (value instanceof String || value instanceof Character) {
                writer.string(value instanceof String ? (String) value : Character.toString(((Character) value).charValue()));
                return true;
            }
            if (type == Boolean.class) {
                writer.append(((Boolean) value).booleanValue() ? "true" : "false");
                return true;
            }
            String number = number(value, type);
            if (number != null) {
                writer.append(number);
                return true;
            }
            if (value instanceof Enum<?>) {
                writer.string(((Enum<?>) value).name());
                return true;
            }
            return false;
        }
        private String number(Object value, Class<?> type) {
            if (type == Byte.class) return Byte.toString(((Byte) value).byteValue());
            if (type == Short.class) return Short.toString(((Short) value).shortValue());
            if (type == Integer.class) return Integer.toString(((Integer) value).intValue());
            if (type == Long.class) return Long.toString(((Long) value).longValue());
            if (type == Float.class) return decimal(((Float) value).floatValue());
            if (type == Double.class) return decimal(((Double) value).doubleValue());
            if (type == BigInteger.class) return ((BigInteger) value).toString();
            if (type == BigDecimal.class) return ((BigDecimal) value).toPlainString();
            return null;
        }
        private String decimal(float value) {
            return Float.isFinite(value) ? Float.toString(value) : "null";
        }
        private String decimal(double value) {
            return Double.isFinite(value) ? Double.toString(value) : "null";
        }
        private void writeStructured(Object value, int depth) throws Exception {
            if (value.getClass().isArray()) {
                array(value, depth);
            } else if (value instanceof Iterable<?>) {
                iterable((Iterable<?>) value, depth);
            } else if (value instanceof Map<?, ?>) {
                map((Map<?, ?>) value, depth);
            } else if (record(value, depth)) {
                return;
            } else {
                bean(value, depth);
            }
        }
        private void array(Object value, int depth) {
            writer.append("[");
            int length = Array.getLength(value);
            int limit = Math.min(length, MAX_CONTAINER_ELEMENTS);
            for (int index = 0; index < limit && !writer.finished(); index++) {
                if (index > 0) writer.append(",");
                write(Array.get(value, index), depth + 1);
            }
            if (length > limit) writer.truncated = true;
            writer.append("]");
        }
        private void iterable(Iterable<?> value, int depth) {
            writer.append("[");
            Iterator<?> values = value.iterator();
            int count = 0;
            while (values.hasNext() && count < MAX_CONTAINER_ELEMENTS && !writer.finished()) {
                if (count > 0) writer.append(",");
                write(values.next(), depth + 1);
                count++;
            }
            if (values.hasNext()) writer.truncated = true;
            writer.append("]");
        }
        private void map(Map<?, ?> value, int depth) {
            writer.append("{");
            Iterator<? extends Map.Entry<?, ?>> entries = value.entrySet().iterator();
            int count = 0;
            while (entries.hasNext() && count < MAX_CONTAINER_ELEMENTS && !writer.finished()) {
                Map.Entry<?, ?> entry = entries.next();
                if (count > 0) writer.append(",");
                writer.string(key(entry.getKey()));
                writer.append(":");
                write(entry.getValue(), depth + 1);
                count++;
            }
            if (entries.hasNext()) writer.truncated = true;
            writer.append("}");
        }
        private void bean(Object value, int depth) throws Exception {
            List<Getter> getters = getters(value.getClass());
            writer.append("{");
            for (int index = 0; index < getters.size() && !writer.finished(); index++) {
                if (index > 0) writer.append(",");
                Getter getter = getters.get(index);
                writer.string(getter.name);
                writer.append(":");
                write(getter.method.invoke(value), depth + 1);
            }
            writer.append("}");
        }
        private boolean record(Object value, int depth) throws Exception {
            Method isRecord;
            try {
                isRecord = Class.class.getMethod("isRecord");
            } catch (NoSuchMethodException ignored) {
                return false;
            }
            if (!Boolean.TRUE.equals(isRecord.invoke(value.getClass()))) return false;
            Object[] components = (Object[]) Class.class.getMethod("getRecordComponents").invoke(value.getClass());
            writer.append("{");
            for (int index = 0; index < components.length && !writer.finished(); index++) {
                if (index > 0) writer.append(",");
                Object component = components[index];
                Method name = component.getClass().getMethod("getName");
                Method accessor = (Method) component.getClass().getMethod("getAccessor").invoke(component);
                writer.string((String) name.invoke(component));
                writer.append(":");
                write(accessor.invoke(value), depth + 1);
            }
            writer.append("}");
            return true;
        }
        private List<Getter> getters(Class<?> type) {
            List<Getter> result = new ArrayList<Getter>();
            for (Method method : type.getMethods()) {
                String name = property(method);
                if (name != null) result.add(new Getter(name, method));
            }
            Collections.sort(result, new Comparator<Getter>() {
                @Override
                public int compare(Getter left, Getter right) {
                    return left.name.compareTo(right.name);
                }
            });
            return result;
        }
        private String property(Method method) {
            if (Modifier.isStatic(method.getModifiers()) || method.getParameterTypes().length != 0
                    || method.getReturnType() == Void.TYPE || "getClass".equals(method.getName())) return null;
            String name = method.getName();
            if (name.startsWith("get") && name.length() > 3) return decapitalize(name.substring(3));
            if (name.startsWith("is") && name.length() > 2
                    && (method.getReturnType() == Boolean.TYPE || method.getReturnType() == Boolean.class)) {
                return decapitalize(name.substring(2));
            }
            return null;
        }
        private String decapitalize(String value) {
            if (value.length() > 1 && Character.isUpperCase(value.charAt(0)) && Character.isUpperCase(value.charAt(1))) return value;
            return Character.toLowerCase(value.charAt(0)) + value.substring(1);
        }
        private String key(Object value) {
            if (value instanceof String) return (String) value;
            if (value instanceof Character) return Character.toString(((Character) value).charValue());
            if (value instanceof Enum<?>) return ((Enum<?>) value).name();
            String scalar = number(value, value == null ? null : value.getClass());
            if (scalar != null) return scalar;
            if (value instanceof Boolean) return ((Boolean) value).booleanValue() ? "true" : "false";
            return "unsupported";
        }
        private boolean takeElement() {
            elements++;
            return elements <= MAX_ELEMENTS;
        }
    }
    private static final class Getter {
        private final String name;
        private final Method method;
        private Getter(String name, Method method) {
            this.name = name;
            this.method = method;
        }
    }
    private static final class Writer {
        private final StringBuilder output = new StringBuilder();
        private boolean truncated;
        private boolean unavailable;
        private void append(String value) {
            if (finished() || output.length() + value.length() > MAX_BYTES) {
                truncated = true;
                return;
            }
            output.append(value);
        }
        private void string(String value) {
            append("\"");
            for (int index = 0; value != null && index < value.length() && !finished(); index++) {
                char character = value.charAt(index);
                if (character == '\\' || character == '\"') append("\\" + character);
                else if (character == '\b') append("\\b");
                else if (character == '\f') append("\\f");
                else if (character == '\n') append("\\n");
                else if (character == '\r') append("\\r");
                else if (character == '\t') append("\\t");
                else if (character < 0x20 || character > 0x7e) unicode(character);
                else append(Character.toString(character));
            }
            append("\"");
        }
        private void unicode(char character) {
            String hex = Integer.toHexString(character);
            append("\\u");
            for (int index = hex.length(); index < 4; index++) append("0");
            append(hex);
        }
        private boolean finished() {
            return truncated || unavailable;
        }
    }
}
