package com.talkanything.testevidence.platform.evidence;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class TraceTreeAssembler {
    List<TraceNode> assemble(List<EvidenceEvent> events) {
        Map<String, TraceNode> nodes = new HashMap<String, TraceNode>();
        for (EvidenceEvent event : events) nodes.put(key(event.getTraceId(), event.getSpanId()), TraceNode.from(event));
        List<TraceNode> roots = new ArrayList<TraceNode>();
        for (EvidenceEvent event : events) {
            TraceNode node = nodes.get(key(event.getTraceId(), event.getSpanId()));
            TraceNode parent = nodes.get(key(event.getTraceId(), event.getParentSpanId()));
            if (parent == null || parent == node || createsCycle(node, parent, nodes)) roots.add(node);
            else parent.children().add(node);
        }
        return roots;
    }

    private boolean createsCycle(TraceNode child, TraceNode candidateParent, Map<String, TraceNode> nodes) {
        Set<String> seen = new HashSet<String>();
        TraceNode current = candidateParent;
        while (current != null && seen.add(key(current.traceId(), current.spanId()))) {
            if (key(current.traceId(), current.spanId()).equals(key(child.traceId(), child.spanId()))) return true;
            current = nodes.get(key(current.traceId(), current.parentSpanId()));
        }
        return false;
    }

    private String key(String traceId, String spanId) {
        return traceId + ':' + spanId;
    }

    record TraceNode(String traceId, String spanId, String parentSpanId, String serviceName, String protocol,
                     String direction, String httpMethod, String target, Integer statusCode,
                     long durationMillis, String errorSummary, String jdbcOperation, String sqlTemplate,
                     JsonNode jdbcParameters, List<TraceNode> children) {
        static TraceNode from(EvidenceEvent event) {
            return new TraceNode(event.getTraceId(), event.getSpanId(), event.getParentSpanId(), event.getServiceName(),
                    event.getProtocol(), event.getDirection(), event.getHttpMethod(), event.getTarget(),
                    event.getStatusCode(), event.getDurationMillis(), event.getErrorSummary(), event.getJdbcOperation(),
                    event.getSqlTemplate(), event.getJdbcParameters(), new ArrayList<TraceNode>());
        }
    }
}
