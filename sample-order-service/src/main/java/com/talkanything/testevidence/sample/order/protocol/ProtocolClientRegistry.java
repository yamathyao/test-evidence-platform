package com.talkanything.testevidence.sample.order.protocol;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProtocolClientRegistry {
    private final Map<String, ProtocolClient> clients;

    public ProtocolClientRegistry(List<ProtocolClient> clients) {
        Map<String, ProtocolClient> configured = new LinkedHashMap<>();
        for (ProtocolClient client : clients) {
            String name = client.name();
            if (name == null || name.isBlank() || configured.putIfAbsent(name, client) != null) {
                throw new IllegalArgumentException("Duplicate or blank protocol client name");
            }
        }
        this.clients = Map.copyOf(configured);
    }

    public ProtocolClient require(String name) {
        ProtocolClient client = clients.get(name);
        if (client == null) {
            throw new IllegalArgumentException("Unsupported protocol client: " + name);
        }
        return client;
    }
}