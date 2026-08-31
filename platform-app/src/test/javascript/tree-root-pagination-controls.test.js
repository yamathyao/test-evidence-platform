const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('provides an independent accessible root-trace pager for the tree view', () => {
  const markup = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  const source = fs.readFileSync(path.join(staticRoot, 'console.js'), 'utf8');

  assert.match(markup, /id="tree-root-pager"[^>]*aria-label="树形明细调用链分页"/);
  assert.match(markup, /id="tree-root-previous"[^>]*aria-label="上一条调用链"/);
  assert.match(markup, /id="tree-root-select"/);
  assert.match(source, /treeRootTracePage: 0/);
  assert.match(source, /function renderTreeRootPager\(roots\)/);
  assert.match(source, /function setTreeRootPage\(page\)/);
  assert.match(source, /if \(state\.traceView === 'tree'\) return renderTraceTree\(state\.currentTrace\.roots \|\| \[\]\)/);
});
