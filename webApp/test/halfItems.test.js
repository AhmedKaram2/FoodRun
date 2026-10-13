import test from 'node:test';
import assert from 'node:assert/strict';
import { halfItemCartLines, halfItemsOpen, portionQuantity, removeHalfItem } from '../src/foodrun/halfItems.js';
import { groupedOrderLines, restaurantOrderText } from '../src/foodrun/restaurantOrderText.js';
import { addMenuLine } from '../src/foodrun/cartEditing.js';

const line = { id: 'line', itemId: 'meal', quantity: 1, variantId: 'large', optionIds: ['sauce'], notes: 'No onions', description: '', unitPrice: null };
const receiptLine = { ...line, description: 'Falafel · Large · Sauce', amount: 501 };
const room = { phase: 'COLLECTING', restaurant: { currency: 'AED', menu: {
  items: [{ id: 'meal', name: 'Falafel', nameAr: 'طعمية', variants: [{ id: 'large', name: 'Large', nameAr: 'كبير', priceMinor: 401 }], optionGroupIds: ['extras'] }],
  optionGroups: [{ id: 'extras', options: [{ id: 'sauce', name: 'Sauce', nameAr: 'صوص', priceDeltaMinor: 100 }] }],
} }, halfItemOffers: [{ id: 'offer', memberId: 'one', lineId: 'line', line: receiptLine, acceptedById: 'two' }] };

test('member carts show half each and conserve the price including variants and extras', () => {
  const first = halfItemCartLines(room, { memberId: 'one', lines: [line] });
  const second = halfItemCartLines(room, { memberId: 'two', lines: [] });
  assert.equal(portionQuantity(first[0]), '½'); assert.equal(portionQuantity(second[0]), '½');
  assert.equal(first[0].amount, 251); assert.equal(second[0].amount, 250);
  assert.equal(second[0].notes, 'No onions');
});

test('unaccepted half retains the whole item and full food cost', () => {
  const pending = { ...room, halfItemOffers: room.halfItemOffers.map(offer => ({ ...offer, acceptedById: null })) };
  const first = halfItemCartLines(pending, { memberId: 'one', lines: [line] });
  assert.equal(portionQuantity(first[0]), 1); assert.equal(first[0].amount, 501);
  assert.deepEqual(halfItemCartLines(pending, { memberId: 'two', lines: [] }), []);
});

test('restaurant copy combines both financial shares into one physical item in Arabic and English', () => {
  const receipts = [{ lines: [{ ...receiptLine, halfShare: true, restaurantQuantity: 1, amount: 251 }] },
    { lines: [{ ...receiptLine, halfShare: true, restaurantQuantity: 0, amount: 250 }] }];
  assert.equal(groupedOrderLines(room, receipts)[0].quantity, 1);
  assert.equal(groupedOrderLines(room, receipts)[0].amount, 501);
  assert.match(restaurantOrderText(room, receipts, 'en'), /1 Falafel · Large · Sauce — No onions/);
  assert.match(restaurantOrderText(room, receipts, 'en'), /Total sandwiches: 1$/);
  assert.match(restaurantOrderText(room, receipts, 'ar'), /١ طعمية · كبير · صوص — No onions/);
  assert.match(restaurantOrderText(room, receipts, 'ar'), /إجمالي السندويشات: ١$/);
});

test('offers close at totals, order placement and the deadline', () => {
  assert.equal(halfItemsOpen(room), true);
  for (const phase of ['REVIEW', 'PLACED', 'FULFILLED', 'ARCHIVED', 'CANCELLED']) assert.equal(halfItemsOpen({ ...room, phase }), false);
  assert.equal(halfItemsOpen({ ...room, deadline: Date.now() - 1 }), false);
});

test('adding another of the same item keeps an existing half offer intact', () => {
  const result = addMenuLine([{ ...line, halfOffered: true }], { ...line, id: 'new' });
  assert.equal(result.length, 2); assert.equal(result[0].quantity, 1);
});

test('requester deletion cancels pending or accepted halves before removing at the new cart revision', async () => {
  for (const acceptedById of [null, 'two']) {
    const currentRoom = { ...room, id: 'room', halfItemOffers: room.halfItemOffers.map(offer => ({ ...offer, acceptedById })) };
    const cart = { memberId: 'one', revision: 4, lines: [line] };
    const extra = { ...line, id: 'new-live-item' };
    const commands = [];
    const send = async (kind, fields, roomId) => {
      commands.push({ kind, fields, roomId });
      return kind === 'CANCEL_HALF_ITEM' ? { ok: true, room: { carts: [{ ...cart, revision: 7, lines: [line, extra] }] } } : { ok: true };
    };
    assert.equal((await removeHalfItem(currentRoom, cart, line, send)).ok, true);
    assert.deepEqual(commands.map(command => command.kind), ['CANCEL_HALF_ITEM', 'CART']);
    assert.equal(commands[0].fields.text, 'offer');
    assert.equal(commands[1].fields.expectedRevision, 7);
    assert.deepEqual(commands[1].fields.cart.lines, [extra]);
    assert.equal(commands[1].roomId, 'room');
  }
});

test('recipient deletion releases only their half and failed cancellation never edits the cart', async () => {
  const commands = [];
  const send = async kind => { commands.push(kind); return { ok: true }; };
  const cart = { memberId: 'two', lines: [] };
  const received = halfItemCartLines(room, cart)[0];
  await removeHalfItem(room, cart, received, send);
  assert.deepEqual(commands, ['CANCEL_HALF_ITEM']);
  commands.length = 0;
  await removeHalfItem(room, { memberId: 'one', lines: [line] }, line, async kind => { commands.push(kind); return null; });
  assert.deepEqual(commands, ['CANCEL_HALF_ITEM']);
  commands.length = 0;
  await removeHalfItem({ ...room, phase: 'PLACED' }, cart, received, send);
  assert.deepEqual(commands, []);
});
