const test = require('node:test');
const assert = require('node:assert/strict');
const { applyFilters, createListQuery, toSearchParams } = require('../../main/resources/static/console-list-query.js');

test('switching a list filter resets only that list page', () => {
  const query = createListQuery({ page: 2, size: 20, filters: { triggerType: 'HTTP' } });
  assert.deepEqual(applyFilters(query, { triggerType: 'BROWSER' }), {
    page: 0, size: 20, filters: { triggerType: 'BROWSER' }
  });
});

test('serializes non-empty filters with page and size', () => {
  const query = createListQuery({ page: 1, size: 50, filters: { query: 'checkout', status: '', completed: false } });
  assert.equal(toSearchParams(query).toString(), 'page=1&size=50&query=checkout&completed=false');
});
