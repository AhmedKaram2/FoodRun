import bundledRestaurants from '../src/foodrun/builtInRestaurants.json';
import FriendGroups from '../src/foodrun/FriendGroups.jsx';
import { AdminUsers } from '../src/foodrun/AdminApp.jsx';
import { mealRoomName } from '../src/foodrun/smartDefaults.js';
// Browser-only fixture harness. Run with Vite in an isolated browser profile:
// await (await import('/test/browserAudit.jsx')).runCreateAudit()
// It captures commands locally; no account, restaurant order, or payment is sent.
import React from 'react';
import { createRoot } from 'react-dom/client';
import { FoodRunClient, CreateRoom, Home, ProfileScreen, RoomScreen, loadRestaurants, storeRestaurants, normalizeRestaurant } from '../src/foodrun/FoodRunApp.jsx';
import RestaurantLibraryScreen from '../src/foodrun/RestaurantLibraryScreen.jsx';
import { t, getLanguage } from '../src/foodrun/i18n.js';
import { NotificationCenter, NotificationActionCard } from '../src/foodrun/NotificationCenter.jsx';
import { CreatePaymentRoom } from '../src/foodrun/PaymentRoom.jsx';
import WalletFunds, { WalletPaymentOption } from '../src/foodrun/WalletFunds.jsx';
import WalletCustody from '../src/foodrun/WalletCustody.jsx';

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
  const savedRestaurant = loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba') || loadRestaurants()[0] || normalizeRestaurant(bundledRestaurants[0]);
  if(!loadRestaurants().some(value=>value.id===savedRestaurant.id)) storeRestaurants([...loadRestaurants(),savedRestaurant]);
  const restaurant = { ...savedRestaurant, contact: { ...savedRestaurant.contact, ...(options.contact || {}) } };
  const room = { id: 'audit-room', name: 'Audit room', code: '123456', ownerId: 'me', payerId: 'me', restaurant, phase, orderNumber: 1,
    members: [{ id: 'me', name: 'Audit User', approved: true, participating: true, eligible: true, ready: true }],
    carts: [], transfers: [], audit: [], expectedNames: [], restaurantOptions: [restaurant], restaurantVotes: [], restaurantPollOpen: false,
    fees: { delivery: 0, service: 0, discount: 0 }, adjustment: 0, billRevision: 1, billApprovals: [], quoteRevision: 1, revision: 1,
    restaurantPaid: false, deliveryMode: false, destination: '', account: null, preparationId: '', preparedIds: [], pastSpins: [], spin: null,
  };
  Object.assign(room, options.room || {});
  const roomData = { ...data, ...(options.baseData || {}), sessions: { [room.id]: { roomId: room.id, roomName: room.name, memberId: options.memberId || 'me' } },
    rooms: { [room.id]: { room, receipts: options.receipts || [], progress: options.progress, history: options.history || [], serverTime: Date.now() } }, online: { [room.id]: true } };
  const props = { data: options.data || (screen === 'room' || options.room ? roomData : data), onBack: () => {}, openRoom: () => {}, setPage: () => {} };
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
  const auditNode = screen === 'payment-create' ? <CreatePaymentRoom {...props} openProfile={() => {}} /> : screen === 'library' ? <RestaurantLibraryScreen language={document.documentElement.lang === 'ar' ? 'ar' : 'en'} {...props} />
    : screen === 'home' ? <Home {...props} /> : screen === 'profile' ? <ProfileScreen {...props} />
    : screen === 'room' ? <RoomScreen {...props} roomId={room.id} /> : <CreateRoom {...props} mode={screen === 'join' ? 'join' : 'create'} />;
  root._auditComponent = {key:screen,node:auditNode}; root.render(auditNode);
  await pause(); return measureAudit();
}
export function measureAudit() {
  return { width: innerWidth, screenWidth: document.documentElement.scrollWidth,
    overflow: document.documentElement.scrollWidth > innerWidth + 1,
    text: host.innerText.slice(0, 500), inputs: [...host.querySelectorAll('input, select')].filter(x => x.getClientRects().length).map(x => ({ label: x.closest('label')?.textContent, fontSize: getComputedStyle(x).fontSize })) };
}
export async function runWheelProtectionAudit() {
  commands.length = 0;
  const members = ['me', 'friend', 'other'].map(id => ({ id, name: id, approved: true, participating: true, eligible: true }));
  const base = { ownerId: 'friend', payerId: null, members };
  const request = { id: 'wheel-request', memberId: 'me', recipientId: 'friend', orderNumber: 1, plan: 'HALF_CHANCE', amount: 500, currency: 'AED', status: 'REQUESTED' };
  const options = () => host.querySelector('.wheel-protection');
  const click = async (label, card = options()) => { const node = [...card.querySelectorAll('button')].find(node => node.textContent === t(label)); assert(node && !node.disabled, `Missing action: ${label}`); node.click(); await pause(); };
  const expectCommand = (kind, fields) => { const actual = commands.at(-1); assert(actual?.kind === kind && Object.entries(fields).every(([key, value]) => actual.fields[key] === value), `Wrong command: ${JSON.stringify(actual)}`); };
  await mountAudit('room', 'LOBBY', { room: base });
  assert(!options(), 'Retired paid wheel options are visible in a new order');
  await mountAudit('room', 'LOBBY', { room: { ...base, wheelProtections: [request] } });
  assert(!options().querySelector('form'), 'Unapproved request asks for payment');
  assert(!options().textContent.includes(t('Approve request')), 'Member can approve their own request');
  await mountAudit('room', 'LOBBY', { memberId: 'friend', room: { ...base, wheelProtections: [request] } });
  await click('Approve request'); expectCommand('REVIEW_WHEEL_PROTECTION', { transferId: request.id, flag: true });
  await mountAudit('room', 'LOBBY', { room: { ...base, wheelProtections: [{ ...request, status: 'AWAITING_PAYMENT' }] } });
  const input = options().querySelector('input'); setValue(input, 'Cash paid'); await pause();
  options().querySelector('form').requestSubmit(); await pause();
  expectCommand('DECLARE_WHEEL_PAYMENT', { transferId: request.id, amount: 500, text: 'Cash paid' });
  await mountAudit('room', 'LOBBY', { memberId: 'friend', room: { ...base, wheelProtections: [{ ...request, status: 'PAYMENT_DECLARED' }] } });
  assert(button('Spin to choose the payer').disabled, 'Owner can spin before confirming payment');
  await click('Confirm received & activate'); expectCommand('CONFIRM_WHEEL_PAYMENT', { transferId: request.id, amount: 500, flag: true });
  await mountAudit('room', 'LOBBY', { memberId: 'friend', room: { ...base, wheelProtections: [{ ...request, status: 'ACTIVE' }] } });
  assert(button('Choose person directly').disabled, 'Owner can bypass paid half chance through direct selection');
  assert(!button('Spin to choose the payer').disabled, 'Owner cannot spin after payment confirmation');
  await mountAudit('room', 'LOBBY', { memberId: 'friend', room: { ...base, wheelProtections: [{ ...request, plan: 'EXCLUDE', amount: 1000, status: 'ACTIVE' }] } });
  button('Choose person directly').click(); await pause();
  const choice = [...host.querySelectorAll('select')].find(node => node.closest('label')?.textContent.includes(t('Ordering person')));
  assert(choice && ![...choice.options].some(option => option.value === 'me'), 'Excluded member remains in direct selection');
  await mountAudit('room', 'LOBBY', { room: { ...base, wheelProtections: [{ ...request, status: 'AWAITING_PAYMENT' }] } });
  assert(!measureAudit().overflow, 'Wheel payment form overflows');
  return { passed: ['paid choices removed', 'approval before payment', 'owner approval', 'exact AED 5 payment declaration', 'owner receipt confirmation', 'pending payment blocks spin', 'half chance blocks direct selection', 'exclusion blocks direct selection'], ...measureAudit() };
}
export async function runAutoArchivePaymentAudit() {
  commands.length = 0;
  const members = ['me', 'friend'].map(id => ({ id, name: id, approved: true, participating: true, eligible: true }));
  const room = { ownerId: 'me', payerId: 'me', members, restaurantPaid: true, autoArchivedAt: Date.now(), autoArchiveFrom: 'FULFILLED', paymentsPending: true };
  const receipts = [{ memberId: 'me', name: 'me', food: 0, total: 0, paid: 0, balance: 0, currency: 'AED', totalText: 'AED 0.00', lines: [] },
    { memberId: 'friend', name: 'friend', food: 1200, total: 1200, paid: 0, balance: 1200, currency: 'AED', totalText: 'AED 12.00', lines: [] }];
  await mountAudit('room', 'ARCHIVED', { room, receipts, progress: { canArchive: false } });
  assert(host.textContent.includes(t('Archived after 24 hours')), 'Automatic archive explanation missing');
  assert(host.querySelector('.record-payment[open]'), 'Archived unpaid bill cannot be recorded');
  assert(button('Send payment reminder'), 'Archived unpaid bill cannot be reminded');
  assert(button('Start next order').disabled, 'Unsettled archived bill can be replaced');
  assert(!button('Update final bill') && !button('Edit share'), 'Archived bill has editing controls');
  host.querySelector('.record-payment form').requestSubmit(); await pause();
  assert(commands.at(-1)?.kind === 'RECORD_PAYMENT' && commands.at(-1).fields.amount === 1200, 'Archived payment records wrong amount');
  await mountAudit('room', 'ARCHIVED', { room, memberId: 'friend', receipts: [receipts[1]], progress: { canArchive: false } });
  const pay = button('Mark paid'); assert(pay && !pay.disabled, 'Member cannot pay archived balance');
  const form = pay.closest('form'); form.requestSubmit(); await pause();
  assert(commands.at(-1)?.kind === 'DECLARE_TRANSFER' && commands.at(-1).fields.amount === 1200, 'Archived member payment records wrong amount');
  await mountAudit('room', 'ARCHIVED', { room: { ...room, transfers: [{ id: 'claim', memberId: 'friend', amount: 1200, status: 'DECLARED', reference: 'Cash' }] }, receipts, progress: { canArchive: false } });
  const confirm = button('Confirm received'); assert(confirm && !confirm.disabled, 'Archived claim cannot be confirmed');
  confirm.click(); await pause(); assert(commands.at(-1).kind === 'CONFIRM_TRANSFER', 'Archived claim sent wrong confirmation command');
  assert(!measureAudit().overflow, 'Archived settlement layout overflows');
  return { passed: ['archive explanation', 'record received', 'payment reminder retained', 'unsettled next order blocked', 'bill editing closed', 'member payment retained', 'pending receipt confirmation'], ...measureAudit() };
}
function setValue(node, value) {
  Object.getOwnPropertyDescriptor(node instanceof HTMLSelectElement ? HTMLSelectElement.prototype : node instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set.call(node, value);
  node.dispatchEvent(new Event(node instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}
const button = text => [...host.querySelectorAll('button')].find(node => node.textContent === t(text));
export async function runOnboardingAudit() {
  commands.length = 0;
  const original = location.href;
  const fixture = { ...data, authReady: true, home: { ...data.home, profile: { name: 'Google User', phone: '', favoriteOrders: [] } },
    user: { uid: 'new-google-user', displayName: 'Google User' }, setError: () => {}, connect: () => {}, clearOfflineReceipts: () => {}, dismissFeedback: () => {} };
  let current = fixture;
  const useFixture = () => current;
  const mount = async () => {
    root?.unmount(); host?.remove(); document.getElementById('root').style.display = 'none';
    host = document.createElement('div'); document.body.append(host); root = createRoot(host);
    root.render(<FoodRunClient useData={useFixture} />); await pause();
  };
  try {
    history.replaceState({}, '', '/'); await mount();
    assert(host.querySelector('.home-banner'), 'Google user with no phone was forced into Profile');
    current = { ...fixture, sessions: { old: { roomId: 'old', roomName: 'Saved room', memberId: 'me' } } };
    localStorage.setItem('foodrun-active-room-v1', JSON.stringify({ userId: fixture.user.uid, hub: fixture.hub, roomId: 'old' }));
    await mount(); assert(host.querySelector('.home-banner'), 'A previously open room replaced the default Home screen');
    current = fixture; localStorage.removeItem('foodrun-active-room-v1'); await mount();
    button('Profile').click(); await pause();
    assert(host.querySelector('.profile-grid'), 'Profile could not be opened');
    assert(host.querySelector('.phone-country select')?.options.length > 200, 'Profile country selector is missing');
    setValue(host.querySelector('.phone-country select'), 'EG'); await pause();
    setValue(host.querySelector('.phone-national input'), '٠١٠١٢٣٤٥٦٧٨'); await pause();
    button('Save profile').click(); await pause();
    assert(commands.at(-1).fields.identity.profile.phone === '+201012345678', 'Profile national number did not use the selected country');
    assert(host.querySelector('.home-banner'), 'Saving Profile did not return to Home');
    button('Profile').click(); await pause();
    host.querySelector('.wordmark').click(); await pause();
    assert(host.querySelector('.home-banner'), 'Logo left an incomplete-profile user stuck');
    button('Profile').click(); await pause();
    current = { ...fixture, user: { uid: 'another-user', displayName: 'Another user' } };
    root.render(<FoodRunClient useData={useFixture} />); await pause(); await pause();
    assert(host.querySelector('.home-banner'), 'Another account inherited Profile as its initial screen');
    current = fixture;
    history.replaceState({}, '', '/?room=123456'); await mount();
    assert(host.querySelector('h1')?.textContent === t('Join your people'), 'Invitation did not preserve its join screen');
    const join = button('Request to join'); assert(join && !join.disabled, 'Missing contact phone still blocks room joining');
    join.click(); await pause();
    assert(commands.at(-1).kind === 'JOIN' && commands.at(-1).fields.code === '123456', 'Invitation code was lost');
    history.replaceState({}, '', '/'); await mount();
    button('Profile').click(); await pause();
    host.querySelector('#receiving-details').open = true;
    assert(host.querySelector('.payment-method-form .phone-country select')?.value === 'AE', 'Aani country code is not explicit');
    setValue(host.querySelector('.payment-method-form .phone-national input'), '٠٥٠١٢٣٤٥٦٧'); await pause();
    button('Save payment details').click(); await pause();
    assert(commands.at(-1).fields.identity.profile.payment.identifier === '+971501234567', 'Arabic Aani mobile number was rejected or corrupted');
    assert(commands.at(-1).fields.identity.profile.phone === '', 'Saving Aani invented a separate contact phone');
    const measured = measureAudit(); assert(!measured.overflow, 'Country picker overflows on mobile');
    return { passed: ['Home after Google sign-in', 'Profile save returns Home', 'logo Home navigation', 'account change resets Home', 'invitation code preserved', 'join without contact phone', 'country selector', 'Arabic Aani number'], language: getLanguage(), ...measured };
  } finally { history.replaceState({}, '', original); }
}
export async function runHalfItemAudit() {
  commands.length = 0;
  const restaurant = { ...(loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba') || normalizeRestaurant(bundledRestaurants[0])), openOrdering: true };
  const members = [{ id: 'me', name: 'Requester', approved: true, participating: true, eligible: true },
    { id: 'friend', name: 'Recipient', approved: true, participating: true, eligible: true }];
  const line = { id: 'sandwich', itemId: '', quantity: 1, description: 'Falafel sandwich / سندويش طعمية', notes: 'No onions', unitPrice: 501, optionIds: [], variantId: null };
  const cart = { memberId: 'me', revision: 1, lines: [line], submitted: true };
  const base = { restaurant, members, carts: [cart], halfItemOffers: [] };
  await mountAudit('room', 'COLLECTING', { room: base });
  host.querySelectorAll('details').forEach(node => { node.open = true; });
  { const myOrderTab = [...host.querySelectorAll('[data-mobile-target]')].find(node => node.dataset.mobileTarget === 'order');
  if(myOrderTab) { myOrderTab.click(); await pause(); } }
  assert(button('Half'), 'Half button is not visible'); button('Half').click(); await pause();
  assert(commands.at(-1)?.kind === 'REQUEST_HALF_ITEM' && commands.at(-1).fields.text === line.id, 'Half button did not request the selected item');
  const offer = { id: 'half-offer', memberId: 'me', lineId: line.id, line: { ...line, amount: 501 }, acceptedById: null };
  await mountAudit('room', 'COLLECTING', { memberId: 'friend', room: { ...base, halfItemOffers: [offer] } });
  assert(host.querySelector('.half-item-offers')?.textContent.includes(t('Another half is available')), 'Other member did not see the offer');
  assert(button('Take the other half'), 'Acceptance button is not visible'); button('Take the other half').click(); await pause();
  assert(commands.at(-1)?.kind === 'ACCEPT_HALF_ITEM' && commands.at(-1).fields.text === offer.id, 'Acceptance did not identify the offer');
  const accepted = { ...offer, acceptedById: 'friend' };
  const receipt = (id, amount, quantity) => ({ memberId: id, name: id, currency: 'AED', lines: [{ ...offer.line, amount, halfShare: true, restaurantQuantity: quantity }],
    food: amount, delivery: 0, service: 0, discount: 0, tax: 0, total: amount, totalText: `AED ${(amount / 100).toFixed(2)}`, paid: 0, balance: amount, balanceText: `AED ${(amount / 100).toFixed(2)}` });
  const receipts = [receipt('me', 251, 1), receipt('friend', 250, 0)];
  await mountAudit('room', 'COLLECTING', { room: { ...base, halfItemOffers: [accepted] }, receipts });
  host.querySelectorAll('details').forEach(node => { node.open = true; });
  { const myOrderTab = [...host.querySelectorAll('[data-mobile-target]')].find(node => node.dataset.mobileTarget === 'order');
  if(myOrderTab) { myOrderTab.click(); await pause(); } }
  assert(!host.querySelector('.half-item-offers'), 'Accepted half offer remains at the top for the requester');
  assert(host.querySelector('.cart-review')?.textContent.includes('½ ×'), 'Requester cart did not display a half');
  assert(host.querySelector('.cart-review')?.textContent.includes('AED 2.51'), 'Requester cart did not show its split food cost');
  const summary = host.querySelector('.summary-items');
  assert(summary?.textContent.includes('1 ×') && !summary?.textContent.includes('2 ×'), 'Restaurant summary duplicated the sandwich');
  await mountAudit('room', 'COLLECTING', { memberId: 'friend', room: { ...base, halfItemOffers: [accepted] }, receipts: [receipts[1]] });
  assert(!host.querySelector('.half-item-offers'), 'Accepted half offer remains at the top for the recipient');
  assert(host.querySelector('.cart-review')?.textContent.includes('½ ×') && host.querySelector('.cart-review')?.textContent.includes('AED 2.50'), 'Recipient cart did not show its half and cost');
  assert(button('Release my half'), 'Recipient cannot release its half');
  assert(host.querySelector('.cart-review').contains(button('Release my half')), 'Half release is not inside the recipient order');
  const measured = measureAudit(); assert(!measured.overflow, 'Half sharing causes horizontal overflow');
  await mountAudit('room', 'PLACED', { room: { ...base, halfItemOffers: [offer] } });
  assert(host.querySelector('.half-item-offers')?.textContent.includes(t('Full item assigned to requester')), 'Unclaimed item does not explain the whole-item fallback');
  assert(!button('Take the other half'), 'Closed ordering still offers acceptance');
  return { passed: ['Half control', 'attention for another member', 'accept command', 'half each', 'exact split cost', 'one restaurant item', 'release control', 'full-item fallback'], language: getLanguage(), ...measured };
}
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
  assert(before('.collected-orders', '.restaurant-order-card') && before('.restaurant-order-card', '.fees-editor'), 'Summary and next action must precede secondary editors');
  host.querySelector('.order-pricing-panel').closest('details').open = true;
  host.querySelector('.fees-editor').closest('details').open = true;
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
    host.querySelector('.order-pricing-panel').closest('details').open = true;
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
  assert([...host.querySelectorAll('input')].some(input => input.value === mealRoomName()), 'Meal and date room default missing');
  const selection = [...host.querySelectorAll('select')].find(node => [...node.options].some(option => option.value === 'names'));
  assert(selection?.value === 'wheel', 'Current wheel must remain default');
  setValue(selection, 'names'); await pause();
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
  assert(commands[0].fields.selectionStyle === 'names', 'Selected animation was not sent');
  assert(JSON.stringify(commands[0].fields.restaurants.map(r => r.id)) === JSON.stringify(ids), 'Unselected restaurants entered the poll');
  assert(commands[0].fields.restaurant.id === ids[0], 'Initial choice is outside poll');
  host.querySelector('.poll-chip').click(); await pause();
  host.querySelector('.poll-chip').click(); await pause();
  assert(host.querySelector('.create-submit button').disabled, 'A one-restaurant poll is enabled');
  button('Use this restaurant').click(); await pause();
  assert(!host.querySelector('.create-submit button').disabled, 'Direct ordering remains blocked');
  return { passed: ['mobile layout', 'no forced keyboard', 'Sharjah filtering', 'multi-selection', 'selected summary', 'exact submitted candidates', 'minimum two choices', 'direct order toggle'], ...measureAudit() };
}
export async function runSharedMenuSaveAudit() {
  const restaurant = { ...normalizeRestaurant(bundledRestaurants[0]), id: 'audit-shared-menu', name: 'Audit shared menu', nameAr: 'قائمة اختبار مشتركة' };
  const oldIds = localStorage.getItem('foodrun-server-catalog-v1');
  storeRestaurants([...loadRestaurants().filter(value=>value.id!==restaurant.id), restaurant]);
  localStorage.setItem('foodrun-server-catalog-v1', JSON.stringify([restaurant.id]));
  let failed = false;
  const fixture = { ...data, identityToken: 'fixture-identity', home: { ...data.home, restaurants: [restaurant] }, send: async (kind, fields) => {
    commands.push({ kind, fields }); if(failed) return null;
    return { ok: true, home: { ...data.home, restaurants: [{ ...restaurant, menu: { ...restaurant.menu, items: [...restaurant.menu.items, ...fields.restaurant.menu.items] } }] } };
  } };
  try {
    await mountAudit('library', 'LOBBY', { data: fixture });
    setValue(host.querySelector('.library-list input[type=search]'), restaurant.name); await pause();
    host.querySelector('.restaurant-row').click(); await pause();
    const inputs = () => host.querySelectorAll('.quick-menu-add input');
    setValue(inputs()[0], 'Saved breakfast item'); setValue(inputs()[2], '7.50'); await pause();
    button('Add & save item').click(); await pause(); await pause();
    assert(commands.at(-1).kind === 'ADD_MENU_ITEMS', 'Quick Add did not persist to the shared catalog');
    assert(commands.at(-1).fields.restaurant.menu.items.length === 1, 'Append must send only the new item');
    assert(commands.at(-1).fields.restaurant.menu.items[0].basePriceMinor === 750, 'Quick Add price was incorrect');
    assert(loadRestaurants().find(r=>r.id===restaurant.id).menu.items.some(item=>item.name==='Saved breakfast item'), 'Saved item disappeared from the library');
    assert(inputs()[0].value === '' && inputs()[2].value === '', 'Successful save did not reset Quick Add');
    failed = true; setValue(inputs()[0], 'Keep failed draft'); setValue(inputs()[2], '4.50'); await pause();
    button('Add & save item').click(); await pause();
    assert(inputs()[0].value === 'Keep failed draft' && inputs()[2].value === '4.50', 'A failed save lost the item draft');
    assert(!loadRestaurants().find(r=>r.id===restaurant.id).menu.items.some(item=>item.name==='Keep failed draft'), 'Failed save was shown as persisted');
    const measured = measureAudit(); assert(!measured.overflow, 'Quick Add overflows on mobile');
    return { passed: ['one-step shared save', 'minor-unit price', 'catalog persistence', 'failure preserves draft'], language: getLanguage(), ...measured };
  } finally { if(oldIds == null) localStorage.removeItem('foodrun-server-catalog-v1'); else localStorage.setItem('foodrun-server-catalog-v1',oldIds); }
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
  const sharedOrder = whatsapp.searchParams.get('text');
  assert(sharedOrder.includes('Total sandwiches: 2'), 'Shared message is missing the total sandwich count');
  assert(!sharedOrder.includes('30 minutes') && !sharedOrder.includes('Expected delivery'), 'Expected delivery leaked into shared message');
  assert(host.querySelector('.restaurant-phone').textContent.includes('+971'), 'Restaurant phone hidden');
  assert(button('Copy phone number'), 'Phone copy action missing');
  button('Order sent').click(); await pause();
  assert(commands.at(-1).kind === 'PLACE' && commands.at(-1).fields.text === '30 minutes', 'Direct placement missing');
  assert(!button('Confirm my total and recipient'), 'Redundant total confirmation remains');
  assert(!measureAudit().overflow, 'Restaurant order overflows');
  await mountAudit('room', 'PLACED', { room: { ...room, restaurantPaid: false }, contact: { whatsappE164: '+971501234567' }, receipts: [receipt('me'), receipt('other')] });
  const restaurantActions = host.querySelector('#restaurant-actions');
  assert(restaurantActions && restaurantActions.textContent.includes(t('Share via WhatsApp')) && restaurantActions.textContent.includes(t('Mark restaurant paid')), 'Selected payer restaurant actions are separated');
  assert(!host.querySelector('#room-payment').textContent.includes(t('Mark restaurant paid')), 'Restaurant payment action is duplicated in the wallet');
  await mountAudit('room', 'PLACED', { room: { ...room, restaurantPaid: true }, contact: { whatsappE164: '+971501234567' }, receipts: [receipt('me'), receipt('other')] });
  assert(host.querySelector('#restaurant-actions').textContent.includes(t('Food collected / delivered')), 'Next restaurant action is not kept in the same card');
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
  return { passed: ['known person without wheel', 'restaurant phone and copy', 'correct WhatsApp recipient', 'optional ETA label', 'blank and whitespace ETA submission', 'compact restaurant message', 'direct placement', 'grouped payer restaurant actions', 'wallet mark paid', 'top payment confirmation', 'visible receiving details', 'optional breakdown'], ...measureAudit() };
}

export async function runCopyLanguageAudit() {
  commands.length = 0;
  const websiteLanguage = document.documentElement.lang;
  const restaurant = loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba');
  const item = restaurant.menu.items.find(value => value.available && value.nameAr);
  const account = { id: 'saved', holder: 'Chosen', bank: 'Aani', method: 'AANI', identifier: '+971501234567', currency: 'AED' };
  const receipts = [{ memberId: 'me', name: 'Chosen', lines: [{ itemId: item.id, description: item.name, quantity: 2, amount: 600, notes: 'No salt' }], total: 600, paid: 0, balance: 600, currency: 'AED' }];
  const options = { room: { account }, receipts, progress: { accountShared: true, canReview: true, reviewBlocker: '' } };
  let copied = '';
  const oldClipboard = Object.getOwnPropertyDescriptor(navigator, 'clipboard');
  Object.defineProperty(navigator, 'clipboard', { configurable: true, value: { writeText: async text => { copied = text; } } });
  try {
    await mountAudit('room', 'COLLECTING', options);
    assert(!host.querySelector('.payer-account-editor'), 'Saved receiving details still require setup');
    assert(!button('Order sent').disabled, 'Saved receiving details block placing the order');
    for (const language of ['ar', 'en']) {
      setValue(host.querySelector('.copy-language select'), language); await pause();
      const preview = host.querySelector('.restaurant-order-card pre');
      assert(preview.textContent.includes(language === 'ar' ? item.nameAr : item.name), 'Wrong copied item language');
      assert(preview.dir === (language === 'ar' ? 'rtl' : 'ltr'), 'Copy preview direction is wrong');
      host.querySelector('.restaurant-order-card .hero-actions button').click(); await pause();
      assert(copied === preview.textContent && copied.includes('No salt'), 'Clipboard differs from preview or loses notes');
      assert(new URL(host.querySelector('.restaurant-order-card a[href^="https://wa.me/"]').href).searchParams.get('text') === copied, 'WhatsApp uses another language');
      assert(document.documentElement.lang === websiteLanguage, 'Copy language changed the website language');
    }
    await mountAudit('room', 'PLACED', options);
    assert(host.querySelector('.copy-language select').value === 'en', 'Copy language choice was not remembered');
    assert(commands.length === 0, 'Copying modified the order or asked to share payment details again');
    assert(!measureAudit().overflow, 'Copy controls overflow');
    return { passed: ['independent copy language', 'clipboard and WhatsApp match preview', 'notes preserved', 'language remembered', 'saved payment needs no confirmation'], ...measureAudit() };
  } finally {
    if (oldClipboard) Object.defineProperty(navigator, 'clipboard', oldClipboard); else delete navigator.clipboard;
  }
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
  await mount(<AdminCleanup request={async (path,body)=>{requests.push({path,body});return path.endsWith('preview')?{scope:body.scope,olderThanDays:body.olderThanDays,count:1,previewToken:'preview-1',targets:[{id:'room',name:'Old room',orderNumber:1,restaurant:'Kitchen'}]}:{removedCount:1};}} onChanged={async()=>{}} />);
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

export async function runAdminDateRangeAudit() {
  const { AdminCleanup } = await import('../src/foodrun/AdminApp.jsx');
  document.getElementById('root').style.display = 'none';
  root?.unmount(); host?.remove(); host = document.createElement('div'); host.id = 'audit-root'; host.className = 'admin-shell'; document.body.append(host); root = createRoot(host);
  const requests = []; let changed = 0, legacy = false;
  root.render(<div className="admin-content"><AdminCleanup initialFilter="date" roomsOnly onChanged={async () => { changed++; }} request={async (path, body) => {
    requests.push({ path, body });
    if (path.endsWith('delete')) return { removedCount: 1 };
    return { scope: body.scope, olderThanDays: body.olderThanDays, ...(legacy ? {} : { fromDate: body.fromDate, toDate: body.toDate, timeZone: body.timeZone }), count: 1, previewToken: 'sample-date-preview', targets: [{ id: 'sample-room', name: 'Lunch with friends', restaurant: 'Sample Kitchen', orderNumber: 1, phase: 'CANCELLED', updatedAt: new Date('2026-10-02T12:00:00Z').getTime() }] };
  }} /></div>);
  await pause();
  assert(button('Preview cleanup').disabled, 'Blank dates allow preview');
  const dates = () => host.querySelectorAll('input[type=date]');
  setValue(dates()[0], '2026-10-01'); setValue(dates()[1], '2026-10-02'); await pause();
  button('Preview cleanup').click(); await pause();
  assert(requests.at(-1).body.fromDate === '2026-10-01' && requests.at(-1).body.toDate === '2026-10-02' && requests.at(-1).body.olderThanDays === 0 && requests.at(-1).body.timeZone, 'Dates/timezone were lost or age also applied');
  assert(button('Delete selected data').disabled, 'Preview allows deletion without confirmation');
  setValue([...host.querySelectorAll('input')].find(input => input.type === 'text'), 'DELETE'); await pause();
  setValue(dates()[1], '2026-10-03'); await pause();
  assert(![...host.querySelectorAll('button')].some(value => value.textContent === t('Delete selected data')), 'Changing dates retained the old deletion preview');
  button('Preview cleanup').click(); await pause();
  assert([...host.querySelectorAll('input')].find(input => input.type === 'text').value === '', 'Confirmation was retained after changing dates');
  setValue([...host.querySelectorAll('input')].find(input => input.type === 'text'), 'DELETE'); await pause();
  button('Delete selected data').click(); await pause();
  assert(requests.at(-1).body.toDate === '2026-10-03' && requests.at(-1).body.previewToken === 'sample-date-preview' && requests.at(-1).body.confirmation === 'DELETE', 'Delete did not use the reviewed range');
  assert(changed === 1 && host.textContent.includes(t('records removed')), 'Deletion did not refresh the admin dashboard');
  setValue(dates()[0], '2026-10-04'); await pause();
  assert(button('Preview cleanup').disabled, 'Reversed range allows preview');
  setValue(dates()[0], '2026-10-01'); await pause(); legacy = true;
  button('Preview cleanup').click(); await pause();
  assert(![...host.querySelectorAll('button')].some(value => value.textContent === t('Delete selected data')), 'An older server ignoring the dates enabled deletion');
  assert(host.textContent.includes(t('The server could not verify this date range. Refresh and preview again.')), 'Unsupported range did not show an explanation');
  legacy = false; button('Preview cleanup').click(); await pause();
  assert(!measureAudit().overflow, 'Date range controls overflow');
  return { passed: ['date fields', 'inclusive range payload and timezone', 'preview and confirmation', 'changed range invalidation', 'dashboard refresh', 'reversed date rejection', 'legacy server rejection'], ...measureAudit() };
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

export async function runNotificationAudit() {
  const now = Date.now();
  const item = { id: 'a'.repeat(40), roomId: 'audit-room', orderNumber: 1, kind: 'payment_sent', title: 'Payment sent', body: 'A member marked a payment sent.', transferId: 'transfer', createdAt: now, read: false, actions: [{ id: 'confirm', title: 'Review payment' }] };
  const fixture = { ...data, online: { 'audit-room': true }, sessions: { 'audit-room': { memberId: 'me' } }, rooms: { 'audit-room': { room: { id: 'audit-room', orderNumber: 1, payerId: 'me', phase: 'FULFILLED', restaurantPaid: true, restaurant: { currency: 'AED' }, members: [{ id: 'member', name: 'Test member' }], transfers: [{ id: 'transfer', memberId: 'member', amount: 400, reference: 'Test transfer', status: 'declared', refund: false }] }, receipts: [] } } };
  commands.length = 0;
  await mountAudit('home');
  let closed = false;
  root.render(<NotificationActionCard selected={{ item, action: 'confirm' }} data={fixture} onClose={() => { closed = true; }} />); await pause();
  assert(commands.length === 0, 'Opening a notification mutated a payment');
  assert(host.textContent.includes('4.00') && host.textContent.includes('Test member'), 'Payment review details missing');
  const confirm = [...host.querySelectorAll('button')].find(node => /Confirm payment received|تأكيد استلام الدفعة/.test(node.textContent));
  assert(confirm, 'Confirm action missing'); confirm.click(); await pause();
  assert(commands.length === 1 && commands[0].kind === 'CONFIRM_TRANSFER', 'Explicit confirmation did not send payment command');
  assert(closed, 'Completed action did not close');
  fixture.rooms['audit-room'].room.transfers[0].status = 'confirmed';
  root.render(<NotificationActionCard selected={{ item, action: 'confirm' }} data={fixture} onClose={() => {}} />); await pause();
  assert(![...host.querySelectorAll('button')].some(node => /Confirm payment received|تأكيد استلام الدفعة/.test(node.textContent)), 'Stale notification still has a confirm action');
  root.render(<NotificationCenter notifications={{ items: [item], enabled: true, enable: () => {}, disable: () => {}, read: () => {} }} onOpen={() => {}} onBack={() => {}} />); await pause();
  assert(!measureAudit().overflow, 'Notification inbox overflows');
  const spin = { id: 'running-test', memberIds: ['me', 'member'], winnerId: 'member', startAt: now - 10000, duration: 6500, turns: 7 };
  await mountAudit('room', 'ACCEPTING', { room: { selectionStyle: 'names', spin, payerId: null, members: [{ id: 'me', name: 'Audit User', approved: true, participating: true, eligible: true, ready: true }, { id: 'member', name: 'أحمد', approved: true, participating: true, eligible: true, ready: true }] } });
  assert(host.querySelector('.running-names.finished .name-runner-selected')?.textContent === 'أحمد', 'Running names did not reveal the server winner');
  assert(!measureAudit().overflow, 'Running names overflows');
  return { passed: ['payment review before confirmation', 'explicit received action', 'stale action prevented', 'inbox layout', 'running names server winner', 'mobile layout'], language: getLanguage(), ...measureAudit() };
}

export async function runDefaultsJoinPaymentAudit() {
  const bank = { id: 'bank', method: 'BANK', holder: 'Audit User', bank: 'Test Bank', identifier: 'AE070331234567890123456', currency: 'AED', version: 1 };
  const aani = { id: 'aani', method: 'AANI', holder: 'Audit User', bank: 'Aani', identifier: '+971500000001', currency: 'AED', version: 1 };
  commands.length = 0;
  await mountAudit('create');
  assert(host.querySelector('.delivery-choice button.active')?.textContent === t('Delivery'), 'Delivery is not the creation default');
  localStorage.removeItem('foodrun-copy-language-v1');
  await mountAudit('room', 'COLLECTING');
  assert(host.querySelector('.copy-language select')?.value === 'ar', 'Order text did not default to Arabic');
  const me = { id: 'me', name: 'Audit User', approved: true, participating: true };
  const late = { id: 'late', name: 'Late joiner', approved: false, participating: false };
  await mountAudit('room', 'COLLECTING', { room: { members: [me, late] } });
  const approve = host.querySelector('.join-request-actions .secondary');
  assert(approve, 'Creator approval button missing');
  approve.click(); await pause();
  assert(commands.at(-1)?.kind === 'APPROVE_LATE_JOIN' && commands.at(-1).fields.memberId === late.id, 'Approve did not target the late joiner');
  await mountAudit('room', 'COLLECTING', { room: { ownerId: 'other', payerId: 'me', members: [me, late] } });
  assert(host.querySelector('.join-request-actions .secondary'), 'Selected payer approval button missing');
  await mountAudit('room', 'COLLECTING', { memberId: 'late', room: { members: [me, late] } });
  assert(host.textContent.includes(t('Waiting for approval')), 'Pending joiner has no waiting notice');
  assert(!host.querySelector('.room-layout, .member-order-panel, .join-request-actions, .payment-methods-editor'), 'Pending joiner sees protected controls');
  await mountAudit('room', 'FULFILLED', { room: { account: bank, accounts: [aani], restaurantPaid: true } });
  const editor = host.querySelector('.payment-methods-editor');
  assert(editor && editor.querySelector('h2').textContent === t('Payment'), 'Inline Payment heading missing');
  assert([...editor.querySelector('.section-title .hero-actions').children].some(node => node.textContent === t('Edit')), 'Edit is not beside Payment');
  assert(editor.querySelectorAll('.saved-payment-method').length === 2, 'Multiple receiving methods missing');
  assert(!measureAudit().overflow, 'Payment methods overflow on mobile');
  editor.querySelector('.section-title .hero-actions button').click(); await pause();
  assert(host.querySelector('.payment-method-form input'), 'Quick Edit did not open receiving fields');
  button('Cancel').click(); await pause();
  button('Add payment method').click(); await pause();
  const phone = host.querySelector('.payment-method-form input[type=tel]');
  assert(phone && phone.value === '', 'Add method reused an existing identifier');
  setValue(phone, '0509999999'); await pause();
  host.querySelector('.payment-method-form').requestSubmit(); await pause();
  assert(commands.at(-1)?.fields.identity?.profile.paymentAccounts.length === 3, 'Adding a method overwrote existing methods');
  const member = { id: 'friend', name: 'Friend', approved: true, participating: true };
  const receipt = { memberId: member.id, name: member.name, total: 300, paid: 0, balance: 300, food: 300, currency: 'AED', lines: [{ description: 'Food', quantity: 1, amount: 300 }] };
  await mountAudit('room', 'FULFILLED', { memberId: member.id, room: { account: bank, accounts: [aani], members: [me, member], restaurantPaid: true }, receipts: [receipt] });
  const methods = host.querySelector('.payment-actions select');
  assert(methods?.options.length === 2, 'Member cannot choose between shared methods');
  setValue(methods, aani.id); await pause();
  host.querySelector('.payment-actions form').requestSubmit(); await pause();
  assert(commands.at(-1)?.kind === 'DECLARE_TRANSFER' && commands.at(-1).fields.accountId === aani.id, 'Member payment did not include the selected receiving method');
  assert(!measureAudit().overflow, 'Member payment controls overflow');
  return { language: getLanguage(), width: innerWidth, passed: ['delivery default', 'Arabic order text default', 'creator approval', 'selected payer approval', 'pending join privacy', 'inline Edit and Add', 'multiple methods', 'method-specific payment'], overflow: measureAudit().overflow };
}

export async function runWalletAudit() {
  commands.length = 0;
  const bank = { id:'wallet-bank', holder:'Holder', bank:'Test Bank', identifier:'AE070331234567890123456', currency:'AED', method:'BANK' };
  const wallet = { balances:[{customerId:'me',customerName:'Alice',holderId:'holder',holderName:'Holder',currency:'AED',available:10000}],topUps:[],payments:[],batches:[] };
  const searches = []; let finishLateSearch;
  const fixture = { ...data, home:{...data.home,profile:{...data.home.profile,userId:'me'},wallet}, setError:()=>{},
    walletQuery:async(kind,fields)=> { if(kind==='WALLET_PEOPLE') { searches.push(fields.text); if(fields.text==='late') return new Promise(resolve => { finishLateSearch=resolve; }); return {walletPeople:[{userId:'holder',name:'Holder'}]}; } return {walletRecipient:{person:{userId:fields.userId,name:fields.userId==='recipient'?'Recipient':'Holder'},accounts:[bank]}}; } };
  const render = async component => {
    document.getElementById('root').style.display='none'; root?.unmount();host?.remove();host=document.createElement('div');host.id='audit-root';document.body.append(host);root=createRoot(host);root.render(component);await pause();
  };
  const click = async (scope, label) => { const value=[...scope.querySelectorAll('button')].find(node=>node.textContent.includes(label));assert(value&&!value.disabled,`Missing wallet button ${label}`);value.click();await pause(); };
  const wait = async predicate => { for(let i=0;i<30;i++){if(predicate()) return;await pause();}throw Error('Wallet UI did not load'); };
  const expect = (kind, fields) => {const command=commands.at(-1);assert(command?.kind===kind&&Object.entries(fields).every(([key,value])=>command.fields[key]===value),`Incorrect wallet command ${JSON.stringify(command)}`);};
  const ar=getLanguage()==='ar';
  await render(<WalletFunds data={fixture}/>);
  assert(host.textContent.includes('AED 100.00')&&host.textContent.includes('Holder'),'Balance does not identify cash holder');
  await click(host,ar?'شحن المحفظة':'Charge wallet');
  await new Promise(resolve => setTimeout(resolve, 350));
  assert(!host.querySelector('.wallet-people button') && searches.length===0,'Wallet lists users or searches before typing');
  setValue(host.querySelector('input[type=search]'), 'Holder@example.test');
  await wait(()=>host.querySelector('.wallet-people button'));
  assert(searches.at(-1)==='Holder@example.test','Email query was not sent');
  setValue(host.querySelector('input[type=search]'), '  '); await pause();
  assert(!host.querySelector('.wallet-people button'),'Clearing wallet search leaves users visible');
  setValue(host.querySelector('input[type=search]'), 'late');
  await wait(()=>finishLateSearch);
  setValue(host.querySelector('input[type=search]'), ''); await pause();
  finishLateSearch({walletPeople:[{userId:'holder',name:'Holder'}]}); await pause();
  assert(!host.querySelector('.wallet-people button'),'Late response reintroduces users after clearing search');
  setValue(host.querySelector('input[type=search]'), 'hold');
  await wait(()=>host.querySelector('.wallet-people button'));
  await click(host.querySelector('.wallet-people'),'Holder');
  await wait(()=>host.querySelector('.wallet-method'));
  assert(host.querySelector('.wallet-method').textContent.includes(bank.identifier),'Selected person receiving method missing');
  host.querySelector('.wallet-top-up').requestSubmit();await pause();expect('WALLET_TOP_UP',{amount:10000,currency:'AED',userId:'holder',accountId:bank.id});
  const topUp={id:'incoming',customerId:'alice',customerName:'Alice',holderId:'me',holderName:'Me',amount:10000,currency:'AED',account:bank,status:'PENDING',createdAt:Date.now()};
  const payment={id:'p1',customerId:'alice',customerName:'Alice',holderId:'me',holderName:'Me',recipientId:'recipient',recipientName:'Recipient',roomId:'one',roomName:'Lunch',orderNumber:1,memberId:'alice',amount:1500,currency:'AED',status:'OWING'};
  const holder={...fixture,home:{...fixture.home,wallet:{...wallet,topUps:[topUp],payments:[payment,{...payment,id:'p2',customerId:'bob',customerName:'Bob',amount:2500}],balances:[]}}};
  await render(<WalletFunds data={holder}/>);
  await click(host,t('Confirm received'));expect('WALLET_REVIEW_TOP_UP',{transferId:'incoming',flag:true});
  await click(host,t('Not received'));expect('WALLET_REVIEW_TOP_UP',{transferId:'incoming',flag:false});
  await click(host,ar?'دفع كامل المبلغ المجمع':'Pay full group amount');await wait(()=>host.querySelector('.wallet-method'));
  assert(host.textContent.includes('AED 40.00')&&host.textContent.includes('Alice')&&host.textContent.includes('Bob'),'Full group omits customer contributions');
  const batchForm=host.querySelector('.wallet-method').closest('form');batchForm.requestSubmit();await pause();expect('WALLET_DECLARE_BATCH',{amount:4000,userId:'recipient',accountId:bank.id,currency:'AED'});
  const batch={id:'batch',holderId:'holder',holderName:'Holder',recipientId:'me',recipientName:'Me',amount:4000,currency:'AED',paymentIds:['p1','p2'],account:bank,status:'PENDING',createdAt:Date.now()};
  const receiving={...holder,home:{...holder.home,wallet:{...holder.home.wallet,topUps:[],batches:[batch]}}};
  await render(<WalletFunds data={receiving}/>);
  await click(host,ar?'تأكيد استلام كامل المبلغ':'Confirm full amount received');expect('WALLET_REVIEW_BATCH',{transferId:'batch',flag:true});
  const receipt={memberId:'me',currency:'AED',balance:1500};const room={id:'one',walletPayments:[]};
  await render(<WalletPaymentOption data={fixture} room={room} receipt={receipt} canPay/>);
  await click(host,ar?'الدفع بالمحفظة':'Pay with wallet');expect('PAY_WITH_WALLET',{amount:1500});
  await render(<WalletPaymentOption data={{...fixture,home:{...fixture.home,wallet:{...wallet,balances:[]}}}} room={room} receipt={receipt} canPay/>);
  assert(host.querySelector('button').disabled,'Wallet can be spent before credit confirmation');
  await render(<WalletFunds data={holder}/>);
  assert(!measureAudit().overflow,'Wallet controls overflow');
  return {passed:['balance identifies holder','no users before typing','email query','clear hides results','late search discarded','name search','selected receiving method','AED 100 pending top-up','holder received/not received','AED 40 grouped transfer for two users','recipient confirms whole group','wallet pays current remaining amount','insufficient balance disables wallet'],...measureAudit()};
}

export async function runWalletCustodyAudit() {
  commands.length = 0;
  const bank = { id:'bank', holder:'Karim', bank:'Test Bank', identifier:'AE070331234567890123456', currency:'AED', method:'BANK' };
  const payment = { id:'allocated', customerId:'customer', customerName:'Anas', holderId:'holder', holderName:'Ahmed', recipientId:'canonical', recipientName:'Karim', roomId:'room', roomName:'Lunch', orderNumber:1, memberId:'customer-member', amount:1500, currency:'AED', status:'OWING', batchId:'' };
  const batch = { id:'batch', holderId:'holder', holderName:'Ahmed', recipientId:'canonical', recipientName:'Karim', amount:1500, currency:'AED', paymentIds:[payment.id], status:'PENDING', account:bank, note:'Bank transfer', createdAt:0 };
  const wallet = { balances:[{customerId:'canonical',customerName:'Karim',holderId:'holder',holderName:'Ahmed',currency:'AED',available:5000}], topUps:[],payments:[payment],batches:[] };
  const fixture = {...data, user:{uid:'other-login-id'}, home:{...data.home, profile:{...data.home.profile,userId:'canonical'}, rooms:[],wallet}, setError:()=>{}, clearJoinBlock:()=>{}};
  const render = async component => {
    document.getElementById('root').style.display='none';root?.unmount();host?.remove();host=document.createElement('div');host.id='audit-root';document.body.append(host);root=createRoot(host);root.render(component);await pause();
  };
  const room = {id:'room',walletPayments:[payment]};
  await render(<WalletCustody room={room} data={fixture}/>);
  assert(host.textContent.includes('Ahmed')&&host.textContent.includes('Anas')&&host.textContent.includes('AED 15.00'),'Wallet responsibility does not identify holder, customer and amount');
  assert(!host.querySelector('button'),'Unsent holder payment can be confirmed');
  const sent = {...payment,status:'SENT',batchId:'batch'};
  const receiving = {...fixture,home:{...fixture.home,wallet:{...wallet,payments:[sent],batches:[batch]}}};
  await render(<WalletCustody room={{...room,walletPayments:[sent]}} data={receiving}/>);
  const confirm = [...host.querySelectorAll('button')].find(node=>node.textContent.includes(getLanguage()==='ar'?'تأكيد استلام كامل المبلغ':'Confirm full amount received'));
  assert(confirm,'Recipient confirmation missing');confirm.click();await pause();
  assert(commands.at(-1)?.kind==='WALLET_REVIEW_BATCH'&&commands.at(-1)?.fields.transferId==='batch'&&commands.at(-1)?.fields.flag===true,'Incorrect grouped confirmation command');
  const refuse = [...host.querySelectorAll('button')].find(node=>node.textContent.includes(t('Not received')));refuse.click();await pause();
  assert(commands.at(-1)?.fields.flag===false,'Incorrect grouped rejection command');
  await render(<WalletCustody room={{...room,walletPayments:[sent]}} data={{...receiving,home:{...receiving.home,profile:{...receiving.home.profile,userId:'customer'}}}}/>);
  assert(!host.querySelector('button'),'Customer can confirm receipt for the payer');
  await render(<Home data={fixture} setPage={()=>{}} openRoom={()=>{}}/>);
  assert(host.querySelector('.home-money-preview')?.textContent.includes('AED 50.00'),'Unified login hides canonical wallet balance on Home');
  assert(!measureAudit().overflow,'Wallet custody or unified account controls overflow');
  return {language:getLanguage(),width:innerWidth,passed:['holder owes on behalf of customer','unsent payment cannot be confirmed','recipient confirms full group','recipient rejects full group','customer cannot confirm payer receipt','canonical balance shared by both logins'],...measureAudit()};
}

// Exercises collapsed information and actions with a large order, in both languages.
export async function runDesignAudit() {
  commands.length = 0;
  const lines = Array.from({ length: 14 }, (_, index) => ({ id: `line-${index}`, description: `Meal ${index + 1}`, quantity: 1, itemId: '', notes: '', unitPrice: 1500, optionIds: [] }));
  const member = { id: 'me', name: 'Audit User', approved: true, participating: true };
  const friend = { ...member, id: 'friend', name: 'Friend' };
  const receipt = { memberId: 'me', name: member.name, currency: 'AED', food: 21000, total: 21000, totalText: 'AED 210.00', paid: 0, balance: 0, lines: lines.map(line => ({ ...line, amount: 1500 })) };
  const account = { id: 'bank', holder: 'Audit User', bank: 'Bank', identifier: 'AE070331234567890123456', currency: 'AED', method: 'BANK_TRANSFER' };
  const options = { room: { account, accounts: [account], members: [member, friend], carts: [{ memberId: 'me', submitted: true, revision: 1, lines }] }, receipts: [receipt], progress: { canReview: true } };
  await mountAudit('room', 'COLLECTING', options);
  await document.fonts.ready;
  const summary = host.querySelector('.order-summary');
  assert(summary.querySelectorAll('.summary-items li').length === 4, 'Large order is not compact by default');
  assert(summary.textContent.includes('210.00'), 'Full total is lost in compact summary');
  assert(host.querySelector('.room-main').firstElementChild === summary, 'Payer summary is not first');
  const more = summary.querySelector('[aria-expanded]'); more.click(); await pause();
  assert(summary.querySelectorAll('.summary-items li').length === 14, 'Expanded summary loses items');
  more.click(); await pause();
  assert(summary.querySelectorAll('.summary-items li').length === 4, 'Summary cannot be collapsed');
  const preview = host.querySelector('.restaurant-list-preview');
  assert(!preview.open && preview.getBoundingClientRect().height < 120, 'Duplicate restaurant list is expanded');
  preview.open = true; await pause();
  assert(preview.querySelector('pre').textContent.includes('Meal 14'), 'Restaurant copy list is incomplete');
  preview.open = false;
  const nav = host.querySelector('.payer-quick-nav');
  window.scrollTo(0, 700); await pause();
  assert(nav.getBoundingClientRect().top >= 0 && nav.getBoundingClientRect().top < 15, 'Order shortcuts scroll out of reach');
  window.scrollTo(0, 0);
  const send = button('Order sent'); assert(send && !send.disabled, 'Primary order action is unavailable');
  send.click(); await pause(); assert(commands.at(-1).kind === 'PLACE', 'Order action no longer sends PLACE');
  await mountAudit('room', 'FULFILLED', { ...options, room: { ...options.room, restaurantPaid: true }, receipts: [receipt, { ...receipt, memberId: 'friend', name: 'Friend', food: 1500, total: 1500, totalText: 'AED 15.00', paid: 0, balance: 1500, lines: [receipt.lines[0]] }], progress: { canArchive: false } });
  const record = host.querySelector('.record-payment');
  assert(record && (!record.matches('details') || record.open) && record.querySelector('input').getClientRects().length, 'Received payment is no longer visibly expanded');
  assert(getComputedStyle(host.querySelector('.primary')).backgroundColor === 'rgb(207, 68, 43)', 'Intrvioo accessible coral primary is inconsistent');
  assert(!measureAudit().overflow, 'New theme overflows');
  return { passed: ['compact complete summary', 'full totals retained', 'summary expand and collapse', 'complete restaurant list', 'sticky shortcuts', 'PLACE command retained', 'received payment expanded', 'Intrvioo coral theme', 'no overflow'], ...measureAudit() };
}

export async function runScreenRedesignAudit() {
  const ar = getLanguage()==='ar', passed=[];
  const assertScreen = label => { assert(!measureAudit().overflow, `${label} overflows`); passed.push(label); };
  for (const screen of ['create','join','library','payment-create']) {
    await mountAudit(screen); assertScreen(screen);
    for (const label of host.querySelectorAll('label:not(.check):not(.upload)')) {
      assert(getComputedStyle(label).textAlign==='start' || getComputedStyle(label).textAlign===(ar?'right':'left'), `${screen} field alignment`);
    }
  }
  const account={id:'bank',holder:'Audit User',bank:'Bank',identifier:'AE070331234567890123456',currency:'AED',method:'BANK'};
  const fixture={...data,home:{...data.home,profile:{...data.home.profile,userId:'me',payment:account},wallet:{balances:[],payments:[],topUps:[],batches:[]}}};
  await mountAudit('profile','LOBBY',{data:fixture});
  const wallet=host.querySelector('.wallet-funds'), details=host.querySelector('#profile-details');
  assert(wallet && Number.parseFloat(getComputedStyle(wallet).paddingInlineStart)>=16,'Wallet content touches card edge');
  assert(wallet.getBoundingClientRect().top < details.getBoundingClientRect().top && !details.open,'Profile details block wallet');
  host.querySelector('a[href="#profile-details"]').click(); await pause(); assert(details.open,'Edit profile shortcut does not expand details');
  assertScreen('profile wallet first and edit shortcut');
  await mountAudit('home','COLLECTING',{room:{createdAt:Date.now()}});
  assert(host.querySelector('.continue-order'), 'Missing continue order shortcut'); assertScreen('home continue order');
  for(const phase of ['COLLECTING','FULFILLED']) {
    await mountAudit('room',phase,{room:{restaurantPaid:true}});
    const invite=host.querySelector('.invite-qr-details'); assert(invite && !invite.open,'QR takes over room layout');
    invite.open=true; await pause(); assert(invite.querySelector('svg').getBoundingClientRect().width>0,'QR cannot expand');
    assertScreen(`room ${phase}`);
  }
  await mountAudit('profile','LOBBY',{data:{...fixture,walletTopUpRequest:{amount:1250,currency:'JOD'}}});
  const topUp=host.querySelector('.wallet-top-up');
  assert(topUp.querySelector('input[inputmode=decimal]').value==='1.250' && topUp.querySelector('select').value==='JOD','Top-up shortfall was not prefilled');
  assert(!topUp.querySelector('.wallet-people'), 'Shortfall top-up shows all users'); assertScreen('exact top-up shortfall');
  return {passed,...measureAudit()};
}

export async function runWalletAnnouncementAudit() {
  const { default: WalletAnnouncement } = await import('../src/foodrun/WalletAnnouncement.jsx');
  const { default: WheelProtection } = await import('../src/foodrun/WheelProtection.jsx');
  const { walletAnnouncementKey } = await import('../src/foodrun/walletAnnouncement.js');
  const { serviceSupportMessage } = await import('../src/foodrun/ServiceSupportNote.jsx');
  const id = `announcement-audit-${Date.now()}`;
  let opened = 0;
  const render = async element => {
    document.getElementById('root').style.display = 'none'; root?.unmount(); host?.remove();
    host = document.createElement('div'); host.id = 'audit-root'; document.body.append(host); root = createRoot(host);
    root.render(element); await pause();
  };
  const fixture = { ...data, user: { uid: id }, home: { ...data.home, profile: { ...data.home.profile, userId: id }, wallet: { balances: [], topUps: [], payments: [], batches: [] } } };
  await mountAudit('home', 'LOBBY', { data: fixture });
  assert(host.querySelector('.wallet-announcement'), 'First Home does not announce wallet');
  assert(host.textContent.includes(t('Transfer the money outside Intrvioo. Your balance updates after the holder confirms receipt.')), 'Announcement does not explain confirmed top-ups');
  button('Got it').click(); await pause();
  assert(!host.querySelector('.wallet-announcement'), 'Dismiss did not hide announcement');
  assert(localStorage.getItem(walletAnnouncementKey(id)) === 'seen', 'Dismissal did not persist');
  await mountAudit('home', 'LOBBY', { data: fixture });
  assert(!host.querySelector('.wallet-announcement'), 'Returning Home repeated announcement');
  await render(<WalletAnnouncement userId={`${id}-other`} onOpen={() => opened++} />);
  assert(host.querySelector('.wallet-announcement'), 'Another account did not receive announcement');
  button('Open wallet').click(); await pause();
  assert(opened === 1 && !host.querySelector('.wallet-announcement'), 'Open wallet did not open and dismiss');
  const room = { id: 'sample-room', ownerId: 'owner', phase: 'LOBBY', members: [{ id: 'owner', name: 'Owner' }], wheelProtections: [] };
  await render(<WheelProtection room={room} me={{ id: 'member', approved: true, participating: true, eligible: true }} data={{ ...data, online: { 'sample-room': true } }} />);
  assert(host.querySelector('.service-support-note')?.textContent === t(serviceSupportMessage), 'Paid feature does not explain server and service funding');
  assert(button('Exclude me from selection · AED 10'), 'Funding notice obscures paid choice');
  const result = { passed: ['first authenticated Home announcement', 'confirmed top-up explanation', 'persistent dismissal', 'return visits', 'separate accounts', 'open wallet', 'paid feature funding notice'], ...measureAudit() };
  localStorage.removeItem(walletAnnouncementKey(id)); localStorage.removeItem(walletAnnouncementKey(`${id}-other`));
  return result;
}

export async function runFriendsTimerAudit() {
  await mountAudit('home');
  commands.length = 0;
  const ar = getLanguage() === 'ar';
  const find = (en, arabic) => { const node = [...host.querySelectorAll('button')].find(node => node.textContent.trim() === (ar ? arabic : en)); assert(node, 'Missing button '+en+': '+[...host.querySelectorAll('button')].map(value=>value.textContent).join('|')); return node; };
  const group = {id:'friends',name:'Office friends',favourite:true,members:[{email:'bob@example.test',userId:'bob',name:'Bob'}]};
  const fixture = {...data, home:{...data.home,profile:{...data.home.profile,userId:'audit-only'},friendGroups:[group]}, walletQuery:async (kind, fields) => ({friendContact:{email:fields.text,userId:fields.text==='bob@example.test'?'bob':'',name:fields.text==='bob@example.test'?'Bob':''}}), setNotice:()=>{}};
  root.render(<FriendGroups data={fixture} onBack={()=>{}} />); await pause();
  setValue(host.querySelector('input'), 'Lunch group');
  setValue(host.querySelector('input[role=combobox]'), 'new@example.test'); await pause();
  await new Promise(resolve => setTimeout(resolve,400));
  const pick = host.querySelector('.friend-search-person'); if(pick) pick.click(); else [...host.querySelectorAll('button')].find(value => value.textContent.includes(ar ? 'دعوة بالبريد' : 'Invite by email')).click(); await pause();
  assert(host.textContent.includes(ar ? 'تُرسل دعوة التسجيل' : 'Sign-up invitation'), 'Missing signup invitation state');
  button('Save').click(); await pause();
  assert(commands.at(-1)?.kind==='SAVE_FRIEND_GROUP' && commands.at(-1).fields.friendGroup.members[0].email==='new@example.test','Group save lost members');
  const groupsMeasure = measureAudit(); assert(!groupsMeasure.overflow, 'Friend groups overflow');
  localStorage.setItem('foodrun-room-preferences:audit-only', JSON.stringify({restaurantId:loadRestaurants()[0].id}));
  await mountAudit('create','LOBBY',{data:fixture});
  const select = [...host.querySelectorAll('select')].find(node=>[...node.options].some(option=>option.value==='audit-only:friends'));
  assert(select,'Missing favourite group selector'); setValue(select,'audit-only:friends'); await pause();
  const timerLabel = [...host.querySelectorAll('label')].find(node=>node.textContent.includes(ar?'بدء العجلة بعد مهلة':'Start the wheel after a join timer'));
  timerLabel.querySelector('input').click(); await pause();
  setValue(host.querySelector('input[type=number]'),'5'); await pause();
  host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); await pause();
  assert(commands.at(-1)?.kind==='CREATE' && commands.at(-1).fields.friendGroupId==='friends' && commands.at(-1).fields.friendGroupOwnerId==='audit-only' && commands.at(-1).fields.joinTimerMinutes===5,'Room lost group or timer');
  assert(!measureAudit().overflow,'Timed room creation overflow');
  const memberFixture = {...fixture, home:{...fixture.home,joinedFriendGroups:[{ownerId:'alice',ownerName:'Alice',group:{...group,favourite:false}},{ownerId:'charlie',ownerName:'Charlie',group}]}};
  await mountAudit('create','LOBBY',{data:memberFixture});
  const memberSelect = [...host.querySelectorAll('select')].find(node=>[...node.options].some(option=>option.value==='alice:friends'));
  assert(memberSelect && [...memberSelect.options].some(option=>option.value==='audit-only:friends') && [...memberSelect.options].some(option=>option.value==='charlie:friends'),'Joined groups or duplicate owner ids are missing');
  setValue(memberSelect,'alice:friends'); await pause();
  host.querySelector('form').dispatchEvent(new Event('submit',{bubbles:true,cancelable:true})); await pause();
  assert(commands.at(-1)?.kind==='CREATE' && commands.at(-1).fields.friendGroupId==='friends' && commands.at(-1).fields.friendGroupOwnerId==='alice','Member announcement selected another owner or group');
  assert(!measureAudit().overflow,'Joined group room creation overflows');
  let target = '';
  root.render(<AdminUsers users={[{id:'owner',name:'Owner',email:'owner@example.test',phone:''},{id:'bob',name:'Bob',email:'bob@example.test',phone:''}]} currentUserId="owner" mutate={()=>{}} onSupport={id=>{target=id;}} />); await pause();
  setValue(host.querySelector('input[type=search]'),'bob@example.test'); await pause();
  assert(host.querySelectorAll('.admin-person').length===1,'Admin email search does not isolate user');
  find('Sign in as this user','تسجيل الدخول باسم هذا المستخدم').click(); await pause();
  assert(target==='bob','Support login selected wrong user');
  return {passed:true,language:getLanguage(),width:innerWidth,overflow:measureAudit().overflow,groupEmail:true,roomTimer:true,adminEmailSearch:true};
}

export async function runGroupMembershipAudit() {
  await mountAudit('home'); commands.length = 0;
  const ar = getLanguage() === 'ar';
  const find = (en, arabic, scope = host) => { const node = [...scope.querySelectorAll('button')].find(value => value.textContent.trim() === (ar ? arabic : en)); assert(node, 'Missing '+en); return node; };
  const group = {id:'owned',name:'Office friends',favourite:true,revision:4,members:[{email:'bob@example.test',userId:'bob',name:'Bob'},{email:'charlie@example.test',userId:'charlie',name:'Charlie'}]};
  const joined = {ownerId:'alice',ownerName:'Alice',group:{...group,id:'joined',name:'Friday lunch'}};
  const fixture = {...data,home:{...data.home,friendGroups:[group],joinedFriendGroups:[joined]},walletQuery:async (kind, fields)=> kind === 'FRIEND_SEARCH' ? ({friendContacts:[{email:'dana@example.test',userId:'dana',name:'Dana'}]}) : ({friendContact:{email:fields.text,userId:'dana',name:'Dana'}})};
  root.render(<FriendGroups data={fixture} onBack={()=>{}} />); await pause();
  button('Edit').click(); await pause();
  setValue(host.querySelector('input'), 'Team lunch'); await pause();
  button('Remove').click(); await pause();
  setValue(host.querySelector('input[role=combobox]'), 'dana@example.test'); await pause();
  await new Promise(resolve => setTimeout(resolve,400));
  const pick = host.querySelector('.friend-search-person'); if(pick) pick.click(); else [...host.querySelectorAll('button')].find(value => value.textContent.includes(ar ? 'دعوة بالبريد' : 'Invite by email')).click(); await pause();
  button('Save').click(); await pause();
  const saved = commands.at(-1); assert(saved?.kind==='SAVE_FRIEND_GROUP' && saved.fields.friendGroup.name==='Team lunch' && saved.fields.friendGroup.revision===4,'Owner edit lost name or revision');
  assert(saved.fields.friendGroup.members.map(value=>value.userId).join(',')==='charlie,dana','Owner add/remove did not update members');
  find('View group','عرض المجموعة').click(); await pause();
  const details = [...host.querySelectorAll('section[aria-label]')].find(value=>value.getAttribute('aria-label')===(ar?'تفاصيل المجموعة':'Group details'));
  assert(details?.textContent.includes('Alice') && details.textContent.includes('bob@example.test'),'Member cannot view group and members');
  assert(!host.querySelector('input') && ![...details.querySelectorAll('button')].some(value=>['Edit','Remove','Save'].some(label=>value.textContent.trim()===t(label))),'Joined group exposed editing controls');
  find('Leave group','مغادرة المجموعة',details).click(); await pause();
  assert(commands.at(-1)?.kind==='LEAVE_FRIEND_GROUP' && commands.at(-1).fields.friendGroupId==='joined' && commands.at(-1).fields.friendGroupOwnerId==='alice','Member leave targeted wrong group');
  root.render(<FriendGroups data={{...fixture,home:{...fixture.home,joinedFriendGroups:[]}}} onBack={()=>{}} />); await pause();
  assert(!host.textContent.includes('Friday lunch'),'Left group is still listed');
  assert(!measureAudit().overflow,'Group membership screen overflows');
  return {passed:true,language:getLanguage(),width:innerWidth,overflow:false,ownerEditing:true,memberView:true,memberLeave:true};
}

export async function runWalletApprovalAudit() {
  const bank = {id:'bank',holder:'Payer',bank:'Test Bank',identifier:'AE070331234567890123456',currency:'AED',method:'BANK'};
  const payment = {id:'request',customerId:'customer',customerName:'Customer',holderId:'payer-user',holderName:'Payer',recipientId:'payer-user',recipientName:'Payer',roomId:'audit-room',roomName:'Lunch',orderNumber:1,memberId:'customer-member',amount:700,currency:'AED',status:'OWING'};
  const transfer = {id:'wallet-request',memberId:'customer-member',amount:700,status:'DECLARED',reference:'Wallet · Payer',recipient:bank};
  const receipts = [{memberId:'me',name:'Payer',lines:[],total:0,food:0,paid:0,balance:0,currency:'AED',totalText:'AED 0.00',balanceText:'AED 0.00'}, {memberId:'customer-member',name:'Customer',lines:[],total:700,food:700,paid:0,balance:700,currency:'AED',totalText:'AED 7.00',balanceText:'AED 7.00'}];
  const room = {members:[{id:'me',name:'Payer',approved:true,participating:true,eligible:true},{id:'customer-member',name:'Customer',approved:true,participating:true,eligible:true}],account:bank,restaurantPaid:true,walletPayments:[payment],transfers:[transfer]};
  const baseData = {...data,home:{...data.home,profile:{...data.home.profile,userId:'payer-user'},wallet:{balances:[],topUps:[],payments:[payment],batches:[]}}};
  commands.length=0;
  await mountAudit('room','FULFILLED',{room,receipts,baseData});
  const approve = [...host.querySelectorAll('button')].find(node=>node.textContent=== (getLanguage()==='ar'?'الموافقة على دفعة المحفظة':'Approve wallet payment'));
  assert(approve,'Wallet request is not in normal payment approval list'); approve.click(); await pause();
  assert(commands.at(-1)?.kind==='CONFIRM_TRANSFER' && commands.at(-1).fields.transferId==='wallet-request','Approval used wrong ledger action');
  const sent = {...payment,holderId:'holder',holderName:'Ahmed',status:'SENT',batchId:'batch'};
  const batch = {id:'batch',recipientId:'payer-user',holderId:'holder',holderName:'Ahmed',amount:1700,currency:'AED',paymentIds:['request','another-order'],status:'PENDING',account:bank};
  await mountAudit('room','FULFILLED',{room:{...room,walletPayments:[sent]},receipts,baseData:{...baseData,home:{...baseData.home,wallet:{...baseData.home.wallet,payments:[sent],batches:[batch]}}}});
  const full = [...host.querySelectorAll('button')].find(node=>node.textContent.includes(t('Confirm received')) && node.textContent.includes('17.00'));
  assert(full,'Normal confirmation does not show the full holder transfer'); full.click(); await pause();
  assert(commands.at(-1)?.kind==='WALLET_REVIEW_BATCH' && commands.at(-1).fields.flag===true,'Group approval did not settle all sides');
  const measurement=measureAudit(); assert(!measurement.overflow,'Wallet approval overflow');
  return {passed:true,language:getLanguage(),width:innerWidth,overflow:measurement.overflow};
}

export async function runMobilePagesAudit() {
  const {default:MobilePageLayout} = await import('../src/foodrun/MobilePageLayout.jsx');
  const {AdminCleanup} = await import('../src/foodrun/AdminApp.jsx');
  const {default:AdminApp} = await import('../src/foodrun/AdminApp.jsx');
  const {default:NotificationPreferences} = await import('../src/foodrun/NotificationPreferences.jsx');
  const screens = [], wrap = async () => {
    const elements = [...host.children];
    // Mount the same layout that surrounds every authenticated route.
    const component = root._auditComponent;
    if(component) { root.render(<MobilePageLayout pageKey={component.key}>{component.node}</MobilePageLayout>); await pause(); }
    for(const tab of host.querySelectorAll('.mobile-section-tabs button')) { tab.click(); await pause(); assert(!measureAudit().overflow,'Mobile tab overflow'); }
    return elements;
  };
  for(const screen of ['home','create','join','profile','library','payment-create','room']) {
    await mountAudit(screen,'FULFILLED');
    const component = root._auditComponent;
    await wrap();
    assert(!measureAudit().overflow,screen+' overflow');
    if(screen === 'room' && innerWidth <= 640) {
      const tabs = [...host.querySelectorAll('.mobile-room-tabs button')];
      assert(tabs.length === 4, 'Room does not have four mobile tabs');
      assert(tabs.map(node => node.textContent).join('|') === ['Overview','My food','Payments','Members'].map(label => t(label)).join('|'), 'Room tab order or labels changed');
      for (let index = 0; index < tabs.length; index++) {
        tabs[index].click(); await pause();
        const id = ['room-overview','room-food','room-payments','room-members'][index];
        const panels = [...host.querySelectorAll('[data-mobile-section]')];
        assert(panels.filter(node => node.dataset.mobileSection === id).every(node => node.dataset.mobileHidden === 'false'), 'A grouped room panel is unreachable');
        assert(panels.filter(node => node.dataset.mobileSection !== id).every(node => node.dataset.mobileHidden === 'true'), 'A different room tab is still visible');
      }
      const shortcut = host.querySelector('a[href="#room-payment"]');
      if (shortcut) { shortcut.click(); await pause(); assert(host.querySelector('[data-mobile-section="room-payments"]').dataset.mobileHidden === 'false', 'Payment shortcut did not open Payments'); }
    }
    if(screen === 'create' && innerWidth <= 640) {
      const panel = host.querySelector('[data-mobile-section="room"]'), roomName = panel.querySelector('input');
      setValue(roomName,''); await pause();
      const event = new Event('invalid',{bubbles:false,cancelable:true}); roomName.dispatchEvent(event); await pause();
      assert(panel.dataset.mobileHidden==='false','Invalid hidden form field did not reveal its mobile tab');
    }
    screens.push({screen,tabs:host.querySelectorAll('.mobile-section-tabs button').length,overflow:false});
  }
  const bank = {id:'bank',holder:'Holder',bank:'Test Bank',identifier:'AE070331234567890123456',currency:'AED',method:'BANK'};
  const balance = {customerId:'audit-only',customerName:'Audit User',holderId:'holder',holderName:'Holder',currency:'AED',available:5000};
  let historyReads = 0;
  const transactions = Array.from({length:25},(_,index)=>({id:'top-up:'+index,kind:'TOP_UP',status:'CONFIRMED',amount:200,currency:'AED',balanceChange:200,createdAt:Date.now()-index*1000,fromName:'Audit User',toName:'Holder',note:'Reference '+index,account:bank,orders:[]}));
  const walletData = {...data,home:{...data.home,profile:{...data.home.profile,userId:'audit-only'},wallet:{balances:[balance],topUps:[],payments:[],batches:[]}},walletQuery:async(kind,fields)=>{ assert(kind==='WALLET_HISTORY','History used mutation');historyReads++;return {walletHistory:{balance,transactions:fields.walletHistoryCursor?transactions.slice(20):transactions.slice(0,20),nextCursor:fields.walletHistoryCursor?'':'older'}};}};
  root.render(<WalletFunds data={walletData}/>); await pause(); host.querySelector('.wallet-selectable').click(); await pause();
  let dialog = document.querySelector('.wallet-history-dialog[open]'); assert(dialog,'Wallet history popup missing'); assert(dialog.textContent.includes('Holder') && dialog.textContent.includes('Reference 0'),'Missing transaction details');
  [...dialog.querySelectorAll('button')].find(node=>node.textContent.includes(getLanguage()==='ar'?'تحميل معاملات أقدم':'Load older transactions')).click(); await pause();
  assert(historyReads===2 && dialog.textContent.includes('Reference 24'),'Wallet older page missing'); assert(!measureAudit().overflow,'Wallet popup overflow');
  dialog.querySelector('button').click(); await pause(); assert(!document.querySelector('.wallet-history-dialog[open]'),'Wallet popup did not close');
  commands.length=0; root.render(<NotificationPreferences data={{...data,home:{...data.home,notificationPreferences:{pushEnabled:true,emailEnabled:true}}}}/>); await pause();
  host.querySelectorAll('input[type=checkbox]').forEach(node=>node.click()); await pause(); host.querySelector('button').click(); await pause();
  assert(commands.at(-1)?.kind==='SET_NOTIFICATION_PREFERENCES' && !commands.at(-1).fields.notificationPreferences.pushEnabled && !commands.at(-1).fields.notificationPreferences.emailEnabled,'Preference toggles not saved');
  const savedFetch = window.fetch;
  const rooms = Array.from({length:18},(_,index)=>({id:'room'+index,name:'Room '+index,code:String(100000+index),phase:'LOBBY',currency:'AED',totalMinor:0,confirmedPaidMinor:0,outstandingMinor:0,wallets:[],members:2,restaurant:'Kitchen',orderNumber:1,revision:1,canDelete:false}));
  const dashboard = {users:Array.from({length:18},(_,index)=>({id:'user'+index,name:'User '+index,email:'user'+index+'@example.test',phone:'',disabled:false})),rooms,archivedOrders:rooms,restaurants:loadRestaurants(),blockRequests:[],settings:{registrationsEnabled:true,roomCreationEnabled:true,maintenanceMessage:''}};
  window.fetch = async (url,...args)=>String(url).includes('/admin/dashboard')?{ok:true,json:async()=>dashboard}:savedFetch(url,...args);
  try {
    root.render(<AdminApp language={getLanguage()} user={{uid:'owner',email:'1ahmedkaram1@gmail.com',emailVerified:true,getIdToken:async()=> 'fixture'}} onBack={()=>{}}/>); await pause(); await pause();
    for(const tab of host.querySelectorAll('.admin-tabs button')) {
      tab.click(); await pause(); const search = host.querySelector('.admin-search input'); assert(search,'Admin search missing');
      setValue(search,'no-match-example'); await pause(); assert(!measureAudit().overflow,'Admin tab overflow');
      setValue(search,''); await pause(); assert(!measureAudit().overflow,'Admin full tab overflow');
    }
    const usersTab = [...host.querySelectorAll('.admin-tabs button')].find(node=>node.textContent===t('Users'));usersTab.click(); await pause();
    assert(host.querySelectorAll('.admin-person').length===8 && host.querySelector('.list-pagination'),'Admin pagination missing');
    setValue(host.querySelector('.admin-search input'),'user17@example.test'); await pause();assert(host.querySelectorAll('.admin-person').length===1 && host.textContent.includes('User 17'),'Admin email search failed');
  } finally { window.fetch = savedFetch; }
  const cleanupCalls = [];
  const request = async(path,selection)=>{cleanupCalls.push(path); return {...selection,count:18,targets:rooms,previewToken:'fixture-preview'};};
  root.render(<AdminCleanup request={request} onChanged={()=>{}} search=""/>);await pause();
  button('Preview cleanup').click();await pause();
  setValue(host.querySelector('input[autocomplete=off]'),'DELETE');await pause();
  root.render(<AdminCleanup request={request} onChanged={()=>{}} search="Room 17"/>);await pause();
  const remove = button('Delete selected data');assert(remove.disabled,'Filtered cleanup can delete unseen records');remove.click();await pause();assert(cleanupCalls.length===1,'Filtered cleanup sent delete request');
  return {passed:true,language:getLanguage(),width:innerWidth,screens,walletHistory:true,notificationPreferences:true,adminSearchTabs:9,adminPagination:true,overflow:false};
}

export async function runGlobalNavigationAudit() {
  const oldUrl=location.href;
  commands.length=0;
  root?.unmount();host?.remove();document.getElementById('root').style.display='none';
  host=document.createElement('div');host.id='audit-root';document.body.append(host);root=createRoot(host);
  const fixture={...data,authReady:true,connectionState:'connected',home:{...data.home,profile:{...data.home.profile,userId:'audit-only'},rooms:[],wallet:{balances:[],topUps:[],payments:[],batches:[]}},clearJoinBlock:()=>{},connect:()=>{},setError:()=>{},clearOfflineReceipts:()=>{},dismissFeedback:()=>{}};
  const useFixture=()=>fixture;
  const nav=async page=>{host.querySelectorAll('.app-navigation button')[page==='home'?0:1].click();await pause();};
  const routes=[['Restaurants & menus','المطاعم والقوائم'],['Friend groups','مجموعات الأصدقاء'],['Notifications','الإشعارات'],['Get the apps','احصل على التطبيقات']];
  try {
    history.replaceState({},'','/');root.render(<FoodRunClient useData={useFixture}/>);await pause();
    assert(host.querySelector('.app-navigation'),'Global navigation missing');
    for(const [label,arabic] of routes){
      await nav('home');
      const target=[...host.querySelectorAll('button')].find(button=>[t(label),getLanguage()==='ar'?arabic:label].includes(button.textContent.trim()));
      assert(target,`Missing route ${label}`);target.click();await pause();await pause();
      await nav('profile');assert(host.querySelector('.profile-grid'),`Profile unreachable from ${label}`);
      await nav('home');assert(host.querySelector('.home-banner'),`Home unreachable from ${label}`);
    }
    await nav('profile');host.querySelector('#profile-details').open=true;
    assert(host.querySelector('.app-navigation').getBoundingClientRect().top>=0,'Navigation scrolled offscreen');
    assert(!measureAudit().overflow,'Global navigation overflows');
    assert(commands.length===0,'Navigation changed account or room data');
    return {passed:true,language:getLanguage(),width:innerWidth,overflow:false,routes:routes.map(([label])=>label)};
  } finally {history.replaceState({},'',oldUrl);}
}

export async function runIosInstallAudit(){
 await mountAudit('home');
 const button=[...host.querySelectorAll('button')].find(value=>value.textContent.trim()===t('Install on iPhone'));assert(button,'iPhone install unavailable');const fold=button.closest('details');if(fold)fold.open=true;button.focus();button.click();await pause();
 const sheet=document.querySelector('.ios-install-sheet');assert(sheet?.getAttribute('aria-modal')==='true','Install is not a modal sheet');assert(sheet.textContent.includes('Safari'),'Safari instructions missing');assert(sheet.querySelectorAll('li').length===4,'Installation steps missing');
 assert(sheet.getBoundingClientRect().width<=innerWidth,'Install sheet overflows');
 document.dispatchEvent(new KeyboardEvent('keydown',{key:'Escape',bubbles:true}));await pause();assert(!document.querySelector('.ios-install-sheet'),'Escape did not close installation');
 assert(document.activeElement===button,'Focus was not restored');
 const ipa=host.querySelector('.ipa-test-download a');assert(ipa?.href.endsWith('FoodRun-iOS-RegisteredDevices-1.7.3.ipa'),'IPA download is missing');
 return {passed:true,language:getLanguage(),width:innerWidth,overflow:false};
}

export async function runInternetDefaultsAudit() {
  await mountAudit('create');
  const style=[...host.querySelectorAll('select')].find(select=>[...select.options].some(option=>option.value==='names'));
  assert(style?.value==='names','New rooms do not default to running names');
  await mountAudit('library');
  assert(host.querySelector('input[type="search"]')?.placeholder,'Restaurant search has no help');
  const fixture={...data,authReady:true,hub:'https://foodrun-api-q6b9.onrender.com',home:null,connectionState:'connecting',hasPending:false,clearJoinBlock:()=>{},connect:()=>{},dismissFeedback:()=>{}};
  const useFixture=()=>fixture;
  root.render(<FoodRunClient useData={useFixture}/>);await pause();
  assert(!host.querySelector('.mode-cards'),'Healthy initial Internet connection shows other hub choices');
  fixture.connectionState='retrying';root.render(<FoodRunClient useData={useFixture}/>);await pause();
  const fallback=[...host.querySelectorAll('button')].find(button=>/Other connection options|خيارات اتصال أخرى/.test(button.textContent));
  assert(fallback,'Failed Internet connection has no fallback');fallback.click();await pause();
  assert(host.querySelector('.mode-cards'),'Connection choices did not open after failure');
  assert(!measureAudit().overflow,'Connection choices overflow');
  return {passed:true,language:getLanguage(),width:innerWidth,defaultStyle:'names',fallbackAfterFailure:true};
}
