const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('makes active Browser capture status and stop control visually prominent', () => {
  const page = fs.readFileSync(path.join(staticRoot, 'index.html'), 'utf8');
  const styles = fs.readFileSync(path.join(staticRoot, 'console.css'), 'utf8');

  assert.match(page, /class="blackbox-capture-status"/);
  assert.match(page, /class="capture-live-dot"/);
  assert.match(page, /class="blackbox-stop"/);
  assert.match(styles, /\.blackbox-capture\s*\{[^}]*border:2px solid var\(--accent\)/);
  assert.match(styles, /\.blackbox-stop\s*\{[^}]*background:var\(--danger\)/);
  assert.match(styles, /@keyframes capture-pulse/);
});
