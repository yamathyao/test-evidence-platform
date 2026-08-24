package com.talkanything.testevidence.sample.fulfillment;

public record FulfillmentRequest(String orderNo, String sku, Integer quantity, String note) { }
