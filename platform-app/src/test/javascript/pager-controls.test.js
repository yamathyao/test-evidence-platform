const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const staticRoot = path.resolve(__dirname, '../../main/resources/static');

test('renders compact icon pagination controls with accessible labels', () => {
  const source = fs.readFileSync(path.join(staticRoot, 'console.js'), 'utf8');
  const styles = fs.readFileSync(path.join(staticRoot, 'console.css'), 'utf8');

  assert.match(source, /pager-icon pager-previous/);
  assert.match(source, /pager-icon pager-next/);
  assert.match(source, /上一页/);
  assert.match(source, /下一页/);
  assert.match(styles, /\.pager-icon\s*\{[^}]*width:30px/);
  assert.match(styles, /\.pager-previous::before\s*\{[^}]*content:'<'/);
  assert.match(styles, /\.pager-next::before\s*\{[^}]*content:'>'/);
});
