import test from 'node:test';
import assert from 'node:assert/strict';
import { wheelSlice, spinRotation, runningNameIndex } from '../src/foodrun/wheel.js';

test('wheel slice areas match 20:80 selection weights and stop at the chosen name', () => {
  const memberIds = ['previous', 'second', 'third'];
  const weights = [20, 80, 80];
  assert.equal(wheelSlice(0, 3, weights).sweep, 40);
  assert.equal(wheelSlice(1, 3, weights).sweep, 160);
  assert.equal(wheelSlice(2, 3, weights).sweep, 160);
  memberIds.forEach((winnerId, index) => {
    const spin = { memberIds, weights, winnerId, startAt: 1000, duration: 6500, turns: 7 };
    assert.equal((wheelSlice(index, 3, weights).center + spinRotation(spin, 7500)) % 360, 0);
  });
});

test('older spins without weights keep equal slices and the correct winner', () => {
  assert.deepEqual(wheelSlice(1, 4), { sweep: 90, center: 90 });
  assert.equal(spinRotation({ memberIds: ['a', 'b'], winnerId: 'b', startAt: 0, duration: 1000, turns: 7 }, 1000) % 360, 180);
});


test('running names stay bounded and finish on the same server winner', () => {
  for (const winnerId of ['a', 'b', 'c']) {
    const spin = { memberIds: ['a', 'b', 'c'], winnerId, startAt: 1000, duration: 6000, turns: 7 };
    assert.equal(runningNameIndex(spin, 0), 0);
    for (let time = 1000; time < 7000; time += 53) assert([0, 1, 2].includes(runningNameIndex(spin, time)));
    assert.equal(runningNameIndex(spin, 7000), spin.memberIds.indexOf(winnerId));
    assert.equal(runningNameIndex(spin, 99999), spin.memberIds.indexOf(winnerId));
  }
});
