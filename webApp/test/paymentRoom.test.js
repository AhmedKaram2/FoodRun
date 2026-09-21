import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentRoomPayload, splitEqually } from '../src/foodrun/paymentRoom.js';
const draft = { name: 'Lunch', restaurant: 'Kitchen', details: 'Sandwiches', photo: '', total: '30.01', ownerId: 'a',
  account: { holder: 'Payer' }, people: [{ userId: 'a' }, { userId: 'b' }], shares: { a: { amount: '10.00' }, b: { amount: '20.01', received: '5.00', description: 'Meal' } } };
test('equal split allocates every minor unit deterministically regardless of display order', () => {
  assert.deepEqual(splitEqually(100, ['c','a','b']), { a: 34, b: 33, c: 33 });
  assert.throws(() => splitEqually(100, ['a','a']));
});
test('payment room carries final shares, prior receipts and the existing receiving account', () => {
  const result = paymentRoomPayload(draft);
  assert.equal(result.amount, 3001);
  assert.deepEqual(result.paymentRoom.shares[1], { userId: 'b', amount: 2001, received: 500, description: 'Meal' });
  assert.equal(result.account, draft.account);
});
test('receipt mismatch, duplicate people, overpayment and missing account fail before sending', () => {
  assert.throws(() => paymentRoomPayload({ ...draft, total: '30.00' }));
  assert.throws(() => paymentRoomPayload({ ...draft, people: [draft.people[0], draft.people[0]] }));
  assert.throws(() => paymentRoomPayload({ ...draft, shares: { ...draft.shares, b: { amount: '20.01', received: '20.02' } } }));
  assert.throws(() => paymentRoomPayload({ ...draft, account: null }));
});
test('a payer with no food has a zero share and no self-transfer', () => {
  const result = paymentRoomPayload({ ...draft, shares: { a: { amount: '0' }, b: { amount: '30.01' } } });
  assert.equal(result.paymentRoom.shares[0].amount, 0);
  assert.throws(() => paymentRoomPayload({ ...draft, shares: { ...draft.shares, a: { amount: '10', received: '1' } } }));
});
