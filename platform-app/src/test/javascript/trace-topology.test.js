const test = require('node:test');
const assert = require('node:assert/strict');
const topology = require('../../main/resources/static/trace-topology.js');

function node(spanId, serviceName, children = []) {
  return {
    spanId,
    parentSpanId: null,
    serviceName,
    protocol: 'HTTP',
    direction: 'SERVER',
    httpMethod: 'POST',
    target: '/orders',
    statusCode: 200,
    durationMillis: 5,
    errorSummary: '',
    jdbcOperation: null,
    sqlTemplate: null,
    jdbcParameters: [],
    children
  };
}

test('keeps repeated service spans as distinct instances for A to B to A to B', () => {
  const roots = [node('a1', 'A', [node('b1', 'B', [node('a2', 'A', [node('b2', 'B')])])])];

  const graph = topology.buildSpanGraph(roots);

  assert.equal(graph.nodes.length, 4);
  assert.deepEqual(graph.edges.map((edge) => [edge.sourceId, edge.targetId]), [
    ['a1', 'b1'], ['b1', 'a2'], ['a2', 'b2']
  ]);
  assert.deepEqual(graph.nodes.map((item) => item.instanceLabel), ['A #1', 'B #1', 'A #2', 'B #2']);
});

test('assigns distinct rows to sibling branches', () => {
  const roots = [node('a', 'A', [node('b', 'B'), node('c', 'C')])];
  const layout = topology.layoutSpanGraph(topology.buildSpanGraph(roots));
  const positions = new Map(layout.nodes.map((item) => [item.id, item]));

  assert.notEqual(positions.get('b').row, positions.get('c').row);
  assert.ok(positions.get('a').row < positions.get('b').row);
});

test('aggregates reverse service calls without merging instances', () => {
  const roots = [node('a1', 'A', [node('b1', 'B', [node('a2', 'A')])])];
  const graph = topology.buildSpanGraph(roots);
  const overview = topology.buildServiceOverview(graph);

  assert.equal(graph.nodes.length, 3);
  assert.deepEqual(overview.edges.map((item) => [item.source, item.target, item.count]), [
    ['A', 'B', 1], ['B', 'A', 1]
  ]);
});

test('keeps duplicate JDBC spans and reports topology truncation after 500 spans', () => {
  const roots = Array.from({ length: 501 }, (_, index) => ({
    spanId: `jdbc-${index}`,
    serviceName: 'provider',
    protocol: 'JDBC',
    direction: 'CLIENT',
    jdbcOperation: 'UPDATE',
    sqlTemplate: 'INSERT INTO x VALUES (?)',
    jdbcParameters: [],
    children: []
  }));
  const graph = topology.buildSpanGraph(roots);
  const renderable = topology.limitRenderableGraph(graph, 500);

  assert.equal(graph.nodes.length, 501);
  assert.equal(renderable.nodes.length, 500);
  assert.equal(renderable.truncated, true);
});

test('marks status and error nodes as failed without exposing raw JDBC values', () => {
  const roots = [{
    spanId: 'jdbc', serviceName: 'fulfillment', protocol: 'JDBC', direction: 'CLIENT',
    statusCode: null, durationMillis: 8, errorSummary: 'SQLException', jdbcOperation: 'UPDATE',
    sqlTemplate: 'UPDATE orders SET status = ? WHERE order_no = ?',
    jdbcParameters: [{ index: 1, setter: 'setString', value: 'PAID', valueLength: 8, sha256: 'abc123' }],
    children: []
  }];
  const graph = topology.buildSpanGraph(roots);

  assert.equal(topology.isFailed(graph.nodes[0]), true);
  assert.equal(graph.nodes[0].jdbcParameters[0].value, undefined);
});

test('reports self call and failed counts in service overview', () => {
  const roots = [node('a1', 'A', [Object.assign(node('a2', 'A'), { statusCode: 502 })])];
  const overview = topology.buildServiceOverview(topology.buildSpanGraph(roots));

  assert.deepEqual(overview.edges, [{
    source: 'A', target: 'A', protocol: 'HTTP', direction: 'SERVER',
    count: 1, failedCount: 1, spanIds: ['a2']
  }]);
});

test('selects exactly one requested root trace and clamps the page index', () => {
  const roots = [node('one', 'A'), node('two', 'B'), node('three', 'C')];

  const selection = topology.selectRootTracePage(roots, 9);

  assert.equal(selection.page, 2);
  assert.equal(selection.pageCount, 3);
  assert.deepEqual(selection.roots.map((item) => item.spanId), ['three']);
});

test('returns an empty page for an empty root-trace collection', () => {
  assert.deepEqual(topology.selectRootTracePage([], 3), {
    page: 0,
    pageCount: 0,
    roots: []
  });
});
