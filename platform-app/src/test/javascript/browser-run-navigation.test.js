const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('keeps Browser run visible and exposes manual stop while collecting', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/console.js'), 'utf8');
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/index.html'), 'utf8');

  assert.match(html, /结束采集/);
  assert.match(source, /\/api\/runs\/\$\{runId\}\/complete/);
  assert.match(source, /DRAINING/);
});
