const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('loads HTTP payload only after selecting request or response detail', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/console.js'), 'utf8');
  const nodeDetail = source.slice(source.indexOf('function renderNodeDetail'), source.indexOf('function renderServiceOverview'));

  assert.match(nodeDetail, /payloadButton\('request', '入参'/);
  assert.match(nodeDetail, /payloadButton\('response', '出参'/);
  assert.match(nodeDetail, /`查看\$\{label\}`/);
  assert.match(nodeDetail, /function loadPayloadOnDemand/);
  assert.doesNotMatch(nodeDetail, /loadPayloadDetail\(panel, node\);/);
});

test('refreshes the node detail when payload evidence is unavailable', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/console.js'), 'utf8');

  assert.match(source, /if \(!loaded\) \{ renderNodeDetail\(node, layout\); return; \}/);
});
