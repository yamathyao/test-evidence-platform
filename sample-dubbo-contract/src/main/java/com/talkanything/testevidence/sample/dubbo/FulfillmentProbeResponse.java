package com.talkanything.testevidence.sample.dubbo;

import java.io.Serializable;

public final class FulfillmentProbeResponse implements Serializable {
    private static final long serialVersionUID = 1L;

    private String service;
    private String orderNo;
    private int statementValue;

    public FulfillmentProbeResponse() {
    }

    public FulfillmentProbeResponse(String service, String orderNo, int statementValue) {
        this.service = service;
        this.orderNo = orderNo;
        this.statementValue = statementValue;
    }

    public String service() {
        return service;
    }

    public String orderNo() {
        return orderNo;
    }

    public int statementValue() {
        return statementValue;
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public int getStatementValue() {
        return statementValue;
    }

    public void setStatementValue(int statementValue) {
        this.statementValue = statementValue;
    }
}
