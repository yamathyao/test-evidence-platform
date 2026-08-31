package com.talkanything.testevidence.sample.fulfillment;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DubboFulfillmentProbeServiceTest {
    @Test
    void delegatesTheDubboRequestToTheExistingStatementProbe() {
        ProtocolProbeService probe = mock(ProtocolProbeService.class);
        when(probe.echo("dubbo-order-1"))
                .thenReturn(new ProtocolEchoResponse("fulfillment", "dubbo-order-1", 1));

        var response = new DubboFulfillmentProbeService(probe).echo("dubbo-order-1");

        assertEquals("fulfillment", response.service());
        assertEquals("dubbo-order-1", response.orderNo());
        assertEquals(1, response.statementValue());
    }
}
