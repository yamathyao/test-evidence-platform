package com.talkanything.testevidence.sample.fulfillment;

import java.time.Instant;

public record FulfillmentRecord(
        String orderNo,
        String sku,
        int quantity,
        String note,
        String status,
        Instant fulfilledAt) { }
