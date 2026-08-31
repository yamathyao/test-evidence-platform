package com.talkanything.testevidence.sample.order.protocol;

public record ProtocolEchoResponse(String service, String orderNo, int statementValue) {
}