package com.talkanything.testevidence.sample.fulfillment;

import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeResponse;
import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeService;
import org.apache.dubbo.config.annotation.DubboService;

@DubboService
public class DubboFulfillmentProbeService implements FulfillmentProbeService {
    private final ProtocolProbeService protocolProbeService;

    public DubboFulfillmentProbeService(ProtocolProbeService protocolProbeService) {
        this.protocolProbeService = protocolProbeService;
    }

    @Override
    public FulfillmentProbeResponse echo(String orderNo) {
        ProtocolEchoResponse response = protocolProbeService.echo(orderNo);
        return new FulfillmentProbeResponse(response.service(), response.orderNo(), response.statementValue());
    }
}
