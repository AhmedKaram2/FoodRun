// Browser-only fixture harness. Run with Vite in an isolated browser profile:
// await (await import('/test/browserAudit.jsx')).runCreateAudit()
// It captures commands locally; no account, restaurant order, or payment is sent.
import React from 'react';
import { createRoot } from 'react-dom/client';
import { CreateRoom, Home, ProfileScreen, RoomScreen, loadRestaurants, storeRestaurants } from '../src/foodrun/FoodRunApp.jsx';
import RestaurantLibraryScreen from '../src/foodrun/RestaurantLibraryScreen.jsx';
import { t } from '../src/foodrun/i18n.js';

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
export async function mountAudit(screen = 'create', phase = 'LOBBY') {
  document.getElementById('root').style.display = 'none';
  root?.unmount(); host?.remove();
  host = document.createElement('div'); host.id = 'audit-root'; document.body.append(host);
  root = createRoot(host);
  const restaurant = loadRestaurants().find(value => value.id === 'builtin-laffah-al-qasba');
  const room = { id: 'audit-room', name: 'Audit room', code: '123456', ownerId: 'me', payerId: 'me', restaurant, phase, orderNumber: 1,
    members: [{ id: 'me', name: 'Audit User', approved: true, participating: true, eligible: true, ready: true }],
    carts: [], transfers: [], audit: [], expectedNames: [], restaurantOptions: [restaurant], restaurantVotes: [], restaurantPollOpen: false,
    fees: { delivery: 0, service: 0, discount: 0 }, adjustment: 0, billRevision: 1, billApprovals: [], quoteRevision: 1, revision: 1,
    restaurantPaid: false, deliveryMode: false, destination: '', account: null, preparationId: '', preparedIds: [], pastSpins: [], spin: null,
  };
  const roomData = { ...data, sessions: { [room.id]: { roomId: room.id, roomName: room.name, memberId: 'me' } },
    rooms: { [room.id]: { room, receipts: [], history: [], serverTime: Date.now() } }, online: { [room.id]: true } };
  const props = { data: screen === 'room' ? roomData : data, onBack: () => {}, openRoom: () => {}, setPage: () => {} };
  root.render(screen === 'library' ? <RestaurantLibraryScreen language={document.documentElement.lang === 'ar' ? 'ar' : 'en'} {...props} />
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
  Object.getOwnPropertyDescriptor(node instanceof HTMLSelectElement ? HTMLSelectElement.prototype : HTMLInputElement.prototype, 'value').set.call(node, value);
  node.dispatchEvent(new Event(node instanceof HTMLSelectElement ? 'change' : 'input', { bubbles: true }));
}
const button = text => [...host.querySelectorAll('button')].find(node => node.textContent === t(text));
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
