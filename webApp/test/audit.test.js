import test from 'node:test';
import assert from 'node:assert/strict';
import { selectionKey, uniquePreviousOrders, uniqueRoomPreviousOrders, userDashboard } from '../src/foodrun/orderHistory.js';
import { mergeRoomReply } from '../src/foodrun/roomState.js';
import { request, normalizeReply } from '../src/foodrun/client.js';
const line = { itemId: 'meal', quantity: 2, optionIds: ['a', 'b'], notes: 'بدون بصل' };
const receipt = { memberId: 'me', lines: [line], balance: 1200, currency: 'AED', name: 'Me' };
const past = (number, restaurantId = 'r', lines = [line]) => ({ number, restaurantId, restaurantName: restaurantId, completedAt: number, receipts: [{ ...receipt, lines }] });
const reply = (revision, history, offset = -1, orderNumber = 4) => ({ memberId: 'me', room: { id: 'room', revision, orderNumber }, history, historyNextOffset: offset });
test('repeats ignore display order and price but preserve quantity, extras and punctuation', () => {
  assert.equal(selectionKey('r', [line]), selectionKey('r', [{ ...line, optionIds: ['b', 'a'], amount: 999 }]));
  assert.notEqual(selectionKey('r', [line]), selectionKey('r', [{ ...line, quantity: 1 }]));
  assert.notEqual(selectionKey('r', [{ ...line, itemId: 'a~b', variantId: 'c' }]), selectionKey('r', [{ ...line, itemId: 'a', variantId: 'b~c' }]));
});
test('room history retains other restaurants without assigning the current restaurant id', () => {
  const choices = uniqueRoomPreviousOrders([past(1), past(2), past(3, 'other')], 'me', { id: 'r', name: 'r' });
  assert.equal(choices.length, 2);
  assert.equal(choices.find(value => value.restaurantId === 'r').repeatCount, 2);
  assert.equal(choices.find(value => value.order.number === 3).restaurantId, 'other');
});
test('reorder options are private to the member and restaurant', () => {
  const data = { sessions: { room: { roomId: 'room', memberId: 'me' } }, rooms: { room: { history: [past(1), past(2), past(3, 'other'), past(4, 'r', [{ ...line, quantity: 1 }])] } } };
  assert.equal(uniquePreviousOrders(data, { id: 'r', name: 'r' }).length, 2);
  assert.equal(uniquePreviousOrders({ ...data, sessions: { room: { roomId: 'room', memberId: 'guest' } } }).length, 0);
});
test('older pages survive live refresh and stale room replies cannot replace current state', () => {
  const first = reply(10, [past(3)], 5);
  const downloaded = mergeRoomReply(first, reply(10, [past(2), past(1)]), true);
  assert.equal(downloaded.history.length, 3);
  const live = mergeRoomReply(downloaded, reply(11, [past(3)], 5));
  assert.equal(live.history.length, 3);
  assert.equal(live.historyNextOffset, -1);
  assert.equal(mergeRoomReply(live, first), live);
  assert.equal(mergeRoomReply(live, reply(12, [past(4)], 5, 5)).historyNextOffset, 5);
});
test('history never merges between identities', () => {
  const next = { ...reply(11, []), memberId: 'other' };
  assert.deepEqual(mergeRoomReply(reply(10, [past(1)]), next), next);
});
test('wallet excludes draft, cancelled and completed orders and handles refunds', () => {
  const data = phase => ({ sessions: { room: { roomId: 'room', memberId: 'me' } }, rooms: { room: { room: { id: 'room', phase, payerId: 'payer', members: [{ id: 'payer', name: 'Payer' }] }, receipts: [receipt] } } });
  for (const phase of ['LOBBY', 'COLLECTING', 'REVIEW', 'CANCELLED', 'ARCHIVED']) assert.equal(userDashboard(data(phase)).toPay, 0);
  assert.equal(userDashboard(data('PLACED')).toPay, 1200);
  const refund = data('FULFILLED'); refund.rooms.room.receipts = [{ ...receipt, balance: -300 }];
  assert.equal(userDashboard(refund).toReceive, 300);
});
test('HTTP validation messages are retained and network/server failures remain retryable', async () => {
  const original = globalThis.fetch;
  try {
    globalThis.fetch = async () => new Response(JSON.stringify({ error: 'Choose a valid menu item' }), { status: 400 });
    await assert.rejects(request('https://example.invalid', {}), error => error.definitive && error.message === 'Choose a valid menu item');
    globalThis.fetch = async () => new Response('Unavailable', { status: 503 });
    await assert.rejects(request('https://example.invalid', {}), error => !error.definitive);
  } finally { globalThis.fetch = original; }
});

test('API receipt amounts render without computed Kotlin properties on the wire', () => {
  const value = normalizeReply({ receipts: [{ total: 3248, balance: -250, currency: 'AED' }], history: [{ receipts: [{ total: 1499, balance: 0, currency: 'AED' }] }] });
  assert.equal(value.receipts[0].totalText, 'AED 32.48');
  assert.equal(value.receipts[0].balanceText, 'AED -2.50');
  assert.equal(value.history[0].receipts[0].totalText, 'AED 14.99');
});

test('offline archive contains only own receipts and no credentials or other people', async () => {
  const { receiptArchive } = await import('../src/foodrun/offlineReceipts.js');
  const r = { memberId: 'me', token: 'secret', room: { id: 'room', name: 'Lunch', orderNumber: 1, phase: 'PLACED', restaurant: { name: 'Kitchen' }, updatedAt: 10, account: { identifier: 'private bank' } }, receipts: [{ ...receipt, total: 1200, paid: 0 }, { ...receipt, memberId: 'other', name: 'Other person' }] };
  const archive = receiptArchive([], r, 20);
  assert.equal(archive.length, 1);
  assert.equal(archive[0].receipt.name, 'Me');
  assert(!JSON.stringify(archive).includes('secret'));
  assert(!JSON.stringify(archive).includes('private bank'));
  assert(!JSON.stringify(archive).includes('Other person'));
  const updated = receiptArchive(archive, { ...r, receipts: [{ ...receipt, paid: 1200, balance: 0 }] }, 30);
  assert.equal(updated.length, 1); assert.equal(updated[0].receipt.balance, 0);
  assert.equal(receiptArchive(updated, { ...r, room: { ...r.room, phase: 'CANCELLED' } }).length, 0);
});

test('menu quantity shortcuts merge equivalent items and preserve configured choices', async () => {
  const { addMenuLine, changeQuantity } = await import('../src/foodrun/cartEditing.js');
  const item = { id: 'line', itemId: 'sandwich', quantity: 1, optionIds: [], notes: '', description: '' };
  const twice = addMenuLine([item], { ...item, id: 'new' });
  assert.equal(twice.length, 1); assert.equal(twice[0].quantity, 2); assert.equal(twice[0].id, 'line');
  assert.equal(addMenuLine(twice, { ...item, notes: 'بدون بصل' }).length, 2);
  assert.equal(changeQuantity(twice, 'line', -1)[0].quantity, 1);
  assert.equal(changeQuantity([item], 'line', -1).length, 0);
  assert.throws(() => addMenuLine([{ ...item, quantity: 99 }], item));
  assert.throws(() => changeQuantity([{ ...item, quantity: 99 }], 'line', 1));
});

test('menu editor validation preserves required extras and rejects broken schemas', async () => {
  const { validateMenu, removeOptionGroup } = await import('../src/foodrun/menuValidation.js');
  const { readFile } = await import('node:fs/promises');
  const catalog = JSON.parse(await readFile(new URL('../src/foodrun/builtInRestaurants.json', import.meta.url)));
  catalog.forEach(validateMenu);
  const restaurant = structuredClone(catalog[0]);
  restaurant.menu.optionGroups = [{ id: 'extras', name: 'Extras', minSelections: 1, maxSelections: 1, options: [{ id: 'cheese', name: 'Cheese', priceDeltaMinor: 100 }] }];
  restaurant.menu.items[0].optionGroupIds = ['extras'];
  validateMenu(restaurant);
  const removed = removeOptionGroup(restaurant.menu, 'extras');
  assert.deepEqual(removed.items[0].optionGroupIds, []);
  assert.equal(restaurant.menu.items[0].optionGroupIds.length, 1);
  restaurant.menu.optionGroups[0].minSelections = 2;
  assert.throws(() => validateMenu(restaurant));
  restaurant.menu.optionGroups[0].minSelections = 1;
  restaurant.menu.items[0].basePriceMinor = 100.5;
  assert.throws(() => validateMenu(restaurant));
});

test('Arabic translation preserves user supplied strings and protocol values', async () => {
  const { t } = await import('../src/foodrun/i18n.js');
  assert.equal(t('Wallet dashboard', 'ar'), 'لوحة المحفظة');
  assert.equal(t('Ahmed – without onions', 'ar'), 'Ahmed – without onions');
  assert.equal(t('DECLARE_TRANSFER', 'ar'), 'DECLARE_TRANSFER');
  assert.equal(t('Wallet dashboard', 'en'), 'Wallet dashboard');
});

test('known menu prices immediately include quantity, selected size and extras', async () => {
  const { menuLineTotal } = await import('../src/foodrun/cartEditing.js');
  const restaurant = { menu: { items: [{ id: 'meal', basePriceMinor: 500, variants: [], optionGroupIds: ['extras'] }], optionGroups: [{ id: 'extras', options: [{ id: 'cheese', priceDeltaMinor: 125 }] }] } };
  assert.equal(menuLineTotal(restaurant, { itemId: 'meal', quantity: 3, optionIds: ['cheese'] }), 1875);
  restaurant.menu.items[0].variants = [{ id: 'large', priceMinor: 800 }];
  assert.equal(menuLineTotal(restaurant, { itemId: 'meal', variantId: 'large', quantity: 2, optionIds: ['cheese'] }), 1850);
  assert.equal(menuLineTotal(restaurant, { description: 'Custom', quantity: 2, unitPrice: null }), null);
  assert.equal(menuLineTotal(restaurant, { description: 'Custom', quantity: 2, unitPrice: 600 }), 1200);
});


test('admin visibility requires the verified allowed account', async () => {
  const { canAccessAdmin } = await import('../src/foodrun/adminAccess.js');
  assert.equal(canAccessAdmin(null), false);
  assert.equal(canAccessAdmin({ email: '1ahmedkaram1@gmail.com', emailVerified: false }), false);
  assert.equal(canAccessAdmin({ email: 'other@gmail.com', emailVerified: true }), false);
  assert.equal(canAccessAdmin({ email: '1AHMEDKARAM1@gmail.com', emailVerified: true }), true);
});
test('wallet retains money due until recipient approval and exposes the pending claim', () => {
  const room = { id: 'room', name: 'Lunch', phase: 'FULFILLED', payerId: 'payer', members: [{ id: 'payer', name: 'Payer' }], transfers: [{ id: 'claim', memberId: 'me', amount: 500, status: 'DECLARED' }] };
  const data = { sessions: { room: { roomId: 'room', memberId: 'me' } }, rooms: { room: { room, receipts: [receipt] } } };
  assert.equal(userDashboard(data).toPay, 1200);
  assert.equal(userDashboard(data).entries[0].pending.amount, 500);
  data.sessions.room.memberId = 'payer';
  assert.equal(userDashboard(data).toReceive, 1200);
  assert.equal(userDashboard(data).entries[0].pending.id, 'claim');
  room.transfers[0].status = 'CONFIRMED';
  data.rooms.room.receipts = [{ ...receipt, balance: 700 }];
  assert.equal(userDashboard(data).toReceive, 700);
  assert.equal(userDashboard(data).entries[0].pending, undefined);
});
