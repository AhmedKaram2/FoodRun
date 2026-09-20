import { canAccessAdmin } from './adminAccess';
import { addMenuLine, changeQuantity, menuLineTotal } from './cartEditing';
import { t, setLanguage as setTranslationLanguage } from './i18n.js';
import { selectionKey, uniquePreviousOrders, uniqueRoomPreviousOrders, userDashboard } from './orderHistory';
import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react';
import {
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
} from 'firebase/auth';
import { auth, googleProvider } from '../firebase';
import { QRCodeSVG } from 'qrcode.react';
import { amount, hubAddress, money, photoData } from './client';
import { useFoodRun } from './useFoodRun';
import { polarPoint, spinRotation, WHEEL_PALETTE, wheelLabel, wheelSlicePath } from './wheel';
import builtInRestaurants from './builtInRestaurants.json';
import { validateMenu } from './menuValidation';

const AdminApp = lazy(() => import('./AdminApp'));
const RestaurantLibraryScreen = lazy(() => import('./RestaurantLibraryScreen'));
const OfflineReceipts = lazy(() => import('./ReceiptArchiveScreen.jsx'));

const phaseLabel = {
  LOBBY: 'Gathering', PREPARING_SPIN: 'Getting ready', SPINNING: 'Selecting',
  ACCEPTING: 'Waiting for acceptance', COLLECTING: 'Collecting food', REVIEW: 'Confirming totals',
  PLACED: 'Order placed', FULFILLED: 'Food arrived', ARCHIVED: 'Complete', CANCELLED: 'Cancelled',
};

const ANDROID_DOWNLOAD_URL = 'https://github.com/AhmedKaram2/FoodRun/releases/download/v1.3/FoodRun-Android-1.3.apk';
const IOS_STORE_URL = import.meta.env.VITE_FOODRUN_IOS_URL?.trim() || '';
const PUBLIC_API_URL = import.meta.env.VITE_FOODRUN_API_URL?.trim().replace(/\/$/, '') || 'https://foodrun-api-q6b9.onrender.com';
const RESTAURANT_LIBRARY_KEY = 'foodrun-restaurants-v1';
const SERVER_CATALOG_KEY = 'foodrun-server-catalog-v1';
const ACTIVE_ROOM_KEY = 'foodrun-active-room-v1';
const LANGUAGE_KEY = 'foodrun-language-v1';
let uiLanguage = localStorage.getItem(LANGUAGE_KEY) === 'ar' ? 'ar' : 'en';
let updateLanguage = () => {};

function tx(english, arabic) { return uiLanguage === 'ar' ? arabic : english; }
function localizedName(value) { return uiLanguage === 'ar' && value?.nameAr ? value.nameAr : value?.name || ''; }
function localizedDescription(value) { return uiLanguage === 'ar' && value?.descriptionAr ? value.descriptionAr : value?.description || ''; }
function LanguageToggle() {
  return <label className="language-picker" title={tx('Choose language', 'اختر اللغة')}>
    <svg aria-hidden="true" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6"><circle cx="12" cy="12" r="9" /><ellipse cx="12" cy="12" rx="4" ry="9" /><path d="M3 12h18M5 6.5h14M5 17.5h14" /></svg>
    <span className="sr-only">Language / اللغة</span>
    <select aria-label="Language / اللغة" value={uiLanguage} onChange={event => updateLanguage(event.target.value)} dir="ltr">
      <option value="en" lang="en">English</option><option value="ar" lang="ar">العربية</option>
    </select>
    <svg className="language-chevron" aria-hidden="true" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="2"><path d="m4 6 4 4 4-4" /></svg>
  </label>;
}

function uid() { return crypto.randomUUID(); }
function clone(value) { return JSON.parse(JSON.stringify(value)); }
function uaePhone(value, mobileOnly = false) {
  const digits = String(value || '').replace(/\D/g, '');
  const local = digits.startsWith('00971') ? digits.slice(5) : digits.startsWith('971') ? digits.slice(3) : digits.startsWith('0') ? digits.slice(1) : digits;
  const valid = mobileOnly ? /^5\d{8}$/.test(local) : /^(?:[2-9]\d{7}|5\d{8})$/.test(local);
  if (!valid) throw Error(mobileOnly ? 'Enter a UAE mobile number, for example +971 50 123 4567.' : 'Enter a UAE phone number, for example +971 4 123 4567.');
  return `+971${local}`;
}
function minorInput(value = 0, currency = 'AED') {
  const digits = ['KWD', 'BHD', 'OMR'].includes(currency) ? 3 : currency === 'JPY' ? 0 : 2;
  return (value / 10 ** digits).toFixed(digits);
}
function blankRestaurant() {
  return {
    id: uid(), name: '', branchName: '', currency: 'AED',
    contact: { phoneE164: null, whatsappE164: null, address: null },
    pricing: { taxTreatment: 'included', taxRateBasisPoints: null, defaultDeliveryFeeMinor: 0, defaultServiceFeeMinor: 0, minimumOrderMinor: 0 },
    notes: '', menu: { categories: [], optionGroups: [], items: [] }, openOrdering: true,
  };
}
function normalizeRestaurant(value) {
  if (!value || typeof value !== 'object' || !String(value.id || '').trim() || !String(value.name || '').trim()) throw Error(t("Restaurant ID and name are required."));
  const currency = String(value.currency || 'AED').toUpperCase();
  if (currency !== 'AED') throw Error(t("Restaurant menus and rooms use AED (Dirham)."));
  const menu = value.menu || {};
  const categories = Array.isArray(menu.categories) ? menu.categories : [];
  const optionGroups = Array.isArray(menu.optionGroups) ? menu.optionGroups : [];
  const items = Array.isArray(menu.items) ? menu.items : [];
  const categoryIds = new Set(categories.map(category => category.id));
  if (items.some(item => !categoryIds.has(item.categoryId))) throw Error(t("Every menu item must reference an existing category."));
  const defaults = blankRestaurant();
  return validateMenu({
    ...defaults, ...value, id: String(value.id).trim(), name: String(value.name).trim(), currency,
    contact: { address: null, ...(value.contact || {}),
      phoneE164: value.contact?.phoneE164 ? uaePhone(value.contact.phoneE164) : null,
      whatsappE164: value.contact?.whatsappE164 ? uaePhone(value.contact.whatsappE164, true) : null },
    pricing: { ...defaults.pricing, ...(value.pricing || {}) },
    menu: { categories, optionGroups: optionGroups.map(group => ({ minSelections: 0, maxSelections: 1, ...group })), items: items.map(item => ({ variants: [], optionGroupIds: [], available: true, ...item })) },
    openOrdering: Boolean(value.openOrdering || items.length === 0),
  });
}
function loadRestaurants() {
  let saved = [];
  let managedIds = [];
  try { saved = JSON.parse(localStorage.getItem(RESTAURANT_LIBRARY_KEY) || '[]').map(normalizeRestaurant); }
  catch { saved = []; }
  try { managedIds = JSON.parse(localStorage.getItem(SERVER_CATALOG_KEY) || '[]'); } catch { managedIds = []; }
  return [...saved, ...builtInRestaurants.map(normalizeRestaurant).filter(builtIn => !managedIds.includes(builtIn.id) && !saved.some(restaurant => restaurant.id === builtIn.id))]
    .sort((left, right) => left.name.localeCompare(right.name));
}
function storeRestaurants(restaurants) {
  localStorage.setItem(RESTAURANT_LIBRARY_KEY, JSON.stringify(restaurants));
}
function restaurantExport(restaurant) {
  return JSON.stringify({ schema: 'foodrun.restaurant', schemaVersion: 1, exportId: restaurant.id, revision: 1, restaurant }, null, 2);
}
function parseRestaurantExport(text) {
  const value = JSON.parse(text);
  if (value.schema !== 'foodrun.restaurant' || value.schemaVersion !== 1 || !value.restaurant) throw Error('Use a Food Run restaurant schema version 1 file.');
  return normalizeRestaurant(value.restaurant);
}
async function copyText(text) {
  if (navigator.clipboard?.writeText) return navigator.clipboard.writeText(text);
  const field = document.createElement('textarea'); field.value = text; field.style.position = 'fixed'; field.style.opacity = '0';
  document.body.appendChild(field); field.select(); document.execCommand('copy'); field.remove();
}
function roomInviteLink(room, hub) {
  const url = new URL(window.location.origin + window.location.pathname);
  url.searchParams.set('room', room.code);
  url.searchParams.set('hub', hub);
  return url.toString();
}
function displayQuantity(quantity, description) {
  return /[\u0600-\u06ff]/.test(description) ? String(quantity).replace(/\d/g, digit => '٠١٢٣٤٥٦٧٨٩'[Number(digit)]) : String(quantity);
}
function combinedOrderText(room, receipts) {
  const grouped = new Map();
  receipts.flatMap(receipt => receipt.lines).forEach(line => {
    const item = room.restaurant.menu.items.find(value => value.id === line.itemId);
    const variant = item?.variants.find(value => value.id === line.variantId);
    const options = room.restaurant.menu.optionGroups.flatMap(group => group.options).filter(value => (line.optionIds || []).includes(value.id));
    const description = item && uiLanguage === 'ar' ? [localizedName(item), variant && localizedName(variant), ...options.map(localizedName)].filter(Boolean).join(' · ') : line.description;
    const key = `${description}\u0000${line.notes || ''}`;
    const previous = grouped.get(key) || { ...line, description, quantity: 0 };
    grouped.set(key, { ...previous, quantity: previous.quantity + line.quantity });
  });
  const lines = [...grouped.values()].map(line => `${displayQuantity(line.quantity, line.description)} ${line.description}${line.notes ? ` — ${line.notes}` : ''}`);
  return [localizedName(room.restaurant), room.deliveryMode ? `${tx('Delivery', 'توصيل')}: ${room.destination || tx('Address to be confirmed', 'العنوان يحدد لاحقاً')}` : tx('Pickup', 'استلام من المطعم'), '', ...lines].join('\n');
}
function receiptText(room, receipt) {
  const account = room.account ? `Pay to: ${room.account.holder} · ${room.account.bank}\n${room.account.identifier}` : 'Receiving account not shared yet';
  return [`Food Run · ${room.name} · order #${room.orderNumber}`, room.restaurant.name, receipt.name,
    ...receipt.lines.map(line => `${line.quantity} × ${line.description} · ${money(line.amount, receipt.currency)}${line.notes ? ` — ${line.notes}` : ''}`),
    `Total: ${receipt.totalText}`, `Paid: ${money(receipt.paid, receipt.currency)}`, `To pay: ${receipt.balanceText}`, account].join('\n');
}

function downloadText(name, text) {
  const url = URL.createObjectURL(new Blob([text], { type: 'application/json' }));
  const link = document.createElement('a'); link.href = url; link.download = name; link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}

function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'FR';
}

function Avatar({ profile, small = false }) {
  return profile?.photo
    ? <img className={`avatar ${small ? 'small' : ''}`} src={profile.photo} alt="" />
    : <span className={`avatar initials ${small ? 'small' : ''}`}>{initials(profile?.name)}</span>;
}

function LiveSelectionWheel({ spin, members, serverTime, active }) {
  const candidates = spin.memberIds.map(id => members.find(member => member.id === id)).filter(Boolean);
  const winnerIndex = spin.memberIds.indexOf(spin.winnerId);
  const finalRotation = spinRotation(spin, spin.startAt + spin.duration);
  const round = useRef(spin);
  const clock = useRef({ serverTime: Number(serverTime) || Date.now(), monotonicTime: performance.now() });
  const initialNow = clock.current.serverTime;
  const [reduceMotion, setReduceMotion] = useState(false);
  const [motion, setMotion] = useState(() => ({
    rotation: active ? spinRotation(spin, Number(serverTime) || Date.now()) : finalRotation,
    stage: !active || initialNow >= spin.startAt + spin.duration ? 'finished' : initialNow < spin.startAt ? 'waiting' : 'spinning',
  }));

  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const changed = () => setReduceMotion(media.matches);
    changed(); media.addEventListener?.('change', changed);
    return () => media.removeEventListener?.('change', changed);
  }, []);

  useEffect(() => {
    if (!active) {
      setMotion({ rotation: finalRotation, stage: 'finished' });
      return undefined;
    }
    if (reduceMotion) {
      const now = clock.current.serverTime + performance.now() - clock.current.monotonicTime;
      setMotion({ rotation: 0, stage: now < round.current.startAt ? 'waiting' : 'spinning' });
      return undefined;
    }
    let frame = 0;
    const draw = () => {
      const now = clock.current.serverTime + performance.now() - clock.current.monotonicTime;
      const spinning = now < round.current.startAt + round.current.duration;
      const stage = !spinning ? 'finished' : now < round.current.startAt ? 'waiting' : 'spinning';
      setMotion({ rotation: spinRotation(round.current, now), stage });
      if (spinning) frame = requestAnimationFrame(draw);
    };
    draw();
    return () => cancelAnimationFrame(frame);
  }, [active, finalRotation, reduceMotion]);

  const count = candidates.length;
  const slice = 360 / Math.max(count, 1);
  const labelSize = count <= 4 ? 18 : count <= 7 ? 15 : 12;
  return <div className={`live-wheel is-${motion.stage}`}>
    <svg viewBox="0 0 400 410" role="img" aria-label={motion.stage === 'finished' ? `${candidates[winnerIndex]?.name || 'Someone'} was selected` : `Selecting one of ${count} people`}>
      <circle className="wheel-aura" cx="200" cy="200" r="198" />
      <circle className="wheel-shadow" cx="200" cy="209" r="188" />
      <circle className="wheel-rim" cx="200" cy="200" r="188" />
      {Array.from({ length: 36 }, (_, index) => {
        const [x, y] = polarPoint(index * 10 - 90, 180);
        return <circle className={index % 3 === 0 ? 'rim-dot strong' : 'rim-dot'} cx={x} cy={y} r="2.3" key={index} />;
      })}
      <g className="wheel-turntable" style={{ transform: `rotate(${motion.rotation}deg)` }}>
        {count === 1
          ? <circle className={`wheel-slice ${motion.stage === 'finished' ? 'selected' : ''}`} cx="200" cy="200" r="172" fill={WHEEL_PALETTE[0]} />
          : candidates.map((candidate, index) => <path className={`wheel-slice ${motion.stage === 'finished' && index === winnerIndex ? 'selected' : ''}`} d={wheelSlicePath(index, count)} fill={WHEEL_PALETTE[index % WHEEL_PALETTE.length]} key={candidate.id} />)}
        {candidates.map((candidate, index) => {
          const angle = -90 + index * slice;
          const [x, y] = polarPoint(angle, 113);
          return <text className="wheel-person-name" x={x} y={y} fontSize={labelSize} textAnchor="middle" dominantBaseline="middle" transform={`rotate(${angle + 180} ${x} ${y})`} key={candidate.id}>{wheelLabel(candidate.name)}</text>;
        })}
        <circle className="wheel-inner-outline" cx="200" cy="200" r="172" />
      </g>
      <circle className="wheel-hub-shadow" cx="200" cy="205" r="43" />
      <circle className="wheel-hub" cx="200" cy="200" r="40" />
      <text className="wheel-hub-icon" x="200" y="196" textAnchor="middle">🥡</text>
      <text className="wheel-hub-label" x="200" y="219" textAnchor="middle">FOOD RUN</text>
      <g className="wheel-pointer"><path d="M184 7 Q184 2 190 2 L200 36 Q202 42 205 36 L216 7 Q217 2 211 2 Z" /></g>
    </svg>
  </div>;
}

function WinnerReveal({ winner, selected, me, room, data }) {
  return <div className="winner-reveal" aria-live="polite">
    <div className="winner-confetti" aria-hidden="true">{Array.from({ length: 22 }, (_, index) => <i style={{ '--confetti-x': `${(index * 43) % 100}%`, '--confetti-delay': `${(index % 7) * -0.12}s`, '--confetti-color': WHEEL_PALETTE[index % WHEEL_PALETTE.length] }} key={index} />)}</div>
    <div className="winner-food-icon" aria-hidden="true"><span>🥡</span><b>★</b></div>
    <p className="eyebrow">{t("TODAY’S PICK")}</p>
    <h2>{winner.name}{t("is ordering!")}</h2>
    <p>{t("Everyone sees the same selected person live.")}</p>
    {selected && winner.id === me.id && <div className="hero-actions"><button className="primary" onClick={() => data.send('ACCEPT_DUTY', {}, room.id)}>{t("I’ll take care of it")}</button><button className="secondary" onClick={() => data.send('DECLINE_DUTY', { text: 'Unavailable this time' }, room.id)}>{t("I can’t this time")}</button></div>}
  </div>;
}

function AppDownloads({ compact = false }) {
  const [iosHelp, setIosHelp] = useState(false);
  return <section id="app-downloads" className={`app-downloads ${compact ? 'compact' : ''}`}>
    <div className="download-heading">
      <div><p className="eyebrow">{t("FOOD RUN ON YOUR PHONE")}</p><h2>{t("Take the table with you.")}</h2></div>
      <p>{t("Use the same account and join Internet Rooms from Android, iPhone, or the web.")}</p>
    </div>
    <div className="download-grid">
      <article className="download-card">
        <span className="platform-icon android" aria-hidden="true">◆</span>
        <div><strong>{t("Android app")}</strong><small>{t("Version 1.3 · Android 8+")}</small></div>
        <a className="primary store-button" href={ANDROID_DOWNLOAD_URL}>{t("Download APK")}</a>
      </article>
      <article className="download-card">
        <span className="platform-icon apple" aria-hidden="true">●</span>
        <div><strong>{t("iPhone app")}</strong><small>{IOS_STORE_URL ? 'Available for iPhone and iPad' : 'Install from Safari · iOS 17+'}</small></div>
        {IOS_STORE_URL
          ? <a className="secondary store-button" href={IOS_STORE_URL}>{t("Open App Store")}</a>
          : <button className="secondary store-button" onClick={() => setIosHelp(value => !value)}>{t("Install on iPhone")}</button>}
      </article>
    </div>
    {iosHelp && <div className="ios-install" role="status"><b>{t("On iPhone or iPad:")}</b>{t("open this page in Safari, tap the Share button, then choose")}<b>{t("Add to Home Screen")}</b>{t("and")}<b>{t("Add")}</b>.</div>}
  </section>;
}

function AuthScreen({ ready, allowRegistration = true }) {
  const [register, setRegister] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async event => {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      if (register) {
        const result = await createUserWithEmailAndPassword(auth, email.trim(), password);
        await updateProfile(result.user, { displayName: name.trim() });
      } else await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (error) { setMessage(error.message.replace(/^Firebase: /, '')); }
    finally { setBusy(false); }
  };
  const reset = async () => {
    if (!email.trim()) return setMessage('Enter your email first.');
    try { await sendPasswordResetEmail(auth, email.trim()); setMessage('Password reset email requested.'); }
    catch (error) { setMessage(error.message.replace(/^Firebase: /, '')); }
  };
  return <main className="auth-shell">
    <section className="auth-story">
      <LanguageToggle />
      <div className="brand-mark">FR</div>
      <p className="eyebrow">{t("FOOD RUN / TOGETHER")}</p>
      <h1>{t("Good food.")}<br />{t("Better together.")}</h1>
      <p>{t("Pick who will order, collect everyone’s food live, and settle every share without the group-chat chaos.")}</p>
      <div className="story-steps"><span>{t("01 Join")}</span><span>{t("02 Select")}</span><span>{t("03 Order")}</span><span>{t("04 Settle")}</span></div>
      <AppDownloads compact />
    </section>
    <section className="auth-card card">
      <p className="eyebrow">{t("YOUR TABLE AWAITS")}</p>
      <h2>{register ? 'Create your profile' : 'Welcome back'}</h2>
      <p className="muted">{t("Use the same account you already use in Intrvioo.")}</p>
      <button className="google" disabled={!ready || busy} onClick={() => signInWithPopup(auth, googleProvider).catch(e => setMessage(e.message))}>{t("Continue with Google")}</button>
      <div className="or"><span />{t("or")}<span /></div>
      <form onSubmit={submit} className="stack">
        {register && <label>{t("Name")}<input value={name} onChange={e => setName(e.target.value)} required /></label>}
        <label>{t("Email")}<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>{t("Password")}<input type="password" minLength="6" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {message && <p className="form-message" role="alert">{message}</p>}
        <button className="primary" disabled={!ready || busy}>{busy ? 'One moment…' : register ? t("Create account") : t("Sign in")}</button>
      </form>
      {!register && <button className="link" onClick={reset}>{t("Forgot password?")}</button>}
      {(allowRegistration || register) && <button className="link switch" onClick={() => { setRegister(!register); setMessage(''); }}>{register ? 'Already registered? Sign in' : 'New to Food Run? Create account'}</button>}
      {!allowRegistration && <p className="form-message">{t("New registration is temporarily disabled by the administrator.")}</p>}
    </section>
  </main>;
}

function HubScreen({ current, connect, error }) {
  const [address, setAddress] = useState(current || 'https://192.168.1.20:8443');
  const [message, setMessage] = useState('');
  const publicApi = PUBLIC_API_URL;
  const submit = event => {
    event.preventDefault();
    try { connect(hubAddress(address)); setMessage(''); }
    catch (e) { setMessage(e.message); }
  };
  return <main className="center-shell"><section className="card setup-card">
    <div className="brand-mark">FR</div><p className="eyebrow">{t("CONNECT YOUR TABLE")}</p>
    <h1>{t("Where should this room live?")}</h1>
    <p className="muted">{t("Use a nearby hub to keep live traffic on your local network, or use the internet API so everyone can join from anywhere.")}</p>
    <div className="mode-cards">
      <button type="button" className="mode-card" onClick={() => setAddress('https://192.168.1.20:8443')}>
        <strong>{t("Nearby hub")}</strong><span>{t("Lowest cloud use")}</span>
      </button>
      <button type="button" className="mode-card" disabled={!publicApi} onClick={() => publicApi && connect(publicApi)}>
        <strong>{t("Internet room")}</strong><span>{publicApi ? t("Join from any network") : 'Set VITE_FOODRUN_API_URL'}</span>
      </button>
    </div>
    <form className="stack" onSubmit={submit}>
      <label>{t("Nearby hub address")}<input value={address} onChange={e => setAddress(e.target.value)} placeholder="https://192.168.1.20:8443" /></label>
      {(message || error) && <p className="form-message" role="alert">{message || error}</p>}
      <button className="primary">{t("Connect to nearby hub")}</button>
    </form>
    <p className="fine">{t("Food Run remembers the API used to create each room. Android, iOS, and web use the same command API and event-driven WebSocket. Firebase holds the shared account and asynchronous backup.")}</p>
  </section></main>;
}

function profileWithFavorites(profile, favoriteOrders) {
  return { ...profile, userId: '', favoriteOrders };
}

function favoriteKey(favorite) {
  return selectionKey(favorite.restaurantId, favorite.lines.map(line => ({ ...line, description: line.description || '' })));
}

function orderSummary(lines, max = 3) {
  const text = lines.slice(0, max).map(line => `${line.quantity} × ${line.label || line.description}`).join(' · ');
  return text + (lines.length > max ? ` · +${lines.length - max} more` : '');
}

function favoriteFromPrevious(choice) {
  const lines = choice.receipt.lines.map(line => ({
    itemId: line.itemId || '', quantity: line.quantity, variantId: line.variantId || null,
    optionIds: line.optionIds || [], notes: line.notes || '', description: line.itemId ? '' : line.description,
    label: line.description,
  }));
  return {
    id: uid(), restaurantId: choice.restaurantId, restaurantName: choice.order.restaurantName,
    title: orderSummary(lines, 2).slice(0, 160), lines, savedAt: Date.now(),
  };
}

function UserDashboard({ data, openRoom, compact = false }) {
  const dashboard = userDashboard(data);
  return <section className={`user-dashboard ${compact ? 'compact' : ''}`}>
    <div className="section-title"><div><p className="eyebrow">{t("MY MONEY & ORDERS")}</p><h2>{t("Wallet dashboard")}</h2></div><span>{dashboard.entries.length}</span></div>
    <div className="dashboard-metrics"><article><small>{t("I need to pay")}</small><b>{money(dashboard.toPay, 'AED')}</b></article><article><small>{t("I need to receive")}</small><b>{money(dashboard.toReceive, 'AED')}</b></article><article><small>{t("Current rooms")}</small><b>{dashboard.currentOrders.length}</b></article></div>
    {dashboard.entries.length > 0 && <div className="grid two dashboard-actions">{dashboard.entries.map((entry, index) => <article className="card money-action" key={`${entry.roomId}:${entry.person}:${index}`}><span className={`status ${entry.kind === 'receive' ? 'live' : ''}`}>{entry.kind === 'refund' ? tx('Refund to send', 'مبلغ مرتجع للإرسال') : entry.kind === 'pay' ? tx('Payment due', 'دفعة مستحقة') : tx('Money to receive', 'مبلغ للاستلام')}</span><h3>{entry.person}</h3><strong>{money(entry.amount, entry.currency)}</strong><small>{entry.roomName}</small>{entry.pending && <p className="muted">{money(entry.pending.amount, entry.currency)} · {tx("Sent · awaiting recipient approval", "تم الإرسال · بانتظار موافقة المستلم")}</p>}<button className="primary" onClick={() => { openRoom(entry.roomId); setTimeout(() => document.getElementById('room-payment')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 100); }}>{entry.pending ? entry.kind === 'receive' ? tx("Review & approve receipt", "مراجعة وتأكيد الاستلام") : tx("View pending payment", "عرض الدفعة المعلقة") : entry.kind === 'pay' ? tx("Pay now / mark paid", "ادفع الآن / سجل الدفع") : t("Open payment")}</button></article>)}</div>}
    {!compact && <div className="card order-dashboard"><h3>{t("Current orders")}</h3>{dashboard.currentOrders.map(({ room }) => <button className="order-dashboard-row" onClick={() => openRoom(room.id)} key={room.id}><span><b>{room.name} · #{room.orderNumber}</b><small>{t(phaseLabel[room.phase])} · {localizedName(room.restaurant)}</small></span><strong>{t("Open →")}</strong></button>)}</div>}
  </section>;
}

function ProfileScreen({ data, onBack, openRoom }) {
  const { profile } = data.home;
  const busy = data.busy, send = data.send;
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({
    name: profile?.name || auth.currentUser?.displayName || '', phone: profile?.phone || '', photo: profile?.photo || '',
    discoverable: profile?.discoverable ?? true, method: profile?.payment?.method || 'AANI',
    holder: profile?.payment?.holder || '', bank: profile?.payment?.bank || '', identifier: profile?.payment?.identifier || '',
  });
  const set = (key, value) => setForm(old => ({ ...old, [key]: value }));
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      const payment = form.identifier.trim() ? {
      id: profile?.payment?.id || crypto.randomUUID(), holder: form.holder.trim() || form.name.trim(),
      bank: form.method === 'AANI' ? t("Aani") : form.bank.trim(), identifier: form.method === 'AANI' ? uaePhone(form.identifier, true) : form.identifier.trim(),
      currency: 'AED', version: profile?.payment?.version || 1, method: form.method,
    } : null;
      const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: {
        userId: '', name: form.name.trim(), phone: uaePhone(form.phone, true), photo: form.photo,
        payment, discoverable: form.discoverable, language: uiLanguage, favoriteOrders: profile?.favoriteOrders || [],
      } } });
      if (reply) onBack();
    } catch (error) { setMessage(error.message); }
  };
  const [previousLimit, setPreviousLimit] = useState(10);
  const favorites = profile?.favoriteOrders || [];
  const previous = uniquePreviousOrders(data);
  const favoriteKeys = new Set(favorites.map(favoriteKey));
  const saveFavorite = async choice => {
    if (!choice.restaurantId) return setMessage('Open a current room for this older restaurant before saving it as a favorite.');
    const favorite = favoriteFromPrevious(choice);
    if (favoriteKeys.has(favoriteKey(favorite))) return setMessage(t("This order is already in your favorites."));
    if (favorites.length >= 30) return setMessage(t("Your 30 favorites are full. Remove one before saving another."));
    const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: profileWithFavorites(profile, [favorite, ...favorites].slice(0, 30)) } });
    if (reply) setMessage(t("Favorite order saved."));
  };
  const removeFavorite = async id => {
    const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: profileWithFavorites(profile, favorites.filter(value => value.id !== id)) } });
    if (reply) setMessage(t("Favorite removed."));
  };
  return <Page title={t("Your profile")} subtitle={t("Your wallet, orders, favorites, and payment details in one place.")} onBack={onBack}>
    <UserDashboard data={data} openRoom={openRoom} />
    {message && <p className="form-message" role="status">{message}</p>}
    <nav className="profile-shortcuts" aria-label={t("Profile sections")}><a className="secondary" href="#favorite-orders">{tx('Favorites', 'المفضلة')}</a><a className="secondary" href="#previous-orders">{tx('Previous orders', 'الطلبات السابقة')}</a><a className="secondary" href="#profile-details" onClick={() => { document.getElementById('profile-details').open = true; }}>{tx('Edit profile', 'تعديل الملف')}</a></nav>
    <details id="profile-details" open={!profile?.name || !profile?.phone} className="profile-details"><summary>{tx('Profile and receiving details', 'الملف الشخصي وبيانات استلام الأموال')}</summary><form className="profile-grid" onSubmit={save}>
      <section className="card profile-photo">
        <Avatar profile={{ name: form.name, photo: form.photo }} />
        <label className="upload">{t("Choose photo")}<input type="file" accept="image/jpeg,image/png,image/webp" onChange={async e => { const file = e.target.files[0]; if (!file) return; try { set('photo', await photoData(file)); setMessage(''); } catch (error) { setMessage(error.message); } }} /></label>
        {form.photo && <button type="button" className="link" onClick={() => set('photo', '')}>{t("Remove photo")}</button>}
      </section>
      <section className="card stack">
        <h3>{t("About you")}</h3>
        <label>{t("Profile name")}<input value={form.name} onChange={e => set('name', e.target.value)} required maxLength="160" /></label>
        <label>{t("UAE mobile number")}<input type="tel" value={form.phone} onChange={e => set('phone', e.target.value)} required placeholder="050 123 4567" /></label>
        <label className="check"><input type="checkbox" checked={form.discoverable} onChange={e => set('discoverable', e.target.checked)} />{t("Let people on this hub invite me")}</label>
      </section>
      <section className="card stack payment-card">
        <div><p className="eyebrow">{t("GET PAID BACK")}</p><h3>{t("Receiving details")}</h3><p className="muted">{t("Shared with room members only after you are selected and choose to share it.")}</p></div>
        <div className="segmented"><button type="button" className={form.method === 'AANI' ? 'active' : ''} onClick={() => set('method', 'AANI')}>{t("Aani")}</button><button type="button" className={form.method === 'BANK' ? 'active' : ''} onClick={() => set('method', 'BANK')}>{t("Bank account")}</button></div>
        <label>{t("Account holder")}<input value={form.holder} onChange={e => set('holder', e.target.value)} placeholder={form.name || t("Your name")} /></label>
        {form.method === 'BANK' && <label>{t("Bank name")}<input value={form.bank} onChange={e => set('bank', e.target.value)} /></label>}
        <label>{form.method === 'AANI' ? t("UAE mobile registered with Aani") : t("IBAN / account number")}<input value={form.identifier} onChange={e => set('identifier', e.target.value)} placeholder={form.method === 'AANI' ? '050 123 4567' : 'AE…'} /></label>
      </section>
      <div className="form-actions"><button className="primary" disabled={busy}>{busy ? t("Saving…") : t("Save profile")}</button></div>
    </form></details>
    <section id="favorite-orders" className="profile-orders"><div className="section-title"><div><p className="eyebrow">{t("FAST REORDER")}</p><h2>{t("Favorite orders")}</h2></div><span>{favorites.length}</span></div>{favorites.length ? <div className="grid two">{favorites.map(favorite => <article className="card saved-order" key={favorite.id}><span className="status live">{t("★ Favorite")}</span><h3>{favorite.restaurantName}</h3><p>{orderSummary(favorite.lines)}</p><button className="link danger" onClick={() => removeFavorite(favorite.id)}>{t("Remove favorite")}</button></article>)}</div> : <div className="card empty small-empty"><span>☆</span><p>{t("Save a previous order below. It will appear inside matching restaurant rooms for one-tap add.")}</p></div>}</section>
    <section id="previous-orders" className="profile-orders"><div className="section-title"><div><p className="eyebrow">{t("ORDER HISTORY")}</p><h2>{t("Unique previous orders")}</h2><p>{t("Exact repeats are grouped; changed quantities, sizes, extras, or notes stay separate.")}</p></div><span>{previous.length}</span></div>{previous.length ? <div className="grid two">{previous.slice(0, previousLimit).map(choice => <article className="card saved-order" key={choice.key}><span className="status">{choice.repeatCount > 1 ? `Repeated ${choice.repeatCount} times` : new Date(choice.order.completedAt).toLocaleDateString()}</span><h3>{choice.order.restaurantName}</h3><p>{orderSummary(choice.receipt.lines)}</p>{!favoriteKeys.has(choice.key) && <button className="secondary" disabled={busy || !choice.restaurantId} onClick={() => saveFavorite(choice)}>{t("☆ Save as favorite")}</button>}</article>)}</div> : <div className="card empty small-empty"><span>🥡</span><p>{t("Completed orders appear here after a room starts its next order.")}</p></div>}{previous.length > previousLimit && <button className="secondary wide" onClick={() => setPreviousLimit(value => value + 10)}>{t("Show more unique orders")}</button>}</section>
  </Page>;
}

function Page({ title, subtitle, onBack, actions, children }) {
  return <main className="app-shell">
    <header className="topbar"><button className="wordmark" onClick={onBack}><span>FR</span> FOOD RUN</button><div className="top-actions"><LanguageToggle />{actions}</div></header>
    <div className="page-heading">{onBack && <button className="back" onClick={onBack}>{uiLanguage === 'ar' ? 'رجوع ←' : '← Back'}</button>}<p className="eyebrow">{t("FOOD RUN / TOGETHER")}</p><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {children}
  </main>;
}

function Home({ data, setPage, openRoom, allowRoomCreation = true }) {
  const { home, rooms, sessions, online } = data;
  const roomCards = Object.values(sessions).map(session => ({ session, reply: rooms[session.roomId] }));
  return <Page title={`Good food, ${home.profile.name?.split(' ')[0] || 'together'}.`} subtitle={t("Start a table or jump back into today’s order.")} actions={<><button className="icon-button" aria-label={t("Notifications")} onClick={async () => { try { if (!('Notification' in window)) return data.setNotice(tx('Notifications are unavailable in this browser. Room updates still appear here.', 'الإشعارات غير متاحة في هذا المتصفح. ستظهر تحديثات الغرفة هنا.')); const permission = await Notification.requestPermission(); data.setNotice(permission === 'granted' ? tx('Notifications enabled.', 'تم تفعيل الإشعارات.') : tx('Enable notifications in browser settings.', 'فعّل الإشعارات من إعدادات المتصفح.')); } catch { data.setNotice('Notifications are unavailable. Room updates still appear here.'); } }}>◔</button><button className="profile-chip" onClick={() => setPage('profile')}><Avatar small profile={home.profile} />{home.profile.name || 'Complete profile'}</button></>}>
    <section className="hero card"><div><p className="eyebrow">{t("A TABLE FOR EVERYONE")}</p><h2>{t("One room. The whole crew.")}</h2><p>{t("Everyone joins live, the wheel picks who orders, and every item and amount stays together.")}</p><div className="hero-actions"><button className="primary light" disabled={!allowRoomCreation} onClick={() => setPage('create')}>{t("Create a room")}</button><button className="secondary light" onClick={() => setPage('join')}>{t("Join with code")}</button><button className="secondary light" onClick={() => setPage('restaurants')}>{t("Restaurants & menus")}</button>{canAccessAdmin(data.user) && <button className="secondary light" onClick={() => setPage('admin')}>{tx('Admin panel', 'لوحة الإدارة')}</button>}</div>{!allowRoomCreation && <p className="form-message">{t("New room creation is temporarily disabled by the administrator.")}</p>}</div><div className="hero-art"><span>🥡</span><span>🍜</span><span>🥗</span></div></section>
    <UserDashboard data={data} openRoom={openRoom} compact />
    <AppDownloads />
    {home.invitations.length > 0 && <section><div className="section-title"><div><p className="eyebrow">{t("YOU’RE INVITED")}</p><h2>{t("Join the table")}</h2></div><span>{home.invitations.length}</span></div><div className="grid two">{home.invitations.map(invite => <article className="card invitation" key={invite.id}><span className="status live">{t("Invitation")}</span><h3>{invite.roomName}</h3><p>{invite.invitedBy}{t("invited you to order #")}{invite.orderNumber}.</p><button className="primary" onClick={async () => { const reply = await data.send('IDENTITY', { identity: { action: 'ACCEPT_INVITE', invitationId: invite.id } }); if (reply?.room) openRoom(reply.room.id); }}>{t("Join room")}</button></article>)}</div></section>}
    <section><div className="section-title"><div><p className="eyebrow">{t("YOUR TABLES")}</p><h2>{t("Live rooms")}</h2></div><span>{roomCards.length}</span></div>
      {roomCards.length ? <div className="grid two">{roomCards.map(({ session, reply }) => {
        const room = reply?.room, member = room?.members.find(m => m.id === session.memberId), winner = room?.members.find(m => m.id === (room.payerId || room.spin?.winnerId));
        const receipt = reply?.receipts.find(r => r.memberId === session.memberId);
        return <article className="card room-card" key={session.roomId} onClick={() => openRoom(session.roomId)}><div className="room-card-top"><span className={`status ${online[session.roomId] ? 'live' : ''}`}>{online[session.roomId] ? '● Live' : 'Offline'}</span><span>#{room?.orderNumber || 1}</span></div><h3>{session.roomName}</h3><p>{room ? t(phaseLabel[room.phase]) : 'Connecting…'}</p>{winner && <div className="winner-row"><span className="avatar initials small">{initials(winner.name)}</span><span><b>{winner.name}</b><small>{room.payerId ? ' is ordering' : ' was selected'}</small></span></div>}{receipt && ['REVIEW','PLACED','FULFILLED'].includes(room.phase) && <div className="amount-row"><span>{t("Your total")}</span><b>{receipt.totalText}</b></div>}<button className="secondary">{room?.phase === 'LOBBY' && !member?.participating ? t("Join this order") : 'Open room'} →</button></article>;
      })}</div> : <div className="card empty"><span>🥢</span><h3>{t("No saved rooms yet")}</h3><p>{t("Create a room and invite your people, or join with a six-digit code.")}</p></div>}
    </section>
  </Page>;
}

function CreateRoom({ data, mode, onBack, openRoom, inviteCode = '' }) {
  const profile = data.home.profile;
  const [restaurants, setRestaurants] = useState(loadRestaurants);
  const [form, setForm] = useState({ room: '', restaurantId: '', restaurant: '', phone: '', code: inviteCode, restaurantPoll: false, deliveryMode: false, destination: '', delivery: '0.00', service: '0.00', discount: '0.00', proportionalDelivery: false });
  const [message, setMessage] = useState('');
  const chosen = restaurants.find(restaurant => restaurant.id === form.restaurantId);
  const currency = chosen?.currency || 'AED';
  const chooseRestaurant = id => {
    const restaurant = restaurants.find(value => value.id === id);
    setForm(old => ({ ...old, restaurantId: id, delivery: restaurant ? minorInput(restaurant.pricing.defaultDeliveryFeeMinor, restaurant.currency) : '0.00', service: restaurant ? minorInput(restaurant.pricing.defaultServiceFeeMinor, restaurant.currency) : '0.00' }));
  };
  const submit = async event => {
    event.preventDefault(); setMessage('');
    try {
      let reply;
      if (mode === 'join') reply = await data.send('JOIN', { code: form.code.trim(), name: profile.name.trim() });
      else {
        const restaurant = chosen ? clone(chosen) : normalizeRestaurant({ ...blankRestaurant(), name: form.restaurant.trim(), contact: { phoneE164: form.phone.trim(), whatsappE164: null, address: null } });
        if (!chosen) { const next = [...restaurants, restaurant]; setRestaurants(next); storeRestaurants(next); }
        const pollRestaurants = (form.restaurantPoll
          ? [restaurant, ...restaurants.filter(option => option.currency === restaurant.currency && option.id !== restaurant.id)].slice(0, 12)
          : [restaurant]).map(clone);
        reply = await data.send('CREATE', {
          name: profile.name.trim(), text: form.room.trim(), restaurant, restaurants: pollRestaurants, expectedNames: [], flag: form.deliveryMode,
          destination: form.deliveryMode ? form.destination.trim() : '', deadline: 0,
          fees: { delivery: form.deliveryMode ? 0 : amount(form.delivery || '0', currency), automaticDelivery: form.deliveryMode, service: amount(form.service || '0', currency), discount: amount(form.discount || '0', currency), proportionalDelivery: form.proportionalDelivery },
        });
      }
      if (reply?.room) openRoom(reply.room.id);
    } catch (error) { setMessage(error.message); }
  };
  return <Page title={mode === 'join' ? 'Join your people' : t("Create a room")} subtitle={mode === 'join' ? 'Open an invitation link, scan its QR, or enter the six-digit code.' : 'Choose the restaurant now, or let everyone vote after joining the room.'} onBack={onBack}>
    <form className="card create-form stack" onSubmit={submit}>
      {mode === 'join' ? <label>{t("Six-digit room code")}<input inputMode="numeric" pattern="[0-9]{6}" value={form.code} onChange={e => setForm({ ...form, code: e.target.value.replace(/\D/g, '').slice(0, 6) })} required autoFocus /></label> : <>
        <label>{t("Room name")}<input value={form.room} onChange={e => setForm({ ...form, room: e.target.value })} placeholder={t("Friday lunch club")} required autoFocus /></label>
        {restaurants.length > 0 && <label>{tx('Saved restaurant', 'المطعم المحفوظ')}<select value={form.restaurantId} onChange={e => chooseRestaurant(e.target.value)}><option value="">{tx('Quick open order', 'طلب مفتوح سريع')}</option>{restaurants.map(restaurant => <option value={restaurant.id} key={restaurant.id}>{localizedName(restaurant)}{restaurant.branchName ? ` · ${restaurant.branchName}` : ''} · {restaurant.menu.items.length} {tx('items', 'صنفاً')}</option>)}</select></label>}
        {!chosen && <div className="form-grid two"><label>{t("Restaurant / order name")}<input value={form.restaurant} onChange={e => setForm({ ...form, restaurant: e.target.value })} placeholder={t("Today’s food order")} required /></label><label>{t("Restaurant phone")}<input type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="+971…" required /></label></div>}
        {chosen && <div className="selected-restaurant"><span><b>{localizedName(chosen)}</b><small>{chosen.menu.items.length ? `${chosen.menu.items.length} ${tx('saved menu items and prices', 'صنفاً محفوظاً بأسعاره')}` : tx('Open order for custom items', 'طلب مفتوح للأصناف المخصصة')}</small></span><strong>{chosen.currency}</strong></div>}
        <div className="segmented restaurant-choice"><button type="button" className={!form.restaurantPoll ? 'active' : ''} onClick={() => setForm({ ...form, restaurantPoll: false })}>{t("Use this restaurant")}</button><button type="button" className={form.restaurantPoll ? 'active' : ''} onClick={() => setForm({ ...form, restaurantPoll: true })}>{t("Start a room poll")}</button></div>
        <p className="field-help">{form.restaurantPoll ? 'Members vote live in the room. You finish the poll before sandwich ordering starts.' : 'The selected restaurant menu opens immediately for everyone in the room.'}</p>
        <div className="segmented delivery-choice"><button type="button" className={!form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: false })}>{t("Pickup")}</button><button type="button" className={form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: true })}>{t("Delivery")}</button></div>
        {form.deliveryMode && <DeliveryRule />}
        {form.deliveryMode && <label>{t("Delivery address and contact")}<input value={form.destination} onChange={e => setForm({ ...form, destination: e.target.value })} required /></label>}
        <div className="form-grid three">{!form.deliveryMode && <label>{t("Delivery fee ·")}{currency}<input inputMode="decimal" value={form.delivery} onChange={e => setForm({ ...form, delivery: e.target.value })} /></label>}<label>{t("Service fee ·")}{currency}<input inputMode="decimal" value={form.service} onChange={e => setForm({ ...form, service: e.target.value })} /></label><label>{t("Shared discount ·")}{currency}<input inputMode="decimal" value={form.discount} onChange={e => setForm({ ...form, discount: e.target.value })} /></label></div>
        {!form.deliveryMode && <label className="check"><input type="checkbox" checked={form.proportionalDelivery} onChange={e => setForm({ ...form, proportionalDelivery: e.target.checked })} />{t("Split delivery by each person’s food total")}<span>{t("Leave off to split the delivery fee equally between people who ordered food.")}</span></label>}
      </>}
      {message && <p className="form-message" role="alert">{message}</p>}
      <button className="primary" disabled={data.busy || !profile.name || !profile.phone}>{data.busy ? 'Connecting…' : mode === 'join' ? 'Request to join' : 'Create room'}</button>
      {(!profile.name || !profile.phone) && <p className="form-message">{t("Complete your name and phone in your profile first.")}</p>}
    </form>
  </Page>;
}

function cartLineDescription(room, line) {
  if (line.description) return line.description;
  const menuItem = room.restaurant.menu.items.find(item => item.id === line.itemId);
  if (!menuItem) return 'Menu item';
  const variant = menuItem.variants.find(value => value.id === line.variantId);
  const optionNames = room.restaurant.menu.optionGroups.flatMap(group => group.options).filter(option => line.optionIds.includes(option.id)).map(localizedName);
  return [localizedName(menuItem), variant && localizedName(variant), ...optionNames].filter(Boolean).join(' · ');
}

function OrderLine({ line, label, currency, canPrice, onPrice, onEdit, onRemove, onQuantity, restaurant }) {
  const lineTotal = line.amount ?? (restaurant ? menuLineTotal(restaurant, line) : line.unitPrice == null ? null : line.unitPrice * line.quantity);
  return <div className="order-line"><div><b>{line.quantity} × {label || line.description}</b>{line.notes && <small>{line.notes}</small>}</div><div>{lineTotal == null ? <span className="status">{t("Awaiting price")}</span> : <b>{money(lineTotal, currency)}</b>}{canPrice && <button type="button" className="link" onClick={onPrice}>{line.unitPrice == null ? 'Add price' : 'Change'}</button>}{onQuantity && <QuantityControl value={line.quantity} min={0} onChange={value => onQuantity(value - line.quantity)} />}{onEdit && <button type="button" className="link" onClick={onEdit}>{t("Edit")}</button>}{onRemove && <button type="button" className="link danger" onClick={onRemove}>{t("Remove")}</button>}</div></div>;
}

function OrderProgress({ room, receipts, payer }) {
  const total = receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const paid = receipts.reduce((sum, receipt) => sum + receipt.paid, 0);
  const index = room.phase === 'LOBBY' ? (room.restaurantPollOpen ? 1 : 2) : ['PREPARING_SPIN', 'SPINNING', 'ACCEPTING'].includes(room.phase) ? 3 : ['COLLECTING', 'REVIEW'].includes(room.phase) ? 4 : 5;
  return <section className="card order-progress"><div className="progress-steps">{[t("Join"), t("Restaurant"), t("Sandwiches"), t("Pick payer"), t("Confirm"), t("Settle")].map((title, step) => <div className={`${step < index ? 'done' : ''} ${step === index ? 'current' : ''}`} key={title}><span>{step < index ? '✓' : step + 1}</span><b>{title}</b></div>)}</div>{receipts.length > 0 && <div className="progress-money"><span><small>{payer ? 'Total food order' : 'Your order total'}</small><b>{money(total, room.restaurant.currency)}</b></span><span><small>{payer ? 'Member payments confirmed' : 'Your confirmed payments'}</small><b>{money(paid, room.restaurant.currency)}</b></span><span><small>{t("Still to settle")}</small><b>{money(receipts.filter(receipt => receipt.memberId !== room.payerId).reduce((sum, receipt) => sum + Math.max(0, receipt.balance), 0), room.restaurant.currency)}</b></span></div>}</section>;
}

function QuantityControl({ value, onChange, min = 1 }) {
  const count = Number(value) || min;
  return <div className="quantity-control" role="group" aria-label={tx('Quantity', 'الكمية')}>
    <button type="button" className="secondary" disabled={count <= min} aria-label={tx('Decrease quantity', 'تقليل الكمية')} onClick={() => onChange(Math.max(min, count - 1))}>−</button>
    <output aria-live="polite">{count}</output>
    <button type="button" className="secondary" disabled={count >= 99} aria-label={tx('Increase quantity', 'زيادة الكمية')} onClick={() => onChange(Math.min(99, count + 1))}>+</button>
  </div>;
}

function MenuItemForm({ room, editing, onSave, onCancel, lines = [] }) {
  const sandwichCategories = new Set(room.restaurant.menu.categories.filter(category => /sandwich|سند/i.test(category.name + ' ' + (category.nameAr || ''))).map(category => category.id));
  const available = room.restaurant.menu.items.filter(item => item.available).sort((a, b) => Number(sandwichCategories.has(b.categoryId)) - Number(sandwichCategories.has(a.categoryId)));
  const [search, setSearch] = useState('');
  const [menuLimit, setMenuLimit] = useState(12);
  const [configured, setConfigured] = useState(!!editing);
  const selectionRef = useRef(null);
  const query = search.trim().toLowerCase();
  const visibleItems = available.filter(item => !query || [item.name, item.nameAr, item.description, item.descriptionAr].some(value => value?.toLowerCase().includes(query)));
  const initialItem = editing ? available.find(item => item.id === editing.itemId) : available[0];
  const [choice, setChoice] = useState({ itemId: initialItem?.id || '', variantId: editing?.variantId || initialItem?.variants[0]?.id || '', optionIds: editing?.optionIds || [], quantity: String(editing?.quantity || 1), notes: editing?.notes || '' });
  const [message, setMessage] = useState('');
  const menuItem = available.find(item => item.id === choice.itemId);
  const groups = menuItem ? room.restaurant.menu.optionGroups.filter(group => menuItem.optionGroupIds.includes(group.id)) : [];
  const selectItem = itemId => { setConfigured(true); setTimeout(() => selectionRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }), 0); const next = available.find(item => item.id === itemId); setChoice({ itemId, variantId: next?.variants[0]?.id || '', optionIds: [], quantity: '1', notes: '' }); setMessage(''); };
  const toggleOption = (group, optionId) => {
    const selected = choice.optionIds.includes(optionId);
    let optionIds = selected ? choice.optionIds.filter(id => id !== optionId) : [...choice.optionIds, optionId];
    if (!selected && group.maxSelections === 1) optionIds = optionIds.filter(id => !group.options.some(option => option.id === id) || id === optionId);
    else if (!selected && optionIds.filter(id => group.options.some(option => option.id === id)).length > group.maxSelections) return;
    setChoice({ ...choice, optionIds });
  };
  const submit = async event => {
    event.preventDefault();
    const invalid = groups.find(group => choice.optionIds.filter(id => group.options.some(option => option.id === id)).length < group.minSelections);
    if (invalid) return setMessage(`Choose at least ${invalid.minSelections} from ${invalid.name}.`);
    const saved = await onSave({ id: editing?.id || uid(), itemId: menuItem.id, quantity: Number(choice.quantity), variantId: choice.variantId || null, optionIds: choice.optionIds, notes: choice.notes.trim(), description: '', unitPrice: null });
    if (saved) { setConfigured(false); setChoice(old => ({ ...old, quantity: '1', notes: '', optionIds: [] })); setMessage(''); }
  };
  if (!available.length) return null;
  return <article className="card">
    <p className="eyebrow">{editing ? tx('EDIT SANDWICH', 'تعديل السندويش') : tx('SANDWICH MENU', 'قائمة السندويشات')}</p>
    <h2>{editing ? tx('Update your item', 'عدّل الصنف') : `${tx('Choose from', 'اختر من')} ${localizedName(room.restaurant)}`}</h2>
    <form className="stack" onSubmit={submit}>
      <label>{tx('Find a sandwich or item', 'ابحث عن سندويش أو صنف')}<input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder={tx('Search in Arabic or English', 'ابحث بالعربية أو الإنجليزية')} /></label>
      {query && <p className="muted" role="status">{visibleItems.length} {tx('matching items', 'أصناف مطابقة')}</p>}
      {!editing && <div className="menu-order-grid">{visibleItems.slice(0, menuLimit).map(item => {
        const simple = !item.variants.length && !item.optionGroupIds.length;
        const count = lines.filter(line => line.itemId === item.id).reduce((sum, line) => sum + line.quantity, 0);
        return <div className="menu-order-item" key={item.id}><b>{localizedName(item)}</b><small>{money(item.variants[0]?.priceMinor ?? item.basePriceMinor, room.restaurant.currency)}</small>{count > 0 && <span className="status">{count} {tx('in your order', 'في طلبك')}</span>}
          <button type="button" className="primary" onClick={async () => { if (!simple) return selectItem(item.id); const saved = await onSave({ id: uid(), itemId: item.id, quantity: 1, variantId: null, optionIds: [], notes: '', description: '', unitPrice: null }); if (saved) setMessage(tx('Added to your order.', 'تمت الإضافة إلى طلبك.')); }}>{simple ? tx('+ Add', '+ إضافة') : tx('Choose size / extras', 'اختيار الحجم والإضافات')}</button>
          {simple && <button type="button" className="link" onClick={() => selectItem(item.id)}>{tx('Quantity / notes', 'الكمية والملاحظات')}</button>}
        </div>;
      })}</div>}
      {!editing && visibleItems.length > menuLimit && <button type="button" className="secondary" onClick={() => setMenuLimit(value => value + 12)}>{tx('Show more items', 'عرض أصناف أخرى')}</button>}
      {message && <p role="status" className="form-message">{message}</p>}
      {configured && <section ref={selectionRef} className="stack menu-selection"><h3>{localizedName(menuItem)}</h3>
      {localizedDescription(menuItem) && <p className="muted">{localizedDescription(menuItem)}</p>}
      {menuItem?.variants.length > 0 && <div><b className="field-label">{tx('Bread / size', 'الخبز أو الحجم')}</b><div className="choice-grid">{menuItem.variants.map(variant => <button type="button" className={choice.variantId === variant.id ? 'selected' : ''} key={variant.id} onClick={() => setChoice({ ...choice, variantId: variant.id })}>{localizedName(variant)}<small>{money(variant.priceMinor, room.restaurant.currency)}</small></button>)}</div></div>}
      {groups.map(group => <div key={group.id}><b className="field-label">{localizedName(group)} <small>{tx('Choose', 'اختر')} {group.minSelections}–{group.maxSelections}</small></b><div className="choice-grid">{group.options.map(option => <button type="button" className={choice.optionIds.includes(option.id) ? 'selected' : ''} key={option.id} onClick={() => toggleOption(group, option.id)}>{localizedName(option)}<small>{option.priceDeltaMinor ? `+ ${money(option.priceDeltaMinor, room.restaurant.currency)}` : tx('Included', 'مشمول')}</small></button>)}</div></div>)}
      {menuItem && <p className="price-preview" aria-live="polite">{money(menuLineTotal(room.restaurant, { ...choice, quantity: 1 }) || 0, room.restaurant.currency)} × {choice.quantity} = <strong>{money(menuLineTotal(room.restaurant, { ...choice, quantity: Number(choice.quantity) }) || 0, room.restaurant.currency)}</strong><small>{tx('Food subtotal · delivery and fees appear in your receipt', 'إجمالي الطعام · التوصيل والرسوم تظهر في إيصالك')}</small></p>}
      <div className="form-grid two"><label>{tx('Quantity', 'الكمية')}<QuantityControl value={choice.quantity} onChange={quantity => setChoice({ ...choice, quantity: String(quantity) })} /></label><label>{tx('Notes', 'ملاحظات')}<input value={choice.notes} onChange={e => setChoice({ ...choice, notes: e.target.value })} placeholder={tx('No onions, extra sauce', 'بدون بصل، صوص إضافي')} /></label></div>
      <div className="hero-actions"><button className="primary">{editing ? tx('Update my order', 'تحديث طلبي') : tx('Add sandwich', 'إضافة سندويش')}</button>{editing && <button type="button" className="secondary" onClick={onCancel}>{tx('Cancel edit', 'إلغاء التعديل')}</button>}</div>
      </section>}
    </form>
  </article>;
}

function MemberOrderPanel({ room, cart, receipt, previousOrders = [], favorites = [], onFavorite, onSave, onSubmit }) {
  const [editingId, setEditingId] = useState('');
  const [custom, setCustom] = useState({ description: '', quantity: '1', notes: '' });
  const [reuseMessage, setReuseMessage] = useState('');
  const editing = cart.lines.find(line => line.id === editingId);
  const edit = line => {
    setEditingId(line.id);
    if (line.description) setCustom({ description: line.description, quantity: String(line.quantity), notes: line.notes || '' });
  };
  const cancel = () => { setEditingId(''); setCustom({ description: '', quantity: '1', notes: '' }); };
  const saveLine = async line => {
    let lines;
    try { lines = editingId ? cart.lines.map(current => current.id === editingId ? line : current) : addMenuLine(cart.lines, line); }
    catch (error) { setReuseMessage(error.message); return null; }
    const result = await onSave(lines);
    if (result) cancel();
    return result;
  };
  const saveCustom = event => {
    event.preventDefault();
    return saveLine({ id: editingId || uid(), itemId: '', quantity: Number(custom.quantity), variantId: null, optionIds: [], notes: custom.notes.trim(), description: custom.description.trim(), unitPrice: null });
  };
  const matchingFavorites = favorites.filter(favorite => favorite.restaurantId === room.restaurant.id);
  const favoriteKeys = new Set(matchingFavorites.map(favoriteKey));
  const previous = previousOrders.filter(choice => !favoriteKeys.has(choice.key)).slice(0, 4);
  const reusableLines = source => source.map(line => {
    if (!line.itemId) return room.restaurant.openOrdering ? { id: uid(), itemId: '', quantity: line.quantity, variantId: null, optionIds: [], notes: line.notes || '', description: line.description, unitPrice: null } : null;
    const item = room.restaurant.menu.items.find(value => value.id === line.itemId && value.available);
    if (!item) return null;
    const variantValid = (!item.variants.length && !line.variantId) || item.variants.some(value => value.id === line.variantId);
    const options = room.restaurant.menu.optionGroups.filter(group => item.optionGroupIds.includes(group.id)).flatMap(group => group.options);
    if (!variantValid || (line.optionIds || []).some(id => !options.some(option => option.id === id))) return null;
    return { id: uid(), itemId: line.itemId, quantity: line.quantity, variantId: line.variantId || null, optionIds: line.optionIds || [], notes: line.notes || '', description: '', unitPrice: null };
  }).filter(Boolean);
  const addSavedOrder = async source => {
    setReuseMessage('');
    const incoming = reusableLines(source);
    if (!incoming.length) return setReuseMessage('These saved items are no longer available on this menu.');
    const merged = cart.lines.map(line => ({ ...line }));
    for (const line of incoming) {
      const old = merged.find(value => value.itemId === line.itemId && value.variantId === line.variantId && JSON.stringify([...(value.optionIds || [])].sort()) === JSON.stringify([...(line.optionIds || [])].sort()) && value.notes === line.notes && value.description === line.description);
      if (old) {
        if (old.quantity + line.quantity > 99) return setReuseMessage('A reused item would exceed the maximum quantity of 99.');
        old.quantity += line.quantity; old.unitPrice = null;
      } else merged.push(line);
    }
    const saved = await onSave(merged);
    if (saved) setReuseMessage(incoming.length < source.length ? `Added ${incoming.length} available items. Some old menu items were skipped.` : 'Saved order added. You can still edit every item.');
  };
  return <section className="stack order-editor">
    {reuseMessage && <p role="status" className="form-message">{t(reuseMessage)}</p>}
    <article className="card early-order"><p className="eyebrow">{t("YOUR SANDWICHES")}</p><h2>{t("Add now. Change any time before totals.")}</h2><p className="muted">{t("Your order stays editable before, during, and after the spin. Saving an edit keeps it in this room after refresh.")}</p></article>
    {(matchingFavorites.length > 0 || previous.length > 0) && <article className="card smart-reorder"><p className="eyebrow">{t("FAST ADD")}</p><h2>{t("Favorites & unique previous orders")}</h2><p className="muted">{t("Exact repeats appear once. Any change to quantity, size, extras, or notes remains a separate choice.")}</p><div className="saved-order-list">{matchingFavorites.map(favorite => <div className="saved-order-row" key={favorite.id}><span><b>★ {favorite.title}</b><small>{orderSummary(favorite.lines)}</small></span><button className="primary" onClick={() => addSavedOrder(favorite.lines)}>{t("Add all")}</button></div>)}{previous.map(choice => <div className="saved-order-row" key={choice.key}><span><b>{choice.repeatCount > 1 ? `Repeated ${choice.repeatCount} times` : new Date(choice.order.completedAt).toLocaleDateString()}</b><small>{orderSummary(choice.receipt.lines)}</small></span><span className="saved-order-buttons"><button className="primary" onClick={() => addSavedOrder(choice.receipt.lines.map(line => ({ ...line, description: line.itemId ? '' : line.description })))}>{t("Add all")}</button><button className="secondary" onClick={() => onFavorite(choice)}>{t("☆ Favorite")}</button></span></div>)}</div>{reuseMessage && <p className={reuseMessage.includes('added') || reuseMessage.includes('Added') ? 'success-message' : 'form-message'}>{reuseMessage}</p>}</article>}
    {room.restaurant.menu.items.length > 0 && (!editing || !editing.description) && <MenuItemForm key={editing?.id || 'new-menu'} room={room} lines={cart.lines} editing={editing} onSave={saveLine} onCancel={cancel} />}
    {room.restaurant.openOrdering && (!editing || editing.description) && <article className="card"><p className="eyebrow">{editing ? 'EDIT CUSTOM ITEM' : 'CUSTOM SANDWICH'}</p><h2>{editing ? 'Update your item' : 'Add an item one by one'}</h2><form className="item-form" onSubmit={saveCustom}><label>{t("Food item")}<input value={custom.description} onChange={e => setCustom({ ...custom, description: e.target.value })} placeholder={t("Falafel sandwich")} required /></label><label>{t("Qty")}<input inputMode="numeric" min="1" max="99" type="number" value={custom.quantity} onChange={e => setCustom({ ...custom, quantity: e.target.value })} required /></label><label className="notes">{t("Notes / extras")}<input value={custom.notes} onChange={e => setCustom({ ...custom, notes: e.target.value })} placeholder={t("No onions, extra sauce")} /></label><div className="hero-actions"><button className="primary">{editing ? 'Update my order' : 'Add custom item'}</button>{editing && <button className="secondary" type="button" onClick={cancel}>{t("Cancel edit")}</button>}</div></form></article>}
    <article className="card"><div className="section-title compact"><div><p className="eyebrow">{t("MY ORDER")}</p><h2>{cart.lines.length ? `${cart.lines.length} item${cart.lines.length === 1 ? '' : 's'}` : 'Nothing added yet'}</h2></div>{receipt && <b>{receipt.totalText}</b>}</div>{cart.lines.map(line => <OrderLine key={line.id} label={cartLineDescription(room, line)} line={line} restaurant={room.restaurant} currency={room.restaurant.currency} onQuantity={delta => onSave(changeQuantity(cart.lines, line.id, delta))} onEdit={() => edit(line)} onRemove={() => onSave(cart.lines.filter(value => value.id !== line.id))} />)}<button className="primary wide" onClick={onSubmit}>{cart.submitted ? 'Save my updated order' : cart.lines.length ? 'Submit my food order' : 'No food this time'}</button>{cart.submitted && <p className="success-message">{t("✓ Saved. You can still edit and submit changes before totals are reviewed.")}</p>}</article>
  </section>;
}

function DeliveryRule() {
  return <p className="delivery-rule">{tx('Delivery is automatic: AED 5 shared equally by 1–4 people ordering food, or AED 1 each for 5 or more. The selected person’s food also counts.', 'يُحسب التوصيل تلقائياً: ٥ دراهم بالتساوي بين ١–٤ أشخاص يطلبون طعاماً، أو درهم لكل شخص عند ٥ فأكثر. يُحسب طلب الشخص المختار أيضاً.')}</p>;
}

function FeeEditor({ room, data }) {
  const currency = room.restaurant.currency;
  const [fees, setFees] = useState({ delivery: minorInput(room.fees.delivery, currency), service: minorInput(room.fees.service, currency), discount: minorInput(room.fees.discount, currency), proportionalDelivery: room.fees.proportionalDelivery });
  const [message, setMessage] = useState('');
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      await data.send('SET_FEES', { fees: { delivery: room.deliveryMode ? 0 : amount(fees.delivery || '0', currency), automaticDelivery: room.deliveryMode, service: amount(fees.service || '0', currency), discount: amount(fees.discount || '0', currency), proportionalDelivery: fees.proportionalDelivery }, text: 'Updated delivery, service, and discount allocation' }, room.id);
    } catch (error) { setMessage(error.message); }
  };
  return <article className="card"><p className="eyebrow">{t("FEES & EXACT SPLIT")}</p><h2>{t("Cover the complete restaurant bill")}</h2><p className="muted">{t("Food Run allocates every minor unit so all member receipts equal the final food, delivery, service, tax, and discount total.")}</p><form className="stack" onSubmit={save}>{room.deliveryMode && <><DeliveryRule />{!room.fees.automaticDelivery && <p className="form-message">{tx("This room uses saved delivery fees. Update fees below to apply automatic delivery and request new total confirmations.", "تستخدم الغرفة رسوم التوصيل المحفوظة. حدّث الرسوم لتطبيق التوصيل التلقائي وإعادة تأكيد الإجمالي.")}</p>}</>}<div className="form-grid three">{!room.deliveryMode && <label>{t("Delivery fee")}<input inputMode="decimal" value={fees.delivery} onChange={e => setFees({ ...fees, delivery: e.target.value })} /></label>}<label>{t("Service fee")}<input inputMode="decimal" value={fees.service} onChange={e => setFees({ ...fees, service: e.target.value })} /></label><label>{t("Discount")}<input inputMode="decimal" value={fees.discount} onChange={e => setFees({ ...fees, discount: e.target.value })} /></label></div>{!room.deliveryMode && <label className="check"><input type="checkbox" checked={fees.proportionalDelivery} onChange={e => setFees({ ...fees, proportionalDelivery: e.target.checked })} />{t("Split delivery by food total")}<span>{t("Turn off for an equal split between everyone with food.")}</span></label>}{message && <p className="form-message">{message}</p>}<button className="secondary">{t("Update fees and reopen totals")}</button></form></article>;
}

function RestaurantOrderCard({ room, receipts, data, finish = false }) {
  const [copied, setCopied] = useState(false);
  const orderText = combinedOrderText(room, receipts);
  const copy = async () => { await copyText(orderText); setCopied(true); setTimeout(() => setCopied(false), 2500); };
  const contact = room.restaurant.contact.phoneE164 || room.restaurant.contact.whatsappE164;
  const whatsApp = `https://wa.me/?text=${encodeURIComponent(orderText)}`;
  return <article className="card restaurant-order-card"><p className="eyebrow">{t("SELECTED TO ORDER")}</p><div className="selected-payer"><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><div><h2>{room.members.find(member => member.id === room.payerId)?.name}</h2><p>{t("Collects the final list, places the order, and confirms payments.")}</p></div></div><pre>{orderText}</pre><div className="hero-actions"><button className="primary" type="button" onClick={copy}>{copied ? '✓ Copied — paste to restaurant' : finish ? tx('Copy list for restaurant', 'نسخ الطلب للمطعم') : tx('Copy restaurant-ready list', 'نسخ الطلب للمطعم')}</button><a className="secondary action-link" href={whatsApp} target="_blank" rel="noreferrer">{t("Share via WhatsApp")}</a>{contact && <a className="secondary action-link" href={`tel:${contact.replace(/[^+\d]/g, '')}`}>{t("Call restaurant")}</a>}</div><p className="fine">{t("WhatsApp opens the prepared order; choose the restaurant chat, a group, or another contact.")}</p></article>;
}

function ReceiptCard({ room, receipt, own = false }) {
  return <article className={`receipt-card ${own ? 'own' : ''}`}><div className="receipt-heading"><span><b>{receipt.name}{own ? ' · You' : ''}</b><small>{receipt.lines.length} line{receipt.lines.length === 1 ? '' : 's'}</small></span><strong>{receipt.totalText}</strong></div>{receipt.lines.map((line, index) => <OrderLine key={`${line.description}:${index}`} line={line} currency={receipt.currency} />)}<div className="fee-breakdown"><span>{t("Food")}<b>{money(receipt.food, receipt.currency)}</b></span><span>{t("Delivery")}<b>{money(receipt.delivery, receipt.currency)}</b></span><span>{t("Service / adjustment")}<b>{money(receipt.service, receipt.currency)}</b></span><span>{t("Discount")}<b>− {money(receipt.discount, receipt.currency)}</b></span><span>{t("Tax")}<b>{money(receipt.tax, receipt.currency)}</b></span></div><div className="receipt-balance"><span>{t("Paid")}<b>{money(receipt.paid, receipt.currency)}</b></span><span>{receipt.balance < 0 ? 'Refund due' : 'To pay'} <strong>{money(Math.abs(receipt.balance), receipt.currency)}</strong></span></div></article>;
}

function WalletPanel({ room, receipts, me, payer }) {
  const currency = room.restaurant.currency;
  const own = receipts.find(receipt => receipt.memberId === me.id);
  if (!own && !payer) return null;
  const pending = room.transfers.filter(transfer => String(transfer.status).toLowerCase() === 'declared');
  const restaurantTotal = receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const confirmed = receipts.filter(receipt => receipt.memberId !== room.payerId).reduce((sum, receipt) => sum + receipt.paid, 0);
  const remaining = receipts.filter(receipt => receipt.memberId !== room.payerId).reduce((sum, receipt) => sum + Math.max(0, receipt.balance), 0);
  const refunds = receipts.reduce((sum, receipt) => sum + Math.max(0, -receipt.balance), 0);
  return <article id="room-payment" className="card wallet-card"><p className="eyebrow">{payer ? 'ROOM WALLET' : 'MY WALLET'}</p><h2>{payer ? 'Every share in one place' : 'Your order and payment'}</h2><div className="progress-money wallet-summary">{payer ? <><span><small>{t("Restaurant total")}</small><b>{money(restaurantTotal, currency)}</b></span><span><small>{t("Your own share")}</small><b>{money(receipts.find(receipt => receipt.memberId === room.payerId)?.total || 0, currency)}</b></span><span><small>{t("Confirmed from others")}</small><b>{money(confirmed, currency)}</b></span><span><small>{t("Members still owe")}</small><b>{money(remaining, currency)}</b></span>{refunds > 0 && <span><small>{t("Refunds you owe")}</small><b>{money(refunds, currency)}</b></span>}</> : <><span><small>{t("My order")}</small><b>{money(own.total, currency)}</b></span><span><small>{t("Confirmed paid")}</small><b>{money(own.paid, currency)}</b></span><span><small>{own.balance < 0 ? 'Owed back to me' : t("I need to pay")}</small><b>{money(Math.abs(own.balance), currency)}</b></span></>}</div>
    {payer && <div className="wallet-people">{receipts.map(receipt => { const claim = pending.find(transfer => transfer.memberId === receipt.memberId); return <div className="transfer-row" key={receipt.memberId}><span><b>{receipt.name}{receipt.memberId === me.id ? ' · You' : ''}</b><small>{t("Order")}{' '}{money(receipt.total, currency)}{t("· confirmed")}{' '}{money(receipt.paid, currency)}{claim ? ` · ${money(claim.amount, currency)} awaiting confirmation` : ''}</small></span><strong>{receipt.memberId === room.payerId ? 'Own share' : receipt.balance < 0 ? `Refund ${money(-receipt.balance, currency)}` : receipt.balance === 0 ? t("Settled") : `Owes ${money(receipt.balance, currency)}`}</strong></div>; })}</div>}
    {!payer && pending.some(transfer => transfer.memberId === me.id) && <p className="success-message">{t("Your payment is marked sent and awaits confirmation from the selected payer.")}</p>}
  </article>;
}

function SettlementPanel({ room, reply, me, owner, payer, data }) {
  const currency = room.restaurant.currency;
  const myReceipt = reply.receipts.find(receipt => receipt.memberId === me.id);
  const total = reply.receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const pending = room.transfers.filter(transfer => String(transfer.status).toLowerCase() === 'declared');
  const ownPending = pending.find(transfer => transfer.memberId === me.id);
  const [payment, setPayment] = useState({ value: myReceipt && myReceipt.balance > 0 ? minorInput(myReceipt.balance, currency) : '', reference: '' });
  const [adjustment, setAdjustment] = useState({ value: '', reason: '' });
  const [message, setMessage] = useState('');
  const guarded = action => { try { setMessage(''); action(); } catch (error) { setMessage(error.message); } };
  const awaitingApproval = room.billRevision > 1 && room.members.some(member => member.approved && !member.removed && !member.guest && member.participating && !room.adjustmentApprovals.includes(member.id));
  const declarePayment = event => { event.preventDefault(); guarded(() => data.send('DECLARE_TRANSFER', { amount: amount(payment.value, currency), text: payment.reference.trim() }, room.id)); };
  const reject = transfer => data.send('REJECT_TRANSFER', { transferId: transfer.id, text: 'Payment was not received or the details do not match.' }, room.id);
  const refund = receipt => {
    const value = window.prompt(`Refund amount for ${receipt.name}`, minorInput(-receipt.balance, currency));
    if (!value) return;
    const reference = window.prompt('Refund reference or cash note');
    if (reference) guarded(() => data.send('DECLARE_REFUND', { memberId: receipt.memberId, amount: amount(value, currency), text: reference }, room.id));
  };
  return <>
    {message && <p className="form-message" role="alert">{message}</p>}
    <WalletPanel room={room} receipts={reply.receipts} me={me} payer={payer} />
    <article className="card placed-banner"><p className="eyebrow">{t("RESTAURANT STATUS")}</p><h2>{room.restaurantPaid ? 'Restaurant payment recorded' : 'Order announced as placed'}</h2><p>{room.restaurantReference}</p></article>
    <article className="card"><div className="section-title compact"><div><p className="eyebrow">{t("RECEIPT")}</p><h2>{payer ? 'Complete order breakdown' : 'Your total, paid, and remaining'}</h2></div>{myReceipt && <button type="button" className="secondary" onClick={() => copyText(receiptText(room, myReceipt))}>{t("Copy my receipt")}</button>}</div>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}{room.account && <div className="pay-to"><span>{t("Pay to")}</span><b>{room.account.holder} · {room.account.bank}</b><code>{room.account.identifier}</code><button type="button" className="secondary" onClick={() => copyText(room.account.identifier).then(() => data.setNotice('Payment details copied.')).catch(error => data.setError(error.message))}>{t("Copy payment details")}</button></div>}</article>
    {payer && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} />}
    {payer && <article className="card stack"><p className="eyebrow">{t("RESTAURANT & FINAL BILL")}</p><h2>{money(total, currency)}</h2><p className="muted">{t("Recording a payment tracks it in Food Run; it does not move money.")}</p>{awaitingApproval && <p className="form-message">{t("Waiting for everyone to approve the revised bill.")}</p>}{!room.restaurantPaid && <button disabled={awaitingApproval || data.busy} className="primary wide" onClick={() => data.send('PAY_RESTAURANT', { amount: total }, room.id)}>{t("Confirm restaurant paid ·")}{' '}{money(total, currency)}</button>}<form className="form-grid adjustment-form" onSubmit={event => { event.preventDefault(); guarded(() => { const raw = adjustment.value.trim(); const minor = amount(raw.replace(/^-/, ''), currency) * (raw.startsWith('-') ? -1 : 1); data.send('ADJUST_BILL', { amount: minor, text: adjustment.reason.trim() }, room.id); }); }}><label>{t("Final bill adjustment")}<input value={adjustment.value} onChange={e => setAdjustment({ ...adjustment, value: e.target.value })} placeholder="-5.00 or 5.00" /></label><label>{t("Reason")}<input value={adjustment.reason} onChange={e => setAdjustment({ ...adjustment, reason: e.target.value })} placeholder={t("Restaurant discount")} /></label><button className="secondary">{t("Update final bill")}</button></form>{room.phase === 'PLACED' && <button className="secondary wide" onClick={() => data.send('FULFILL', {}, room.id)}>{t("Food collected / delivered")}</button>}</article>}
    {!payer && myReceipt && room.restaurantPaid && myReceipt.balance > 0 && !ownPending && <article className="card notice-card"><p className="eyebrow">{t("PAY YOUR SHARE")}</p><h2>{myReceipt.balanceText}{t("remaining")}</h2><form className="stack" onSubmit={declarePayment}><label>{t("Amount sent")}<input inputMode="decimal" value={payment.value} onChange={e => setPayment({ ...payment, value: e.target.value })} required /></label><button className="secondary" type="button" onClick={() => setPayment(old => ({ ...old, value: minorInput(myReceipt.balance, currency) }))}>{t("Use full remaining amount ·")}{' '}{money(myReceipt.balance, currency)}</button><label>{t("Transfer reference / cash note")}<input value={payment.reference} onChange={e => setPayment({ ...payment, reference: e.target.value })} required /></label><button className="primary">{tx("I paid · notify recipient", "دفعت · إشعار المستلم")}</button></form></article>}
    {!payer && myReceipt?.balance === 0 && <article className="card settled-card"><span>✓</span><div><h2>{t("Paid and settled")}</h2><p>{t("Your selected orderer confirmed your payment.")}</p></div></article>}
    {room.billRevision > 1 && me.approved && !me.guest && me.participating && !room.adjustmentApprovals.includes(me.id) && <button className="primary wide" onClick={() => data.send('APPROVE_ADJUSTMENT', { expectedRevision: room.billRevision }, room.id)}>{t("Approve revised final bill")}</button>}
    {room.transfers.length > 0 && <article className="card"><p className="eyebrow">{t("PAYMENT ACTIVITY")}</p><h2>{t("Sent and confirmed")}</h2>{room.transfers.map(transfer => { const member = room.members.find(value => value.id === transfer.memberId); const declared = String(transfer.status).toLowerCase() === 'declared'; return <div className="transfer-row" key={transfer.id}><span><b>{transfer.refund ? 'Refund' : 'Payment'} · {member?.name}</b><small>{transfer.reference} · {String(transfer.status).toLowerCase()}</small></span><strong>{money(transfer.amount, currency)}</strong>{declared && payer && !transfer.refund && <span className="transfer-actions"><button className="primary" onClick={() => data.send('CONFIRM_TRANSFER', { transferId: transfer.id }, room.id)}>{tx("Approve · money received", "موافقة · استلمت المبلغ")}</button><button className="secondary" onClick={() => reject(transfer)}>{t("Reject")}</button></span>}{declared && transfer.refund && transfer.memberId === me.id && <span className="transfer-actions"><button className="primary" onClick={() => data.send('CONFIRM_REFUND', { transferId: transfer.id }, room.id)}>{t("Confirm refund")}</button><button className="secondary" onClick={() => reject(transfer)}>{t("Reject")}</button></span>}</div>; })}</article>}
    {payer && room.restaurantPaid && reply.receipts.filter(receipt => receipt.balance < 0 && !pending.some(transfer => transfer.memberId === receipt.memberId)).map(receipt => <button className="secondary wide" key={receipt.memberId} onClick={() => refund(receipt)}>{t("Record refund to")}{' '}{receipt.name} · {money(-receipt.balance, currency)}</button>)}
    {(owner || payer) && room.phase === 'FULFILLED' && <article className="card complete-card"><h2>{t("Finish this order")}</h2><p>{reply.progress?.canArchive ? 'Every payment and refund is settled.' : reply.progress?.archiveBlocker || 'Waiting for all payments to settle.'}</p><button className="primary wide" disabled={!reply.progress?.canArchive} onClick={() => data.send('ARCHIVE', { text: 'Order completed and settled' }, room.id)}>{t("Complete and archive order")}</button></article>}
  </>;
}

function RestaurantPoll({ room, me, owner, data }) {
  const myVote = room.restaurantVotes?.find(vote => vote.memberId === me.id)?.restaurantId;
  return <article className="card restaurant-poll"><p className="eyebrow">{tx('STEP 1 · RESTAURANT POLL', 'الخطوة ١ · تصويت المطعم')}</p><h2>{tx('Where should the group order from?', 'من أي مطعم نطلب؟')}</h2><p className="muted">{tx('Vote updates live for everyone. The organizer closes the poll, then sandwich ordering opens and stays editable through the spin.', 'تظهر الأصوات مباشرة للجميع. ينهي منظم الغرفة التصويت ثم تفتح قائمة السندويشات وتبقى قابلة للتعديل أثناء الاختيار.')}</p><div className="poll-grid">{(room.restaurantOptions || []).map(restaurant => { const votes = (room.restaurantVotes || []).filter(vote => vote.restaurantId === restaurant.id); const voters = votes.map(vote => room.members.find(member => member.id === vote.memberId)?.name).filter(Boolean); return <section className={`poll-option ${myVote === restaurant.id ? 'selected' : ''}`} key={restaurant.id}><span><b>{localizedName(restaurant)}</b><small>{restaurant.menu.items.length} {tx('sandwiches/items', 'سندويشات وأصناف')}</small><small>{voters.length ? voters.join(', ') : tx('No votes yet', 'لا أصوات بعد')}</small></span><strong>{votes.length}</strong>{!me.guest && me.participating && <button className={myVote === restaurant.id ? 'secondary' : 'primary'} disabled={myVote === restaurant.id} onClick={() => data.send('VOTE_RESTAURANT', { text: restaurant.id }, room.id)}>{myVote === restaurant.id ? tx('✓ Your vote', '✓ صوتك') : tx('Vote', 'تصويت')}</button>}</section>; })}</div>{owner && <button className="primary wide" disabled={!room.restaurantVotes?.length} onClick={() => data.send('FINALIZE_RESTAURANT', {}, room.id)}>{tx('Finish poll & use leading restaurant', 'إنهاء التصويت واختيار المطعم المتصدر')}</button>}</article>;
}

function RoomInviteCard({ room, hub }) {
  const link = roomInviteLink(room, hub);
  const [copied, setCopied] = useState('');
  const copy = async (value, kind) => { await copyText(value); setCopied(kind); setTimeout(() => setCopied(''), 2200); };
  return <section className="card room-invite"><div className="invite-qr"><QRCodeSVG value={link} size={164} level="M" marginSize={2} title={`Join ${room.name}`} /></div><div className="invite-details"><p className="eyebrow">{t("INVITE TO THIS ROOM")}</p><h3>{t("Scan, tap, or enter the code")}</h3><strong className="invite-code">{room.code}</strong><p className="muted">{t("The link remembers this room’s internet or local server and opens the join screen with the code filled in.")}</p><div className="hero-actions"><button className="primary" onClick={() => copy(link, 'link')}>{copied === 'link' ? '✓ Link copied' : 'Copy invitation link'}</button><button className="secondary" onClick={() => copy(room.code, 'code')}>{copied === 'code' ? '✓ Code copied' : 'Copy code'}</button>{navigator.share && <button className="secondary" onClick={() => navigator.share({ title: `Join ${room.name} on Food Run`, text: `Room code: ${room.code}`, url: link })}>{t("Share")}</button>}</div></div></section>;
}

function RoomManagement({ room, data, owner, payer }) {
  const [reason, setReason] = useState('');
  const [contact, setContact] = useState(room.restaurant.contact.phoneE164 || '');
  const [tax, setTax] = useState(room.restaurant.pricing.taxTreatment);
  const [rate, setRate] = useState(String((room.restaurant.pricing.taxRateBasisPoints || 0) / 100));
  const [minimum, setMinimum] = useState(minorInput(room.restaurant.pricing.minimumOrderMinor, room.restaurant.currency));
  const [message, setMessage] = useState('');
  const run = (kind, fields = {}) => {
    if (!reason.trim()) return setMessage(tx('Enter a short reason for this change.', 'اكتب سبباً مختصراً لهذا التغيير.'));
    setMessage(''); return data.send(kind, { text: reason.trim(), ...fields }, room.id);
  };
  const saveRestaurant = () => {
    try {
      const taxRateBasisPoints = tax === 'added' ? amount(rate, 'AED') : null;
      if (taxRateBasisPoints > 10000) throw Error(tx('Tax must be between 0 and 100%.', 'يجب أن تكون الضريبة بين ٠ و١٠٠٪.'));
      return run('UPDATE_RESTAURANT', { restaurant: { ...room.restaurant,
        contact: { ...room.restaurant.contact, phoneE164: uaePhone(contact) },
        pricing: { ...room.restaurant.pricing, taxTreatment: tax, taxRateBasisPoints, minimumOrderMinor: amount(minimum, room.restaurant.currency) },
      } });
    } catch (error) { setMessage(error.message); }
  };
  return <details className="card room-tools"><summary>{tx('Manage this order', 'إدارة هذا الطلب')}</summary>
    <div className="stack">
      <label>{tx('Reason for change', 'سبب التغيير')}<input value={reason} maxLength="500" onChange={event => setReason(event.target.value)} /></label>
      {message && <p className="form-message" role="alert">{message}</p>}
      {owner && room.phase === 'PREPARING_SPIN' && <button className="secondary" onClick={() => run('ABORT_PREPARE')}>{tx('Cancel preparation and return to room', 'إلغاء التحضير والعودة إلى الغرفة')}</button>}
      {(owner || payer) && ['REVIEW', 'COLLECTING'].includes(room.phase) && <button className="secondary" onClick={() => run('REOPEN')}>{tx('Reopen food editing', 'فتح تعديل الطلبات مجدداً')}</button>}
      {(owner || payer) && ['LOBBY', 'COLLECTING', 'REVIEW'].includes(room.phase) && <>
        <label>{tx('Restaurant phone', 'رقم المطعم')}<input type="tel" value={contact} onChange={event => setContact(event.target.value)} /></label>
        <label>{tx('Tax treatment', 'الضريبة')}<select value={tax} onChange={event => setTax(event.target.value)}><option value="included">{tx('Included in prices', 'مشمولة في الأسعار')}</option><option value="added">{tx('Added to bill', 'تضاف إلى الفاتورة')}</option><option value="unspecified">{tx('Confirm later', 'التأكيد لاحقاً')}</option></select></label>
        {tax === 'added' && <label>{tx('Tax rate %', 'نسبة الضريبة ٪')}<input inputMode="decimal" value={rate} onChange={event => setRate(event.target.value)} /></label>}
        <label>{tx('Minimum order (AED)', 'الحد الأدنى للطلب (درهم)')}<input inputMode="decimal" value={minimum} onChange={event => setMinimum(event.target.value)} /></label>
        <button className="secondary" onClick={saveRestaurant}>{tx('Update restaurant details', 'تحديث بيانات المطعم')}</button>
      </>}
      {owner && room.phase === 'LOBBY' && room.members.filter(member => member.approved && !member.removed && !member.guest && member.id !== room.ownerId).map(member => <div className="saved-order-row" key={member.id}><b>{member.name}</b><span className="saved-order-buttons"><button className="secondary" onClick={() => run('HANDOVER', { memberId: member.id })}>{tx('Make organizer', 'تعيين منظماً')}</button><button className="link danger" onClick={() => run('REMOVE', { memberId: member.id })}>{tx('Remove', 'إزالة')}</button></span></div>)}
    </div>
  </details>;
}

function RoomScreen({ data, roomId, onBack }) {
  const reply = data.rooms[roomId], session = data.sessions[roomId], room = reply?.room;
  const [price, setPrice] = useState({ memberId: '', lineId: '', value: '' });
  const [reference, setReference] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  if (!room || !session) return <Page title={t("Connecting to room…")} onBack={onBack}><div className="card empty"><div className="spinner" /><p>{t("Waiting for the latest room state.")}</p></div></Page>;
  const me = room.members.find(member => member.id === session.memberId);
  const owner = room.ownerId === me.id, payer = room.payerId === me.id;
  const orderingMembers = room.members.filter(member => member.approved && !member.removed && !member.guest && member.participating);
  const myCart = room.carts.find(cart => cart.memberId === me.id) || { memberId: me.id, revision: 0, lines: [], submitted: false, confirmedQuote: -1 };
  const myReceipt = reply.receipts.find(receipt => receipt.memberId === me.id);
  const winner = room.members.find(member => member.id === room.spin?.winnerId);
  const saveCart = lines => data.send('CART', { cart: { ...myCart, lines }, expectedRevision: myCart.revision }, room.id);
  const selected = winner && room.phase === 'ACCEPTING';
  const orderingOpen = ['LOBBY','PREPARING_SPIN','SPINNING','ACCEPTING','COLLECTING'].includes(room.phase) && !room.restaurantPollOpen;
  const allConfirmed = orderingMembers.every(member => room.carts.find(cart => cart.memberId === member.id)?.confirmedQuote === room.quoteRevision);
  const saveCurrentRestaurant = () => {
    const saved = loadRestaurants(); storeRestaurants([...saved.filter(restaurant => restaurant.id !== room.restaurant.id), clone(room.restaurant)].sort((a, b) => a.name.localeCompare(b.name)));
    data.setNotice(`${room.restaurant.name} and its current prices are saved on this device.`);
  };
  const favorites = data.home.profile.favoriteOrders || [];
  const previousOrders = uniquePreviousOrders(data, room.restaurant);
  const roomHistoryChoices = uniqueRoomPreviousOrders(reply.history, me.id, room.restaurant);
  const saveFavorite = async choice => {
    const favorite = favoriteFromPrevious(choice);
    const existing = data.home.profile.favoriteOrders || [];
    if (existing.some(value => favoriteKey(value) === favoriteKey(favorite))) return data.setNotice(t("This order is already in your favorites."));
    if (existing.length >= 30) return data.setNotice(t("Your 30 favorites are full. Remove one before saving another."));
    const saved = await data.send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: profileWithFavorites(data.home.profile, [favorite, ...existing]) } });
    if (saved) data.setNotice(t("Favorite order saved."));
  };
  const startNextOrder = () => data.send('NEXT_ORDER', { restaurant: room.restaurant, restaurants: room.restaurantOptions || [room.restaurant], expectedNames: [], flag: room.deliveryMode, destination: room.destination, deadline: 0, fees: room.fees }, room.id);
  return <Page title={room.name} subtitle={`Order #${room.orderNumber} · ${t(phaseLabel[room.phase])} · code ${room.code}`} onBack={onBack} actions={<span className={`status ${data.online[room.id] ? 'live' : ''}`}>{data.online[room.id] ? '● Live' : 'Offline'}</span>}>
    <OrderProgress room={room} receipts={reply.receipts} payer={payer} />
    <fieldset disabled={data.busy} className="room-fieldset" aria-busy={data.busy}><div className="room-layout">
      <section className="room-main stack">
        {['PREPARING_SPIN','SPINNING','ACCEPTING'].includes(room.phase) && <article className="card selection-card">
          {room.phase === 'PREPARING_SPIN' && <><div className="spinner" /><h2>{t("Getting everyone in sync…")}</h2><p>{t("Every participating device is joining the live selection.")}</p></>}
          {room.phase === 'SPINNING' && <><p className="eyebrow">{t("LIVE SELECTION")}</p><h2>{t("Who will order?")}</h2><LiveSelectionWheel key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active /></>}
          {room.phase === 'ACCEPTING' && <><LiveSelectionWheel key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active={false} /><WinnerReveal winner={winner} selected={selected} me={me} room={room} data={data} /></>}
        </article>}
        {!me.approved && <article className="card notice-card"><h2>{t("Waiting for approval")}</h2><p>{t("The organizer will approve your request before you can join this order.")}</p></article>}
        {me.approved && room.phase === 'LOBBY' && <><article className="card"><div className="section-title compact"><div><p className="eyebrow">{t("WHO’S IN?")}</p><h2>{t("Join today’s order")}</h2></div><span>{orderingMembers.length}</span></div>{!me.guest && <div className="hero-actions"><button className={me.participating ? 'secondary' : 'primary'} onClick={() => data.send('PARTICIPATE', { flag: !me.participating }, room.id)}>{me.participating ? t("Skip this order") : t("Join this order")}</button></div>}<p className="muted">{t("Once approved and joined, you are ready automatically. Add sandwiches now; no extra ready step is needed.")}</p>{owner && !room.restaurantPollOpen && <button className="primary wide" disabled={!orderingMembers.length} onClick={() => data.send('PREPARE_SPIN', {}, room.id)}>{t("Spin to choose the payer")}</button>}</article>{room.restaurantPollOpen && <RestaurantPoll room={room} me={me} owner={owner} data={data} />}</>}
        {me.approved && !me.guest && me.participating && orderingOpen && <MemberOrderPanel key={`${room.orderNumber}:${room.restaurant.id}`} room={room} cart={myCart} receipt={myReceipt} previousOrders={previousOrders} favorites={favorites} onFavorite={saveFavorite} onSave={saveCart} onSubmit={() => data.send('SUBMIT_CART', { expectedRevision: myCart.revision }, room.id)} />}
        {me.approved && room.phase === 'COLLECTING' && <>
          {payer && <article className="card"><p className="eyebrow">{t("PRICE CUSTOM ITEMS")}</p><h2>{t("Menu prices are already saved")}</h2><p className="muted">{t("Only open-order items need a unit price.")}</p>{room.carts.flatMap(cart => cart.lines.filter(line => line.description).map(line => ({ cart, line }))).map(({ cart, line }) => <OrderLine key={line.id} label={`${room.members.find(member => member.id === cart.memberId)?.name} · ${line.description}`} line={line} restaurant={room.restaurant} currency={room.restaurant.currency} canPrice onPrice={() => setPrice({ memberId: cart.memberId, lineId: line.id, value: line.unitPrice == null ? '' : minorInput(line.unitPrice, room.restaurant.currency) })} />)}{price.lineId && <form className="price-form" onSubmit={async e => { e.preventDefault(); const result = await data.send('PRICE_ITEM', { memberId: price.memberId, text: price.lineId, amount: amount(price.value, room.restaurant.currency) }, room.id); if (result) setPrice({ memberId: '', lineId: '', value: '' }); }}><label>{t("Unit price")}<input autoFocus inputMode="decimal" value={price.value} onChange={e => setPrice({ ...price, value: e.target.value })} /></label><button className="primary">{t("Save price")}</button></form>}</article>}
          {payer && <article className="card collected-orders"><p className="eyebrow">{tx("EVERYONE'S ORDERS", "طلبات الجميع")}</p><h2>{tx('Your restaurant order', 'الطلب الذي سترسله للمطعم')}</h2><p>{tx('You were selected to order and pay the full bill. Each person reimburses their share to you.', 'تم اختيارك للطلب ودفع الفاتورة كاملة. يسدد كل شخص حصته لك.')}</p>{reply.receipts.map(receipt => <ReceiptCard key={receipt.memberId} room={room} receipt={receipt} own={receipt.memberId === me.id} />)}</article>}
          {payer && !room.account && <article className="card notice-card"><h2>{t("Share your receiving details")}</h2><p>{t("Use the Aani or bank details saved in your profile so people know where to pay.")}</p><button className="primary" disabled={!data.home.profile.payment} onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>{data.home.profile.payment ? 'Share saved payment method' : 'Add payment details in profile'}</button></article>}
          {(owner || payer) && <FeeEditor key={`${room.quoteRevision}:${room.fees.delivery}:${room.fees.service}:${room.fees.discount}`} room={room} data={data} />}
          {payer && reply.receipts.some(receipt => receipt.lines.length) && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} finish />}
          {(owner || payer) && <article className="card next-step"><h2>{t("Finish collecting")}</h2><p>{reply.progress?.canReview ? 'Every person submitted, every custom item is priced, and payment details are ready.' : reply.progress?.reviewBlocker || 'Waiting for everyone to submit.'}</p><button className="primary wide" disabled={!reply.progress?.canReview} onClick={() => data.send('REVIEW', {}, room.id)}>{t("Review everyone’s totals")}</button></article>}
        </>}
        {room.phase === 'REVIEW' && <>
          <WalletPanel room={room} receipts={reply.receipts} me={me} payer={payer} />
          <article className="card"><p className="eyebrow">{t("CHECK YOUR SHARE")}</p><h2>{t("Confirm totals")}</h2>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}{myCart.confirmedQuote !== room.quoteRevision ? <button className="primary wide" onClick={() => data.send('CONFIRM_QUOTE', { expectedRevision: room.quoteRevision }, room.id)}>{t("Confirm my total and recipient")}</button> : <p className="success-message">{t("✓ Your total and payment recipient are confirmed.")}</p>}{payer && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} finish />}{payer && <form className="place-form" onSubmit={e => { e.preventDefault(); data.send('PLACE', { text: reference }, room.id); }}><label>{t("Restaurant confirmation / ETA")}<input value={reference} onChange={e => setReference(e.target.value)} placeholder={t("Confirmed · ready in 30 minutes")} required /></label><button className="primary" disabled={!allConfirmed}>{t("Announce order placed")}</button></form>}{!allConfirmed && <p className="muted">{t("Waiting for")}{orderingMembers.filter(member => room.carts.find(cart => cart.memberId === member.id)?.confirmedQuote !== room.quoteRevision).map(member => member.name).join(', ')}{t("to confirm.")}</p>}</article>
          {(owner || payer) && <FeeEditor key={`${room.quoteRevision}:${room.fees.delivery}:${room.fees.service}:${room.fees.discount}`} room={room} data={data} />}
        </>}
        {['PLACED','FULFILLED'].includes(room.phase) && <SettlementPanel room={room} reply={reply} me={me} owner={owner} payer={payer} data={data} />}
        {['ARCHIVED','CANCELLED'].includes(room.phase) && <article className="card empty"><span>✓</span><h2>{room.phase === 'ARCHIVED' ? 'Order complete' : 'Order cancelled'}</h2><p>{t("The room, final receipts, restaurant, and prices stay saved for the next meal.")}</p>{owner && <button className="primary" onClick={startNextOrder}>{t("Start next order")}</button>}</article>}
        {(roomHistoryChoices.length > 0 || reply.historyNextOffset >= 0) && <details className="card history-card"><summary>{t("My unique previous orders ·")}{roomHistoryChoices.length}</summary>{roomHistoryChoices.map(choice => <div className="past-order" key={choice.key}><div><b>{choice.order.restaurantName} · {orderSummary(choice.receipt.lines)}</b><small>{choice.repeatCount > 1 ? `Repeated ${choice.repeatCount} times · ` : ''}{new Date(choice.order.completedAt).toLocaleString()}</small></div><ReceiptCard room={{ ...room, payerId: null, account: choice.order.account, orderNumber: choice.order.number, restaurant: { ...room.restaurant, name: choice.order.restaurantName } }} receipt={choice.receipt} own /><button className="link" onClick={() => copyText(receiptText({ ...room, orderNumber: choice.order.number, account: choice.order.account, restaurant: { ...room.restaurant, name: choice.order.restaurantName } }, choice.receipt))}>{t("Copy receipt")}</button><div className="hero-actions"><button className="secondary" disabled={!choice.restaurantId || favorites.some(value => favoriteKey(value) === choice.key)} onClick={() => saveFavorite(choice)}>{favorites.some(value => favoriteKey(value) === choice.key) ? t("★ Favorite") : t("☆ Save as favorite")}</button></div></div>)}{reply.historyNextOffset >= 0 && <button className="secondary wide" disabled={data.busy} onClick={() => data.loadOlderHistory(room.id)}>{t("Load older orders")}</button>}</details>}
      </section>
      <aside className="room-side stack">
        <RoomInviteCard room={room} hub={data.hub} />
        {room.payerId && <section className="card payer-side"><p className="eyebrow">{t("ORDERING PERSON")}</p><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><h3>{room.members.find(member => member.id === room.payerId)?.name}</h3><p>{payer ? tx('You order, pay the full restaurant bill, and receive everyone’s share.', 'تطلب وتدفع فاتورة المطعم كاملة وتستلم حصص الآخرين.') : tx('Add your food, confirm your total, then pay this selected person.', 'أضف طعامك وأكد الإجمالي ثم ادفع حصتك لهذا الشخص المختار.')}</p></section>}
        <section className="card"><div className="section-title compact"><div><p className="eyebrow">{t("AT THE TABLE")}</p><h3>{room.members.filter(member => !member.removed).length}{t("people")}</h3></div>{owner && room.phase === 'LOBBY' && <button className="icon-button" aria-label={t("Invite registered people")} onClick={() => setInviteOpen(!inviteOpen)}>＋</button>}</div>{room.members.filter(member => !member.removed).map(member => <div className="member" key={member.id}><span className="avatar initials small">{initials(member.name)}</span><span><b>{member.name}{member.id === me.id ? ' · You' : ''}</b><small>{member.id === room.payerId ? t("Selected to order") : member.id === room.ownerId ? t("Organizer") : !member.approved ? t("Waiting for approval") : room.carts.find(cart => cart.memberId === member.id)?.submitted ? 'Food submitted' : member.participating ? 'Joined automatically' : 'Skipping'}</small></span>{payer && room.phase === 'COLLECTING' && !member.approved && !member.guest && !member.latePayerApproved && <button className="link" onClick={() => data.send('APPROVE_LATE_JOIN', { memberId: member.id }, room.id)}>{t("Accept late order")}</button>}{owner && !member.approved && ['LOBBY', 'COLLECTING'].includes(room.phase) && (room.phase === 'LOBBY' || member.guest || payer || member.latePayerApproved) && <button className="link" onClick={() => data.send('APPROVE', { memberId: member.id }, room.id)}>{t("Approve")}</button>}</div>)}{inviteOpen && <div className="invite-list"><p>{t("Invite registered people")}</p>{data.home.people.map(person => <button className="person-button" key={person.userId} onClick={() => data.send('IDENTITY', { identity: { action: 'INVITE', userId: person.userId } }, room.id)}><span className="avatar initials small">{initials(person.name)}</span>{person.name}<b>{t("Invite")}</b></button>)}</div>}</section>
        <section className="card room-tools"><p className="eyebrow">{t("ROOM TOOLS")}</p><button className="secondary wide" onClick={saveCurrentRestaurant}>{t("Save restaurant & prices")}</button>{payer && ['COLLECTING','REVIEW'].includes(room.phase) && data.home.profile.payment && <button className="secondary wide" onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>{t("Use saved payment details")}</button>}{owner && ['LOBBY','COLLECTING','REVIEW','ACCEPTING'].includes(room.phase) && <button className="link danger wide" onClick={() => { const reason = window.prompt('Why are you cancelling this order?'); if (reason) data.send('CANCEL', { text: reason }, room.id); }}>{t("Cancel today’s order")}</button>}</section>
        {(owner || payer) && <RoomManagement key={`${room.id}:${room.orderNumber}:${room.restaurant.id}`} room={room} data={data} owner={owner} payer={payer} />}
        {selected && <section className="card winner-mini"><p className="eyebrow">{t("SELECTED")}</p><h3>{winner.name}</h3><p>{t("Sandwich entry remains open while the payer confirms.")}</p></section>}
      </aside>
    </div></fieldset>
  </Page>;
}

function FoodRunClient() {
  const data = useFoodRun();
  const [siteConfig, setSiteConfig] = useState({ registrationsEnabled: true, roomCreationEnabled: true, maintenanceMessage: '' });
  const inviteCode = useMemo(() => new URLSearchParams(window.location.search).get('room')?.replace(/\D/g, '').slice(0, 6) || '', []);
  const [page, setPage] = useState(window.location.pathname.replace(/\/+$/, '') === '/admin' ? 'admin' : inviteCode ? 'join' : 'home');
  useEffect(() => { window.scrollTo({ top: 0 }); }, [page]);
  const [roomId, setRoomId] = useState('');
  const restoredRoom = useRef(false);
  useEffect(() => { fetch(`${PUBLIC_API_URL}/config`).then(response => response.ok ? response.json() : Promise.reject()).then(setSiteConfig).catch(() => {}); }, []);
  useEffect(() => {
    if (!data.hub) return;
    fetch(`${data.hub}/catalog`).then(response => response.ok ? response.json() : Promise.reject()).then(values => {
      const catalog = values.map(normalizeRestaurant);
      let previousIds = [];
      try { previousIds = JSON.parse(localStorage.getItem(SERVER_CATALOG_KEY) || '[]'); } catch { previousIds = []; }
      const local = loadRestaurants().filter(restaurant => !previousIds.includes(restaurant.id) && !catalog.some(server => server.id === restaurant.id));
      storeRestaurants([...local, ...catalog]);
      localStorage.setItem(SERVER_CATALOG_KEY, JSON.stringify([...new Set([...previousIds, ...builtInRestaurants.map(restaurant => restaurant.id), ...catalog.map(restaurant => restaurant.id)])]));
    }).catch(() => {});
  }, [data.hub]);
  const openRoom = id => {
    localStorage.setItem(ACTIVE_ROOM_KEY, JSON.stringify({ userId: data.user?.uid, hub: data.hub, roomId: id }));
    window.history.replaceState({}, '', window.location.pathname);
    setRoomId(id); setPage('room');
  };
  const closeRoom = () => { localStorage.removeItem(ACTIVE_ROOM_KEY); setRoomId(''); setPage('home'); };
  useEffect(() => {
    if (page === 'admin' || restoredRoom.current || inviteCode || !data.user || !data.hub) return;
    let saved;
    try { saved = JSON.parse(localStorage.getItem(ACTIVE_ROOM_KEY) || 'null'); } catch { saved = null; }
    if (saved?.userId === data.user.uid && saved?.hub === data.hub && data.sessions[saved.roomId]) {
      setRoomId(saved.roomId); setPage('room'); restoredRoom.current = true;
    } else if (Object.keys(data.sessions).length > 0) restoredRoom.current = true;
  }, [data.user, data.hub, data.sessions, inviteCode]);
  const alerts = useMemo(() => <>{siteConfig.maintenanceMessage && <div className="banner notice">{siteConfig.maintenanceMessage}</div>}{data.error && <div className="banner error" role="alert">{data.error}{data.hasPending && <button onClick={data.retry}>{t("Retry saved request")}</button>}</div>}{data.notice && <div className="banner notice">{data.notice}<button aria-label={t("Close")} onClick={() => data.setNotice('')}>×</button></div>}</>, [siteConfig.maintenanceMessage, data.error, data.hasPending, data.notice]);
  if (data.user && page === 'offline') return <>{alerts}<OfflineReceipts receipts={data.offlineReceipts} language={uiLanguage} onBack={() => setPage('home')} onClear={data.clearOfflineReceipts} /></>;
  const offlineAction = data.user && <button className="secondary" onClick={() => setPage('offline')}>{tx('Downloaded receipts', 'الإيصالات المحفوظة')}</button>;
  if (!data.authReady || (page !== 'admin' && data.user && data.hub && !data.home && !data.error)) return <><div className="splash"><div className="brand-mark">FR</div><div className="spinner" /><p>{t("Setting the table…")}</p>{offlineAction}</div>{alerts}</>;
  if (!data.user) return <AuthScreen ready={data.authReady} allowRegistration={siteConfig.registrationsEnabled} />;
  if (page === 'admin') return canAccessAdmin(data.user) ? <AdminApp key={data.user.uid} language={uiLanguage} user={data.user} onBack={() => { window.history.replaceState({}, '', '/'); setPage('home'); }} /> : <main className="center-shell"><section className="card"><p>{tx('This account cannot access administration.', 'هذا الحساب لا يملك صلاحية الإدارة.')}</p><button onClick={() => { window.history.replaceState({}, '', '/'); setPage('home'); }}>{t('Home')}</button></section></main>;
  if (!data.hub || (!data.home && data.error)) return <><HubScreen current={data.hub} connect={data.connect} error={data.error} /><div className="center-actions">{offlineAction}</div>{alerts}</>;
  const home = data.home;
  const profileMissing = !home.profile.name || !home.profile.phone;
  let content;
  if (page === 'downloads') content = <Page title={t("Get Food Run")} subtitle={t("Install the mobile app and keep your table close.")} onBack={() => setPage('home')}><AppDownloads /></Page>;
  else if (page === 'profile' || profileMissing) content = <ProfileScreen data={data} openRoom={openRoom} onBack={() => setPage('home')} />;
  else if (page === 'restaurants') content = <RestaurantLibraryScreen language={uiLanguage} onBack={() => setPage('home')} />;
  else if (page === 'create' || page === 'join') content = <CreateRoom data={data} mode={page} inviteCode={inviteCode} onBack={() => setPage('home')} openRoom={openRoom} />;
  else if (page === 'room') content = <RoomScreen data={data} roomId={roomId} onBack={closeRoom} />;
  else content = <Home data={data} setPage={setPage} openRoom={openRoom} allowRoomCreation={siteConfig.roomCreationEnabled} />;
  return <>{alerts}{content}<footer><span>Food Run</span>{offlineAction}<button onClick={() => setPage('restaurants')}>{t("Restaurants & menus")}</button><button onClick={() => setPage('downloads')}>{t("Get the apps")}</button><button onClick={() => setPage('profile')}>{t("Profile")}</button><button onClick={() => data.connect('')}>{t("Switch room server")}</button><button onClick={() => signOut(auth)}>{t("Sign out")}</button></footer></>;
}

export default function FoodRunApp() {
  const [language, setLanguage] = useState(uiLanguage);
  uiLanguage = language;
  setTranslationLanguage(language);
  updateLanguage = value => { localStorage.setItem(LANGUAGE_KEY, value); setLanguage(value); };
  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }, [language]);
  return <Suspense fallback={<div className="splash"><p>{tx('Loading…', 'جارٍ التحميل…')}</p></div>}>{<FoodRunClient />}</Suspense>;
}

export { Home, ProfileScreen, RoomScreen, MemberOrderPanel, PUBLIC_API_URL, Page, LanguageToggle, loadRestaurants, storeRestaurants, blankRestaurant, normalizeRestaurant, clone, uid, minorInput, parseRestaurantExport, restaurantExport, downloadText, copyText };
