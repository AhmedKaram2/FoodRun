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
  const props = { data: screen === 'room' || options.room ? roomData : data, onBack: () => {}, openRoom: () => {}, setPage: () => {} };
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
  Object.getOwnPropertyDescriptor(node instanceof HTMLSelectElement ? HTMLSelectElement.prototype : node instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype, 'value').set.call(node, value);
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
  setValue(eta, '30 minutes'); await pause();
  const whatsapp = new URL(host.querySelector('a[href^="https://wa.me/"]').href);
  assert(whatsapp.pathname === '/971501234567', 'WhatsApp did not target the restaurant');
  assert(whatsapp.searchParams.get('text').includes('30 minutes'), 'Expected delivery is missing from message');
  assert(host.querySelector('.restaurant-phone').textContent.includes('+971'), 'Restaurant phone hidden');
  assert(button('Copy phone number'), 'Phone copy action missing');
  button('Order sent · save expected arrival').click(); await pause();
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
  return { passed: ['known person without wheel', 'restaurant phone and copy', 'correct WhatsApp recipient', 'ETA in message', 'direct placement', 'wallet mark paid', 'top payment confirmation', 'visible receiving details', 'optional breakdown'], ...measureAudit() };
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
  host.querySelector('#profile-details').open=true;
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
