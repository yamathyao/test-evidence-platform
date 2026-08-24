package com.alibaba.dubbo.rpc.filter;

public class ContextFilter {
    public Object invoke(Object invoker, Object invocation) { return invocation; }
}
