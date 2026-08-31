package com.talkanything.testevidence.agent.http;

import com.talkanything.testevidence.agent.context.TestContextHolder;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

class WebClientInstrumentationTest {
    @AfterEach
    void clearContext() { TestContextHolder.clear(); }

    @Test
    void copiesRequestHeadersAndPreservesMonoCompletion() {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1);
        ClientRequest original = ClientRequest.create(HttpMethod.GET, URI.create("http://inventory/items")).build();

        ClientRequest copied = (ClientRequest) WebClientMonoAdvice.copyRequest(original);
        ClientResponse response = WebClientMonoAdvice.wrap(copied, Mono.just(ClientResponse.create(HttpStatus.OK).build())).block();

        assertEquals("run-1", copied.headers().getFirst("X-Test-Run-Id"));
        assertEquals(200, response.rawStatusCode());
    }

    @Test
    void copiesHeadersWhenAgentClassLoaderCannotSeeSpring() throws Exception {
        TestContextHolder.enter("run-1", "case-1", "profile-1", 1, true);
        ClientRequest original = ClientRequest.create(HttpMethod.GET, URI.create("http://inventory/items")).build();
        Class<?> isolatedAdvice = Class.forName(WebClientMonoAdvice.class.getName(), true,
                new SpringHiddenAdviceClassLoader(WebClientMonoAdvice.class.getClassLoader()));
        Method copyRequest = isolatedAdvice.getMethod("copyRequest", Object.class);

        ClientRequest copied = (ClientRequest) copyRequest.invoke(null, original);

        assertNotSame(original, copied);
        assertEquals("run-1", copied.headers().getFirst("X-Test-Run-Id"));
        assertEquals("true", copied.headers().getFirst("X-Test-Capture-Http-Payload"));
    }

    private static final class SpringHiddenAdviceClassLoader extends ClassLoader {
        private static final String ADVICE = WebClientMonoAdvice.class.getName();

        private SpringHiddenAdviceClassLoader(ClassLoader parent) { super(parent); }

        @Override protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.springframework.")) throw new ClassNotFoundException(name);
            if (!name.equals(ADVICE) && !name.startsWith(ADVICE + "$")) return super.loadClass(name, resolve);
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) loaded = defineAdviceClass(name);
            if (resolve) resolveClass(loaded);
            return loaded;
        }

        private Class<?> defineAdviceClass(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream input = getParent().getResourceAsStream(resource)) {
                if (input == null) throw new ClassNotFoundException(name);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                for (int read; (read = input.read(buffer)) != -1;) output.write(buffer, 0, read);
                byte[] bytes = output.toByteArray();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (java.io.IOException exception) {
                throw new ClassNotFoundException(name, exception);
            }
        }
    }
}
