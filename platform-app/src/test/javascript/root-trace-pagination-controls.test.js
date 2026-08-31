const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('provides an accessible root-trace pager outside the trace tablist', () => {
  const markup = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  const styles = fs.readFileSync(path.join(staticRoot, 'console.css'), 'utf8');

  assert.match(markup, /<div[^>]*id="topology-root-pager"[^>]*role="group"[^>]*aria-label="拓扑调用链分页"/);
  assert.match(markup, /id="topology-root-previous"[^>]*aria-label="上一条调用链"/);
  assert.match(markup, /id="topology-root-next"[^>]*aria-label="下一条调用链"/);
  assert.match(markup, /id="topology-root-select"/);
  assert.match(styles, /\.topology-root-pager\s*\{/);
});

test('keeps overview and tree global while selecting one root for the topology', () => {
  const source = fs.readFileSync(path.join(staticRoot, 'console.js'), 'utf8');

  assert.match(source, /rootTracePage: 0/);
  assert.match(source, /topology\.selectRootTracePage\(availableRoots, state\.rootTracePage\)/);
  assert.match(source, /renderServiceOverview\(state\.currentTrace\.roots \|\| \[\]\)/);
  assert.match(source, /renderTraceTree\(state\.currentTrace\.roots \|\| \[\]\)/);
  assert.match(source, /function setTopologyRootPage\(page\) \{[\s\S]*state\.selectedSpanId = null;/);
  assert.match(source, /const selected = layout\.nodes\.find\(\(item\) => item\.id === state\.selectedSpanId\) \|\| null;/);
});
