const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('renders service relationships in dedicated cards without truncating service names', () => {
  const source = fs.readFileSync(path.join(staticRoot, 'console.js'), 'utf8');
  const styles = fs.readFileSync(path.join(staticRoot, 'console.css'), 'utf8');

  assert.match(source, /function appendOverviewRelationCard\(/);
  assert.match(source, /edge\.protocol.*edge\.direction/);
  assert.doesNotMatch(source, /svgText\(group, truncate\(name\), 12, 29, 'topology-node-title'\)/);
  assert.match(styles, /\.overview-relation-card\s*\{/);
  assert.match(styles, /\.overview-relation-card-text\s*\{/);
});
