package com.talkanything.testevidence.sample.order.protocol;

public interface ProtocolClient {
    String name();

    ProtocolEchoResponse invoke(ProtocolInvocation request);
}