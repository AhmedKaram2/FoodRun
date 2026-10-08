import test from 'node:test';
import assert from 'node:assert/strict';
import { newestAccountReply } from '../src/foodrun/accountState.js';

test('an older HOME socket message cannot undo an acknowledged wallet update', () => {
  const current = { serverTime: 2000, home: { profile: { userId: 'me' }, wallet: { payments: [{ status: 'SETTLED' }] } } };
  const delayed = { serverTime: 1000, home: { profile: { userId: 'me' }, wallet: { payments: [{ status: 'OWING' }] } } };
  assert.equal(newestAccountReply(current, delayed), current);
  const updated = { ...current, serverTime: 3000 };
  assert.equal(newestAccountReply(current, updated), updated);
});
test('timestamps from another account or an older server cannot prevent initial bootstrap', () => {
  const previous = { serverTime: 2000, home: { profile: { userId: 'other' } } };
  const incoming = { serverTime: 1000, home: { profile: { userId: 'me' } } };
  assert.equal(newestAccountReply(previous, incoming), incoming);
  assert.equal(newestAccountReply(null, incoming), incoming);
  assert.equal(newestAccountReply(incoming, { ...incoming, serverTime: 0 }).serverTime, 0);
});
