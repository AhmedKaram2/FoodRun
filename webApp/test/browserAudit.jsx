// Browser-only fixture harness. Run with Vite in an isolated browser profile:
// await (await import('/test/browserAudit.jsx')).runCreateAudit()
// It captures commands locally; no account, restaurant order, or payment is sent.
import React from 'react';
import { createRoot } from 'react-dom/client';
import { CreateRoom, Home, ProfileScreen, RoomScreen, loadRestaurants, storeRestaurants } from '../src/foodrun/FoodRunApp.jsx';
import RestaurantLibraryScreen from '../src/foodrun/RestaurantLibraryScreen.jsx';
import { t } from '../src/foodrun/i18n.js';
import { CreatePaymentRoom } from '../src/foodrun/PaymentRoom.jsx';

let root, host;
export const commands = [];
const pause = () => new Promise(resolve => setTimeout(resolve, 60));
const assert = (condition, message) => { if (!condition) throw Error(message); };
const data = {
  home: { profile: { name: 'Audit User', phone: '+971500000001', favoriteOrders: [] }, people: [], invitations: [] },
  user: { uid: 'audit-only' }, rooms: {}, sessions: {}, online: {}, busy: false, hub: 'https://audit.invalid',
  send: async (kind, fields) => { commands.push({ kind, fields }); return { ok: true }; },
  setNotice: () => {},
};
export async function mountAudit(screen = 'create', phase = 'LOBBY', options = {}) {
  document.getElementById('root').style.display = 'none';
  root?.unmount(); host?.remove();
  host = document.createElement('div'); host.id = 'audit-root'; document.body.append(host);
  root = createRoot(host);
  const savedRestaurant = loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba');
  const restaurant = { ...savedRestaurant, contact: { ...savedRestaurant.contact, ...(options.contact || {}) } };
  const room = { id: 'audit-room', name: 'Audit room', code: '123456', ownerId: 'me', payerId: 'me', restaurant, phase, orderNumber: 1,
    members: [{ id: 'me', name: 'Audit User', approved: true, participating: true, eligible: true, ready: true }],
    carts: [], transfers: [], audit: [], expectedNames: [], restaurantOptions: [restaurant], restaurantVotes: [], restaurantPollOpen: false,
    fees: { delivery: 0, service: 0, discount: 0 }, adjustment: 0, billRevision: 1, billApprovals: [], quoteRevision: 1, revision: 1,
    restaurantPaid: false, deliveryMode: false, destination: '', account: null, preparationId: '', preparedIds: [], pastSpins: [], spin: null,
  };
  Object.assign(room, options.room || {});
  const roomData = { ...data, sessions: { [room.id]: { roomId: room.id, roomName: room.name, memberId: options.memberId || 'me' } },
    rooms: { [room.id]: { room, receipts: options.receipts || [], progress: options.progress, history: options.history || [], serverTime: Date.now() } }, online: { [room.id]: true } };
  const props = { data: screen === 'room' || options.room ? roomData : options.data || data, onBack: () => {}, openRoom: () => {}, setPage: () => {} };
  if (options.send) roomData.send = options.send;
  if (options.liveCart || options.liveCommands) roomData.send = async (kind, fields) => {
    commands.push({ kind, fields });
    if (kind === 'CART') {
      room.carts = [{ ...fields.cart, revision: fields.expectedRevision + 1, submitted: false, confirmedQuote: -1 }];
    }
    if (kind === 'VOTE_RESTAURANT') room.restaurantVotes = [{ memberId: 'me', restaurantId: fields.text }];
    if (kind === 'PRICE_ITEM') {
      room.carts = room.carts.map(cart => cart.memberId !== fields.memberId ? cart : { ...cart, lines: cart.lines.map(line => line.id !== fields.text ? line : { ...line, unitPrice: fields.flag ? null : fields.amount }) });
      room.quoteRevision++; room.revision++; if (!['PLACED','FULFILLED'].includes(room.phase)) room.phase = 'COLLECTING';
    }
    if (kind === 'SET_FEES') {
      room.fees = fields.fees; room.restaurant = fields.restaurant || room.restaurant; room.quoteRevision++; room.revision++;
      roomData.rooms[room.id].progress = { ...roomData.rooms[room.id].progress, canReview: room.restaurant.pricing.taxTreatment !== 'unspecified', reviewBlocker: '' };
    }
    root.render(<RoomScreen {...props} roomId={room.id} />);
    return { ok: true };
  };
  root.render(screen === 'payment-create' ? <CreatePaymentRoom {...props} openProfile={() => {}} /> : screen === 'library' ? <RestaurantLibraryScreen language={document.documentElement.lang === 'ar' ? 'ar' : 'en'} {...props} />
    : screen === 'home' ? <Home {...props} /> : screen === 'profile' ? <ProfileScreen {...props} />
    : screen === 'room' ? <RoomScreen {...props} roomId={room.id} /> : <CreateRoom {...props} mode={screen === 'join' ? 'join' : 'create'} />);
  await pause(); return measureAudit();
}
export function measureAudit() {
  return { width: innerWidth, screenWidth: document.documentElement.scrollWidth,
    overflow: document.documentElement.scrollWidth > innerWidth + 1,
    text: host.innerText.slice(0, 500), inputs: [...host.querySelectorAll('input, select')].filter(x => x.getClientRects().length).map(x => ({ label: x.closest('label')?.textContent, fontSize: getComputedStyle(x).fontSize })) };
}
function setValue(node, value) {
  Object.getOwnPropertyDescriptor(node instanceof HTMLSelectElement ? HTMLSelectElement.prototype : node instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set.call(node, value);
  node.dispatchEvent(new Event(node instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}
const button = text => [...host.querySelectorAll('button')].find(node => node.textContent === t(text));
export async function runPollPricingAudit() {
  commands.length = 0;
  const catalog = loadRestaurants();
  const restaurant = catalog.find(value => value.id === 'builtin-laffah-al-qasba');
  const poll = { restaurantPollOpen: true, restaurantOptions: [restaurant, { ...restaurant, id: 'second', name: 'Second restaurant', nameAr: 'مطعم آخر' }] };
  await mountAudit('room', 'LOBBY', { room: poll, liveCommands: true });
  assert(host.querySelector('.poll-dialog').open, 'Entering an open poll did not show the popup');
  host.querySelector('.poll-dialog-options button').click(); await pause();
  assert(commands.length === 1 && commands[0].kind === 'VOTE_RESTAURANT', 'Poll choice did not vote exactly once');
  assert(!host.querySelector('.poll-dialog').open, 'Saved vote did not close popup');
  await mountAudit('room', 'LOBBY', { room: { ...poll, restaurantVotes: [{ memberId: 'me', restaurantId: restaurant.id }] } });
  assert(!host.querySelector('.poll-dialog').open, 'Existing voter was prompted again');
  await mountAudit('room', 'LOBBY', { room: poll, send: async () => null });
  host.querySelector('.poll-dialog-options button').click(); await pause();
  assert(host.querySelector('.poll-dialog').open && host.querySelector('.poll-dialog [role=alert]'), 'Failed vote closed popup or lost error');
  button('Choose later').click(); await pause();
  assert(!host.querySelector('.poll-dialog').open, 'Choose later did not dismiss popup');
  await mountAudit('room', 'LOBBY', { room: { ...poll, members: [{ id: 'me', approved: true, guest: true, participating: false }] } });
  assert(!host.querySelector('.poll-dialog').open, 'Guest saw voting popup');
  const item = restaurant.menu.items.find(value => value.available);
  const line = { id: 'priced-line', itemId: item.id, variantId: item.variants[0]?.id || null, optionIds: [], notes: '', description: '', unitPrice: null, quantity: 2 };
  const room = { restaurant, carts: [{ memberId: 'me', revision: 1, lines: [line], submitted: true }] };
  await mountAudit('room', 'COLLECTING', { room, liveCommands: true });
  const before = (first, second) => !!(host.querySelector(first).compareDocumentPosition(host.querySelector(second)) & Node.DOCUMENT_POSITION_FOLLOWING);
  assert(before('.collected-orders', '.restaurant-order-card') && before('.fees-editor', '.restaurant-order-card'), 'Send controls must follow collected orders and fees');
  assert(host.querySelector('.order-pricing-panel'), 'Selected person cannot edit menu item prices');
  button('Edit price').click(); await pause();
  setValue(host.querySelector('.order-pricing-panel input'), '7.50'); await pause();
  host.querySelector('.order-pricing-panel form').requestSubmit(); await pause();
  assert(commands.at(-1).kind === 'PRICE_ITEM' && commands.at(-1).fields.amount === 750, 'Unit price was not saved in minor units');
  assert(host.querySelector('.order-pricing-panel').textContent.includes('15.00'), 'Quantity was not applied to adjusted unit price');
  button('Edit price').click(); await pause(); button('Use menu price').click(); await pause();
  assert(commands.at(-1).fields.flag === true, 'Menu price restore command missing');
  const discount = [...host.querySelectorAll('label')].find(node => node.textContent.startsWith(t('Shared discount (whole order)'))).querySelector('input');
  setValue(host.querySelector('.fees-editor select'), 'included'); await pause();
  setValue(discount, '5.25'); await pause(); discount.closest('form').requestSubmit(); await pause();
  assert(commands.at(-1).kind === 'SET_FEES' && commands.at(-1).fields.fees.discount === 525, 'Shared discount not saved');
  assert(commands.at(-1).fields.restaurant.pricing.taxTreatment === 'included', 'Confirmed tax not sent with fees');
  assert(host.querySelector('.fees-editor select').value === 'included', 'Saved tax choice was not retained');
  assert(!measureAudit().overflow, 'Price editor overflows');
  await mountAudit('room', 'REVIEW', { room });
  assert(host.querySelector('.order-pricing-panel'), 'Price editor missing during review');
  await mountAudit('room', 'COLLECTING', { room: { ...room, ownerId: 'organizer', payerId: 'other', members: [{ id: 'me', name: 'Me', approved: true, participating: true }, { id: 'other', name: 'Other', approved: true, participating: true }] } });
  assert(!host.querySelector('.order-pricing-panel'), 'Nonpayer can see item price controls');
  for (const phase of ['PLACED', 'FULFILLED']) {
    await mountAudit('room', phase, { room: { ...room, billRevision: 2, adjustmentApprovals: [] }, liveCommands: true });
    assert(host.querySelector('.order-pricing-panel'), 'Menu price editing disappears after sending');
    button('Edit price').click(); await pause();
    setValue(host.querySelector('.order-pricing-panel input'), '8.00'); await pause();
    host.querySelector('.order-pricing-panel form').requestSubmit(); await pause();
    assert(commands.at(-1).kind === 'PRICE_ITEM', 'Placed menu price was not saved');
    assert(!button('Approve revised final bill'), 'Repeated approval is still requested');
    const pay = [...host.querySelectorAll('button')].find(node => node.textContent.startsWith(t('Mark restaurant paid')));
    assert(pay && !pay.disabled, 'Payment is blocked on repeat approvals');
  }
  await mountAudit('room', 'LOBBY', { room: poll, liveCommands: true });
  assert(!measureAudit().overflow, 'Poll popup overflows');
  return { passed: ['entry popup', 'vote saves and closes', 'existing voter skipped', 'failed vote retry', 'dismiss', 'guest excluded', 'menu price edit', 'quantity multiplication', 'restore menu price', 'shared discount', 'review phase', 'ordinary members cannot price'], ...measureAudit() };
}
export async function runOwnerPricingAudit() {
  commands.length = 0;
  const restaurant = loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba');
  const item = restaurant.menu.items.find(value => value.available);
  const members = ['me', 'payer', 'member'].map(id => ({ id, name: id, approved: true, participating: true }));
  const line = { id: 'other-item', itemId: item.id, variantId: item.variants[0]?.id || null, optionIds: [], description: '', notes: '', quantity: 2, unitPrice: null };
  const room = { restaurant, ownerId: 'me', payerId: 'payer', members, carts: [{ memberId: 'member', revision: 1, submitted: true, lines: [line] }] };
  for (const phase of ['COLLECTING', 'REVIEW', 'PLACED', 'FULFILLED']) {
    await mountAudit('room', phase, { room, liveCommands: true });
    const details = host.querySelector('.order-edit-options');
    if (details) details.open = true;
    assert(host.querySelector('.order-pricing-panel'), `Owner cannot price in ${phase}`);
    button('Edit price').click(); await pause();
    setValue(host.querySelector('.order-pricing-panel input'), '7.50'); await pause();
    host.querySelector('.order-pricing-panel form').requestSubmit(); await pause();
    const saved = commands.at(-1);
    assert(saved.kind === 'PRICE_ITEM' && saved.fields.memberId === 'member' && saved.fields.amount === 750, 'Owner price change did not target the item');
    assert(host.querySelector('.order-pricing-panel').textContent.includes('15.00'), 'Owner change did not recalculate quantity');
    button('Edit price').click(); await pause(); button('Use menu price').click(); await pause();
    assert(commands.at(-1).fields.flag === true, 'Owner cannot restore the menu price');
    assert(![...host.querySelectorAll('button')].some(node => node.textContent.startsWith(t('Mark restaurant paid'))), 'Owner received payer payment controls');
    assert(!measureAudit().overflow, `Owner editor overflows in ${phase}`);
    await mountAudit('room', phase, { room: { ...room, ownerId: 'organizer' } });
    assert(!host.querySelector('.order-pricing-panel'), `Ordinary member can price in ${phase}`);
  }
  for (const phase of ['ARCHIVED', 'CANCELLED']) {
    await mountAudit('room', phase, { room });
    assert(!host.querySelector('.order-pricing-panel'), `Owner can price a closed order in ${phase}`);
  }
  await mountAudit('room', 'COLLECTING', { room, liveCommands: true });
  return { passed: ['owner editing in all four active stages', 'other member item prices', 'quantity totals', 'restore menu price', 'payer controls retained', 'ordinary members excluded', 'closed orders unchanged'], ...measureAudit() };
}

export async function runReorderAudit() {
  commands.length = 0;
  await mountAudit('room');
  assert(!host.querySelector('.last-order'), 'New customer sees a last order');
  const restaurant = structuredClone(loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba'));
  const item = restaurant.menu.items.find(value => value.available);
  item.variants = [{ id: 'audit-large', name: 'Large', priceMinor: 1500 }];
  item.optionGroupIds = ['audit-extras'];
  restaurant.menu.optionGroups.push({ id: 'audit-extras', name: 'Extras', minSelections: 1, maxSelections: 1, options: [{ id: 'audit-cheese', name: 'Cheese', priceDeltaMinor: 200 }] });
  const groups = restaurant.menu.optionGroups.filter(group => item.optionGroupIds.includes(group.id));
  const optionIds = groups.flatMap(group => group.options.slice(0, Math.max(1, group.minSelections)).map(option => option.id));
  const lines = [{ itemId: item.id, variantId: item.variants[0].id, optionIds, quantity: 2, description: item.name, notes: 'No onions', amount: 1 },
    { itemId: 'removed-item', quantity: 1, description: 'Unavailable meal', optionIds: [] }];
  const history = [{ number: 1, restaurantId: restaurant.id, restaurantName: restaurant.name, completedAt: 1700000000000, receipts: [{ memberId: 'me', lines }] }];
  await mountAudit('room', 'LOBBY', { history, liveCart: true, room: { restaurant } });
  assert(host.querySelector('.last-order').textContent.includes('2 ×'), 'Last order summary missing quantity');
  button('Reorder').click(); await pause();
  assert(commands.length === 0, 'Preview mutated the cart');
  assert(document.activeElement.id === 'reorder-review-title', 'Review heading did not receive focus');
  assert(host.querySelector('.reorder-review').textContent.includes(t('This item is unavailable.')), 'Unavailable item was not flagged');
  assert(host.querySelector('.reorder-review').textContent.includes('34.00'), 'Current size and extra prices were not used');
  assert(!measureAudit().overflow, 'Reorder review overflows');
  button('Cancel').click(); await pause();
  assert(!host.querySelector('.reorder-review') && commands.length === 0, 'Cancel changed the cart');
  button('Reorder').click(); await pause();
  button('Add to cart').click(); button('Add to cart').click(); await pause();
  assert(commands.length === 1 && commands[0].kind === 'CART', 'Reorder should save once without submitting');
  const cart = commands[0].fields.cart;
  assert(cart.lines.length === 1 && cart.lines[0].quantity === 2, 'Available quantity was not restored');
  assert(cart.lines[0].variantId === lines[0].variantId && JSON.stringify(cart.lines[0].optionIds) === JSON.stringify(optionIds) && cart.lines[0].notes === 'No onions', 'Customizations were lost');
  assert(cart.lines[0].unitPrice === null && !Object.hasOwn(cart.lines[0], 'amount'), 'Historical prices were reused');
  assert(host.querySelector('.cart-review').textContent.includes(t('Not added to your cart')), 'Skipped items disappeared after adding');
  assert(document.activeElement.classList.contains('cart-review'), 'Cart was not focused for review');
  assert(!measureAudit().overflow, 'Cart review overflows');
  const missing = [{ ...history[0], receipts: [{ memberId: 'me', lines: [lines[1]] }] }];
  await mountAudit('room', 'LOBBY', { history: missing, liveCart: true, room: { restaurant } });
  button('Reorder').click(); await pause();
  assert(button('Add to cart').disabled, 'All-unavailable order can be added');
  assert(commands.length === 1, 'All-unavailable order sent a command');
  return { passed: ['new customer hidden', 'last order summary', 'review before mutation', 'cancel', 'unavailable item warning', 'quantity and customizations restored', 'current prices', 'double-click protection', 'cart focused', 'no auto-submit', 'all unavailable blocked'], ...measureAudit() };
}
export async function runCreateAudit() {
  commands.length = 0;
  await mountAudit();
  assert(!measureAudit().overflow, 'Create Room overflows');
  button('Start a room poll').click(); await pause();
  assert(host.querySelector('[role=dialog]'), 'Poll selection did not open');
  assert(document.activeElement.tagName !== 'INPUT', 'Picker unexpectedly opens keyboard');
  const filters = host.querySelectorAll('.restaurant-picker-filters select');
  setValue(filters[0], 'Sharjah'); await pause();
  const catalog = loadRestaurants();
  const ids = ['builtin-al-kalha', 'builtin-al-mahla', 'builtin-sultan'];
  for (const id of ids) {
    const restaurant = catalog.find(r => r.id === id);
    const card = [...host.querySelectorAll('.restaurant-pick-card')].find(node => [restaurant.name, restaurant.nameAr].includes(node.querySelector('b')?.textContent));
    assert(card, `Missing Sharjah restaurant: ${id}`); card.click(); await pause();
  }
  assert(host.querySelectorAll('.restaurant-pick-card[aria-pressed=true]').length === 3, 'Selected states are missing');
  button('Use selected restaurants').click(); await pause();
  assert(!host.querySelector('[role=dialog]'), 'Picker did not close');
  assert(host.querySelectorAll('.poll-chip').length === 3, 'Chosen restaurant summary missing');
  host.querySelector('.create-form').requestSubmit(); await pause();
  assert(commands.length === 1, 'Create should send once');
  assert(JSON.stringify(commands[0].fields.restaurants.map(r => r.id)) === JSON.stringify(ids), 'Unselected restaurants entered the poll');
  assert(commands[0].fields.restaurant.id === ids[0], 'Initial choice is outside poll');
  host.querySelector('.poll-chip').click(); await pause();
  host.querySelector('.poll-chip').click(); await pause();
  assert(host.querySelector('.create-submit button').disabled, 'A one-restaurant poll is enabled');
  button('Use this restaurant').click(); await pause();
  assert(!host.querySelector('.create-submit button').disabled, 'Direct ordering remains blocked');
  return { passed: ['mobile layout', 'no forced keyboard', 'Sharjah filtering', 'multi-selection', 'selected summary', 'exact submitted candidates', 'minimum two choices', 'direct order toggle'], ...measureAudit() };
}
export async function runLibraryAudit() {
  await mountAudit('library');
  const restaurants = loadRestaurants();
  const original = restaurants.find(r => r.id === 'builtin-laffah-al-qasba');
  [...host.querySelectorAll('.restaurant-row')].find(x => x.textContent.includes(original.name)).click(); await pause();
  const price = host.querySelector('input[aria-label="' + t('Base price') + '"]');
  assert(price.value === '8.00', '800 minor units did not render as 8.00');
  const added = { ...original, id: 'audit-restaurant', name: 'Audit fresh catalog' };
  storeRestaurants([...restaurants, added]); await pause();
  assert(host.querySelector('.library-list').textContent.includes(added.name), 'Mounted library did not refresh');
  storeRestaurants(restaurants);
  await pause();
  return { passed: ['AED 8.00 price', 'catalog refresh while mounted'], ...measureAudit() };
}

export async function runOrderFlowAudit() {
  commands.length = 0;
  const other = { id: 'other', name: 'Known orderer', approved: true, participating: true, eligible: true, ready: false };
  const self = { ...other, id: 'me', name: 'Audit User' };
  const room = { members: [self, other], account: { id: 'account', holder: 'Audit User', bank: 'Audit Bank', identifier: '123456789012', currency: 'AED', version: 1 }, restaurantPaid: true };
  const receipt = (id, paid = 0) => ({ memberId: id, name: id, lines: [{ description: 'Shawarma', quantity: 1, amount: 1000 }], food: 1000, delivery: 0, service: 0, tax: 0, discount: 0, total: 1000, totalText: 'AED 10.00', paid, balance: 1000 - paid, currency: 'AED' });
  await mountAudit('room', 'LOBBY', { room });
  button('Choose person directly').click(); await pause();
  const choice = [...host.querySelectorAll('select')].find(node => [...node.options].some(option => option.value === 'other'));
  setValue(choice, 'other'); await pause();
  [...host.querySelectorAll('button')].filter(node => node.textContent === t('Choose person directly')).at(-1).click(); await pause();
  assert(commands.at(-1).kind === 'SELECT_PAYER' && commands.at(-1).fields.memberId === 'other', 'Direct choice sends wrong person');
  assert(!measureAudit().overflow, 'Direct selection overflows');
  await mountAudit('room', 'COLLECTING', { room, contact: { whatsappE164: '+971501234567' }, receipts: [receipt('me'), receipt('other')], progress: { canReview: true, reviewBlocker: '' } });
  const eta = host.querySelector('.restaurant-order-card input');
  assert(eta.closest('label').textContent === t('Expected delivery / pickup (optional)'), 'Expected arrival is not marked optional');
  assert(!eta.required && eta.value === '', 'Expected arrival should start empty and optional');
  assert(!button('Order sent').disabled, 'Missing expected arrival blocks submission');
  button('Order sent').click(); await pause();
  assert(commands.at(-1).kind === 'PLACE' && commands.at(-1).fields.text === '', 'Placement without expected arrival failed');
  setValue(eta, '   '); await pause();
  assert(!button('Order sent').disabled, 'Whitespace expected arrival blocks submission');
  button('Order sent').click(); await pause();
  assert(commands.at(-1).kind === 'PLACE' && commands.at(-1).fields.text === '', 'Whitespace expected arrival was not treated as empty');
  setValue(eta, '30 minutes'); await pause();
  const whatsapp = new URL(host.querySelector('a[href^="https://wa.me/"]').href);
  assert(whatsapp.pathname === '/971501234567', 'WhatsApp did not target the restaurant');
  assert(whatsapp.searchParams.get('text').includes('30 minutes'), 'Expected delivery is missing from message');
  assert(host.querySelector('.restaurant-phone').textContent.includes('+971'), 'Restaurant phone hidden');
  assert(button('Copy phone number'), 'Phone copy action missing');
  button('Order sent').click(); await pause();
  assert(commands.at(-1).kind === 'PLACE' && commands.at(-1).fields.text === '30 minutes', 'Direct placement missing');
  assert(!button('Confirm my total and recipient'), 'Redundant total confirmation remains');
  assert(!measureAudit().overflow, 'Restaurant order overflows');
  await mountAudit('home', 'PLACED', { room, memberId: 'other', receipts: [receipt('other')] });
  assert(host.textContent.includes('123456789012'), 'Wallet payment details are hidden');
  button('Mark paid').closest('form').requestSubmit(); await pause();
  assert(commands.at(-1).kind === 'DECLARE_TRANSFER' && commands.at(-1).fields.amount === 1000, 'Wallet mark paid failed');
  assert(!measureAudit().overflow, 'Wallet overflows');
  const pending = { id: 'claim', memberId: 'other', amount: 1000, status: 'declared', reference: 'Audit payment', refund: false };
  await mountAudit('room', 'PLACED', { room: { ...room, transfers: [pending] }, receipts: [receipt('me',1000), receipt('other')] });
  button('Confirm received').click(); await pause();
  assert(commands.at(-1).kind === 'CONFIRM_TRANSFER' && commands.at(-1).fields.transferId === 'claim', 'Top confirmation action failed');
  assert(host.querySelector('.payment-breakdown') && !host.querySelector('.payment-breakdown').open, 'Details are not optional');
  assert(host.querySelector('.payment-priority').textContent.includes('123456789012'), 'Chosen person payment details hidden');
  return { passed: ['known person without wheel', 'restaurant phone and copy', 'correct WhatsApp recipient', 'optional ETA label', 'blank and whitespace ETA submission', 'ETA in message', 'direct placement', 'wallet mark paid', 'top payment confirmation', 'visible receiving details', 'optional breakdown'], ...measureAudit() };
}

export async function runOwnerBlockAudit() {
  commands.length = 0;
  await mountAudit('room', 'LOBBY', { room: { members: [
    { id: 'me', name: 'Owner', approved: true, participating: true, ready: true, eligible: true },
    { id: 'other', name: 'Requested member', approved: true, participating: true, ready: true, eligible: true },
  ] } });
  const summary = [...host.querySelectorAll('summary')].find(node => node.textContent === t('Request a user block'));
  assert(summary, 'Owner request form missing'); summary.click(); await pause();
  const form = summary.parentElement.querySelector('form');
  setValue(form.querySelectorAll('select')[1], '72');
  setValue(form.querySelector('textarea'), 'Repeated disruptive behavior'); await pause();
  form.requestSubmit(); await pause();
  assert(commands.at(-1).kind === 'REQUEST_BLOCK' && commands.at(-1).fields.memberId === 'other' && commands.at(-1).fields.amount === 72, 'Incorrect block request');
  assert(commands.at(-1).fields.text === 'Repeated disruptive behavior', 'Reason missing');
  return { passed: ['room owner request', 'selected member', 'three-day duration', 'reason preserved'], ...measureAudit() };
}

export async function runAdminAudit() {
  const { AdminUsers, AdminCleanup, AdminBlockRequests } = await import('../src/foodrun/AdminApp.jsx');
  const { default: BlockedNotice } = await import('../src/foodrun/BlockedNotice.jsx');
  const calls = []; const mutate = async (path, body) => { calls.push({ path, body }); return { ok: true }; };
  const mount = async element => { document.getElementById('root').style.display = 'none'; root?.unmount(); host?.remove(); host = document.createElement('div'); host.id='audit-root';host.className='admin-shell';document.body.append(host); root=createRoot(host);root.render(element);await pause(); };
  await mount(<AdminUsers users={[{ id:'admin',name:'Admin' },{ id:'member',name:'Audit Member' }]} rooms={[{ id:'room',name:'Audit room' }]} currentUserId="admin" mutate={mutate} busy={false} />);
  const card = [...host.querySelectorAll('.admin-person')].find(node => node.textContent.includes('Audit Member'));
  const form = card.querySelector('form');setValue(form.querySelectorAll('select')[0],'room');setValue(form.querySelectorAll('select')[1],'72');setValue(form.querySelector('input'),'Repeated disruption');await pause();form.requestSubmit();await pause();
  assert(calls.at(-1).body.scopeRoomId==='room' && calls.at(-1).body.durationHours===72,'Admin room scope or duration lost');
  assert([...host.querySelectorAll('.admin-person')][0].querySelector('form')===null,'Admin can block own account');
  assert(!measureAudit().overflow,'Admin users overflow');
  await mount(<AdminBlockRequests requests={[{id:'request',userName:'Audit Member',requesterName:'Owner',roomName:'Room',durationHours:24,reason:'Needs review',createdAt:Date.now(),status:'pending'}]} mutate={mutate} busy={false} />);
  button('Approve block').click();await pause();assert(calls.at(-1).path==='/admin/block-request' && calls.at(-1).body.action==='approve','Request approval failed');
  const requests=[];
  await mount(<AdminCleanup request={async (path,body)=>{requests.push({path,body});return path.endsWith('preview')?{count:1,previewToken:'preview-1',targets:[{id:'room',name:'Old room',orderNumber:1,restaurant:'Kitchen'}]}:{removedCount:1};}} onChanged={async()=>{}} />);
  button('Preview cleanup').click();await pause();assert(button('Delete selected data').disabled,'Cleanup enabled without confirmation');
  const confirmation=[...host.querySelectorAll('input')].find(node=>node.type==='text');setValue(confirmation,'DELETE');await pause();button('Delete selected data').click();await pause();
  assert(requests.at(-1).body.previewToken==='preview-1' && requests.at(-1).body.confirmation==='DELETE','Cleanup selection not preserved');
  let returned=false;
  const now=Date.now();await mount(<BlockedNotice block={{until:now+120000,serverTime:now,receivedAt:now,durationHours:1,reason:'Test room restriction'}} retry={()=>{}} onBack={()=>{returned=true;}} />);
  const before=host.querySelector('[role=timer]').textContent;
  await new Promise(resolve=>setTimeout(resolve,1150));const after=host.querySelector('[role=timer]').textContent;
  assert(before!==after,'Blocked countdown did not update');
  assert(getComputedStyle(host.querySelector('.block-timer')).color==='rgb(179, 38, 30)','Countdown is not red');
  button('Back to my account').click();assert(returned,'Blocked user cannot return to account');
  assert(!measureAudit().overflow,'Blocked notice overflows');
  return { passed:['room-scoped admin block','self-block protection','owner request approval','cleanup preview and confirmation','red live countdown','return to account'], ...measureAudit() };
}

export async function runPaymentProfileAudit() {
  commands.length = 0;
  const receipt = { memberId:'other', name:'Member', lines:[], total:1200, paid:200, balance:1000, currency:'AED' };
  const options = { room:{ account:{id:'account',holder:'Chosen',bank:'Aani',method:'AANI',identifier:'+971501234567'}, members:[{id:'me',name:'Chosen'}], restaurantPaid:true }, memberId:'other', receipts:[receipt], history:[{number:0, restaurantName:'History kitchen',completedAt:Date.now()-86400000, receipts:[{...receipt,paid:1200,balance:0}]}] };
  await mountAudit('home','PLACED',options);
  const homeDue = host.querySelector('.wallet-direction.pay .wallet-direction-heading strong').textContent;
  assert(host.textContent.includes('History kitchen'),'Home payment history missing');
  await mountAudit('profile','PLACED',options);
  assert(host.querySelector('.wallet-direction.pay .wallet-direction-heading strong').textContent===homeDue,'Profile balance differs from Home');
  assert(host.textContent.includes('History kitchen'),'Profile payment history missing');
  assert(button('Mark paid'),'Profile payment action missing');
  assert(host.querySelector('.payment-details').textContent.includes(t('Aani · UAE mobile number')),'Aani receiving details mislabeled');
  assert(host.querySelector('#profile-details').tagName === 'SECTION', 'Profile details can still collapse');
  assert(host.querySelector('#profile-details').compareDocumentPosition(host.querySelector('#profile-wallet')) & Node.DOCUMENT_POSITION_FOLLOWING, 'Wallet appears before profile details');
  let input=host.querySelector('.payment-card input[type=tel]');
  setValue(input,'0501234567');await pause();
  button('Bank account').click();await pause();
  input=host.querySelector('.payment-card input[dir=ltr]');assert(input.value==='','Aani phone leaked into IBAN');
  setValue(input,'ae07 0331 2345 6789 0123 456');await pause();
  const bank=host.querySelectorAll('.payment-card input')[1];setValue(bank,'Example Bank');await pause();
  button('Aani').click();await pause();assert(host.querySelector('.payment-card input[type=tel]').value==='0501234567','Aani draft lost');
  assert(host.querySelectorAll('.payment-card input').length===2,'Aani asks for bank name');
  button('Bank account').click();await pause();
  host.querySelector('.profile-grid').requestSubmit();await pause();
  assert(commands.at(-1).fields.identity.profile.payment.method==='BANK','Wrong bank method');
  assert(commands.at(-1).fields.identity.profile.payment.identifier==='AE070331234567890123456','Bank IBAN not normalized');
  button('Aani').click();await pause();host.querySelector('.profile-grid').requestSubmit();await pause();
  assert(commands.at(-1).fields.identity.profile.payment.identifier==='+971501234567','Aani mobile not normalized');
  assert(!measureAudit().overflow,'Profile payments overflow');
  return {passed:['Home/Profile same balance','Home/Profile payment history','profile direct payment','Aani mobile label','separate bank and phone drafts','IBAN normalization','Aani mobile normalization'], ...measureAudit()};
}

export async function runSummaryAndAdminProfileAudit() {
  commands.length = 0;
  const account = { id: 'account', holder: 'Audit User', bank: 'Aani', method: 'AANI', identifier: '+971501234567', currency: 'AED', version: 1 };
  const receipt = (memberId, quantity, notes = '') => ({ memberId, name: memberId, lines: [{ description: 'Falafel', quantity, amount: quantity * 300, notes }], food: quantity * 300, delivery: 0, service: 0, tax: 0, discount: 0, total: quantity * 300, paid: 0, balance: quantity * 300, currency: 'AED' });
  await mountAudit('room', 'PLACED', { room: { account, restaurantPaid: true, members: [{ id: 'me', name: 'Audit User', approved: true, participating: true }, { id: 'other', name: 'Other', approved: true, participating: true }] }, receipts: [receipt('me', 1), receipt('other', 2), receipt('third', 1, 'No salad')] });
  const summary = host.querySelector('.order-summary');
  assert(host.querySelector('.room-main').firstElementChild === summary, 'Chosen person does not see the summary first');
  assert(summary.querySelectorAll('.summary-items li').length === 2, 'Identical items were not combined or notes were lost');
  assert(summary.querySelector('.summary-quantity').textContent === '3 ×', 'Combined quantity is incorrect');
  assert(summary.textContent.includes('No salad') && summary.textContent.includes('12.00'), 'Notes or full total missing');
  assert(!host.querySelector('.order-edit-options').open, 'Price editing clutters the summary');
  assert(!host.querySelector('.orders-by-person').open, 'Per-person detail clutters the summary');
  host.querySelector('.edit-receiving-details').open = true;
  const phone = host.querySelector('.edit-receiving-details input[type=tel]');
  setValue(phone, '0509876543'); await pause();
  host.querySelector('.edit-receiving-details form').requestSubmit(); await pause();
  assert(commands.length === 1 && commands[0].kind === 'SHARE_ACCOUNT', 'Payment edit is not one atomic room/profile command');
  assert(commands[0].fields.account.identifier === '+971509876543', 'Payment phone was not normalized');
  assert(!measureAudit().overflow, 'Summary overflows');
  const { AdminUsers } = await import('../src/foodrun/AdminApp.jsx');
  const mutations = [];
  root.render(<AdminUsers users={[{ id: 'target', name: 'Target User', phone: '+971501234567', profile: { userId: 'target', name: 'Target User', phone: '+971501234567', language: 'en', discoverable: true, photo: '', payment: account } }]} currentUserId="admin" rooms={[]} busy={false} mutate={async (path, body) => { mutations.push({ path, body }); return { ok: true }; }} />);
  await pause(); button('Edit user details').click(); await pause();
  const form = host.querySelector('.admin-profile-editor');
  const input = label => [...form.querySelectorAll('label')].find(value => value.textContent === t(label)).querySelector('input');
  setValue(input('Profile name'), 'Updated User'); setValue(input('UAE mobile number'), '0505555555'); await pause();
  setValue(form.querySelector('select'), 'ar'); input('Let people on this hub invite me').click(); await pause();
  button('Bank account').click(); await pause();
  setValue(input('Bank name'), 'Test Bank'); setValue(input('UAE IBAN'), 'ae07 0331 2345 6789 0123 456'); await pause();
  form.requestSubmit(); await pause();
  const change = mutations.at(-1)?.body;
  assert(change?.action === 'profile' && change.userId === 'target', 'Admin edit did not target the selected profile');
  assert(change.profile.name === 'Updated User' && change.profile.phone === '+971505555555', 'Admin contact changes missing');
  assert(change.profile.language === 'ar' && change.profile.discoverable === false, 'Admin profile settings missing');
  assert(change.profile.payment.identifier === 'AE070331234567890123456', 'Admin IBAN not saved');
  assert(!measureAudit().overflow, 'Admin editor overflows');
  return { passed: ['summary first', 'combined quantities', 'separate preparation notes', 'total visible', 'secondary price and person details', 'atomic payment update', 'admin contact and profile settings', 'admin IBAN'], ...measureAudit() };
}

export async function runPaymentRoomAudit() {
  commands.length = 0;
  const account = { id: 'account', holder: 'Audit User', bank: 'Aani', identifier: '+971500000001', method: 'aani', currency: 'AED' };
  const fixture = { ...data, home: { ...data.home, profile: { ...data.home.profile, payment: account }, people: [{ userId: 'friend', name: 'Friend' }] } };
  await mountAudit('payment-create', 'LOBBY', { data: fixture });
  const input = label => [...host.querySelectorAll('label')].find(node => node.textContent.startsWith(t(label)))?.querySelector('input,textarea');
  for (const [label, value] of [['Room name','Friday lunch'],['What did you order?','Sandwiches and drinks'],['Receipt total','30.01']]) {
    setValue(input(label), value); await pause();
  }
  host.querySelector('input[type=checkbox]').click(); await pause();
  button('Split equally').click(); await pause();
  const canvas = document.createElement('canvas'); canvas.width = 600; canvas.height = 1200;
  const ctx = canvas.getContext('2d'); ctx.fillStyle = 'white'; ctx.fillRect(0,0,600,1200); ctx.fillStyle = 'black'; ctx.fillText('Audit receipt 30.01',20,30);
  const blob = await new Promise(resolve => canvas.toBlob(resolve, 'image/png'));
  const transfer = new DataTransfer(); transfer.items.add(new File([blob], 'receipt.png', { type: 'image/png' }));
  const upload = host.querySelector('input[type=file]'); upload.files = transfer.files; upload.dispatchEvent(new Event('change', { bubbles: true }));
  await new Promise(resolve => setTimeout(resolve, 300));
  assert(host.querySelector('.receipt-photo'), 'Receipt image not previewed');
  host.querySelector('form').requestSubmit(); await pause();
  assert(commands.length === 1 && commands[0].kind === 'CREATE_PAYMENT_ROOM', 'Payment room did not create directly');
  const payload = commands[0].fields;
  assert(payload.paymentRoom.shares.reduce((sum,s) => sum+s.amount,0) === 3001, 'Equal split lost a minor unit');
  assert(payload.paymentRoom.details.receiptPhoto.startsWith('data:image/jpeg;'), 'Receipt photo missing');
  assert(!measureAudit().overflow, 'Payment creation overflows');
  const room = { paymentRoom: payload.paymentRoom.details, restaurantPaid: true, account,
    members: [{ id:'me', name:'Payer', approved:true, participating:true },{ id:'friend', name:'Friend', approved:true, participating:true }],
    carts: [{ memberId:'me', submitted:true, lines:[] },{ memberId:'friend', submitted:true, lines:[] }] };
  const receipts = [{ memberId:'me', name:'Payer', food:0,total:0,paid:0,balance:0,currency:'AED',lines:[] },
    { memberId:'friend', name:'Friend',food:3001,total:3001,paid:500,balance:2501,currency:'AED',lines:[{ description:'Meal',quantity:1,amount:3001 }] }];
  await mountAudit('room','FULFILLED',{ room, receipts });
  assert(button('Edit share') && host.innerText.includes(t('Record payment received')), 'Payer cannot manage shares or payments');
  assert(!host.querySelector('.restaurant-order-card') && !host.querySelector('.room-invite-card'), 'Payment room exposes ordering flow');
  assert(!measureAudit().overflow, 'Payment wallet overflows');
  await mountAudit('room','FULFILLED',{ room, receipts:[receipts[1]], memberId:'friend' });
  assert(!button('Edit share') && !host.innerText.includes(t('Record payment received')), 'Member received payer controls');
  assert(!button('Approve revised final bill'), 'Member must approve the bill again');
  return { passed:['direct creation','user selection','exact equal split','portrait receipt photo','payer share and payment controls','member-only wallet','no ordering steps'], ...measureAudit() };
}
