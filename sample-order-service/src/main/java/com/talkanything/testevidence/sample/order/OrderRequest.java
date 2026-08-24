package com.talkanything.testevidence.sample.order;

public record OrderRequest(String orderNo, String sku, Integer quantity, String note) { }
