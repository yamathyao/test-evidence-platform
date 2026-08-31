package com.talkanything.testevidence.sample.order.protocol;
import feign.Param;import feign.RequestLine;
interface ProtocolFeignApi { @RequestLine("GET /internal/protocols/echo?orderNo={orderNo}") ProtocolEchoResponse echo(@Param("orderNo") String orderNo); }