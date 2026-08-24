package com.talkanything.testevidence.sample.fulfillment;

import java.time.Instant;

public record FulfillmentResponse(
        String orderNo,
        String sku,
        int quantity,
        String note,
        String status,
        Instant fulfilledAt) {
    static FulfillmentResponse from(FulfillmentRecord record) {
        return new FulfillmentResponse(record.orderNo(), record.sku(), record.quantity(), record.note(),
                record.status(), record.fulfilledAt());
    }
}
