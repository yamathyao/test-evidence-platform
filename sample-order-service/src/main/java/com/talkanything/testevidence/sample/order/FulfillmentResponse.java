package com.talkanything.testevidence.sample.order;

import java.time.Instant;

public record FulfillmentResponse(
        String orderNo,
        String sku,
        int quantity,
        String note,
        String status,
        Instant fulfilledAt) { }
