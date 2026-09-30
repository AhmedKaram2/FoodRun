import test from 'node:test';
import assert from 'node:assert/strict';
import { canRemindPayment, reminderKey } from '../src/foodrun/paymentReminders.js';

const room = { id: 'room', orderNumber: 2, payerId: 'payer', ownerId: 'owner', phase: 'FULFILLED', restaurantPaid: true,
  members: [{ id: 'member', approved: true, participating: true }], transfers: [] };
const receipt = { memberId: 'member', balance: 1250 };

test('only the chosen payer can remind an active member with an unpaid balance', () => {
  assert.equal(canRemindPayment(room, 'payer', receipt), true);
  for (const actor of ['owner', 'member', 'stranger']) assert.equal(canRemindPayment(room, actor, receipt), false);
  for (const balance of [0, -100]) assert.equal(canRemindPayment(room, 'payer', { ...receipt, balance }), false);
  assert.equal(canRemindPayment(room, 'payer', { ...receipt, memberId: 'payer' }), false);
  for (const patch of [{ removed: true }, { approved: false }, { participating: false }, { guest: true }]) {
    assert.equal(canRemindPayment({ ...room, members: [{ ...room.members[0], ...patch }] }, 'payer', receipt), false);
  }
});
test('pending payment claims, unpaid restaurant bills and closed rooms suppress reminders', () => {
  assert.equal(canRemindPayment({ ...room, transfers: [{ memberId: 'member', status: 'declared' }] }, 'payer', receipt), false);
  assert.equal(canRemindPayment({ ...room, restaurantPaid: false }, 'payer', receipt), false);
  for (const phase of ['LOBBY', 'COLLECTING', 'REVIEW', 'ARCHIVED', 'CANCELLED']) {
    assert.equal(canRemindPayment({ ...room, phase }, 'payer', receipt), false);
  }
  assert.equal(canRemindPayment({ ...room, phase: 'PLACED' }, 'payer', receipt), true);
  assert.notEqual(reminderKey(room, 'member'), reminderKey({ ...room, orderNumber: 3 }, 'member'));
});
