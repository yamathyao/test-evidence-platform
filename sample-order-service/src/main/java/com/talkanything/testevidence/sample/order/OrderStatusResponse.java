package com.talkanything.testevidence.sample.order;

import java.time.Instant;

public record OrderStatusResponse(
        String orderNo,
        String sku,
        int quantity,
        String note,
        String status,
        Instant fulfilledAt) {
    static OrderStatusResponse from(FulfillmentResponse response) {
        return new OrderStatusResponse(response.orderNo(), response.sku(), response.quantity(), response.note(),
                response.status(), response.fulfilledAt());
    }
}
