const test = require('node:test');
const assert = require('node:assert/strict');

const { loadOptions } = require('../../main/resources/static/console-options-loader.js');

test('retries both option endpoints once after a transient initial failure', async () => {
  let profileCalls = 0;
  let caseCalls = 0;
  let waits = 0;

  const result = await loadOptions(
    async () => {
      profileCalls += 1;
      if (profileCalls === 1) throw new Error('temporarily unavailable');
      return [{ id: 'profile-1' }];
    },
    async () => {
      caseCalls += 1;
      return [{ id: 'case-1' }];
    },
    async () => { waits += 1; }
  );

  assert.deepEqual(result, { profiles: [{ id: 'profile-1' }], cases: [{ id: 'case-1' }] });
  assert.equal(profileCalls, 2);
  assert.equal(caseCalls, 2);
  assert.equal(waits, 1);
});
