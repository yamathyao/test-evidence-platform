(function attachTopology(root, factory) {
  const api = factory();
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  root.TraceTopology = api;
})(typeof window !== 'undefined' ? window : globalThis, function createTopology() {
  function buildSpanGraph(roots) {
    const nodes = [];
    const edges = [];
    const instances = new Map();
    const visited = new Set();

    function visit(raw, parentId, depth) {
      if (!raw || visited.has(raw.spanId)) return;
      visited.add(raw.spanId);
      const serviceName = raw.serviceName || 'Unknown service';
      const number = (instances.get(serviceName) || 0) + 1;
      instances.set(serviceName, number);
      const item = {
        ...raw,
        id: raw.spanId,
        parentId,
        depth,
        serviceName,
        instanceLabel: `${serviceName} #${number}`,
        children: undefined,
        jdbcParameters: safeJdbcParameters(raw.jdbcParameters)
      };
      nodes.push(item);
      if (parentId) edges.push({ sourceId: parentId, targetId: item.id });
      (raw.children || []).forEach((child) => visit(child, item.id, depth + 1));
    }

    (roots || []).forEach((rootNode) => visit(rootNode, null, 0));
    return { nodes, edges };
  }

  function safeJdbcParameters(parameters) {
    return (parameters || []).map(({ value, ...summary }) => summary);
  }

  function laneName(item) {
    return item.protocol === 'JDBC' ? 'JDBC' : item.serviceName;
  }

  function isFailed(item) {
    return Boolean(item.errorSummary)
      || (Number.isInteger(item.statusCode) && item.statusCode >= 400);
  }

  function layoutSpanGraph(graph) {
    const lanes = [];
    graph.nodes.forEach((item) => {
      const lane = laneName(item);
      if (lane !== 'JDBC' && !lanes.includes(lane)) lanes.push(lane);
    });
    if (graph.nodes.some((item) => laneName(item) === 'JDBC')) lanes.push('JDBC');

    const byId = new Map(graph.nodes.map((item) => [item.id, item]));
    const children = new Map(graph.nodes.map((item) => [item.id, []]));
    graph.edges.forEach((edge) => children.get(edge.sourceId).push(edge.targetId));
    let row = 0;

    function place(id) {
      const item = byId.get(id);
      item.row = row++;
      item.lane = laneName(item);
      item.laneIndex = lanes.indexOf(item.lane);
      children.get(id).forEach(place);
    }

    graph.nodes.filter((item) => !item.parentId).forEach((item) => place(item.id));
    return {
      ...graph,
      lanes,
      width: Math.max(1, lanes.length) * 260,
      height: Math.max(1, row) * 108
    };
  }

  function buildServiceOverview(graph) {
    const byId = new Map(graph.nodes.map((item) => [item.id, item]));
    const entries = new Map();
    graph.edges.forEach((edge) => {
      const targetItem = byId.get(edge.targetId);
      const source = laneName(byId.get(edge.sourceId));
      const target = laneName(targetItem);
      const key = [source, target, targetItem.protocol, targetItem.direction].join('|');
      const value = entries.get(key) || {
        source,
        target,
        protocol: targetItem.protocol,
        direction: targetItem.direction,
        count: 0,
        failedCount: 0,
        spanIds: []
      };
      value.count += 1;
      if (isFailed(targetItem)) value.failedCount += 1;
      value.spanIds.push(targetItem.id);
      entries.set(key, value);
    });
    return {
      nodes: [...new Set(graph.nodes.map(laneName))],
      edges: [...entries.values()]
    };
  }

  function limitRenderableGraph(graph, maxNodes) {
    const nodes = graph.nodes.slice(0, maxNodes);
    const ids = new Set(nodes.map((item) => item.id));
    return {
      ...graph,
      nodes,
      edges: graph.edges.filter((edge) => ids.has(edge.sourceId) && ids.has(edge.targetId)),
      truncated: graph.nodes.length > maxNodes
    };
  }

  return { buildSpanGraph, buildServiceOverview, isFailed, laneName, layoutSpanGraph, limitRenderableGraph };
});
