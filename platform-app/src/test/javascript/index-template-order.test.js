const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

test('places the HTTP payload capture switch in the shared case form', () => {
  const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/index.html'), 'utf8');
  const caseForm = html.slice(html.indexOf('<form id="case-form"'), html.indexOf('</form>', html.indexOf('<form id="case-form"')));

  assert.match(caseForm, /id="http-payload-capture-enabled"/);
  assert.doesNotMatch(html, /http-payload-capture-template/);
});
