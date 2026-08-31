package com.talkanything.testevidence.sample.dubbo;

import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfillmentProbeContractTest {
    @Test
    void exposesAStableSingleMethodContractAndResponseFields() {
        FulfillmentProbeService service = orderNo -> new FulfillmentProbeResponse("fulfillment", orderNo, 1);

        FulfillmentProbeResponse response = service.echo("dubbo-order-1");

        assertEquals("fulfillment", response.service());
        assertEquals("dubbo-order-1", response.orderNo());
        assertEquals(1, response.statementValue());
    }

    @Test
    void responseIsSerializableForDubboTransport() {
        assertTrue(Serializable.class.isAssignableFrom(FulfillmentProbeResponse.class));
    }

    @Test
    void responseSupportsHessianFieldDeserialization() throws NoSuchMethodException {
        assertTrue(FulfillmentProbeResponse.class.getConstructor() != null);
        assertTrue(java.util.Arrays.stream(FulfillmentProbeResponse.class.getDeclaredFields())
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .noneMatch(field -> Modifier.isFinal(field.getModifiers())));
    }
}
