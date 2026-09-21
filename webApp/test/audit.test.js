import test from 'node:test';
import assert from 'node:assert/strict';
import { selectionKey, uniquePreviousOrders, uniqueRoomPreviousOrders, userDashboard } from '../src/foodrun/orderHistory.js';
import { mergeRoomReply } from '../src/foodrun/roomState.js';
import { request, normalizeReply } from '../src/foodrun/client.js';
const line = { itemId: 'meal', quantity: 2, optionIds: ['a', 'b'], notes: 'بدون بصل' };
const receipt = { memberId: 'me', lines: [line], balance: 1200, currency: 'AED', name: 'Me' };
test('restaurant polls contain only the explicitly chosen restaurants', async () => {
  const { selectedPollRestaurants } = await import('../src/foodrun/restaurantPoll.js');
  const restaurants = Array.from({ length: 15 }, (_, index) => ({ id: String(index), currency: 'AED' }));
  assert.deepEqual(selectedPollRestaurants(restaurants, ['7', '2', '7']).map(value => value.id), ['7', '2']);
  assert.throws(() => selectedPollRestaurants(restaurants, []));
  assert.throws(() => selectedPollRestaurants(restaurants, ['2']));
  assert.throws(() => selectedPollRestaurants(restaurants, ['2', 'deleted']));
  assert.throws(() => selectedPollRestaurants(restaurants, restaurants.map(value => value.id)));
  assert.equal(selectedPollRestaurants(restaurants, restaurants.slice(0, 12).map(value => value.id)).length, 12);
  assert.throws(() => selectedPollRestaurants([{ id: 'a', currency: 'AED' }, { id: 'b', currency: 'USD' }], ['a', 'b']));
});
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

test('Sharjah catalogue keeps only Google ratings of four or higher', async () => {
  const { sharjahRestaurants } = await import('../src/foodrun/sharjahRestaurants.js');
  assert(sharjahRestaurants.length >= 9);
  assert(sharjahRestaurants.every(restaurant => restaurant.googleRating >= 4 && restaurant.googleRating <= 5));
  assert(sharjahRestaurants.every(restaurant => restaurant.googleRatingVerifiedOn === '2026-09-20'));
  assert(sharjahRestaurants.some(restaurant => restaurant.id === 'builtin-al-rabiah-al-khadra'));
  assert(sharjahRestaurants.some(restaurant => restaurant.id === 'builtin-falafel-frayha'));
  const shawerman = sharjahRestaurants.find(restaurant => restaurant.id === 'builtin-shawerman');
  assert.equal(shawerman.googleRating, 4.8);
  assert.equal(shawerman.menu.items.length, 8);
  const laffah = sharjahRestaurants.find(restaurant => restaurant.id === 'builtin-laffah-al-qasba');
  assert.equal(laffah.googleRating, 4.2);
  assert.equal(laffah.menu.items.length, 9);
  assert.equal(sharjahRestaurants.find(restaurant => restaurant.id === 'builtin-al-farooj-al-shami').googleRating, 4.4);
  assert.equal(sharjahRestaurants.some(restaurant => restaurant.id === 'builtin-al-farooj-fresh'), false);
});

test('Dubai catalogue has ten Egyptian, ten Arabic and shawarma, and ten other restaurants', async () => {
  const { dubaiRestaurants, dubaiEgyptianRestaurants, dubaiArabicShawarmaRestaurants, dubaiOtherRestaurants } = await import('../src/foodrun/dubaiRestaurants.js');
  assert.equal(dubaiEgyptianRestaurants.length, 10);
  assert.equal(dubaiArabicShawarmaRestaurants.length, 10);
  assert.equal(dubaiOtherRestaurants.length, 10);
  assert.equal(dubaiRestaurants.length, 30);
  assert(dubaiRestaurants.every(restaurant => restaurant.emirate === 'Dubai'));
  assert(dubaiRestaurants.every(restaurant => restaurant.googleRating >= 4 && restaurant.googleRating <= 5));
  assert(dubaiRestaurants.every(restaurant => restaurant.googleRatingVerifiedOn === '2026-09-20'));
});

test('server catalog keeps bundled restaurants and restores missing Sharjah metadata', async () => {
  const { mergeRestaurantCatalog } = await import('../src/foodrun/restaurantCatalog.js');
  const bundledValues = [
    { id: 'builtin-sultan', name: 'Sultan Restaurant', emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Sharjah', mealTypes: ['breakfast'] },
    { id: 'builtin-dubai', name: 'Dubai Restaurant', emirate: 'Dubai', area: 'Deira', mealTypes: ['lunch'] },
  ];
  const merged = mergeRestaurantCatalog({
    serverValues: [{ id: 'builtin-sultan', name: 'Sultan Restaurant', emirate: '', area: '', mealTypes: [] }],
    bundledValues,
    currentValues: [...bundledValues, { id: 'personal', name: 'Personal Restaurant' }],
    previousManagedIds: ['builtin-sultan'],
    normalize: value => value,
  });
  assert.deepEqual(merged.restaurants.map(value => value.id), ['personal', 'builtin-sultan', 'builtin-dubai']);
  assert.equal(merged.restaurants.find(value => value.id === 'builtin-sultan').emirate, 'Sharjah');
  assert.deepEqual(merged.restaurants.find(value => value.id === 'builtin-sultan').mealTypes, ['breakfast']);
});

test('explicit administrator deletions override bundled fallback while older catalogs retain it', async () => {
  const { mergeRestaurantCatalog } = await import('../src/foodrun/restaurantCatalog.js');
  const bundledValues = [{ id: 'removed', name: 'Removed' }, { id: 'new', name: 'New' }];
  const args = { serverValues: [], bundledValues, currentValues: bundledValues, previousManagedIds: [], normalize: value => value };
  assert.equal(mergeRestaurantCatalog(args).restaurants.length, 2);
  const deleted = mergeRestaurantCatalog({ ...args, deletedRestaurantIds: ['removed'] });
  assert.deepEqual(deleted.restaurants.map(value => value.id), ['new']);
  assert(deleted.managedIds.includes('removed'));
  assert.equal(mergeRestaurantCatalog({ ...args, previousManagedIds: deleted.managedIds, serverValues: [bundledValues[0]] }).restaurants.length, 2);
});

test('offline restaurant upgrades restore location without replacing edited menus or contact details', async () => {
  const { restoreRestaurantMetadata } = await import('../src/foodrun/restaurantCatalog.js');
  const saved = { id: 'sultan', name: 'My Sultan', emirate: '', mealTypes: [], menu: { items: [{ id: 'local', basePriceMinor: 800 }] }, contact: { phoneE164: '+971500000001' } };
  const bundled = { id: 'sultan', name: 'Sultan', emirate: 'Sharjah', mealTypes: ['breakfast'], menu: { items: [] } };
  const [upgraded] = restoreRestaurantMetadata([saved], [bundled]);
  assert.equal(upgraded.emirate, 'Sharjah');
  assert.deepEqual(upgraded.mealTypes, ['breakfast']);
  assert.deepEqual(upgraded.menu, saved.menu);
  assert.deepEqual(upgraded.contact, saved.contact);
  assert.equal(upgraded.name, 'My Sultan');
  assert.equal(saved.emirate, '');
  assert.equal(restoreRestaurantMetadata([{ ...saved, emirate: 'Dubai' }], [bundled])[0].emirate, 'Dubai');
});

test('Arabic translation preserves user supplied strings and protocol values', async () => {
  const { t, tf } = await import('../src/foodrun/i18n.js');
  assert.equal(t('Wallet dashboard', 'ar'), 'متابعة الحسابات');
  assert.equal(t('Ahmed – without onions', 'ar'), 'Ahmed – without onions');
  assert.equal(t('DECLARE_TRANSFER', 'ar'), 'DECLARE_TRANSFER');
  assert.equal(t('Wallet dashboard', 'en'), 'Wallet dashboard');
  assert.equal(t('Breakfast', 'ar'), 'فطار');
  assert.equal(t('Create room', 'ar'), 'اعمل الغرفة');
  assert.equal(tf('Join {name}', { name: 'Home & AED 8.00' }, 'ar'), 'ادخل Home & AED 8.00');
  assert.equal(tf('Room code: {code}', { code: '001234' }, 'ar'), 'كود الغرفة: 001234');
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


test('menu categories keep sandwiches separate from party prices and support Arabic searches', async () => {
  const { menuCategories, defaultMenuCategory, browsedMenuItems } = await import('../src/foodrun/menuBrowsing.js');
  const { readFile } = await import('node:fs/promises');
  const catalog = JSON.parse(await readFile(new URL('../src/foodrun/builtInRestaurants.json', import.meta.url)));
  const restaurant = catalog.find(value => value.id === 'builtin-bait-al-waleema');
  const initial = defaultMenuCategory(restaurant);
  assert.equal(menuCategories(restaurant)[0].name, 'Sandwiches');
  assert.equal(browsedMenuItems(restaurant, initial).length, 17);
  assert(browsedMenuItems(restaurant, initial).every(item => item.basePriceMinor < 2000));
  const parties = menuCategories(restaurant).find(value => value.name === 'Parties');
  assert.deepEqual(browsedMenuItems(restaurant, `category:${parties.id}`).map(item => item.basePriceMinor), [22500, 48000, 47000, 80000, 120000]);
  assert.equal(browsedMenuItems(restaurant, initial, 'حواوشي').length, 1);
  assert.equal(browsedMenuItems(restaurant, `category:${parties.id}`, 'حواوشي').length, 0);
  assert.equal(browsedMenuItems(restaurant, 'all').length, 126);
});

test('wallet directions identify the chosen person, retain same-name people and separate refunds', () => {
  const data = { sessions: {}, rooms: {} };
  const add = (id, memberId, payerId, members, receipts) => {
    data.sessions[id] = { roomId: id, memberId };
    data.rooms[id] = { room: { id, name: id, phase: 'PLACED', payerId, members, account: { holder: 'Bank holder' }, transfers: [] }, receipts };
  };
  add('lunch', 'me', 'chosen', [{ id: 'chosen', name: 'Ali' }], [{ ...receipt, balance: 1500 }]);
  add('dinner', 'me', 'me', [], [{ ...receipt, memberId: 'a', name: 'Same', balance: 900 }, { ...receipt, memberId: 'b', name: 'Same', balance: 700 }, { ...receipt, memberId: 'c', name: 'Refund', balance: -200 }]);
  const result = userDashboard(data);
  assert.equal(result.toPay, 1700); assert.equal(result.toReceive, 1600);
  assert.equal(result.payEntries[0].person, 'Ali'); assert.equal(result.payEntries[0].personId, 'chosen');
  assert.equal(result.payEntries[1].kind, 'refund');
  assert.deepEqual(result.receiveEntries.map(value => value.personId), ['a', 'b']);
});

test('optional delivery address retains automatic delivery mode without requiring an address', async () => {
  const { deliveryDestination } = await import('../src/foodrun/roomSetup.js');
  assert.equal(deliveryDestination(true, '  '), 'The selected orderer will arrange delivery with the restaurant.');
  assert.equal(deliveryDestination(true, ' Office reception '), 'Office reception');
  assert.equal(deliveryDestination(false, 'Office'), '');
});
