package com.talkanything.testevidence.sample.order;

import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeResponse;
import com.talkanything.testevidence.sample.dubbo.FulfillmentProbeService;
import org.apache.dubbo.config.annotation.DubboReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DubboFulfillmentProbeClient {
    private static final Logger logger = LoggerFactory.getLogger(DubboFulfillmentProbeClient.class);

    @DubboReference(url = "${sample.fulfillment.dubbo-url}", check = false)
    private FulfillmentProbeService fulfillmentProbeService;

    public FulfillmentProbeResponse echo(String orderNo) {
        try {
            return fulfillmentProbeService.echo(orderNo);
        } catch (RuntimeException exception) {
            logger.error("Dubbo fulfillment probe call failed", exception);
            throw new IllegalStateException("Dubbo fulfillment probe failed", exception);
        }
    }
}
