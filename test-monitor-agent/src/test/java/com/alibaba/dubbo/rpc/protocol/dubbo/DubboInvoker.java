package com.alibaba.dubbo.rpc.protocol.dubbo;

public class DubboInvoker {
    private final Class<?> serviceType;

    public DubboInvoker(Class<?> serviceType) {
        this.serviceType = serviceType;
    }

    public Class<?> getInterface() { return serviceType; }
    public Object doInvoke(Object invocation) { return invocation; }
}
