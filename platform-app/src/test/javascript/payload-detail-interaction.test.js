const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('loads protocol payload only after selecting request or response detail', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/console.js'), 'utf8');
  const nodeDetail = source.slice(source.indexOf('function renderNodeDetail'), source.indexOf('function renderServiceOverview'));

  assert.match(nodeDetail, /payloadButton\('request', node\.protocol === 'DUBBO' \? '方法参数' : '入参'/);
  assert.match(nodeDetail, /payloadButton\('response', node\.protocol === 'DUBBO' \? '返回值' : '出参'/);
  assert.match(nodeDetail, /`查看\$\{label\}`/);
  assert.match(nodeDetail, /function loadPayloadOnDemand/);
  assert.match(nodeDetail, /trace\/payloads\/\$\{node\.id\}/);
  assert.doesNotMatch(nodeDetail, /loadPayloadDetail\(panel, node\);/);
});

test('refreshes the node detail when payload evidence is unavailable', () => {
  const source = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/console.js'), 'utf8');

  assert.match(source, /if \(!loaded\) \{ renderNodeDetail\(node, layout\); return; \}/);
});
