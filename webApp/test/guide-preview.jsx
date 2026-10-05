// Development-only capture harness. Vite does not include this entry in the production build.
import React, { useState } from 'react';
import { createRoot } from 'react-dom/client';
import '../src/foodrun/foodrun.css';

const params = new URLSearchParams(location.search), language = params.get('lang') === 'ar' ? 'ar' : 'en', screen = params.get('screen') || 'sign-in';
localStorage.setItem('foodrun-language-v1', language);
document.documentElement.lang = language; document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
const { AuthScreen, CreateRoom, RoomScreen, Home } = await import('../src/foodrun/FoodRunApp.jsx');
const { setLanguage } = await import('../src/foodrun/i18n.js');
setLanguage(language);
localStorage.setItem('foodrun-copy-language-v1', language);
const { CreatePaymentRoom } = await import('../src/foodrun/PaymentRoom.jsx');
const { default: FeedbackBanner } = await import('../src/foodrun/FeedbackBanner.jsx');
const { useFeedback } = await import('../src/foodrun/useFeedback.js');
const names = language === 'ar' ? ['عمر','سارة','نور'] : ['Omar','Sara','Nour'];
const restaurant = {
  id: 'guide-kitchen', name: 'Neighborhood Kitchen', nameAr: 'مطبخ الحي', branchName: '', currency: 'AED', revision: 1,
  contact: { phoneE164: '+971500000000', whatsappE164: null, address: language === 'ar' ? 'عنوان المطعم' : 'Restaurant address' },
  pricing: { taxTreatment: 'included', taxRateBasisPoints: null, defaultDeliveryFeeMinor: 0, defaultServiceFeeMinor: 0, minimumOrderMinor: 0 },
  openOrdering: true, notes: '', emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Al Majaz', areaAr: 'المجاز', cuisine: '', mealTypes: ['lunch'],
  menu: { categories: [{ id: 'sandwiches', name: 'Sandwiches', nameAr: 'سندويشات', sortOrder: 0 }], optionGroups: [],
    items: [{ id: 'falafel', categoryId: 'sandwiches', name: 'Falafel sandwich', nameAr: 'سندويش فلافل', description: '', basePriceMinor: 1200, variants: [], optionGroupIds: [], available: true, sortOrder: 0 },
      { id: 'shawarma', categoryId: 'sandwiches', name: 'Chicken shawarma', nameAr: 'شاورما فراخ', description: '', basePriceMinor: 1800, variants: [], optionGroupIds: [], available: true, sortOrder: 1 }] },
};
localStorage.setItem('foodrun-restaurants-v1', JSON.stringify([restaurant]));
const members = names.map((name, i) => ({ id: `member-${i}`, name, approved: true, eligible: true, ready: true, participating: true, removed: false, guest: false }));
const account = { id: 'example-account', holder: names[1], bank: 'Example Bank', identifier: 'AE07 0331 2345 6789 0123 456', currency: 'AED', version: 1, method: 'BANK' };
const room = { id: 'guide-room', code: '123456', ownerId: members[0].id, name: language === 'ar' ? 'غدا المجموعة' : 'Lunch with the group', restaurant,
  expectedNames: [], deliveryMode: false, destination: '', deadline: 0, fees: { delivery: 0, service: 0, discount: 0, proportionalDelivery: false, automaticDelivery: false },
  phase: screen === 'selection' ? 'ACCEPTING' : screen === 'collector' ? 'REVIEW' : ['pay','settle'].includes(screen) ? 'FULFILLED' : 'COLLECTING',
  members, carts: members.map((member, i) => ({ memberId: member.id, revision: 1, submitted: true, confirmedQuote: 1,
    lines: [{ id: `line-${i}`, itemId: i === 2 ? 'shawarma' : 'falafel', quantity: 1, variantId: null, optionIds: [], notes: '', description: '', unitPrice: null }] })),
  revision: 1, quoteRevision: 1, preparationId: '', preparedIds: [], spin: screen === 'selection' ? { id: 'guide-spin', memberIds: members.map(m => m.id), winnerId: members[1].id, startAt: Date.now() - 7000, duration: 6500, turns: 7, weights: [80,80,80] } : null,
  pastSpins: [], payerId: members[1].id, account, accounts: [account], transfers: [], audit: [], restaurantReference: '', restaurantPaid: ['pay','settle'].includes(screen),
  createdAt: Date.now(), updatedAt: Date.now(), billRevision: 1, adjustment: 0, adjustmentApprovals: [], orderNumber: 1, restaurantOptions: [restaurant], restaurantVotes: [], restaurantPollOpen: false,
  paymentRoom: null, selectionStyle: 'wheel',
};
const receipts = members.map((member, i) => ({ memberId: member.id, name: member.name, currency: 'AED', revision: 1,
  lines: [{ description: language === 'ar' ? i === 2 ? 'شاورما فراخ' : 'سندويش فلافل' : i === 2 ? 'Chicken shawarma' : 'Falafel sandwich', itemId: i === 2 ? 'shawarma' : 'falafel', quantity: 1, amount: i === 2 ? 1800 : 1200, notes: '', variantId: null, optionIds: [] }],
  food: i === 2 ? 1800 : 1200, delivery: 0, service: 0, tax: 0, discount: 0, total: i === 2 ? 1800 : 1200, paid: i === 1 ? 1200 : 0, balance: i === 1 ? 0 : i === 2 ? 1800 : 1200 }));
const actor = ['selection','collector','settle'].includes(screen) ? members[1] : members[0];
const reply = { ok: true, room, memberId: actor.id, receipts: actor.id === room.payerId ? receipts : receipts.filter(r => r.memberId === actor.id), history: [], historyNextOffset: -1, serverTime: Date.now(), progress: { canReview: screen === 'collector', reviewBlocker: '', canArchive: false, archiveBlocker: 'Waiting for payments' } };
const data = { user: { uid: 'guide-user', email: 'guide@example.test', emailVerified: true }, hub: 'https://example.test', identityToken: '',
  home: { profile: { userId: 'guide-user', name: actor.name, phone: '+971500000000', language, payment: account, paymentAccounts: [account], favoriteOrders: [], photo: '', discoverable: true },
    people: members.filter(m => m.id !== actor.id).map(m => ({ userId: m.id, name: m.name, photo: '' })), invitations: [], rooms: [], deletedRoomIds: [] },
  sessions: { [room.id]: { roomId: room.id, roomName: room.name, memberId: actor.id, token: 'example-session' } }, rooms: { [room.id]: reply }, online: { [room.id]: true },
  busy: false, error: '', notice: '', hasPending: false, roomBlocks: {}, paymentReminderTimes: {}, paymentReminderStates: {},
  send: async () => null, setNotice: () => {}, setError: () => {}, checkPaymentReminder: () => {}, loadOlderHistory: null };
function FeedbackPreview() {
  const feedback = useFeedback();
  const [pending] = useState(true);
  window.guideFeedback = feedback;
  return <main className="app-shell"><h1>Feedback preview</h1><button id="success" onClick={() => feedback.setNotice('Payment recorded.')}>Success</button><button id="failure" onClick={() => feedback.setError('Connection failed. Your request is saved.')}>Failure</button>
    <p id="retained-error">{feedback.error}</p><button id="saved-request" disabled={!pending}>Retry saved request</button>
    {feedback.feedback && <FeedbackBanner key={feedback.feedback.id} feedback={feedback.feedback} onDismiss={feedback.dismissFeedback} retry={() => {}} />}</main>;
}
let content;
if (screen === 'feedback') content = <FeedbackPreview />;
else if (screen === 'sign-in') content = <AuthScreen ready={false} />;
else if (screen === 'home') content = <Home data={data} setPage={() => {}} openRoom={() => {}} />;
else if (screen === 'join' || screen === 'create') content = <CreateRoom data={data} mode={screen} inviteCode={screen === 'join' ? '123456' : ''} onBack={() => {}} openRoom={() => {}} />;
else if (screen === 'payment-room') content = <CreatePaymentRoom data={data} onBack={() => {}} openRoom={() => {}} openProfile={() => {}} />;
else content = <RoomScreen data={data} roomId={room.id} onBack={() => {}} />;
createRoot(document.getElementById('root')).render(content);
requestAnimationFrame(() => requestAnimationFrame(() => { window.guideReady = true; }));
