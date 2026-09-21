import test from 'node:test';
import assert from 'node:assert/strict';
import { lastRestaurantOrder, matchesRestaurant, nextRestaurantHistoryRoom, prepareReorder, mergeReorder } from '../src/foodrun/reorder.js';

const restaurant = { id: 'restaurant', name: 'Kitchen', currency: 'AED', openOrdering: true, menu: {
  items: [{ id: 'meal', name: 'Meal', basePriceMinor: 1000, available: true, variants: [{ id: 'large', priceMinor: 1500 }], optionGroupIds: ['extras'] }],
  optionGroups: [{ id: 'extras', minSelections: 1, maxSelections: 2, options: [{ id: 'cheese', priceDeltaMinor: 200 }, { id: 'sauce', priceDeltaMinor: 50 }] }],
} };
const saved = { itemId: 'meal', variantId: 'large', optionIds: ['cheese'], quantity: 2, notes: 'No onions', description: 'Meal · Large · Cheese', amount: 900, unitPrice: 450 };
const past = (number, memberId = 'me', restaurantId = restaurant.id) => ({ number, completedAt: number * 1000, restaurantId, restaurantName: 'Kitchen', receipts: [{ memberId, lines: [saved] }] });
const data = history => ({ sessions: { room: { roomId: 'room', memberId: 'me' } }, rooms: { room: { history } } });

test('last order uses newest personal order across rooms and keeps same-name branches separate', () => {
  const input = data([past(1), past(3), past(9, 'other'), past(10, 'me', 'other-branch')]);
  input.sessions.other = { roomId: 'other', memberId: 'my-other-membership' };
  input.rooms.other = { history: [past(5, 'my-other-membership')] };
  assert.equal(lastRestaurantOrder(input, restaurant).order.number, 5);
  assert.equal(lastRestaurantOrder(data([past(9, 'other')]), restaurant), null);
  assert.equal(lastRestaurantOrder(data([]), restaurant), null);
  assert.equal(matchesRestaurant({ restaurantName: ' kitchen ' }, restaurant), true);
  assert.equal(matchesRestaurant({ restaurantId: 'another', restaurantName: 'Kitchen' }, restaurant), false);
  input.rooms.other.memberId = 'stale-user';
  assert.equal(lastRestaurantOrder(input, restaurant).order.number, 3);
});

test('placed orders count before archival but drafts and cancellations do not', () => {
  const input = data([past(1)]);
  input.rooms.room.room = { restaurant, orderNumber: 2, updatedAt: 2000, phase: 'PLACED' };
  input.rooms.room.receipts = [{ memberId: 'me', lines: [saved] }];
  assert.equal(lastRestaurantOrder(input, restaurant).order.number, 2);
  for (const phase of ['LOBBY', 'REVIEW', 'CANCELLED']) {
    input.rooms.room.room.phase = phase;
    assert.equal(lastRestaurantOrder(input, restaurant).order.number, 1);
  }
});

test('history lookup continues past pages with other restaurants or other members', () => {
  const input = data([past(5, 'me', 'other'), past(4, 'other')]);
  input.rooms.room.historyNextOffset = 2;
  assert.equal(nextRestaurantHistoryRoom(input, restaurant), 'room');
  input.rooms.room.history.push(past(3));
  assert.equal(nextRestaurantHistoryRoom(input, restaurant), '');
  input.rooms.room.history = [];
  input.rooms.room.historyNextOffset = -1;
  assert.equal(nextRestaurantHistoryRoom(input, restaurant), '');
});

test('reorder retains quantity and exact customizations and prices only from the current menu', () => {
  const [entry] = prepareReorder(restaurant, [saved], () => 'new-id');
  assert.equal(entry.issue, '');
  assert.equal(entry.total, 3400);
  assert.deepEqual(entry.line, { id: 'new-id', itemId: 'meal', quantity: 2, variantId: 'large', optionIds: ['cheese'], notes: 'No onions', description: '', unitPrice: null });
  assert.equal(saved.amount, 900);
  assert.notEqual(entry.line.optionIds, saved.optionIds);
});

test('missing items, unavailable items, removed sizes and extras are individually flagged', () => {
  const unavailable = structuredClone(restaurant);
  unavailable.menu.items.push({ ...unavailable.menu.items[0], id: 'unavailable', available: false });
  const entries = prepareReorder(unavailable, [saved, { ...saved, itemId: 'deleted' }, { ...saved, itemId: 'unavailable' }, { ...saved, variantId: 'removed-size' }, { ...saved, optionIds: ['removed-extra'] }]);
  assert.equal(entries.filter(entry => !entry.issue).length, 1);
  assert(entries.slice(1).every(entry => entry.issue && entry.total === null));
  assert.deepEqual(mergeReorder([], entries).map(line => line.itemId), ['meal']);
});

test('changed required extras and selection limits never silently alter a configuration', () => {
  for (const optionIds of [[], ['cheese', 'cheese']]) assert(prepareReorder(restaurant, [{ ...saved, optionIds }])[0].issue);
  const changed = structuredClone(restaurant);
  changed.menu.optionGroups[0].maxSelections = 1;
  assert(prepareReorder(changed, [{ ...saved, optionIds: ['cheese', 'sauce'] }])[0].issue);
  for (const quantity of [0, 100, 1.5]) assert(prepareReorder(restaurant, [{ ...saved, quantity }])[0].issue);
});

test('custom order prices are cleared for fresh pricing and blocked when open ordering is disabled', () => {
  const custom = { description: 'Special meal', quantity: 3, notes: 'Mild', unitPrice: 500 };
  const [entry] = prepareReorder(restaurant, [custom]);
  assert.equal(entry.line.description, custom.description);
  assert.equal(entry.line.notes, custom.notes);
  assert.equal(entry.line.quantity, 3);
  assert.equal(entry.line.unitPrice, null);
  assert.equal(entry.total, null);
  assert(prepareReorder({ ...restaurant, openOrdering: false }, [custom])[0].issue);
});

test('merge preserves existing cart and aborts atomically on quantity or cart limits', () => {
  const entries = prepareReorder(restaurant, [saved]);
  const existing = [{ ...entries[0].line, id: 'existing', quantity: 1 }];
  assert.equal(mergeReorder(existing, entries)[0].quantity, 3);
  assert.equal(existing[0].quantity, 1);
  const full = [{ ...existing[0], quantity: 99 }];
  assert.throws(() => mergeReorder(full, entries), /99/);
  assert.equal(full[0].quantity, 99);
  const hundred = Array.from({ length: 100 }, (_, i) => ({ ...existing[0], id: String(i), notes: String(i) }));
  assert.throws(() => mergeReorder(hundred, entries), /100/);
  assert.deepEqual(mergeReorder(existing, prepareReorder(restaurant, [{ ...saved, itemId: 'gone' }])), existing);
});

test('order price overrides include extras once and never carry over into a reorder', async () => {
  const { menuLineTotal } = await import('../src/foodrun/cartEditing.js');
  const priced = { ...saved, description: '', unitPrice: 800 };
  assert.equal(menuLineTotal(restaurant, priced), 1600);
  assert.equal(menuLineTotal(restaurant, { ...priced, unitPrice: 0 }), 0);
  assert.equal(prepareReorder(restaurant, [priced])[0].total, 3400);
});
