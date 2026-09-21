import test from 'node:test';
import assert from 'node:assert/strict';
import { roomFeeUpdate } from '../src/foodrun/roomFees.js';
const room = { deliveryMode: true, restaurant: { id: 'r', currency: 'AED', menu: { items: [] }, pricing: { taxTreatment: 'unspecified', minimumOrderMinor: 0 } } };
const draft = { service: '10', discount: '25', taxTreatment: 'included', taxRate: '', proportionalDelivery: false };
test('fees and explicit tax treatment use a single request without altering the menu', () => {
  const update = roomFeeUpdate(room, draft);
  assert.equal(update.fees.service, 1000); assert.equal(update.fees.discount, 2500);
  assert.equal(update.restaurant.pricing.taxTreatment, 'included');
  assert.equal(update.restaurant.pricing.taxRateBasisPoints, null);
  assert.deepEqual(update.restaurant.menu, room.restaurant.menu);
  assert.equal(room.restaurant.pricing.taxTreatment, 'unspecified');
});
test('added tax supports zero and exact percent precision but rejects missing or invalid choices', () => {
  assert.equal(roomFeeUpdate(room, { ...draft, taxTreatment: 'added', taxRate: '5.25' }).restaurant.pricing.taxRateBasisPoints, 525);
  assert.equal(roomFeeUpdate(room, { ...draft, taxTreatment: 'added', taxRate: '0' }).restaurant.pricing.taxRateBasisPoints, 0);
  for (const taxRate of ['', '-1', '101']) assert.throws(() => roomFeeUpdate(room, { ...draft, taxTreatment: 'added', taxRate }));
  assert.throws(() => roomFeeUpdate(room, { ...draft, taxTreatment: 'unspecified' }));
});
