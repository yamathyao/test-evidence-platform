const test = require('node:test');
const assert = require('node:assert/strict');

const { startCountdown } = require('../../main/resources/static/blackbox-countdown.js');

test('counts down once per second and ends at zero', () => {
  const values = [];
  const scheduled = [];

  startCountdown(3, (remaining) => values.push(remaining), (callback) => {
    scheduled.push(callback);
    return scheduled.length - 1;
  });
  scheduled.shift()();
  scheduled.shift()();
  scheduled.shift()();

  assert.deepEqual(values, [3, 2, 1, 0]);
});

test('cancelling prevents the next scheduled tick', () => {
  const values = [];
  const scheduled = [];
  const cancelled = [];

  const stop = startCountdown(2, (remaining) => values.push(remaining), (callback) => {
    scheduled.push(callback);
    return scheduled.length - 1;
  }, (id) => cancelled.push(id));
  stop();
  scheduled[0]();

  assert.deepEqual(values, [2]);
  assert.deepEqual(cancelled, [0]);
});
