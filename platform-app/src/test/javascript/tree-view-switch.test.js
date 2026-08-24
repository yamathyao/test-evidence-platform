const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('renders the tree when the tree view is selected', () => {
  const source = fs.readFileSync(path.join(staticRoot, 'console.js'), 'utf8');
  const page = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  const styles = fs.readFileSync(path.join(staticRoot, 'console.css'), 'utf8');

  assert.match(source, /if \(state\.traceView === 'tree'\) return renderTraceTree\(state\.currentTrace\.roots \|\| \[\]\)/);
  assert.match(source, /byId\('trace-layout'\)\.hidden = view === 'tree'/);
  assert.match(page, /id="trace-layout" class="trace-layout"/);
  assert.match(styles, /\.trace-layout\[hidden\], \.trace-tree\[hidden\]\s*\{\s*display:none;\s*\}/);
});
