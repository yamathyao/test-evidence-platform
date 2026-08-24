package com.talkanything.testevidence.sample.order;

import java.time.Instant;

public record OrderResponse(
        String orderNo,
        boolean processed,
        String sku,
        int quantity,
        String note,
        String status,
        Instant fulfilledAt) {
    static OrderResponse from(FulfillmentResponse response) {
        return new OrderResponse(response.orderNo(), "FULFILLED".equals(response.status()), response.sku(),
                response.quantity(), response.note(), response.status(), response.fulfilledAt());
    }
}
