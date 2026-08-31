package com.talkanything.testevidence.sample.order.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProtocolClientRegistryTest {
    @Test
    void returnsClientByStableProtocolName() {
        ProtocolClient client = new StubProtocolClient("okhttp");

        assertSame(client, new ProtocolClientRegistry(List.of(client)).require("okhttp"));
    }

    @Test
    void rejectsUnknownProtocol() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtocolClientRegistry(List.of()).require("unknown"));
    }

    @Test
    void rejectsDuplicateProtocolNames() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProtocolClientRegistry(List.of(
                        new StubProtocolClient("okhttp"), new StubProtocolClient("okhttp"))));
    }

    private record StubProtocolClient(String name) implements ProtocolClient {
        @Override
        public ProtocolEchoResponse invoke(ProtocolInvocation request) {
            throw new UnsupportedOperationException();
        }
    }
}