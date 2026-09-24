import RunningNames from './RunningNames.jsx';
import { useNotifications } from './useNotifications.js';
import { NotificationCenter, NotificationActionCard } from './NotificationCenter.jsx';
import { CreatePaymentRoom, PaymentReceipt, PaymentShareEditor, RecordPayment } from './PaymentRoom.jsx';
import { roomFeeUpdate } from './roomFees.js';
import RestaurantPollPrompt from './RestaurantPollPrompt.jsx';
import OrderPricingPanel from './OrderPricingPanel.jsx';
import ReorderReview from './ReorderReview.jsx';
import { lastRestaurantOrder, nextRestaurantHistoryRoom } from './reorder.js';
import { paymentDraft, paymentAccount, uaePhone } from './paymentDetails.js';
import PaymentFields from './PaymentFields.jsx';
import { groupedOrderLines, restaurantOrderText } from './restaurantOrderText.js';
import BlockedNotice from './BlockedNotice.jsx';
import NativeGoogleSignIn from './NativeGoogleSignIn.jsx';
import { roomInvitation } from './roomInvitation.js';
import { deliveryDestination } from './roomSetup';
import { menuCategories, defaultMenuCategory, browsedMenuItems } from './menuBrowsing';
import { canAccessAdmin } from './adminAccess';
import { addMenuLine, changeQuantity, menuLineTotal } from './cartEditing';
import { t, tf, setLanguage as setTranslationLanguage } from './i18n.js';
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
import { polarPoint, spinRotation, WHEEL_PALETTE, wheelLabel, wheelSlice, wheelSlicePath } from './wheel';
import builtInRestaurants from './builtInRestaurants.json';
import { sharjahRestaurants } from './sharjahRestaurants';
import { dubaiRestaurants } from './dubaiRestaurants';
import { mergeRestaurantCatalog, restoreRestaurantMetadata } from './restaurantCatalog';
import { selectedPollRestaurants } from './restaurantPoll';
import { validateMenu } from './menuValidation';

const AdminApp = lazy(() => import('./AdminApp'));
const RestaurantLibraryScreen = lazy(() => import('./RestaurantLibraryScreen'));
const OfflineReceipts = lazy(() => import('./ReceiptArchiveScreen.jsx'));

const phaseLabel = {
  LOBBY: 'Gathering', PREPARING_SPIN: 'Getting ready', SPINNING: 'Selecting',
  ACCEPTING: 'Waiting for acceptance', COLLECTING: 'Collecting food', REVIEW: 'Ready for the restaurant',
  PLACED: 'Order placed', FULFILLED: 'Food arrived', ARCHIVED: 'Complete', CANCELLED: 'Cancelled',
};

const ANDROID_DOWNLOAD_URL = 'https://github.com/AhmedKaram2/FoodRun/releases/download/v1.5.0/FoodRun-Android-1.5.0.apk';
const IOS_STORE_URL = import.meta.env.VITE_FOODRUN_IOS_URL?.trim() || '';
const PUBLIC_API_URL = import.meta.env.VITE_FOODRUN_API_URL?.trim().replace(/\/$/, '') || 'https://foodrun-api-q6b9.onrender.com';
const RESTAURANT_LIBRARY_KEY = 'foodrun-restaurants-v1';
const SERVER_CATALOG_KEY = 'foodrun-server-catalog-v1';
const RESTAURANT_LIBRARY_EVENT = 'foodrun-restaurants-changed';
const ACTIVE_ROOM_KEY = 'foodrun-active-room-v1';
const LANGUAGE_KEY = 'foodrun-language-v1';
const LEGACY_RESTAURANT_METADATA = {
  'builtin-sultan': { emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Sharjah', areaAr: 'الشارقة', cuisine: 'Egyptian', cuisineAr: 'مصري', mealTypes: ['breakfast','lunch','dinner'] },
  'builtin-al-kalha': { emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Sharjah', areaAr: 'الشارقة', cuisine: 'Levantine', cuisineAr: 'شامي', mealTypes: ['breakfast','lunch','dinner'] },
  'builtin-al-mahla': { emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Sharjah', areaAr: 'الشارقة', cuisine: 'Egyptian', cuisineAr: 'مصري', mealTypes: ['breakfast','lunch','dinner'] },
  'builtin-bait-al-waleema': { emirate: 'Sharjah', emirateAr: 'الشارقة', area: 'Al Majaz', areaAr: 'المجاز', cuisine: 'Egyptian', cuisineAr: 'مصري', mealTypes: ['lunch','dinner'] },
};
const BUNDLED_RESTAURANTS = [...builtInRestaurants.map(value => ({ ...value, ...(LEGACY_RESTAURANT_METADATA[value.id] || {}) })), ...sharjahRestaurants, ...dubaiRestaurants];
let uiLanguage = localStorage.getItem(LANGUAGE_KEY) === 'ar' ? 'ar' : 'en';
let updateLanguage = () => {};

function tx(english, arabic) { return uiLanguage === 'ar' ? (t(english, 'ar') === english ? arabic : t(english, 'ar')) : english; }
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
    emirate: '', emirateAr: '', area: '', areaAr: '', cuisine: '', cuisineAr: '', mealTypes: [], googleRating: null, googleRatingCount: null, googleRatingVerifiedOn: '',
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
    emirate: String(value.emirate || '').trim(), emirateAr: String(value.emirateAr || '').trim(), area: String(value.area || '').trim(), areaAr: String(value.areaAr || '').trim(), cuisine: String(value.cuisine || '').trim(), cuisineAr: String(value.cuisineAr || '').trim(),
    mealTypes: [...new Set((Array.isArray(value.mealTypes) ? value.mealTypes : []).filter(meal => ['breakfast','lunch','dinner'].includes(meal)))],
    googleRating: value.googleRating == null ? null : Number(value.googleRating), googleRatingCount: value.googleRatingCount == null ? null : Number(value.googleRatingCount), googleRatingVerifiedOn: String(value.googleRatingVerifiedOn || ''),
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
  try { saved = restoreRestaurantMetadata(JSON.parse(localStorage.getItem(RESTAURANT_LIBRARY_KEY) || '[]'), BUNDLED_RESTAURANTS).map(normalizeRestaurant); }
  catch { saved = []; }
  try { managedIds = JSON.parse(localStorage.getItem(SERVER_CATALOG_KEY) || '[]'); } catch { managedIds = []; }
  return [...saved, ...BUNDLED_RESTAURANTS.map(normalizeRestaurant).filter(builtIn => !managedIds.includes(builtIn.id) && !saved.some(restaurant => restaurant.id === builtIn.id))]
    .sort((left, right) => left.name.localeCompare(right.name));
}
function storeRestaurants(restaurants) {
  localStorage.setItem(RESTAURANT_LIBRARY_KEY, JSON.stringify(restaurants));
  window.dispatchEvent(new Event(RESTAURANT_LIBRARY_EVENT));
}
export function useRestaurantLibrary() {
  const [restaurants, setRestaurants] = useState(loadRestaurants);
  useEffect(() => {
    const refresh = () => setRestaurants(loadRestaurants());
    const storageChanged = event => {
      if (event.key === null || [RESTAURANT_LIBRARY_KEY, SERVER_CATALOG_KEY].includes(event.key)) refresh();
    };
    window.addEventListener(RESTAURANT_LIBRARY_EVENT, refresh);
    window.addEventListener('storage', storageChanged);
    refresh();
    return () => {
      window.removeEventListener(RESTAURANT_LIBRARY_EVENT, refresh);
      window.removeEventListener('storage', storageChanged);
    };
  }, []);
  return [restaurants, setRestaurants];
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

function LiveSelectionWheel(props) {
  return props.style === 'names' ? <RunningNames {...props} /> : <WheelAnimation {...props} />;
}
function WheelAnimation({ spin, members, serverTime, active }) {
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
  const labelSize = count <= 4 ? 18 : count <= 7 ? 15 : 12;
  return <div className={`live-wheel is-${motion.stage}`}>
    <svg viewBox="0 0 400 410" role="img" aria-label={motion.stage === 'finished' ? tf('{name} was selected', { name: candidates[winnerIndex]?.name || t('Someone') }) : tf('Selecting one of {count} people', { count })}>
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
          : candidates.map((candidate, index) => <path className={`wheel-slice ${motion.stage === 'finished' && index === winnerIndex ? 'selected' : ''}`} d={wheelSlicePath(index, count, 172, 200, spin.weights)} fill={WHEEL_PALETTE[index % WHEEL_PALETTE.length]} key={candidate.id} />)}
        {candidates.map((candidate, index) => {
          const geometry = wheelSlice(index, count, spin.weights);
          const angle = -90 + geometry.center;
          const [x, y] = polarPoint(angle, 113);
          return <text className="wheel-person-name" x={x} y={y} fontSize={Math.min(labelSize, Math.max(7, geometry.sweep / 3))} textAnchor="middle" dominantBaseline="middle" transform={`rotate(${angle + 180} ${x} ${y})`} key={candidate.id}>{wheelLabel(candidate.name)}</text>;
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
    {selected && winner.id === me.id && <div className="hero-actions"><button className="primary" onClick={() => data.send('ACCEPT_DUTY', {}, room.id)}>{t("I’ll take care of it")}</button><button className="secondary" onClick={() => data.send('DECLINE_DUTY', { text: t("Unavailable this time") }, room.id)}>{t("I can’t this time")}</button></div>}
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
        <div><strong>{t("Android app")}</strong><small>{t("Version 1.5.0 · Android 8+")}</small></div>
        <a className="primary store-button" href={ANDROID_DOWNLOAD_URL}>{t("Download APK")}</a>
      </article>
      <article className="download-card">
        <span className="platform-icon apple" aria-hidden="true">●</span>
        <div><strong>{t("iPhone app")}</strong><small>{IOS_STORE_URL ? t("Available for iPhone and iPad") : t("Install from Safari · iOS 17+")}</small></div>
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
      <h2>{register ? t("Create your profile") : t("Welcome back")}</h2>
      <p className="muted">{t("Use the same account you already use in Intrvioo.")}</p>
      <button className="google" disabled={!ready || busy} onClick={() => signInWithPopup(auth, googleProvider).catch(e => setMessage(e.message))}>{t("Continue with Google")}</button>
      <div className="or"><span />{t("or")}<span /></div>
      <form onSubmit={submit} className="stack">
        {register && <label>{t("Name")}<input value={name} onChange={e => setName(e.target.value)} required /></label>}
        <label>{t("Email")}<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>{t("Password")}<input type="password" minLength="6" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {message && <p className="form-message" role="alert">{message}</p>}
        <button className="primary" disabled={!ready || busy}>{busy ? t("One moment…") : register ? t("Create account") : t("Sign in")}</button>
      </form>
      {!register && <button className="link" onClick={reset}>{t("Forgot password?")}</button>}
      {(allowRegistration || register) && <button className="link switch" onClick={() => { setRegister(!register); setMessage(''); }}>{register ? t("Already registered? Sign in") : t("New to Food Run? Create account")}</button>}
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
    <div className="section-title"><div><p className="eyebrow">{t("MY MONEY & ORDERS")}</p><h2>{t("Wallet")}</h2></div><span>{dashboard.entries.length}</span></div>
    <div className="wallet-directions">{[
      { key: 'pay', title: t("I need to pay"), total: dashboard.toPay, entries: dashboard.payEntries, empty: tx('You have no payments due.', 'لا توجد مبالغ مستحقة عليك.') },
      { key: 'receive', title: t("I need to receive"), total: dashboard.toReceive, entries: dashboard.receiveEntries, empty: tx('No one owes you a payment.', 'لا توجد مبالغ مستحقة لك.') },
    ].map(section => <section className={`card wallet-direction ${section.key}`} key={section.key} aria-label={section.title}>
      <div className="wallet-direction-heading"><h3>{section.title}</h3><strong>{money(section.total, 'AED')}</strong></div>
      {!section.entries.length && <p className="muted">{section.empty}</p>}
      {section.entries.map(entry => <article className="wallet-person" key={`${entry.roomId}:${entry.personId}`}>
        <div className="wallet-person-heading"><span className="wallet-avatar" aria-hidden="true">{entry.person.slice(0, 1)}</span><h4>{entry.person}</h4><strong>{money(entry.amount, entry.currency)}</strong></div>
        <small>{entry.roomName}{entry.kind === 'refund' ? ` · ${tx('Refund to send', 'مبلغ مرتجع للإرسال')}` : ''}</small>
        <p>{localizedName(entry.room.restaurant)} · {tf('Order #{number}', { number: entry.room.orderNumber })}</p>
        <small>{t('Order')} {money(entry.receipt.total, entry.currency)} · {t('Paid')} {money(entry.receipt.paid, entry.currency)}</small>
        <PaymentActionLine room={entry.room} receipt={entry.receipt} memberId={entry.memberId} data={data} />
        {section.key === 'pay' && entry.kind !== 'refund' && <PaymentDetails account={entry.room.account} data={data} />}
        <button className="secondary wide" onClick={() => openRoom(entry.roomId)}>{t('Order and payment details')}</button>
      </article>)}
    </section>)}</div>
    <section id="payment-history" className="card payment-history"><h3>{t("Payment history")}</h3><p className="muted">{t("Order totals and confirmed payments, newest first.")}</p>
      {!dashboard.paymentHistory.length && <p>{t("No payment history yet")}</p>}
      {dashboard.paymentHistory.slice(0, compact ? 3 : 30).map(entry => <article className="payment-history-row" key={entry.key}>
        <h4>{entry.restaurantName} · #{entry.number}</h4><small>{entry.roomName} · {new Date(entry.at).toLocaleDateString(uiLanguage === 'ar' ? 'ar-EG' : 'en-AE')}</small>
        <p>{t("Order")} {money(entry.receipt.total, entry.receipt.currency)} · {t("Paid")} {money(entry.receipt.paid, entry.receipt.currency)}</p>
        <button className="secondary" onClick={() => openRoom(entry.roomId)}>{t("Order and payment details")}</button>
      </article>)}
    </section>
    {!compact && dashboard.currentOrders.length > 0 && <div className="card order-dashboard"><h3>{t("Current orders")}</h3>{dashboard.currentOrders.map(({ room }) => <button className="order-dashboard-row" onClick={() => openRoom(room.id)} key={room.id}><span><b>{room.name} · #{room.orderNumber}</b><small>{t(phaseLabel[room.phase])} · {localizedName(room.restaurant)}</small></span><strong>{t("Open →")}</strong></button>)}</div>}
  </section>;
}

function ProfileScreen({ data, onBack, openRoom }) {
  const { profile } = data.home;
  const busy = data.busy, send = data.send;
  const [message, setMessage] = useState('');
  const [form, setForm] = useState({
    name: profile?.name || auth.currentUser?.displayName || '', phone: profile?.phone || '', photo: profile?.photo || '',
    discoverable: profile?.discoverable ?? true, ...paymentDraft(profile?.payment),
  });
  const [dirty, setDirty] = useState(false);
  const syncedProfile = useRef(profile);
  useEffect(() => {
    if (!dirty && syncedProfile.current !== profile) {
      syncedProfile.current = profile;
      setForm(old => { const payment = paymentDraft(profile?.payment); return { name: profile?.name || '', phone: profile?.phone || '', photo: profile?.photo || '', discoverable: profile?.discoverable ?? true, ...payment, ...(profile?.payment ? { iban: payment.iban || old.iban, aaniPhone: payment.aaniPhone || old.aaniPhone } : {}) }; });
    }
  }, [profile, dirty]);
  const set = (key, value) => { setDirty(true); setForm(old => ({ ...old, [key]: value })); };
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      const payment = paymentAccount(form, profile?.payment, form.name);
      const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: {
        userId: '', name: form.name.trim(), phone: uaePhone(form.phone, true), photo: form.photo,
        payment, discoverable: form.discoverable, language: uiLanguage, favoriteOrders: profile?.favoriteOrders || [],
      } } });
      if (reply) { setDirty(false); setMessage(t("Profile and payment details saved.")); }
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
    {message && <p className="form-message" role="status">{message}</p>}
    <nav className="profile-shortcuts" aria-label={t("Profile sections")}><a className="secondary" href="#profile-details">{t('Your details')}</a><a className="secondary" href="#profile-wallet">{t('Wallet')}</a><a className="secondary" href="#payment-history">{t('Payment history')}</a><a className="secondary" href="#favorite-orders">{tx('Favorites', 'المفضلة')}</a></nav>
    <section id="profile-details" className="profile-details"><h2>{t('Your details')}</h2><form className="profile-grid" onSubmit={save}>
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
        <div><p className="eyebrow">{t("GET PAID BACK")}</p><h3>{t("Receiving details")}</h3><p className="muted">{t("Changes update your profile and all active rooms where you collect payments.")}</p></div>
        <PaymentFields form={form} set={set} name={form.name} />
      </section>
      <div className="form-actions"><button className="primary" disabled={busy}>{busy ? t("Saving…") : t("Save profile")}</button></div>
    </form></section>
    <div id="profile-wallet"><UserDashboard data={data} openRoom={openRoom} /></div>
    <section id="favorite-orders" className="profile-orders"><div className="section-title"><div><p className="eyebrow">{t("FAST REORDER")}</p><h2>{t("Favorite orders")}</h2></div><span>{favorites.length}</span></div>{favorites.length ? <div className="grid two">{favorites.map(favorite => <article className="card saved-order" key={favorite.id}><span className="status live">{t("★ Favorite")}</span><h3>{favorite.restaurantName}</h3><p>{orderSummary(favorite.lines)}</p><button className="link danger" onClick={() => removeFavorite(favorite.id)}>{t("Remove favorite")}</button></article>)}</div> : <div className="card empty small-empty"><span>☆</span><p>{t("Save a previous order below. It will appear inside matching restaurant rooms for one-tap add.")}</p></div>}</section>
    <section id="previous-orders" className="profile-orders"><div className="section-title"><div><p className="eyebrow">{t("ORDER HISTORY")}</p><h2>{t("Unique previous orders")}</h2><p>{t("Exact repeats are grouped; changed quantities, sizes, extras, or notes stay separate.")}</p></div><span>{previous.length}</span></div>{previous.length ? <div className="grid two">{previous.slice(0, previousLimit).map(choice => <article className="card saved-order" key={choice.key}><span className="status">{choice.repeatCount > 1 ? tf('Repeated {count} times', { count: choice.repeatCount }) : new Date(choice.order.completedAt).toLocaleDateString()}</span><h3>{choice.order.restaurantName}</h3><p>{orderSummary(choice.receipt.lines)}</p>{!favoriteKeys.has(choice.key) && <button className="secondary" disabled={busy || !choice.restaurantId} onClick={() => saveFavorite(choice)}>{t("☆ Save as favorite")}</button>}</article>)}</div> : <div className="card empty small-empty"><span>🥡</span><p>{t("Completed orders appear here after a room starts its next order.")}</p></div>}{previous.length > previousLimit && <button className="secondary wide" onClick={() => setPreviousLimit(value => value + 10)}>{t("Show more unique orders")}</button>}</section>
  </Page>;
}

function Page({ title, subtitle, onBack, actions, children }) {
  return <main className="app-shell">
    <header className="topbar"><button className="wordmark" onClick={onBack}><span>FR</span> FOOD RUN</button><div className="top-actions"><LanguageToggle />{actions}</div></header>
    <div className="page-heading">{onBack && <button className="back" onClick={onBack}>{uiLanguage === 'ar' ? 'رجوع ←' : t("← Back")}</button>}<p className="eyebrow">{t("FOOD RUN / TOGETHER")}</p><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {children}
  </main>;
}

function Home({ data, setPage, openRoom, allowRoomCreation = true }) {
  const { home, rooms, sessions, online } = data;
  const roomCards = Object.values(sessions).map(session => ({ session, reply: rooms[session.roomId] })).sort((a, b) => (b.reply?.room?.createdAt || 0) - (a.reply?.room?.createdAt || 0));
  return <Page title={tf('Good food, {name}.', { name: home.profile.name?.split(' ')[0] || t('together') })} subtitle={t("Start a table or jump back into today’s order.")} actions={<><button className="icon-button" aria-label={t("Notifications")} onClick={() => setPage('notifications')}>◔</button><button className="profile-chip" onClick={() => setPage('profile')}><Avatar small profile={home.profile} />{home.profile.name || t("Complete profile")}</button></>}>
    {data.roomBlocks?.['*'] && <BlockedNotice block={data.roomBlocks['*']} retry={() => data.connect(data.hub)} inline />}
    <section className="hero card"><div><p className="eyebrow">{t("A TABLE FOR EVERYONE")}</p><h2>{t("One room. The whole crew.")}</h2><p>{t("Everyone joins live, the wheel picks who orders, and every item and amount stays together.")}</p><div className="hero-actions"><button className="primary light" disabled={!allowRoomCreation} onClick={() => setPage('create')}>{t("Create a room")}</button><button className="secondary light" disabled={!allowRoomCreation} onClick={() => setPage('payment-create')}>{t("Payment room")}</button><button className="secondary light" onClick={() => setPage('join')}>{t("Join with code")}</button><button className="secondary light" onClick={() => setPage(t("restaurants"))}>{t("Restaurants & menus")}</button>{canAccessAdmin(data.user) && <button className="secondary light" onClick={() => setPage('admin')}>{tx('Admin panel', 'لوحة الإدارة')}</button>}</div>{!allowRoomCreation && <p className="form-message">{t("New room creation is temporarily disabled by the administrator.")}</p>}</div><div className="hero-art"><span>🥡</span><span>🍜</span><span>🥗</span></div></section>
    <UserDashboard data={data} openRoom={openRoom} compact />
    <AppDownloads />
    {home.invitations.length > 0 && <section><div className="section-title"><div><p className="eyebrow">{t("YOU’RE INVITED")}</p><h2>{t("Join the table")}</h2></div><span>{home.invitations.length}</span></div><div className="grid two">{home.invitations.map(invite => <article className="card invitation" key={invite.id}><span className="status live">{t("Invitation")}</span><h3>{invite.roomName}</h3><p>{invite.invitedBy}{t("invited you to order #")}{invite.orderNumber}.</p><button className="primary" onClick={async () => { const reply = await data.send('IDENTITY', { identity: { action: 'ACCEPT_INVITE', invitationId: invite.id } }); if (reply?.room) openRoom(reply.room.id); }}>{t("Join room")}</button></article>)}</div></section>}
    <section><div className="section-title"><div><p className="eyebrow">{t("YOUR TABLES")}</p><h2>{t("Live rooms")}</h2></div><span>{roomCards.length}</span></div>
      {roomCards.length ? <div className="grid two">{roomCards.map(({ session, reply }) => {
        const room = reply?.room, member = room?.members.find(m => m.id === session.memberId), winner = room?.members.find(m => m.id === (room.payerId || room.spin?.winnerId));
        const receipt = reply?.receipts.find(r => r.memberId === session.memberId);
        return <article className="card room-card" key={session.roomId} onClick={() => openRoom(session.roomId)}><div className="room-card-top"><span className={`status ${online[session.roomId] ? 'live' : ''}`}>{online[session.roomId] ? t("● Live") : t("Offline")}</span><span>#{room?.orderNumber || 1}</span></div><h3>{session.roomName}</h3><p>{room ? t(phaseLabel[room.phase]) : t("Connecting…")}</p>{winner && <div className="winner-row"><span className="avatar initials small">{initials(winner.name)}</span><span><b>{winner.name}</b><small>{room.payerId ? ' is ordering' : ' was selected'}</small></span></div>}{receipt && ['REVIEW','PLACED','FULFILLED'].includes(room.phase) && <div className="amount-row"><span>{t("Your total")}</span><b>{receipt.totalText}</b></div>}<button className="secondary">{room?.phase === 'LOBBY' && !member?.participating ? t("Join this order") : t("Open room")} →</button></article>;
      })}</div> : <div className="card empty"><span>🥢</span><h3>{t("No saved rooms yet")}</h3><p>{t("Create a room and invite your people, or join with a six-digit code.")}</p></div>}
    </section>
  </Page>;
}

function RestaurantPicker({ restaurants, selectedId, onSelect, onClose, multiple = false, selectedIds = [] }) {
  const dialog = useRef(null);
  const close = useRef(onClose);
  close.current = onClose;
  const [search, setSearch] = useState('');
  const [emirate, setEmirate] = useState('');
  const [area, setArea] = useState('');
  const [meal, setMeal] = useState('');
  useEffect(() => {
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    // Keep the results visible on phones; open the keyboard only when Search is tapped.
    dialog.current?.querySelector('button')?.focus();
    const onKey = event => {
      if (event.key === 'Escape') { event.preventDefault(); close.current(); }
      if (event.key !== 'Tab') return;
      const focusable = [...dialog.current.querySelectorAll('button, input, select, summary, [tabindex="0"]')].filter(node => !node.disabled && node.getClientRects().length);
      const first = focusable[0], last = focusable.at(-1);
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => {
      document.body.style.overflow = overflow;
      document.removeEventListener('keydown', onKey);
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, []);
  const resetFilters = () => { setSearch(''); setEmirate(''); setArea(''); setMeal(''); };
  const emirates = [...new Set(restaurants.map(value => value.emirate).filter(Boolean))].sort();
  const areas = [...new Set(restaurants.filter(value => !emirate || value.emirate === emirate).map(value => value.area).filter(Boolean))].sort();
  const visible = restaurants.filter(restaurant => {
    const haystack = [restaurant.name, restaurant.nameAr, restaurant.emirate, restaurant.emirateAr, restaurant.area, restaurant.areaAr, restaurant.cuisine, restaurant.cuisineAr, restaurant.contact?.address].join(' ').toLowerCase();
    return (!search.trim() || haystack.includes(search.trim().toLowerCase())) && (!emirate || restaurant.emirate === emirate) && (!area || restaurant.area === area) && (!meal || restaurant.mealTypes?.includes(meal));
  });
  const mealName = value => ({ breakfast: tx('Breakfast', 'فطور'), lunch: tx('Lunch', 'غداء'), dinner: tx('Dinner', 'عشاء') })[value];
  return <div className="restaurant-picker-backdrop" role="presentation" onMouseDown={event => { if (event.target === event.currentTarget) onClose(); }}>
    <section ref={dialog} className="restaurant-picker card" role="dialog" aria-modal="true" aria-labelledby="restaurant-picker-title">
      <div className="section-title compact"><div><p className="eyebrow">{tx('UAE RESTAURANTS', 'مطاعم الإمارات')}</p><h2 id="restaurant-picker-title">{multiple ? tx('Choose poll restaurants', 'اختر مطاعم التصويت') : tx('Choose a restaurant', 'اختر مطعماً')}</h2></div><button className="icon-button" type="button" onClick={onClose} aria-label={tx('Close', 'إغلاق')}>×</button></div>
      <div className="restaurant-picker-filters">
        <label className="restaurant-search">{tx('Search', 'بحث')}<input type="search" enterKeyHint="search" value={search} onChange={event => setSearch(event.target.value)} placeholder={tx('Name, cuisine or address', 'الاسم أو المطبخ أو العنوان')} /></label>
        <label>{tx('Emirate', 'الإمارة')}<select value={emirate} onChange={event => { setEmirate(event.target.value); setArea(''); }}><option value="">{tx('All Emirates', 'كل الإمارات')}</option>{emirates.map(value => <option value={value} key={value}>{uiLanguage === 'ar' ? restaurants.find(item => item.emirate === value)?.emirateAr || value : value}</option>)}</select></label>
        <label>{tx('Area', 'المنطقة')}<select value={area} onChange={event => setArea(event.target.value)}><option value="">{tx('All areas', 'كل المناطق')}</option>{areas.map(value => <option value={value} key={value}>{uiLanguage === 'ar' ? restaurants.find(item => item.area === value)?.areaAr || value : value}</option>)}</select></label>
        <label>{tx('Meal', 'الوجبة')}<select value={meal} onChange={event => setMeal(event.target.value)}><option value="">{tx('Any meal', 'كل الوجبات')}</option><option value="breakfast">{mealName('breakfast')}</option><option value="lunch">{mealName('lunch')}</option><option value="dinner">{mealName('dinner')}</option></select></label>
      </div>
      <div className="restaurant-results-heading"><span role="status">{visible.length} {tx('restaurants', 'مطعماً')}</span>{(search || emirate || area || meal) && <button type="button" className="link" onClick={resetFilters}>{tx('Clear filters', 'مسح الفلاتر')}</button>}</div>
      <div className="restaurant-picker-results">
        {!multiple && <button type="button" className={`restaurant-pick-card ${!selectedId ? 'selected' : ''}`} onClick={() => { onSelect(''); onClose(); }}><span className="restaurant-pick-icon">＋</span><span><b>{tx('Quick open order', 'طلب مفتوح سريع')}</b><small>{tx('Enter any restaurant and add custom items', 'أدخل أي مطعم وأضف الأصناف يدوياً')}</small></span></button>}
        {visible.map(restaurant => <button type="button" aria-pressed={multiple ? selectedIds.includes(restaurant.id) : selectedId === restaurant.id} disabled={multiple && selectedIds.length >= 12 && !selectedIds.includes(restaurant.id)} className={`restaurant-pick-card ${(multiple ? selectedIds.includes(restaurant.id) : selectedId === restaurant.id) ? 'selected' : ''}`} onClick={() => { onSelect(restaurant.id); if (!multiple) onClose(); }} key={restaurant.id}>
          <span className="restaurant-pick-icon">{multiple ? selectedIds.includes(restaurant.id) ? '✓' : '＋' : '🍽️'}</span><span><b>{localizedName(restaurant)}</b><small>{[uiLanguage === 'ar' ? restaurant.emirateAr || restaurant.emirate : restaurant.emirate, uiLanguage === 'ar' ? restaurant.areaAr || restaurant.area : restaurant.area, uiLanguage === 'ar' ? restaurant.cuisineAr || restaurant.cuisine : restaurant.cuisine].filter(Boolean).join(' · ')}</small><small>{restaurant.googleRating ? `★ ${restaurant.googleRating} Google · ` : ''}{restaurant.menu.items.length ? `${restaurant.menu.items.length} ${tx('priced items', 'صنفاً بأسعاره')}` : tx('Open order · confirm prices', 'طلب مفتوح · أكد الأسعار')}</small><span className="meal-tags">{(restaurant.mealTypes || []).map(value => <em key={value}>{mealName(value)}</em>)}</span></span>
        </button>)}
        {!visible.length && <div className="empty"><span>🔎</span><h3>{tx('No matching restaurants', 'لا توجد مطاعم مطابقة')}</h3><p>{tx('Try another name, emirate, area or meal.', 'جرّب اسماً أو إمارة أو منطقة أو وجبة أخرى.')}</p></div>}
      </div>
      {multiple && <div className="poll-picker-footer"><span role="status">{selectedIds.length}/12 · {tx('Choose at least 2', 'اختر مطعمين على الأقل')}</span><button type="button" className="primary" disabled={selectedIds.length < 2} onClick={onClose}>{tx('Use selected restaurants', 'استخدم المطاعم المختارة')}</button></div>}
    </section>
  </div>;
}

export function CreateRoom({ data, mode, onBack, openRoom, inviteCode = '' }) {
  const profile = data.home.profile;
  const [restaurants, setRestaurants] = useRestaurantLibrary();
  const [form, setForm] = useState({ room: 'Mohre', selectionStyle: 'wheel', restaurantId: '', restaurant: '', phone: '', code: inviteCode, restaurantPoll: false, deliveryMode: false, destination: "Mohre, Backside Parking, Security gate, Opposite Suni's Restaurant https://maps.app.goo.gl/cLba7hYb9Rtfqyjr5", delivery: '0.00', service: '0.00', discount: '0.00', proportionalDelivery: false });
  const [message, setMessage] = useState('');
  const [restaurantPicker, setRestaurantPicker] = useState(false);
  const [pollIds, setPollIds] = useState([]);
  useEffect(() => {
    const available = new Set(restaurants.map(value => value.id));
    setPollIds(old => old.every(id => available.has(id)) ? old : old.filter(id => available.has(id)));
  }, [restaurants]);
  const pollChoices = pollIds.map(id => restaurants.find(value => value.id === id)).filter(Boolean);
  const togglePollRestaurant = id => setPollIds(old => old.includes(id) ? old.filter(value => value !== id) : old.length < 12 ? [...old, id] : old);
  const startPoll = () => { setForm(old => ({ ...old, restaurantPoll: true })); if (!pollIds.length && form.restaurantId) setPollIds([form.restaurantId]); setRestaurantPicker(true); };
  const chosen = restaurants.find(restaurant => restaurant.id === form.restaurantId);
  const currency = chosen?.currency || 'AED';
  const chooseRestaurant = id => {
    const restaurant = restaurants.find(value => value.id === id);
    setForm(old => ({ ...old, room: old.room || (restaurant ? localizedName(restaurant) : ''), restaurantId: id, delivery: restaurant ? minorInput(restaurant.pricing.defaultDeliveryFeeMinor, restaurant.currency) : '0.00', service: restaurant ? minorInput(restaurant.pricing.defaultServiceFeeMinor, restaurant.currency) : '0.00' }));
  };
  const submit = async event => {
    event.preventDefault(); setMessage('');
    try {
      let reply;
      if (mode === 'join') reply = await data.send('JOIN', { code: form.code.trim(), name: profile.name.trim() });
      else {
        const pollRestaurants = form.restaurantPoll ? selectedPollRestaurants(restaurants, pollIds).map(clone) : [];
        const restaurant = form.restaurantPoll ? pollRestaurants[0] : chosen ? clone(chosen) : normalizeRestaurant({ ...blankRestaurant(), name: form.restaurant.trim(), contact: { phoneE164: form.phone.trim(), whatsappE164: null, address: null } });
        if (!chosen && !form.restaurantPoll) { const next = [...restaurants, restaurant]; setRestaurants(next); storeRestaurants(next); }
        reply = await data.send('CREATE', {
          name: profile.name.trim(), text: form.room.trim(), restaurant, restaurants: form.restaurantPoll ? pollRestaurants : [restaurant], expectedNames: [], flag: form.deliveryMode,
          selectionStyle: form.selectionStyle, destination: deliveryDestination(form.deliveryMode, form.destination), deadline: 0,
          fees: { delivery: form.deliveryMode ? 0 : amount(form.delivery || '0', currency), automaticDelivery: form.deliveryMode, service: amount(form.service || '0', currency), discount: amount(form.discount || '0', currency), proportionalDelivery: form.proportionalDelivery },
        });
      }
      if (reply?.room) openRoom(reply.room.id);
    } catch (error) { setMessage(error.message); }
  };
  return <Page title={mode === 'join' ? t("Join your people") : t("Create a room")} subtitle={mode === 'join' ? t("Open an invitation link, scan its QR, or enter the six-digit code.") : t("Choose the restaurant now, or let everyone vote after joining the room.")} onBack={onBack}>
    <form className="card create-form stack" onSubmit={submit} onInvalid={event => { const details = event.target.closest('details'); if (details) details.open = true; }}>
      {mode === 'join' ? <label>{t("Six-digit room code")}<input className="room-code-input" inputMode="numeric" autoComplete="off" enterKeyHint="go" pattern="[0-9]{6}" value={form.code} onChange={e => setForm({ ...form, code: e.target.value.replace(/\D/g, '').slice(0, 6) })} required /></label> : <>
        <div className="segmented restaurant-choice" role="group" aria-label={tx('Restaurant selection', 'اختيار المطعم')}><button type="button" aria-pressed={!form.restaurantPoll} className={!form.restaurantPoll ? 'active' : ''} onClick={() => setForm({ ...form, restaurantPoll: false })}>{t("Use this restaurant")}</button><button type="button" aria-pressed={form.restaurantPoll} className={form.restaurantPoll ? 'active' : ''} onClick={startPoll}>{t("Start a room poll")}</button></div>
        {restaurants.length > 0 && <button className="restaurant-picker-trigger" type="button" onClick={() => setRestaurantPicker(true)}><span><small>{tx('RESTAURANT', 'المطعم')}</small><b>{form.restaurantPoll ? tx(`Choose poll restaurants · ${pollChoices.length} selected`, `اختر مطاعم التصويت · ${pollChoices.length} محدد`) : chosen ? localizedName(chosen) : tx('Choose from UAE restaurants', 'اختر من مطاعم الإمارات')}</b><em>{!form.restaurantPoll && chosen ? [uiLanguage === 'ar' ? chosen.emirateAr || chosen.emirate : chosen.emirate, uiLanguage === 'ar' ? chosen.areaAr || chosen.area : chosen.area, uiLanguage === 'ar' ? chosen.cuisineAr || chosen.cuisine : chosen.cuisine].filter(Boolean).join(' · ') : tx('Search by name, emirate, area or meal', 'ابحث بالاسم أو الإمارة أو المنطقة أو الوجبة')}</em></span><strong>⌕</strong></button>}
        {form.restaurantPoll && <div className="poll-selection"><p className="field-help">{tx('Choose 2–12 restaurants. Only these choices will enter the poll.', 'اختر من مطعمين إلى 12 مطعماً. هذه المطاعم فقط ستدخل التصويت.')}</p>{pollChoices.map(restaurant => <button type="button" className="poll-chip" key={restaurant.id} onClick={() => togglePollRestaurant(restaurant.id)} aria-label={tx(`Remove ${localizedName(restaurant)} from poll`, `إزالة ${localizedName(restaurant)} من التصويت`)}>{localizedName(restaurant)} <span aria-hidden="true">×</span></button>)}</div>}
        {!chosen && !form.restaurantPoll && <div className="form-grid two"><label>{t("Restaurant / order name")}<input value={form.restaurant} onChange={e => setForm({ ...form, restaurant: e.target.value })} placeholder={t("Today’s food order")} required /></label><label>{t("Restaurant phone")}<input type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="+971…" required /></label></div>}
        {chosen && !form.restaurantPoll && <div className="selected-restaurant"><span><b>{localizedName(chosen)}</b><small>{chosen.menu.items.length ? `${chosen.menu.items.length} ${tx('saved menu items and prices', 'صنفاً محفوظاً بأسعاره')}` : tx('Open order for custom items', 'طلب مفتوح للأصناف المخصصة')}</small></span><strong>{chosen.currency}</strong></div>}
        <label>{t("Room name")}<input value={form.room} enterKeyHint="next" onChange={e => setForm({ ...form, room: e.target.value })} placeholder={t("Friday lunch club")} required /><span className="field-help">{tx('Choose a restaurant to fill this in, or give your group a name.', 'اختر مطعماً لملء الاسم أو سمّ مجموعتك.')}</span></label>
        <label>{tx('Selection animation', 'طريقة عرض الاختيار')}<select value={form.selectionStyle} onChange={e => setForm({ ...form, selectionStyle: e.target.value })}><option value="wheel">{tx('Wheel', 'العجلة')}</option><option value="names">{tx('Running names', 'الأسماء المتحركة')}</option></select></label>
        <div className="segmented delivery-choice" role="group" aria-label={tx('Order type', 'نوع الطلب')}><button type="button" aria-pressed={!form.deliveryMode} className={!form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: false })}>{t("Pickup")}</button><button type="button" aria-pressed={form.deliveryMode} className={form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: true })}>{t("Delivery")}</button></div>
        {form.deliveryMode && <DeliveryRule />}
        {form.deliveryMode && <label>{tx("Delivery address · optional", "عنوان التوصيل · اختياري")}<input autoComplete="street-address" value={form.destination} onChange={e => setForm({ ...form, destination: e.target.value })} maxLength={1000} /><span className="field-help">{tx("Leave blank and the selected orderer will arrange delivery with the restaurant.", "اتركه فارغاً وسيتولى الشخص المختار ترتيب التوصيل مع المطعم.")}</span></label>}
        <details className="create-options"><summary>{tx('More options', 'خيارات إضافية')}<small>{tx('Fees and discounts', 'الرسوم والخصومات')}</small></summary><div className="stack">
        <div className="form-grid three">{!form.deliveryMode && <label>{t("Delivery fee ·")}{currency}<input inputMode="decimal" value={form.delivery} onChange={e => setForm({ ...form, delivery: e.target.value })} /></label>}<label>{t("Service fee ·")}{currency}<input inputMode="decimal" value={form.service} onChange={e => setForm({ ...form, service: e.target.value })} /></label><label>{t("Shared discount ·")}{currency}<input inputMode="decimal" value={form.discount} onChange={e => setForm({ ...form, discount: e.target.value })} /></label></div>
        {!form.deliveryMode && <label className="check"><input type="checkbox" checked={form.proportionalDelivery} onChange={e => setForm({ ...form, proportionalDelivery: e.target.checked })} />{t("Split delivery by each person’s food total")}<span>{t("Leave off to split the delivery fee equally between people who ordered food.")}</span></label>}
        </div></details>
      </>}
      {message && <p className="form-message" role="alert">{message}</p>}
      <div className="create-submit"><button className="primary" disabled={data.busy || !profile.name || !profile.phone || (mode !== 'join' && form.restaurantPoll && pollChoices.length < 2)}>{data.busy ? tx('Connecting…', 'جارٍ الاتصال…') : mode === 'join' ? tx('Request to join', 'طلب الانضمام') : tx('Create room', 'إنشاء الغرفة')}</button></div>
      {(!profile.name || !profile.phone) && <p className="form-message">{t("Complete your name and phone in your profile first.")}</p>}
    </form>
    {restaurantPicker && <RestaurantPicker restaurants={restaurants} selectedId={form.restaurantId} multiple={form.restaurantPoll} selectedIds={pollIds} onSelect={form.restaurantPoll ? togglePollRestaurant : chooseRestaurant} onClose={() => { setRestaurantPicker(false); if (form.restaurantPoll && pollChoices.length) setForm(old => ({ ...old, room: old.room || tx('Our restaurant poll', 'تصويت مطعمنا') })); }} />}
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
  return <div className="order-line"><div><b>{line.quantity} × {label || line.description}</b>{line.notes && <small>{line.notes}</small>}</div><div>{lineTotal == null ? <span className="status">{t("Awaiting price")}</span> : <b>{money(lineTotal, currency)}</b>}{canPrice && <button type="button" className="link" onClick={onPrice}>{line.unitPrice == null ? t("Add price") : 'Change'}</button>}{onQuantity && <QuantityControl value={line.quantity} min={0} onChange={value => onQuantity(value - line.quantity)} />}{onEdit && <button type="button" className="link" onClick={onEdit}>{t("Edit")}</button>}{onRemove && <button type="button" className="link danger" onClick={onRemove}>{t("Remove")}</button>}</div></div>;
}

function OrderProgress({ room, receipts, payer }) {
  const total = receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const paid = receipts.reduce((sum, receipt) => sum + receipt.paid, 0);
  const index = room.phase === 'LOBBY' ? (room.restaurantPollOpen ? 1 : 2) : ['PREPARING_SPIN', 'SPINNING', 'ACCEPTING'].includes(room.phase) ? 3 : ['COLLECTING', 'REVIEW'].includes(room.phase) ? 4 : 5;
  return <section className="card order-progress"><div className="progress-steps">{[t("Join"), t("Restaurant"), t("Sandwiches"), t("Pick payer"), t("Send order"), t("Settle")].map((title, step) => <div className={`${step < index ? 'done' : ''} ${step === index ? 'current' : ''}`} key={title}><span>{step < index ? '✓' : step + 1}</span><b>{title}</b></div>)}</div>{receipts.length > 0 && <div className="progress-money"><span><small>{payer ? t("Total food order") : t("Your order total")}</small><b>{money(total, room.restaurant.currency)}</b></span><span><small>{payer ? t("Member payments confirmed") : t("Your confirmed payments")}</small><b>{money(paid, room.restaurant.currency)}</b></span><span><small>{t("Still to settle")}</small><b>{money(receipts.filter(receipt => receipt.memberId !== room.payerId).reduce((sum, receipt) => sum + Math.max(0, receipt.balance), 0), room.restaurant.currency)}</b></span></div>}</section>;
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
  const categories = menuCategories(room.restaurant);
  const [category, setCategory] = useState(() => defaultMenuCategory(room.restaurant));
  const selectedCategory = categories.find(value => `category:${value.id}` === category);
  const [search, setSearch] = useState('');
  const [menuLimit, setMenuLimit] = useState(12);
  const [configured, setConfigured] = useState(!!editing);
  const selectionRef = useRef(null);
  const visibleItems = browsedMenuItems(room.restaurant, category, search);
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
  return <article className="card sandwich-menu">
    <p className="eyebrow">{editing ? tx('EDIT ITEM', 'تعديل الصنف') : selectedCategory ? localizedName(selectedCategory) : tx('ALL MENU CATEGORIES', 'كل أقسام القائمة')}</p>
    <h2>{editing ? tx('Update your item', 'عدّل الصنف') : `${tx('Choose from', 'اختر من')} ${localizedName(room.restaurant)}`}</h2>
    <form className="stack" onSubmit={submit}>
      {!editing && <label>{tx('Menu category', 'قسم القائمة')}<select value={category} onChange={event => { setCategory(event.target.value); setMenuLimit(12); setConfigured(false); setMessage(''); }}>{categories.map(value => <option key={value.id} value={`category:${value.id}`}>{localizedName(value)}</option>)}<option value="all">{tx('All categories', 'كل الأقسام')}</option></select></label>}
      <label>{tx('Find a sandwich or item', 'ابحث عن سندويش أو صنف')}<input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder={tx('Search in Arabic or English', 'ابحث بالعربية أو الإنجليزية')} /></label>
      {search.trim() && <p className="muted" role="status">{visibleItems.length} {tx('matching items', 'أصناف مطابقة')}</p>}
      {!editing && <div className="menu-order-grid">{visibleItems.slice(0, menuLimit).map(item => {
        const simple = !item.variants.length && !item.optionGroupIds.length;
        const count = lines.filter(line => line.itemId === item.id).reduce((sum, line) => sum + line.quantity, 0);
        return <div className="menu-order-item" key={item.id}><span className="menu-category-label">{localizedName(room.restaurant.menu.categories.find(value => value.id === item.categoryId))}</span><b>{localizedName(item)}</b><small>{money(item.variants[0]?.priceMinor ?? item.basePriceMinor, room.restaurant.currency)}{item.variants.length > 0 ? ` · ${localizedName(item.variants[0])}` : ''}</small>{count > 0 && <span className="status">{count} {tx('in your order', 'في طلبك')}</span>}
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
      <div className="hero-actions"><button className="primary">{editing ? tx('Update my order', 'تحديث طلبي') : tx('Add item', 'إضافة الصنف')}</button>{editing && <button type="button" className="secondary" onClick={onCancel}>{tx('Cancel edit', 'إلغاء التعديل')}</button>}</div>
      </section>}
    </form>
  </article>;
}

function MemberOrderPanel({ room, cart, receipt, lastOrder = null, previousOrders = [], favorites = [], onFavorite, onSave, onSubmit }) {
  const [editingId, setEditingId] = useState('');
  const [custom, setCustom] = useState({ description: '', quantity: '1', notes: '' });
  const [reuseMessage, setReuseMessage] = useState('');
  const [reuseError, setReuseError] = useState(false);
  const [reorderSource, setReorderSource] = useState(null);
  const [reorderIssues, setReorderIssues] = useState([]);
  const cartReview = useRef(null);
  const editing = cart.lines.find(line => line.id === editingId);
  const edit = line => {
    setEditingId(line.id);
    if (line.description) setCustom({ description: line.description, quantity: String(line.quantity), notes: line.notes || '' });
  };
  const cancel = () => { setEditingId(''); setCustom({ description: '', quantity: '1', notes: '' }); };
  const saveLine = async line => {
    let lines;
    try { lines = editingId ? cart.lines.map(current => current.id === editingId ? line : current) : addMenuLine(cart.lines, line); }
    catch (error) { setReuseError(true); setReuseMessage(error.message); return null; }
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
  const addSavedOrder = source => { setReuseMessage(''); setReorderIssues([]); setReorderSource(source); };
  const reordered = issues => {
    setReorderSource(null); setReorderIssues(issues);
    setReuseError(false);
    setReuseMessage(t('Available items added at current prices. Review quantities and customizations before submitting your order.'));
    cancel();
    cartReview.current?.focus();
    cartReview.current?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };
  return <section id="room-menu" className="stack order-editor">
    {lastOrder && <article className="card smart-reorder last-order" aria-labelledby="last-order-title">
      <div className="section-title compact"><div><p className="eyebrow">{localizedName(room.restaurant)}</p><h2 id="last-order-title">{t('Your last order')}</h2></div>
        <time dateTime={new Date(lastOrder.order.completedAt).toISOString()}>{new Date(lastOrder.order.completedAt).toLocaleDateString(uiLanguage === 'ar' ? 'ar-AE' : 'en-AE')}</time></div>
      <p>{orderSummary(lastOrder.receipt.lines)}</p>
      <button className="primary" disabled={!!reorderSource} onClick={() => addSavedOrder(lastOrder.receipt.lines)}>{t('Reorder')}</button>
    </article>}
    {reorderSource && <ReorderReview restaurant={room.restaurant} source={reorderSource} cartLines={cart.lines} onSave={onSave} onApplied={reordered} onCancel={() => setReorderSource(null)} />}
    <article className="card early-order"><p className="eyebrow">{t("YOUR SANDWICHES")}</p><h2>{t("Add now. Change any time before totals.")}</h2><p className="muted">{t("Your order stays editable before, during, and after the spin. Saving an edit keeps it in this room after refresh.")}</p></article>
    {(matchingFavorites.length > 0 || previous.length > 0) && <article className="card smart-reorder"><p className="eyebrow">{t("FAST ADD")}</p><h2>{t("Favorites & unique previous orders")}</h2><p className="muted">{t("Exact repeats appear once. Any change to quantity, size, extras, or notes remains a separate choice.")}</p><div className="saved-order-list">{matchingFavorites.map(favorite => <div className="saved-order-row" key={favorite.id}><span><b>★ {favorite.title}</b><small>{orderSummary(favorite.lines)}</small></span><button className="primary" disabled={!!reorderSource} onClick={() => addSavedOrder(favorite.lines)}>{t("Add all")}</button></div>)}{previous.map(choice => <div className="saved-order-row" key={choice.key}><span><b>{choice.repeatCount > 1 ? tf('Repeated {count} times', { count: choice.repeatCount }) : new Date(choice.order.completedAt).toLocaleDateString()}</b><small>{orderSummary(choice.receipt.lines)}</small></span><span className="saved-order-buttons"><button className="primary" disabled={!!reorderSource} onClick={() => addSavedOrder(choice.receipt.lines)}>{t("Add all")}</button><button className="secondary" onClick={() => onFavorite(choice)}>{t("☆ Favorite")}</button></span></div>)}</div></article>}
    {room.restaurant.menu.items.length > 0 && (!editing || !editing.description) && <MenuItemForm key={`${room.restaurant.id}:${editing?.id || 'new-menu'}`} room={room} lines={cart.lines} editing={editing} onSave={saveLine} onCancel={cancel} />}
    {room.restaurant.openOrdering && (!editing || editing.description) && <article className="card"><p className="eyebrow">{editing ? 'EDIT CUSTOM ITEM' : 'CUSTOM SANDWICH'}</p><h2>{editing ? t("Update your item") : t("Add an item one by one")}</h2><form className="item-form" onSubmit={saveCustom}><label>{t("Food item")}<input value={custom.description} onChange={e => setCustom({ ...custom, description: e.target.value })} placeholder={t("Falafel sandwich")} required /></label><label>{t("Qty")}<input inputMode="numeric" min="1" max="99" type="number" value={custom.quantity} onChange={e => setCustom({ ...custom, quantity: e.target.value })} required /></label><label className="notes">{t("Notes / extras")}<input value={custom.notes} onChange={e => setCustom({ ...custom, notes: e.target.value })} placeholder={t("No onions, extra sauce")} /></label><div className="hero-actions"><button className="primary">{editing ? t("Update my order") : t("Add custom item")}</button>{editing && <button className="secondary" type="button" onClick={cancel}>{t("Cancel edit")}</button>}</div></form></article>}
    <article className="card cart-review" ref={cartReview} tabIndex={-1} aria-label={t('Review your cart')}>
      {reuseMessage && <p role="status" className={reuseError ? 'form-message' : 'success-message'}>{t(reuseMessage)}</p>}
      {!!reorderIssues.length && <div className="form-message"><b>{t('Not added to your cart')}</b><ul>{reorderIssues.map(entry => <li key={entry.line.id}>{entry.line.quantity} × {entry.label}: {t(entry.issue)}</li>)}</ul></div>}
      <div className="section-title compact"><div><p className="eyebrow">{t("MY ORDER")}</p><h2>{cart.lines.length ? tf('{count} items', { count: cart.lines.length }) : t("Nothing added yet")}</h2></div>{receipt && <b>{receipt.totalText}</b>}</div>{cart.lines.map(line => <OrderLine key={line.id} label={cartLineDescription(room, line)} line={line} restaurant={room.restaurant} currency={room.restaurant.currency} onQuantity={delta => onSave(changeQuantity(cart.lines, line.id, delta))} onEdit={() => edit(line)} onRemove={() => onSave(cart.lines.filter(value => value.id !== line.id))} />)}<button className="primary wide" onClick={onSubmit}>{cart.submitted ? t("Save my updated order") : cart.lines.length ? t("Submit my food order") : t("No food this time")}</button>{cart.submitted && <p className="success-message">{t("✓ Saved. You can still edit and submit changes before totals are reviewed.")}</p>}</article>
  </section>;
}

function DeliveryRule() {
  return <p className="delivery-rule">{tx('Delivery is automatic by default: AED 5 shared equally by up to 5 people ordering food, or AED 1 each for more than 5. The organizer or selected payer can change it below.', 'يُحسب التوصيل تلقائياً افتراضياً: ٥ دراهم بالتساوي حتى ٥ أشخاص يطلبون طعاماً، أو درهم لكل شخص عند أكثر من ٥. يمكن للمنظم أو الشخص المختار تغييره أدناه.')}</p>;
}

function FeeEditor({ room, data }) {
  const currency = room.restaurant.currency;
  const [fees, setFees] = useState({ delivery: minorInput(room.fees.delivery, currency), automaticDelivery: room.deliveryMode && room.fees.automaticDelivery, service: minorInput(room.fees.service, currency), discount: minorInput(room.fees.discount, currency), proportionalDelivery: room.fees.proportionalDelivery, taxTreatment: room.restaurant.pricing.taxTreatment, taxRate: String((room.restaurant.pricing.taxRateBasisPoints ?? 0) / 100) });
  const [message, setMessage] = useState('');
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      const result = await data.send('SET_FEES', roomFeeUpdate(room, fees), room.id);
      if (result) data.setNotice(t('Fees and tax saved for this order.'));
      else setMessage(t('Fees and tax were not saved. Please try again.'));
    } catch (error) { setMessage(error.message); }
  };
  return <article id="room-fees" className="card fees-editor"><p className="eyebrow">{t("FEES, TAX & DISCOUNT")}</p><h2>{t("Cover the complete restaurant bill")}</h2><p className="muted">{t("Food Run allocates every minor unit so all member receipts equal the final food, delivery, service, tax, and discount total.")}</p><form className="stack" onSubmit={save}>{room.deliveryMode && <><DeliveryRule /><label className="check"><input type="checkbox" checked={fees.automaticDelivery} onChange={e => setFees({ ...fees, automaticDelivery: e.target.checked })} />{tx('Use the automatic delivery rule', 'استخدم قاعدة التوصيل التلقائية')}</label></>}<div className="form-grid three">{(!room.deliveryMode || !fees.automaticDelivery) && <label>{t("Delivery fee")}<input inputMode="decimal" value={fees.delivery} onChange={e => setFees({ ...fees, delivery: e.target.value })} /></label>}<label>{t("Service fee")}<input inputMode="decimal" value={fees.service} onChange={e => setFees({ ...fees, service: e.target.value })} /></label><label>{t("Shared discount (whole order)")}<input inputMode="decimal" value={fees.discount} onChange={e => setFees({ ...fees, discount: e.target.value })} /></label></div><div className="form-grid two"><label>{t('Tax treatment')}<select value={fees.taxTreatment} onChange={event => setFees({ ...fees, taxTreatment: event.target.value })}><option value="unspecified" disabled>{t('Choose tax treatment')}</option><option value="included">{t('Included in prices')}</option><option value="added">{t('Added to bill')}</option></select></label>{fees.taxTreatment === 'added' && <label>{t('Tax rate %')}<input inputMode="decimal" value={fees.taxRate} onChange={event => setFees({ ...fees, taxRate: event.target.value })} required /></label>}</div><p className="field-help">{t('Service fees are separate from tax. Choose included if the menu prices already include tax; no extra tax will be added.')}</p>{(!room.deliveryMode || !fees.automaticDelivery) && <label className="check"><input type="checkbox" checked={fees.proportionalDelivery} onChange={e => setFees({ ...fees, proportionalDelivery: e.target.checked })} />{t("Split delivery by food total")}<span>{t("Turn off for an equal split between everyone with food.")}</span></label>}{message && <p className="form-message" role="alert">{data.error || message}</p>}<button className="secondary">{t("Save fees, tax and discount")}</button></form></article>;
}

function RestaurantOrderCard({ room, receipts, data, finish = false, expectedArrival = room.restaurantReference, setExpectedArrival, canPlace = false, blocker = '' }) {
  const [copied, setCopied] = useState(false);
  const [copyLanguage, setCopyLanguage] = useState(() => ['en', 'ar'].includes(localStorage.getItem('foodrun-copy-language-v1')) ? localStorage.getItem('foodrun-copy-language-v1') : uiLanguage);
  const orderText = restaurantOrderText(room, receipts, copyLanguage, expectedArrival);
  const chooseCopyLanguage = value => { setCopyLanguage(value); setCopied(false); localStorage.setItem('foodrun-copy-language-v1', value); };
  const copy = async () => { await copyText(orderText); setCopied(true); setTimeout(() => setCopied(false), 2500); };
  const contact = room.restaurant.contact.phoneE164 || room.restaurant.contact.whatsappE164;
  const whatsAppNumber = (room.restaurant.contact.whatsappE164 || (/^\+9715\d{8}$/.test(contact || '') ? contact : '') || '').replace(/\D/g, '');
  const whatsApp = `https://wa.me/${whatsAppNumber}?text=${encodeURIComponent(orderText)}`;
  const requirement = blockerRequirement(blocker);
  return <article className="card restaurant-order-card"><p className="eyebrow">{t("SELECTED TO ORDER")}</p><div className="selected-payer"><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><div><h2>{room.members.find(member => member.id === room.payerId)?.name}</h2><p>{t("Collects the final list, places the order, and confirms payments.")}</p></div></div>{setExpectedArrival && <label>{t('Expected delivery / pickup (optional)')}<input value={expectedArrival} onChange={event => setExpectedArrival(event.target.value)} placeholder={t('For example: 30 minutes')} maxLength={500} /></label>}<label className="copy-language">{t('Order list language')}<select value={copyLanguage} onChange={event => chooseCopyLanguage(event.target.value)} dir="ltr"><option value="en" lang="en">English</option><option value="ar" lang="ar">العربية</option></select></label><pre dir={copyLanguage === 'ar' ? 'rtl' : 'ltr'} lang={copyLanguage}>{orderText}</pre><div className="hero-actions"><button className="primary" type="button" onClick={copy}>{copied ? t("✓ Copied — paste to restaurant") : finish ? tx('Copy list for restaurant', 'نسخ الطلب للمطعم') : tx('Copy restaurant-ready list', 'نسخ الطلب للمطعم')}</button><a className="secondary action-link" href={whatsApp} target="_blank" rel="noreferrer">{t("Share via WhatsApp")}</a>{contact && <a className="secondary action-link" href={`tel:${contact.replace(/[^+\d]/g, '')}`}>{t("Call restaurant")}</a>}</div><p className="fine">{t(whatsAppNumber ? 'Opens the restaurant chat with your order ready to send.' : 'No WhatsApp number is saved. Choose the restaurant chat after WhatsApp opens.')}</p>{setExpectedArrival && <><button className="primary wide" disabled={!canPlace || data.busy} onClick={() => data.send('PLACE', { text: expectedArrival.trim() }, room.id)}>{t('Order sent')}</button>{blocker && <p className="form-message send-blocker">{t(blocker)} {requirement && <a href={requirement.href}>{requirement.label}</a>}</p>}</>}</article>;
}

function blockerRequirement(blocker = '') {
  const value = blocker.toLowerCase();
  if (value.includes('account')) return { href: '#payer-account', label: tx('Add payment details here', 'أضف بيانات الدفع هنا') };
  if (value.includes('price every custom')) return { href: '#order-pricing', label: tx('Set missing prices here', 'حدد الأسعار الناقصة هنا') };
  if (value.includes('submit a cart')) return { href: '#room-members', label: tx('See who is still pending', 'شاهد من لم يرسل طلبه') };
  if (value.includes('tax treatment') || value.includes('discount')) return { href: '#room-fees', label: tx('Fix fees and tax here', 'عدّل الرسوم والضريبة هنا') };
  if (value.includes('phone or whatsapp')) return { href: '#room-management', label: tx('Add restaurant phone here', 'أضف رقم المطعم هنا') };
  if (value.includes('minimum order') || value.includes('no food')) return { href: '#room-menu', label: tx('Add food from the menu', 'أضف طعاماً من القائمة') };
  return null;
}

function ReceiptCard({ room, receipt, own = false }) {
  if (!receipt.lines.length && receipt.total === 0 && receipt.paid === 0 && receipt.balance === 0) return <article className="receipt-card no-food-receipt"><b>{receipt.name}{own ? t(' · You') : ''}</b><p>{t('No food this time · No charges')}</p></article>;
  return <article className={`receipt-card ${own ? 'own' : ''}`}><div className="receipt-heading"><span><b>{receipt.name}{own ? t(' · You') : ''}</b><small>{receipt.lines.length} line{receipt.lines.length === 1 ? '' : 's'}</small></span><strong>{receipt.totalText}</strong></div>{receipt.lines.map((line, index) => <OrderLine key={`${line.description}:${index}`} line={line} currency={receipt.currency} />)}<div className="fee-breakdown"><span>{t("Food")}<b>{money(receipt.food, receipt.currency)}</b></span><span>{t("Delivery")}<b>{money(receipt.delivery, receipt.currency)}</b></span><span>{t("Service / adjustment")}<b>{money(receipt.service, receipt.currency)}</b></span><span>{t("Discount")}<b>− {money(receipt.discount, receipt.currency)}</b></span><span>{t("Tax")}<b>{money(receipt.tax, receipt.currency)}</b></span></div><div className="receipt-balance"><span>{t("Paid")}<b>{money(receipt.paid, receipt.currency)}</b></span><span>{receipt.balance < 0 ? t("Refund due") : t("To pay")} <strong>{money(Math.abs(receipt.balance), receipt.currency)}</strong></span></div></article>;
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
  return <article className="card wallet-card"><p className="eyebrow">{payer ? t('ROOM WALLET') : t('MY WALLET')}</p><h2>{payer ? t("Every share in one place") : t("Your order and payment")}</h2><div className="progress-money wallet-summary">{payer ? <><span><small>{t("Restaurant total")}</small><b>{money(restaurantTotal, currency)}</b></span><span><small>{t("Your own share")}</small><b>{money(receipts.find(receipt => receipt.memberId === room.payerId)?.total || 0, currency)}</b></span><span><small>{t("Confirmed from others")}</small><b>{money(confirmed, currency)}</b></span><span><small>{t("Members still owe")}</small><b>{money(remaining, currency)}</b></span>{refunds > 0 && <span><small>{t("Refunds you owe")}</small><b>{money(refunds, currency)}</b></span>}</> : <><span><small>{t("My order")}</small><b>{money(own.total, currency)}</b></span><span><small>{t("Confirmed paid")}</small><b>{money(own.paid, currency)}</b></span><span><small>{own.balance < 0 ? t("Owed back to me") : t("I need to pay")}</small><b>{money(Math.abs(own.balance), currency)}</b></span></>}</div>
    {payer && <div className="wallet-people">{receipts.map(receipt => { const claim = pending.find(transfer => transfer.memberId === receipt.memberId); return <div className="transfer-row" key={receipt.memberId}><span><b>{receipt.name}{receipt.memberId === me.id ? t(' · You') : ''}</b><small>{t("Order")}{' '}{money(receipt.total, currency)}{t("· confirmed")}{' '}{money(receipt.paid, currency)}{claim ? ' · ' + tf('{amount} awaiting confirmation', { amount: money(claim.amount, currency) }) : ''}</small></span><strong>{receipt.memberId === room.payerId ? t("Own share") : receipt.balance < 0 ? tf('Refund {amount}', { amount: money(-receipt.balance, currency) }) : receipt.balance === 0 ? t("Settled") : tf('Owes {amount}', { amount: money(receipt.balance, currency) })}</strong></div>; })}</div>}
    {!payer && pending.some(transfer => transfer.memberId === me.id) && <p className="success-message">{t("Your payment is marked sent and awaits confirmation from the selected payer.")}</p>}
  </article>;
}

function PaymentDetails({ account, data }) {
  if (!account) return null;
  return <div className="pay-to payment-details"><span>{t(account.method === "AANI" ? "Aani · UAE mobile number" : "Bank transfer · IBAN")}</span><b>{account.holder} · {account.bank}</b><code dir="ltr">{account.identifier}</code><button type="button" className="secondary" onClick={() => copyText(account.identifier).then(() => data.setNotice(t("Payment details copied."))).catch(error => data.setNotice(error.message))}>{t("Copy payment details")}</button></div>;
}

function PayerAccountEditor({ room, data }) {
  const profile = data.home.profile;
  const [form, setForm] = useState(paymentDraft(room.account || profile.payment));
  const [message, setMessage] = useState('');
  const [dirty, setDirty] = useState(false);
  useEffect(() => { if (!dirty) setForm(paymentDraft(room.account || profile.payment)); }, [room.account, profile.payment, dirty]);
  const set = (key, value) => { setDirty(true); setForm(old => ({ ...old, [key]: value })); };
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      const account = paymentAccount(form, profile.payment, profile.name, true);
      const saved = await data.send('SHARE_ACCOUNT', { account }, room.id);
      if (saved) { setDirty(false); setMessage(t('Payment details updated in your profile and active rooms.')); }
      else setMessage(t('Payment details were not saved. Please try again.'));
    } catch (error) { setMessage(error.message); }
  };
  return <article id="payer-account" className="card payer-account-editor"><h2>{t('Receiving details')}</h2><p className="muted">{t('Changes update your profile and all active rooms where you collect payments.')}</p><form className="stack" onSubmit={save}><PaymentFields form={form} set={set} name={profile.name} required />{message && <p className="form-message" role="status">{message}</p>}<button className="primary wide" disabled={data.busy}>{t('Save payment details')}</button></form></article>;
}

function PaymentActionLine({ room, receipt, memberId, data }) {
  const payer = room.payerId === memberId;
  const pending = room.transfers.find(value => value.memberId === receipt.memberId && String(value.status).toLowerCase() === 'declared');
  const [value, setValue] = useState(minorInput(Math.abs(receipt.balance), receipt.currency));
  const [reference, setReference] = useState('');
  const [error, setError] = useState('');
  useEffect(() => setValue(minorInput(Math.abs(receipt.balance), receipt.currency)), [receipt.balance, receipt.currency]);
  if (receipt.memberId === room.payerId) return <p className="field-help">{t("Your own share")} · {money(receipt.total, receipt.currency)}</p>;
  const canConfirm = pending && (pending.refund ? pending.memberId === memberId : payer);
  const canDeclare = room.restaurantPaid && !pending && (payer ? receipt.balance < 0 : receipt.memberId === memberId && receipt.balance > 0);
  const submit = async event => {
    event.preventDefault(); setError('');
    try {
      const paid = amount(value, receipt.currency);
      if (paid <= 0 || paid > Math.abs(receipt.balance)) throw Error(t('Amount exceeds the remaining balance.'));
      await data.send(payer ? 'DECLARE_REFUND' : 'DECLARE_TRANSFER', { amount: paid, text: reference.trim() || (payer ? 'Refund sent' : 'Payment sent'), ...(payer ? { memberId: receipt.memberId } : {}) }, room.id);
    } catch (failure) { setError(failure.message); }
  };
  return <div className="payment-actions stack">
    {pending && <p className="field-help">{money(pending.amount, receipt.currency)} · {pending.reference} · {t('Awaiting confirmation')}</p>}
    {canConfirm && <div className="hero-actions"><button className="primary" disabled={data.busy} onClick={() => data.send(pending.refund ? 'CONFIRM_REFUND' : 'CONFIRM_TRANSFER', { transferId: pending.id }, room.id)}>{t('Confirm received')}</button><button className="secondary" disabled={data.busy} onClick={() => data.send('REJECT_TRANSFER', { transferId: pending.id, text: 'Payment was not received or the details do not match.' }, room.id)}>{t('Not received')}</button></div>}
    {canDeclare && <form className="stack" onSubmit={submit}><div className="form-grid two"><label>{t('Amount sent')}<input inputMode="decimal" value={value} onChange={event => setValue(event.target.value)} required /></label><label>{t('Payment note (optional)')}<input value={reference} onChange={event => setReference(event.target.value)} maxLength={160} placeholder={t('Bank transfer or cash')} /></label></div><button className="primary wide" disabled={data.busy}>{payer ? t('Mark refund sent') : t('Mark paid')}</button></form>}
    {!room.restaurantPaid && receipt.balance !== 0 && <p className="field-help">{t('Waiting for restaurant payment to be recorded.')}</p>}
    {payer && receipt.balance > 0 && !pending && room.restaurantPaid && <p className="field-help">{t('Waiting for payment')}</p>}
    {error && <p role="alert" className="form-message">{error}</p>}
  </div>;
}

function OrderSummary({ room, receipts, className = '' }) {
  const lines = groupedOrderLines(room, receipts, uiLanguage);
  const total = receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  return <article className={`card order-summary ${className}`}>
    <div className="section-title compact"><div><p className="eyebrow">{t('ORDER SUMMARY')}</p><h2>{localizedName(room.restaurant)}</h2></div><strong className="order-summary-total">{money(total, room.restaurant.currency)}</strong></div>
    <p className="field-help">{t('Total includes delivery, fees and discounts.')}</p>
    <ul className="summary-items">{lines.map((line, index) => <li key={index}><span className="summary-quantity">{line.quantity} ×</span><div><b>{line.description}</b>{line.notes && <small>{line.notes}</small>}</div><strong>{money(line.amount, room.restaurant.currency)}</strong></li>)}</ul>
    {!lines.length && <p>{t('No food yet')}</p>}
    <details className="orders-by-person"><summary>{t('Orders by person')}</summary>{receipts.map(receipt => <div className="person-order-summary" key={receipt.memberId}><div><b>{receipt.name}</b><strong>{money(receipt.total, receipt.currency)}</strong></div>{receipt.lines.map((line, index) => <p key={index}>{line.quantity} × {line.description}{line.notes ? ` — ${line.notes}` : ''}</p>)}</div>)}</details>
  </article>;
}

function SettlementPanel({ room, reply, me, owner, payer, data }) {
  const currency = room.restaurant.currency;
  const total = reply.receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const [adjustment, setAdjustment] = useState({ value: '', reason: '' });
  const [message, setMessage] = useState('');
  return <>
    {payer && !room.paymentRoom && <OrderSummary room={room} receipts={reply.receipts} />}
    {room.paymentRoom ? payer && <PaymentShareEditor room={room} receipts={reply.receipts} data={data} /> : (owner || payer) && <details className="card order-edit-options"><summary>{t('Edit item prices')}</summary><OrderPricingPanel room={room} data={data} describeLine={line => cartLineDescription(room, line)} /></details>}
    <article id="room-payment" className="card stack payment-priority"><p className="eyebrow">{payer ? t('ROOM WALLET') : t('PAY YOUR SHARE')}</p><h2>{payer ? t('Payments') : t('Your payment')}</h2>
      {payer && <div className="settlement-totals"><span><small>{t('Your own share')}</small><b>{money(reply.receipts.find(receipt => receipt.memberId === me.id)?.total || 0, currency)}</b></span><span><small>{t('Received')}</small><b>{money(reply.receipts.filter(receipt => receipt.memberId !== me.id).reduce((sum, receipt) => sum + receipt.paid, 0), currency)}</b></span><span><small>{t('Still to collect')}</small><b>{money(reply.receipts.filter(receipt => receipt.memberId !== me.id).reduce((sum, receipt) => sum + Math.max(0, receipt.balance), 0), currency)}</b></span></div>}
      {payer && !room.restaurantPaid && <button disabled={data.busy} className="primary wide" onClick={() => data.send('PAY_RESTAURANT', { amount: total }, room.id)}>{t('Mark restaurant paid')} · {money(total, currency)}</button>}
      {room.billRevision > 1 && <p className="field-help">{t('Updated totals apply automatically. No new approval is needed.')}</p>}
      {reply.receipts.filter(receipt => !payer || receipt.memberId !== me.id).map(receipt => <section className="wallet-person" key={receipt.memberId}><div className="wallet-person-heading"><b>{receipt.name}</b><strong>{receipt.balance === 0 ? t('Settled') : `${receipt.balance < 0 ? t('Refund due') : t('Due')} · ${money(Math.abs(receipt.balance), currency)}`}</strong></div><small>{t('Order')} {money(receipt.total, currency)} · {t('Paid')} {money(receipt.paid, currency)}</small><PaymentActionLine room={room} receipt={receipt} memberId={me.id} data={data} />{payer && room.paymentRoom && <RecordPayment room={room} receipt={receipt} data={data} />}</section>)}
      <PaymentDetails account={room.account} data={data} />
      {payer && <details className="edit-receiving-details"><summary>{t('Edit receiving details')}</summary><PayerAccountEditor room={room} data={data} /></details>}
      <p className="fine">{t('Recording a payment tracks it in Food Run; it does not move money.')}</p>
    </article>
    {!room.paymentRoom && <article className="card placed-banner"><p className="eyebrow">{t('RESTAURANT STATUS')}</p><h2>{room.restaurantPaid ? t('Restaurant payment recorded') : t('Order announced as placed')}</h2><p>{room.restaurantReference}</p>{payer && room.phase === 'PLACED' && <button className="secondary wide" onClick={() => data.send('FULFILL', {}, room.id)}>{t('Food collected / delivered')}</button>}</article>}
    <details className="card payment-breakdown"><summary>{t('Order and payment details')}</summary><WalletPanel room={room} receipts={reply.receipts} me={me} payer={payer} />{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}{room.transfers.map(transfer => <div className="transfer-row" key={transfer.id}><span>{room.members.find(member => member.id === transfer.memberId)?.name} · {transfer.reference}<small>{t(String(transfer.status).toLowerCase())}</small></span><b>{money(transfer.amount, currency)}</b></div>)}{payer && !room.paymentRoom && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} />}</details>
    {payer && <details className="card"><summary>{t('Adjust final bill')}</summary><form className="stack" onSubmit={event => { event.preventDefault(); try { const raw = adjustment.value.trim(); data.send('ADJUST_BILL', { amount: amount(raw.replace(/^-/, ''), currency) * (raw.startsWith('-') ? -1 : 1), text: adjustment.reason.trim() }, room.id); } catch (failure) { setMessage(failure.message); } }}><label>{t('Final bill adjustment')}<input inputMode="decimal" value={adjustment.value} onChange={event => setAdjustment({ ...adjustment, value: event.target.value })} /></label><label>{t('Reason')}<input value={adjustment.reason} onChange={event => setAdjustment({ ...adjustment, reason: event.target.value })} required /></label><button className="secondary">{t('Update final bill')}</button>{message && <p className="form-message">{message}</p>}</form></details>}
    {(owner || payer) && room.phase === 'FULFILLED' && <article className="card complete-card"><h2>{t('Finish this order')}</h2><p>{reply.progress?.canArchive ? t('Every payment and refund is settled.') : reply.progress?.archiveBlocker || t('Waiting for all payments to settle.')}</p><button className="primary wide" disabled={!reply.progress?.canArchive} onClick={() => data.send('ARCHIVE', { text: 'Order completed and settled' }, room.id)}>{t('Complete and archive order')}</button></article>}
  </>;
}

function RestaurantPoll({ room, me, owner, data }) {
  const myVote = room.restaurantVotes?.find(vote => vote.memberId === me.id)?.restaurantId;
  return <article className="card restaurant-poll"><p className="eyebrow">{tx('STEP 1 · RESTAURANT POLL', 'الخطوة ١ · تصويت المطعم')}</p><h2>{tx('Where should the group order from?', 'من أي مطعم نطلب؟')}</h2><p className="muted">{tx('Vote updates live for everyone. The organizer closes the poll, then sandwich ordering opens and stays editable through the spin.', 'تظهر الأصوات مباشرة للجميع. ينهي منظم الغرفة التصويت ثم تفتح قائمة السندويشات وتبقى قابلة للتعديل أثناء الاختيار.')}</p><div className="poll-grid">{(room.restaurantOptions || []).map(restaurant => { const votes = (room.restaurantVotes || []).filter(vote => vote.restaurantId === restaurant.id); const voters = votes.map(vote => room.members.find(member => member.id === vote.memberId)?.name).filter(Boolean); return <section className={`poll-option ${myVote === restaurant.id ? 'selected' : ''}`} key={restaurant.id}><span><b>{localizedName(restaurant)}</b><small>{restaurant.menu.items.length} {tx('sandwiches/items', 'سندويشات وأصناف')}</small><small>{voters.length ? voters.join(', ') : tx('No votes yet', 'لا أصوات بعد')}</small></span><strong>{votes.length}</strong>{!me.guest && me.participating && <button className={myVote === restaurant.id ? 'secondary' : 'primary'} disabled={myVote === restaurant.id} onClick={() => data.send('VOTE_RESTAURANT', { text: restaurant.id }, room.id)}>{myVote === restaurant.id ? tx('✓ Your vote', '✓ صوتك') : tx('Vote', 'تصويت')}</button>}</section>; })}</div>{owner && <button className="primary wide" disabled={!room.restaurantVotes?.length} onClick={() => data.send('FINALIZE_RESTAURANT', {}, room.id)}>{tx('Finish poll & use leading restaurant', 'إنهاء التصويت واختيار المطعم المتصدر')}</button>}</article>;
}

function RestaurantContactCard({ restaurant, data }) {
  const phone = restaurant.contact.phoneE164 || restaurant.contact.whatsappE164;
  return <article className="card restaurant-contact"><div><p className="eyebrow">{t('RESTAURANT')}</p><h2>{localizedName(restaurant)}</h2><p>{[uiLanguage === 'ar' ? restaurant.emirateAr || restaurant.emirate : restaurant.emirate, uiLanguage === 'ar' ? restaurant.areaAr || restaurant.area : restaurant.area, restaurant.contact.address].filter(Boolean).join(' · ')}</p>{phone && <a className="restaurant-phone" dir="ltr" href={`tel:${phone}`}>{phone}</a>}</div>{phone && <button className="secondary" onClick={() => copyText(phone).then(() => data.setNotice(t('Phone number copied.'))).catch(error => data.setNotice(error.message))}>{t('Copy phone number')}</button>}</article>;
}

function RoomInviteCard({ room, hub }) {
  const link = roomInviteLink(room, hub);
  const invitation = roomInvitation(room, link, uiLanguage);
  const [copied, setCopied] = useState('');
  const copy = async (value, kind) => { await copyText(value); setCopied(kind); setTimeout(() => setCopied(''), 2200); };
  return <section className="card room-invite"><div className="invite-heading"><p className="eyebrow">{t("INVITE TO THIS ROOM")}</p><h3>{tx("Invite your group", "ادعُ مجموعتك")}</h3><p>{invitation.split('\n')[1]} · {tf('Order #{number}', { number: room.orderNumber })}</p></div><div className="invite-qr"><QRCodeSVG value={link} size={164} level="M" marginSize={2} title={tf('Join {name}', { name: room.name })} /></div><div className="invite-details"><strong className="invite-code" aria-label={tx("Room code", "رمز الغرفة")}>{room.code}</strong><p className="muted">{tx("Scan the QR or share the link or code to join this room.", "امسح الرمز أو شارك الرابط أو الكود للانضمام إلى الغرفة.")}</p><div className="hero-actions"><button className="primary" onClick={() => copy(invitation, 'link')}>{copied === 'link' ? tx('✓ Link copied', '✓ تم نسخ الرابط') : t('Copy invitation')}</button><button className="secondary" onClick={() => copy(room.code, 'code')}>{copied === 'code' ? tx('✓ Code copied', '✓ تم نسخ الكود') : tx('Copy code', 'نسخ الكود')}</button>{navigator.share && <button className="secondary" onClick={() => navigator.share({ title: tf('Join {name} on Food Run', { name: room.name }), text: invitation })}>{t("Share")}</button>}</div></div></section>;
}

function BlockRequestForm({ room, data }) {
  const candidates = room.members.filter(member => member.approved && !member.removed && !member.guest && member.id !== room.ownerId);
  const [target, setTarget] = useState(''), [duration, setDuration] = useState('24'), [reason, setReason] = useState('');
  if (!candidates.length) return null;
  const selected = candidates.some(member => member.id === target) ? target : candidates[0].id;
  return <details className="card"><summary>{t('Request a user block')}</summary><form className="stack" onSubmit={async event => {
    event.preventDefault(); const result = await data.send('REQUEST_BLOCK', { memberId: selected, amount: Number(duration), text: reason.trim() }, room.id);
    if (result) { setReason(''); data.setNotice(t('Block request sent to the admin for review.')); }
  }}><p>{t('The admin reviews your request before any access is blocked.')}</p><label>{t('Member')}<select value={selected} onChange={e => setTarget(e.target.value)}>{candidates.map(member => <option key={member.id} value={member.id}>{member.name}</option>)}</select></label>
    <label>{t('Requested duration')}<select value={duration} onChange={e => setDuration(e.target.value)}>{[[1,'1 hour'],[6,'6 hours'],[24,'1 day'],[72,'3 days'],[168,'7 days'],[720,'30 days']].map(([value,label]) => <option key={value} value={value}>{t(label)}</option>)}</select></label>
    <label>{t('Reason')}<textarea required minLength={5} maxLength={300} value={reason} onChange={e => setReason(e.target.value)} /></label><button disabled={data.busy} className="secondary">{t('Send block request')}</button>
  </form></details>;
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
  return <details id="room-management" className="card room-tools"><summary>{tx('Manage this order', 'إدارة هذا الطلب')}</summary>
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
  const historyRequest = useRef('');
  const historyRoomId = room ? nextRestaurantHistoryRoom(data, room.restaurant) : '';
  const historyOffset = data.rooms[historyRoomId]?.historyNextOffset;
  useEffect(() => {
    if (!historyRoomId || data.busy || data.hasPending || data.error || !data.loadOlderHistory) return;
    const key = `${data.user?.uid}:${room.restaurant.id}:${historyRoomId}:${historyOffset}`;
    if (historyRequest.current === key) return;
    historyRequest.current = key;
    data.loadOlderHistory(historyRoomId);
  }, [historyRoomId, historyOffset, room?.restaurant.id, data.busy, data.hasPending, data.error, data.user?.uid, data.loadOlderHistory]);
  const [reference, setReference] = useState('');
  const [payerMode, setPayerMode] = useState('wheel');
  const [payerChoice, setPayerChoice] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  if (!room || !session) return <Page title={t("Connecting to room…")} onBack={onBack}><div className="card empty"><div className="spinner" /><p>{t("Waiting for the latest room state.")}</p></div></Page>;
  const me = room.members.find(member => member.id === session.memberId);
  const owner = room.ownerId === me.id, payer = room.payerId === me.id;
  const orderingMembers = room.members.filter(member => member.approved && !member.removed && !member.guest && member.participating);
  const selectedPayerId = orderingMembers.some(member => member.id === payerChoice) ? payerChoice : orderingMembers[0]?.id || '';
  const settling = ['PLACED', 'FULFILLED'].includes(room.phase);
  const myCart = room.carts.find(cart => cart.memberId === me.id) || { memberId: me.id, revision: 0, lines: [], submitted: false, confirmedQuote: -1 };
  const myReceipt = reply.receipts.find(receipt => receipt.memberId === me.id);
  const winner = room.members.find(member => member.id === room.spin?.winnerId);
  const saveCart = lines => data.send('CART', { cart: { ...myCart, lines }, expectedRevision: myCart.revision }, room.id);
  const selected = winner && room.phase === 'ACCEPTING';
  const orderingOpen = ['LOBBY','PREPARING_SPIN','SPINNING','ACCEPTING','COLLECTING'].includes(room.phase) && !room.restaurantPollOpen;
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
  if (room.paymentRoom) return <Page title={room.name} subtitle={t('Payment room · split and settle an existing bill')} onBack={onBack} actions={<span className={`status ${data.online[room.id] ? 'live' : ''}`}>{data.online[room.id] ? t('● Live') : t('Offline')}</span>}>
    <fieldset disabled={data.busy} className="room-fieldset stack"><PaymentReceipt room={room} data={data} payer={payer} />
      {settling ? <SettlementPanel room={room} reply={reply} me={me} owner={owner} payer={payer} data={data} /> : <article className="card stack"><h2>{t('Payment room completed')}</h2>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}</article>}
    </fieldset>
  </Page>;
  return <Page title={room.name} subtitle={tf('Order #{number} · {phase} · code {code}', { number: room.orderNumber, phase: t(phaseLabel[room.phase]), code: room.code })} onBack={onBack} actions={<span className={`status ${data.online[room.id] ? 'live' : ''}`}>{data.online[room.id] ? t("● Live") : t("Offline")}</span>}>
    {!settling && <OrderProgress room={room} receipts={reply.receipts} payer={payer} />}
    <fieldset disabled={data.busy} className="room-fieldset" aria-busy={data.busy}><div className="room-layout">
      <section className="room-main stack">
        {settling && <SettlementPanel room={room} reply={reply} me={me} owner={owner} payer={payer} data={data} />}
        {room.lastChosenMemberId && <article className="card last-chosen"><p className="eyebrow">{t('LAST CHOSEN')}</p><h2>{room.members.find(member => member.id === room.lastChosenMemberId)?.name || room.lastChosenName}</h2>{room.phase === 'LOBBY' && <p>{t('Next wheel: the last chosen person has weight 20; each other person has weight 80. Only eligible people join the wheel.')}</p>}</article>}
        <RestaurantContactCard restaurant={room.restaurant} data={data} />
        {['PREPARING_SPIN','SPINNING','ACCEPTING'].includes(room.phase) && <article className="card selection-card">
          {room.phase === 'PREPARING_SPIN' && <><div className="spinner" /><h2>{t("Getting everyone in sync…")}</h2><p>{t("Every participating device is joining the live selection.")}</p></>}
          {room.phase === 'SPINNING' && <><p className="eyebrow">{t("LIVE SELECTION")}</p><h2>{t("Who will order?")}</h2><LiveSelectionWheel style={room.selectionStyle} key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active /></>}
          {room.phase === 'ACCEPTING' && <><LiveSelectionWheel style={room.selectionStyle} key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active={false} /><WinnerReveal winner={winner} selected={selected} me={me} room={room} data={data} /></>}
        </article>}
        {!me.approved && <article className="card notice-card"><h2>{t("Reconnect to update room access")}</h2><p>{t("Refresh the room. If access is still unavailable, update the room server.")}</p></article>}
        {me.approved && !me.guest && !me.participating && room.phase !== 'LOBBY' && <article className="card notice-card"><h2>{t("You’re in the room")}</h2><p>{t("You’re viewing this order. You can join when the next order opens.")}</p></article>}
        {me.approved && room.phase === 'LOBBY' && <><article className="card"><div className="section-title compact"><div><p className="eyebrow">{t("WHO’S IN?")}</p><h2>{t("Join today’s order")}</h2></div><span>{orderingMembers.length}</span></div>{!me.guest && <div className="hero-actions"><button className={me.participating ? 'secondary' : 'primary'} onClick={() => data.send('PARTICIPATE', { flag: !me.participating }, room.id)}>{me.participating ? t("Skip this order") : t("Join this order")}</button></div>}<p className="muted">{t("You join and become ready automatically. Add your food now; no approval is needed.")}</p>{owner && !room.restaurantPollOpen && <div className="stack"><div className="segmented"><button type="button" className={payerMode === 'wheel' ? 'active' : ''} onClick={() => setPayerMode('wheel')}>{t('Use the wheel')}</button><button type="button" className={payerMode === 'direct' ? 'active' : ''} onClick={() => setPayerMode('direct')}>{t('Choose person directly')}</button></div>{payerMode === 'direct' ? <><label>{t('Ordering person')}<select value={selectedPayerId} onChange={event => setPayerChoice(event.target.value)}>{orderingMembers.map(member => <option key={member.id} value={member.id}>{member.name}</option>)}</select></label><button className="primary wide" disabled={!selectedPayerId} onClick={() => data.send('SELECT_PAYER', { memberId: selectedPayerId }, room.id)}>{t('Choose person directly')}</button></> : <button className="primary wide" disabled={!orderingMembers.length} onClick={() => data.send('PREPARE_SPIN', {}, room.id)}>{t('Spin to choose the payer')}</button>}</div>}</article>{room.restaurantPollOpen && <RestaurantPoll room={room} me={me} owner={owner} data={data} />}</>}
        {me.approved && !me.guest && me.participating && orderingOpen && <MemberOrderPanel key={`${room.orderNumber}:${room.restaurant.id}`} room={room} cart={myCart} receipt={myReceipt} lastOrder={lastRestaurantOrder(data, room.restaurant)} previousOrders={previousOrders} favorites={favorites} onFavorite={saveFavorite} onSave={saveCart} onSubmit={() => data.send('SUBMIT_CART', { expectedRevision: myCart.revision }, room.id)} />}
        {me.approved && room.phase === 'COLLECTING' && <>

          {payer && <OrderSummary room={room} receipts={reply.receipts} className="collected-orders" />}
          {payer && !room.account && <PayerAccountEditor room={room} data={data} />}
          {owner && !payer && <FeeEditor key={`${room.quoteRevision}:${room.fees.delivery}:${room.fees.service}:${room.fees.discount}`} room={room} data={data} />}


        </>}
        {payer && room.phase === 'REVIEW' && <OrderSummary room={room} receipts={reply.receipts} />}
        {room.phase === 'REVIEW' && <details className="card payment-breakdown"><summary>{t('Order details')}</summary>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}<PaymentDetails account={room.account} data={data} />{owner && !payer && <FeeEditor key={room.quoteRevision} room={room} data={data} />}</details>}
        {(owner || payer) && ['COLLECTING','REVIEW'].includes(room.phase) && <OrderPricingPanel room={room} data={data} describeLine={line => cartLineDescription(room, line)} />}
        {payer && ['COLLECTING','REVIEW'].includes(room.phase) && <><FeeEditor key={`payer-fees:${room.quoteRevision}`} room={room} data={data} /><PaymentDetails account={room.account} data={data} /><RestaurantOrderCard room={room} receipts={reply.receipts} data={data} expectedArrival={reference} setExpectedArrival={setReference} canPlace={reply.progress?.canReview} blocker={reply.progress?.reviewBlocker} /></>}
        {['ARCHIVED','CANCELLED'].includes(room.phase) && <article className="card empty"><span>✓</span><h2>{room.phase === 'ARCHIVED' ? t("Order complete") : t("Order cancelled")}</h2><p>{t("The room, final receipts, restaurant, and prices stay saved for the next meal.")}</p>{owner && <button className="primary" onClick={startNextOrder}>{t("Start next order")}</button>}</article>}
        {(roomHistoryChoices.length > 0 || reply.historyNextOffset >= 0) && <details className="card history-card"><summary>{t("My unique previous orders ·")}{roomHistoryChoices.length}</summary>{roomHistoryChoices.map(choice => <div className="past-order" key={choice.key}><div><b>{choice.order.restaurantName} · {orderSummary(choice.receipt.lines)}</b><small>{choice.repeatCount > 1 ? tf('Repeated {count} times', { count: choice.repeatCount }) + ' · ' : ''}{new Date(choice.order.completedAt).toLocaleString()}</small></div><ReceiptCard room={{ ...room, payerId: null, account: choice.order.account, orderNumber: choice.order.number, restaurant: { ...room.restaurant, name: choice.order.restaurantName } }} receipt={choice.receipt} own /><button className="link" onClick={() => copyText(receiptText({ ...room, orderNumber: choice.order.number, account: choice.order.account, restaurant: { ...room.restaurant, name: choice.order.restaurantName } }, choice.receipt))}>{t("Copy receipt")}</button><div className="hero-actions"><button className="secondary" disabled={!choice.restaurantId || favorites.some(value => favoriteKey(value) === choice.key)} onClick={() => saveFavorite(choice)}>{favorites.some(value => favoriteKey(value) === choice.key) ? t("★ Favorite") : t("☆ Save as favorite")}</button></div></div>)}{reply.historyNextOffset >= 0 && <button className="secondary wide" disabled={data.busy} onClick={() => data.loadOlderHistory(room.id)}>{t("Load older orders")}</button>}</details>}
      </section>
      <aside className="room-side stack">
        <RoomInviteCard room={room} hub={data.hub} />
        {owner && <BlockRequestForm room={room} data={data} />}
        {room.payerId && <section className="card payer-side"><p className="eyebrow">{t("ORDERING PERSON")}</p><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><h3>{room.members.find(member => member.id === room.payerId)?.name}</h3><p>{payer ? tx('You order, pay the full restaurant bill, and receive everyone’s share.', 'تطلب وتدفع فاتورة المطعم كاملة وتستلم حصص الآخرين.') : tx('Submit your food, then pay your share to the selected person.', 'ابعت طلبك، وبعدها ادفع حصتك للشخص اللي هيطلب.')}</p></section>}
        <section id="room-members" className="card"><div className="section-title compact"><div><p className="eyebrow">{t("AT THE TABLE")}</p><h3>{room.members.filter(member => !member.removed).length} {t("people")}</h3></div>{owner && room.phase === 'LOBBY' && <button className="icon-button" aria-label={t("Invite registered people")} onClick={() => setInviteOpen(!inviteOpen)}>＋</button>}</div>{room.members.filter(member => !member.removed).map(member => { const cart = room.carts.find(value => value.memberId === member.id); const canRemove = owner && member.id !== room.ownerId && (room.phase === 'LOBBY' || room.phase === 'COLLECTING' && member.id !== room.payerId && !cart?.submitted); return <div className="member" key={member.id}><span className="avatar initials small">{initials(member.name)}</span><span><b>{member.name}{member.id === me.id ? t(' · You') : ''}</b><small>{member.id === room.payerId ? t("Selected to order") : member.id === room.ownerId ? t("Organizer") : cart?.submitted ? t("Food submitted") : member.participating ? t("Joined automatically") : 'Skipping'}</small></span>{canRemove && <button className="member-remove" aria-label={tf('Remove {name} from room', { name: member.name })} title={t('Remove from room')} onClick={() => { const reason = window.prompt(tf('Why are you removing {name}?', { name: member.name })); if (reason?.trim()) data.send('REMOVE', { memberId: member.id, text: reason.trim() }, room.id); }}>×</button>}</div>; })}{inviteOpen && <div className="invite-list"><p>{t("Invite registered people")}</p>{data.home.people.map(person => <button className="person-button" key={person.userId} onClick={() => data.send('IDENTITY', { identity: { action: 'INVITE', userId: person.userId } }, room.id)}><span className="avatar initials small">{initials(person.name)}</span>{person.name}<b>{t("Invite")}</b></button>)}</div>}</section>
        <section className="card room-tools"><p className="eyebrow">{t("ROOM TOOLS")}</p><button className="secondary wide" onClick={saveCurrentRestaurant}>{t("Save restaurant & prices")}</button>{payer && ['COLLECTING','REVIEW'].includes(room.phase) && data.home.profile.payment && <button className="secondary wide" onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>{t("Use saved payment details")}</button>}{owner && ['LOBBY','COLLECTING','REVIEW','ACCEPTING'].includes(room.phase) && <button className="link danger wide" onClick={() => { const reason = window.prompt(t("Why are you cancelling this order?")); if (reason) data.send('CANCEL', { text: reason }, room.id); }}>{t("Cancel today’s order")}</button>}</section>
        {(owner || payer) && <RoomManagement key={`${room.id}:${room.orderNumber}:${room.restaurant.id}:${room.restaurant.pricing.taxTreatment}:${room.restaurant.pricing.taxRateBasisPoints}`} room={room} data={data} owner={owner} payer={payer} />}
        {selected && <section className="card winner-mini"><p className="eyebrow">{t("SELECTED")}</p><h3>{winner.name}</h3><p>{t("Sandwich entry remains open while the payer confirms.")}</p></section>}
      </aside>
    </div></fieldset>
    <RestaurantPollPrompt key={`${room.id}:${room.orderNumber}:${me.id}`} room={room} me={me} data={data} language={uiLanguage} />
  </Page>;
}

function FoodRunClient() {
  const data = useFoodRun();
  const notifications = useNotifications({ ...data, language: uiLanguage });
  const [notificationAction, setNotificationAction] = useState(null);
  const notificationLink = useRef(new URLSearchParams(window.location.search));
  const [siteConfig, setSiteConfig] = useState({ registrationsEnabled: true, roomCreationEnabled: true, maintenanceMessage: '' });
  const inviteCode = useMemo(() => new URLSearchParams(window.location.search).get('room')?.replace(/\D/g, '').slice(0, 6) || '', []);
  const [page, setPage] = useState(window.location.pathname.replace(/\/+$/, '') === '/admin' ? 'admin' : inviteCode ? 'join' : 'home');
  useEffect(() => { window.scrollTo({ top: 0 }); }, [page]);
  const [roomId, setRoomId] = useState('');
  const restoredRoom = useRef(false);
  useEffect(() => { fetch(`${PUBLIC_API_URL}/config`).then(response => response.ok ? response.json() : Promise.reject()).then(setSiteConfig).catch(() => {}); }, []);
  useEffect(() => {
    if (!data.hub) return;
    const controller = new AbortController();
    fetch(`${data.hub}/catalog?includeDeleted=true`, { signal: controller.signal }).then(response => response.ok ? response.json() : Promise.reject()).then(catalog => {
      if (controller.signal.aborted) return;
      const values = Array.isArray(catalog) ? catalog : catalog.restaurants;
      const deletedRestaurantIds = Array.isArray(catalog) ? [] : catalog.deletedRestaurantIds || [];
      let previousIds = [];
      try { previousIds = JSON.parse(localStorage.getItem(SERVER_CATALOG_KEY) || '[]'); } catch { previousIds = []; }
      const merged = mergeRestaurantCatalog({ serverValues: values, bundledValues: BUNDLED_RESTAURANTS, currentValues: loadRestaurants(), previousManagedIds: previousIds, normalize: normalizeRestaurant, deletedRestaurantIds });
      localStorage.setItem(SERVER_CATALOG_KEY, JSON.stringify(merged.managedIds));
      storeRestaurants(merged.restaurants);
    }).catch(() => {});
    return () => controller.abort();
  }, [data.hub]);
  const openRoom = id => {
    setNotificationAction(null);
    localStorage.setItem(ACTIVE_ROOM_KEY, JSON.stringify({ userId: data.user?.uid, hub: data.hub, roomId: id }));
    window.history.replaceState({}, '', window.location.pathname);
    setRoomId(id); setPage('room');
  };
  const openNotification = (item, action) => {
    notifications.read(item.id);
    openRoom(item.roomId);
    setNotificationAction({ item, action });
  };
  useEffect(() => {
    const id = notificationLink.current.get('notification');
    const item = notifications.items.find(value => value.id === id);
    if (!item || !data.sessions[item.roomId]) return;
    const action = notificationLink.current.get('action') || 'open';
    notificationLink.current = new URLSearchParams();
    openNotification(item, action);
  }, [notifications.items, data.sessions]);
  useEffect(() => { setNotificationAction(null); }, [data.user?.uid, data.hub]);
  const closeRoom = () => { localStorage.removeItem(ACTIVE_ROOM_KEY); setRoomId(''); setPage('home'); };
  useEffect(() => {
    if (page === 'admin' || restoredRoom.current || inviteCode || !data.user || !data.hub) return;
    let saved;
    try { saved = JSON.parse(localStorage.getItem(ACTIVE_ROOM_KEY) || 'null'); } catch { saved = null; }
    if (saved?.userId === data.user.uid && saved?.hub === data.hub && data.sessions[saved.roomId]) {
      setRoomId(saved.roomId); setPage('room'); restoredRoom.current = true;
    } else if (Object.keys(data.sessions).length > 0) restoredRoom.current = true;
  }, [data.user, data.hub, data.sessions, inviteCode]);
  useEffect(() => {
    if (roomId && data.home?.deletedRoomIds?.includes(roomId)) closeRoom();
  }, [roomId, data.home?.deletedRoomIds]);
  const alerts = useMemo(() => <>{siteConfig.maintenanceMessage && <div className="banner notice">{siteConfig.maintenanceMessage}</div>}{data.error && <div className="banner error" role="alert">{data.error}{data.hasPending && <button onClick={data.retry}>{t("Retry saved request")}</button>}</div>}{data.notice && <div className="banner notice">{data.notice}<button aria-label={t("Close")} onClick={() => data.setNotice('')}>×</button></div>}</>, [siteConfig.maintenanceMessage, data.error, data.hasPending, data.notice]);
  const roomBlock = page === 'room' ? data.roomBlocks?.[roomId] || data.roomBlocks?.['*'] : null;
  const block = roomBlock || (['join','create','payment-create'].includes(page) ? data.joinBlock : null);
  if (block) return <BlockedNotice block={block} retry={() => data.connect(data.hub)} onBack={() => { data.clearJoinBlock(); closeRoom(); }} />;
  if (data.user && page === 'offline') return <>{alerts}<OfflineReceipts receipts={data.offlineReceipts} language={uiLanguage} onBack={() => setPage('home')} onClear={data.clearOfflineReceipts} /></>;
  const offlineAction = data.user && <button className="secondary" onClick={() => setPage('offline')}>{tx('Downloaded receipts', 'الإيصالات المحفوظة')}</button>;
  if (!data.authReady || (page !== 'admin' && data.user && data.hub && !data.home && !data.error)) return <><div className="splash"><div className="brand-mark">FR</div><div className="spinner" /><p>{t("Setting the table…")}</p>{offlineAction}</div>{alerts}</>;
  if (!data.user) return <AuthScreen ready={data.authReady} allowRegistration={siteConfig.registrationsEnabled} />;
  if (page === 'admin') return canAccessAdmin(data.user) ? <AdminApp key={data.user.uid} language={uiLanguage} user={data.user} onBack={() => { window.history.replaceState({}, '', '/'); setPage('home'); }} /> : <main className="center-shell"><section className="card"><p>{tx('This account cannot access administration.', 'هذا الحساب لا يملك صلاحية الإدارة.')}</p><button onClick={() => { window.history.replaceState({}, '', '/'); setPage('home'); }}>{t('Home')}</button></section></main>;
  if (!data.hub || (!data.home && data.error)) return <><HubScreen current={data.hub} connect={data.connect} error={data.error} /><div className="center-actions">{offlineAction}</div>{alerts}</>;
  const home = data.home;
  const profileMissing = !home.profile.name || !home.profile.phone;
  let content;
  if (page === 'notifications') content = <NotificationCenter notifications={notifications} onOpen={openNotification} onBack={() => setPage('home')} />;
  else if (page === 'downloads') content = <Page title={t("Get Food Run")} subtitle={t("Install the mobile app and keep your table close.")} onBack={() => setPage('home')}><AppDownloads /></Page>;
  else if (page === 'profile' || profileMissing) content = <ProfileScreen data={data} openRoom={openRoom} onBack={() => setPage('home')} />;
  else if (page === 'restaurants') content = <RestaurantLibraryScreen language={uiLanguage} onBack={() => setPage('home')} />;
  else if (page === 'payment-create') content = <CreatePaymentRoom data={data} onBack={() => setPage('home')} openRoom={openRoom} openProfile={() => setPage('profile')} />;
  else if (page === 'create' || page === 'join') content = <CreateRoom data={data} mode={page} inviteCode={inviteCode} onBack={() => setPage('home')} openRoom={openRoom} />;
  else if (page === 'room') content = <RoomScreen data={data} roomId={roomId} onBack={closeRoom} />;
  else content = <Home data={data} setPage={setPage} openRoom={openRoom} allowRoomCreation={siteConfig.roomCreationEnabled} />;
  return <>{alerts}{page === 'room' && notificationAction && <NotificationActionCard key={notificationAction.item.id + notificationAction.action} selected={notificationAction} data={data} onClose={() => setNotificationAction(null)} />}{content}<footer><span>Food Run</span><button onClick={() => setPage('notifications')}>{t('Notifications')} {notifications.items.filter(item => !item.read).length || ''}</button>{offlineAction}<button onClick={() => setPage(t("restaurants"))}>{t("Restaurants & menus")}</button><button onClick={() => setPage('downloads')}>{t("Get the apps")}</button><button onClick={() => setPage('profile')}>{t("Profile")}</button><button onClick={() => data.connect('')}>{t("Switch room server")}</button><button onClick={async () => { await notifications.disable(); await signOut(auth); }}>{t("Sign out")}</button></footer></>;
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
  return <Suspense fallback={<div className="splash"><p>{tx('Loading…', 'جارٍ التحميل…')}</p></div>}>{new URLSearchParams(window.location.search).has('nativeSignIn') ? <NativeGoogleSignIn /> : <FoodRunClient />}</Suspense>;
}

export { Home, ProfileScreen, RoomScreen, MemberOrderPanel, PUBLIC_API_URL, Page, LanguageToggle, loadRestaurants, storeRestaurants, blankRestaurant, normalizeRestaurant, clone, uid, minorInput, parseRestaurantExport, restaurantExport, downloadText, copyText };
