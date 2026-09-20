import { useEffect, useMemo, useRef, useState } from 'react';
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
  return <div className="language-toggle"><button type="button" className={uiLanguage === 'en' ? 'active' : ''} onClick={() => updateLanguage('en')}>English</button><button type="button" className={uiLanguage === 'ar' ? 'active' : ''} onClick={() => updateLanguage('ar')}>العربية</button></div>;
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
  if (!value || typeof value !== 'object' || !String(value.id || '').trim() || !String(value.name || '').trim()) throw Error('Restaurant ID and name are required.');
  const currency = String(value.currency || 'AED').toUpperCase();
  if (currency !== 'AED') throw Error('Restaurant menus and rooms use AED (Dirham).');
  const menu = value.menu || {};
  const categories = Array.isArray(menu.categories) ? menu.categories : [];
  const optionGroups = Array.isArray(menu.optionGroups) ? menu.optionGroups : [];
  const items = Array.isArray(menu.items) ? menu.items : [];
  const categoryIds = new Set(categories.map(category => category.id));
  if (items.some(item => !categoryIds.has(item.categoryId))) throw Error('Every menu item must reference an existing category.');
  const defaults = blankRestaurant();
  return {
    ...defaults, ...value, id: String(value.id).trim(), name: String(value.name).trim(), currency,
    contact: { address: null, ...(value.contact || {}),
      phoneE164: value.contact?.phoneE164 ? uaePhone(value.contact.phoneE164) : null,
      whatsappE164: value.contact?.whatsappE164 ? uaePhone(value.contact.whatsappE164, true) : null },
    pricing: { ...defaults.pricing, ...(value.pricing || {}) },
    menu: { categories, optionGroups, items },
    openOrdering: Boolean(value.openOrdering || items.length === 0),
  };
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

function RestaurantLibraryScreen({ onBack }) {
  const [restaurants, setRestaurants] = useState(loadRestaurants);
  const [draft, setDraft] = useState(blankRestaurant);
  const [categoryName, setCategoryName] = useState('');
  const [newItem, setNewItem] = useState({ categoryId: '', name: '', price: '' });
  const [message, setMessage] = useState('');
  const saveList = next => { setRestaurants(next); storeRestaurants(next); };
  const set = (key, value) => setDraft(old => ({ ...old, [key]: value }));
  const setContact = (key, value) => setDraft(old => ({ ...old, contact: { ...old.contact, [key]: value || null } }));
  const setPricing = (key, value) => setDraft(old => ({ ...old, pricing: { ...old.pricing, [key]: value } }));
  const save = event => {
    event.preventDefault(); setMessage('');
    try {
      const normalized = normalizeRestaurant(draft);
      const next = [...restaurants.filter(item => item.id !== normalized.id), normalized].sort((a, b) => a.name.localeCompare(b.name));
      saveList(next); setDraft(clone(normalized)); setMessage('Restaurant and menu saved on this device.');
    } catch (error) { setMessage(error.message); }
  };
  const addCategory = () => {
    const name = categoryName.trim(); if (!name) return;
    const category = { id: uid(), name, sortOrder: draft.menu.categories.length };
    setDraft(old => ({ ...old, menu: { ...old.menu, categories: [...old.menu.categories, category] } }));
    setNewItem(old => ({ ...old, categoryId: old.categoryId || category.id })); setCategoryName('');
  };
  const addMenuItem = () => {
    if (!newItem.categoryId || !newItem.name.trim() || !newItem.price) return setMessage('Choose a category, item name, and price.');
    try {
      const item = { id: uid(), categoryId: newItem.categoryId, name: newItem.name.trim(), description: '', basePriceMinor: amount(newItem.price, draft.currency), available: true, variants: [], optionGroupIds: [] };
      setDraft(old => ({ ...old, menu: { ...old.menu, items: [...old.menu.items, item] }, openOrdering: false }));
      setNewItem(old => ({ ...old, name: '', price: '' })); setMessage('');
    } catch (error) { setMessage(error.message); }
  };
  const removeCategory = id => setDraft(old => ({ ...old, menu: { ...old.menu, categories: old.menu.categories.filter(category => category.id !== id), items: old.menu.items.filter(item => item.categoryId !== id) } }));
  const importFile = async file => {
    if (!file) return;
    try { const imported = parseRestaurantExport(await file.text()); setDraft(imported); setMessage('Menu imported. Review it, then save.'); }
    catch (error) { setMessage(error.message); }
  };
  return <Page title="Restaurants & menus" subtitle="Save restaurant details and prices once, or import the same Food Run schema used by the mobile apps." onBack={onBack}>
    <div className="library-layout">
      <aside className="card library-list">
        <div className="section-title compact"><div><p className="eyebrow">SAVED ON THIS DEVICE</p><h3>{restaurants.length} restaurant{restaurants.length === 1 ? '' : 's'}</h3></div><button className="icon-button" type="button" onClick={() => { setDraft(blankRestaurant()); setMessage(''); }}>＋</button></div>
        {restaurants.length === 0 && <p className="muted">Add your first restaurant or import a menu from Food Run mobile.</p>}
        {restaurants.map(restaurant => <button type="button" className={`restaurant-row ${draft.id === restaurant.id ? 'active' : ''}`} key={restaurant.id} onClick={() => { setDraft(clone(restaurant)); setMessage(''); }}><span><b>{restaurant.name}</b><small>{restaurant.menu.items.length} menu items · {restaurant.currency}</small></span><strong>›</strong></button>)}
        <label className="upload wide">Import Food Run JSON<input type="file" accept="application/json,.json" onChange={event => importFile(event.target.files?.[0])} /></label>
      </aside>
      <form className="stack" onSubmit={save}>
        <section className="card editor-card stack">
          <div className="section-title compact"><div><p className="eyebrow">RESTAURANT</p><h2>{draft.name || 'New restaurant'}</h2></div>{restaurants.some(item => item.id === draft.id) && <button type="button" className="link danger" onClick={() => { saveList(restaurants.filter(item => item.id !== draft.id)); setDraft(blankRestaurant()); }}>Delete</button>}</div>
          <div className="form-grid three"><label>Restaurant name<input value={draft.name} onChange={e => set('name', e.target.value)} required /></label><label>Branch<input value={draft.branchName} onChange={e => set('branchName', e.target.value)} /></label><label>Currency<input value="AED · UAE Dirham" readOnly /></label></div>
          <div className="form-grid three"><label>UAE phone<input value={draft.contact.phoneE164 || ''} onChange={e => setContact('phoneE164', e.target.value)} placeholder="050 123 4567" /></label><label>UAE WhatsApp<input value={draft.contact.whatsappE164 || ''} onChange={e => setContact('whatsappE164', e.target.value)} placeholder="050 123 4567" /></label><label>Address<input value={draft.contact.address || ''} onChange={e => setContact('address', e.target.value)} /></label></div>
          <div className="form-grid three"><label>Tax treatment<select value={draft.pricing.taxTreatment} onChange={e => setPricing('taxTreatment', e.target.value)}><option value="included">Included</option><option value="added">Added to bill</option><option value="unspecified">Confirm later</option></select></label>{draft.pricing.taxTreatment === 'added' && <label>Tax rate %<input type="number" min="0" max="100" step="0.01" value={(draft.pricing.taxRateBasisPoints || 0) / 100} onChange={e => setPricing('taxRateBasisPoints', Math.round(Number(e.target.value) * 100))} /></label>}<label>Minimum order<input inputMode="decimal" value={minorInput(draft.pricing.minimumOrderMinor, draft.currency)} onChange={e => { try { setPricing('minimumOrderMinor', amount(e.target.value || '0', draft.currency)); } catch { /* validate when saved */ } }} /></label></div>
          <div className="form-grid three"><label>Default delivery fee<input inputMode="decimal" value={minorInput(draft.pricing.defaultDeliveryFeeMinor, draft.currency)} onChange={e => { try { setPricing('defaultDeliveryFeeMinor', amount(e.target.value || '0', draft.currency)); } catch { /* validate when saved */ } }} /></label><label>Default service fee<input inputMode="decimal" value={minorInput(draft.pricing.defaultServiceFeeMinor, draft.currency)} onChange={e => { try { setPricing('defaultServiceFeeMinor', amount(e.target.value || '0', draft.currency)); } catch { /* validate when saved */ } }} /></label><label className="check field-check"><input type="checkbox" checked={draft.openOrdering} onChange={e => set('openOrdering', e.target.checked)} /> Allow custom items</label></div>
        </section>
        <section className="card editor-card stack">
          <div><p className="eyebrow">MENU & SAVED PRICES</p><h2>Items people can choose</h2><p className="muted">Imported sizes and extras are preserved. You can also build a simple priced menu here.</p></div>
          <div className="inline-add"><input value={categoryName} onChange={e => setCategoryName(e.target.value)} placeholder="New category, e.g. Mains" /><button type="button" className="secondary" onClick={addCategory}>Add category</button></div>
          {draft.menu.categories.map(category => <div className="menu-category-editor" key={category.id}><div><h3>{category.name}</h3><button type="button" className="link danger" onClick={() => removeCategory(category.id)}>Remove category</button></div>{draft.menu.items.filter(item => item.categoryId === category.id).map(menuItem => <div className="saved-menu-item" key={menuItem.id}><span><b>{menuItem.name}</b><small>{menuItem.variants.length ? `${menuItem.variants.length} sizes` : money(menuItem.basePriceMinor, draft.currency)}{menuItem.optionGroupIds.length ? ` · ${menuItem.optionGroupIds.length} extra groups` : ''}</small></span><button type="button" className="link danger" onClick={() => setDraft(old => ({ ...old, menu: { ...old.menu, items: old.menu.items.filter(item => item.id !== menuItem.id) } }))}>Remove</button></div>)}</div>)}
          {draft.menu.categories.length > 0 && <div className="form-grid item-adder"><label>Category<select value={newItem.categoryId} onChange={e => setNewItem({ ...newItem, categoryId: e.target.value })}><option value="">Choose</option>{draft.menu.categories.map(category => <option value={category.id} key={category.id}>{category.name}</option>)}</select></label><label>Item name<input value={newItem.name} onChange={e => setNewItem({ ...newItem, name: e.target.value })} /></label><label>Base price<input inputMode="decimal" value={newItem.price} onChange={e => setNewItem({ ...newItem, price: e.target.value })} placeholder="0.00" /></label><button type="button" className="secondary" onClick={addMenuItem}>Add menu item</button></div>}
          {draft.menu.categories.length === 0 && <div className="empty small-empty"><span>🍽️</span><p>Add a category, or keep custom items enabled for an open order.</p></div>}
        </section>
        {message && <p className={message.includes('saved') || message.includes('imported') ? 'success-message' : 'form-message'} role="status">{message}</p>}
        <div className="editor-actions"><button type="button" className="secondary" onClick={() => downloadText(`${(draft.name || 'restaurant').replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.foodrun.json`, restaurantExport(draft))}>Export JSON</button><button type="button" className="secondary" onClick={() => copyText(restaurantExport(draft)).then(() => setMessage('Restaurant JSON copied.'))}>Copy JSON</button><button className="primary">Save restaurant</button></div>
      </form>
    </div>
  </Page>;
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
    <p className="eyebrow">TODAY’S PICK</p>
    <h2>{winner.name} is ordering!</h2>
    <p>Everyone sees the same selected person live.</p>
    {selected && winner.id === me.id && <div className="hero-actions"><button className="primary" onClick={() => data.send('ACCEPT_DUTY', {}, room.id)}>I’ll take care of it</button><button className="secondary" onClick={() => data.send('DECLINE_DUTY', { text: 'Unavailable this time' }, room.id)}>I can’t this time</button></div>}
  </div>;
}

function AppDownloads({ compact = false }) {
  const [iosHelp, setIosHelp] = useState(false);
  return <section id="app-downloads" className={`app-downloads ${compact ? 'compact' : ''}`}>
    <div className="download-heading">
      <div><p className="eyebrow">FOOD RUN ON YOUR PHONE</p><h2>Take the table with you.</h2></div>
      <p>Use the same account and join Internet Rooms from Android, iPhone, or the web.</p>
    </div>
    <div className="download-grid">
      <article className="download-card">
        <span className="platform-icon android" aria-hidden="true">◆</span>
        <div><strong>Android app</strong><small>Version 1.3 · Android 8+</small></div>
        <a className="primary store-button" href={ANDROID_DOWNLOAD_URL}>Download APK</a>
      </article>
      <article className="download-card">
        <span className="platform-icon apple" aria-hidden="true">●</span>
        <div><strong>iPhone app</strong><small>{IOS_STORE_URL ? 'Available for iPhone and iPad' : 'Install from Safari · iOS 17+'}</small></div>
        {IOS_STORE_URL
          ? <a className="secondary store-button" href={IOS_STORE_URL}>Open App Store</a>
          : <button className="secondary store-button" onClick={() => setIosHelp(value => !value)}>Install on iPhone</button>}
      </article>
    </div>
    {iosHelp && <div className="ios-install" role="status"><b>On iPhone or iPad:</b> open this page in Safari, tap the Share button, then choose <b>Add to Home Screen</b> and <b>Add</b>.</div>}
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
      <p className="eyebrow">FOOD RUN / TOGETHER</p>
      <h1>Good food.<br />Better together.</h1>
      <p>Pick who will order, collect everyone’s food live, and settle every share without the group-chat chaos.</p>
      <div className="story-steps"><span>01 Join</span><span>02 Select</span><span>03 Order</span><span>04 Settle</span></div>
      <AppDownloads compact />
    </section>
    <section className="auth-card card">
      <p className="eyebrow">YOUR TABLE AWAITS</p>
      <h2>{register ? 'Create your profile' : 'Welcome back'}</h2>
      <p className="muted">Use the same account you already use in Intrvioo.</p>
      <button className="google" disabled={!ready || busy} onClick={() => signInWithPopup(auth, googleProvider).catch(e => setMessage(e.message))}>Continue with Google</button>
      <div className="or"><span />or<span /></div>
      <form onSubmit={submit} className="stack">
        {register && <label>Name<input value={name} onChange={e => setName(e.target.value)} required /></label>}
        <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>Password<input type="password" minLength="6" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {message && <p className="form-message" role="alert">{message}</p>}
        <button className="primary" disabled={!ready || busy}>{busy ? 'One moment…' : register ? 'Create account' : 'Sign in'}</button>
      </form>
      {!register && <button className="link" onClick={reset}>Forgot password?</button>}
      {(allowRegistration || register) && <button className="link switch" onClick={() => { setRegister(!register); setMessage(''); }}>{register ? 'Already registered? Sign in' : 'New to Food Run? Create account'}</button>}
      {!allowRegistration && <p className="form-message">New registration is temporarily disabled by the administrator.</p>}
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
    <div className="brand-mark">FR</div><p className="eyebrow">CONNECT YOUR TABLE</p>
    <h1>Where should this room live?</h1>
    <p className="muted">Use a nearby hub to keep live traffic on your local network, or use the internet API so everyone can join from anywhere.</p>
    <div className="mode-cards">
      <button type="button" className="mode-card" onClick={() => setAddress('https://192.168.1.20:8443')}>
        <strong>Nearby hub</strong><span>Lowest cloud use</span>
      </button>
      <button type="button" className="mode-card" disabled={!publicApi} onClick={() => publicApi && connect(publicApi)}>
        <strong>Internet room</strong><span>{publicApi ? 'Join from any network' : 'Set VITE_FOODRUN_API_URL'}</span>
      </button>
    </div>
    <form className="stack" onSubmit={submit}>
      <label>Nearby hub address<input value={address} onChange={e => setAddress(e.target.value)} placeholder="https://192.168.1.20:8443" /></label>
      {(message || error) && <p className="form-message" role="alert">{message || error}</p>}
      <button className="primary">Connect to nearby hub</button>
    </form>
    <p className="fine">Food Run remembers the API used to create each room. Android, iOS, and web use the same command API and event-driven WebSocket. Firebase holds the shared account and asynchronous backup.</p>
  </section></main>;
}

function ProfileScreen({ profile, busy, send, onBack }) {
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
      bank: form.method === 'AANI' ? 'Aani' : form.bank.trim(), identifier: form.method === 'AANI' ? uaePhone(form.identifier, true) : form.identifier.trim(),
      currency: 'AED', version: profile?.payment?.version || 1, method: form.method,
    } : null;
      const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: {
        userId: '', name: form.name.trim(), phone: uaePhone(form.phone, true), photo: form.photo,
        payment, discoverable: form.discoverable, language: uiLanguage,
      } } });
      if (reply) onBack();
    } catch (error) { setMessage(error.message); }
  };
  return <Page title="Your profile" subtitle="The details your people need when you’re selected." onBack={onBack}>
    <form className="profile-grid" onSubmit={save}>
      <section className="card profile-photo">
        <Avatar profile={{ name: form.name, photo: form.photo }} />
        <label className="upload">Choose photo<input type="file" accept="image/jpeg,image/png,image/webp" onChange={async e => e.target.files[0] && set('photo', await photoData(e.target.files[0]))} /></label>
        {form.photo && <button type="button" className="link" onClick={() => set('photo', '')}>Remove photo</button>}
      </section>
      <section className="card stack">
        <h3>About you</h3>
        <label>Profile name<input value={form.name} onChange={e => set('name', e.target.value)} required maxLength="160" /></label>
        <label>UAE mobile number<input type="tel" value={form.phone} onChange={e => set('phone', e.target.value)} required placeholder="050 123 4567" /></label>
        <label className="check"><input type="checkbox" checked={form.discoverable} onChange={e => set('discoverable', e.target.checked)} /> Let people on this hub invite me</label>
      </section>
      <section className="card stack payment-card">
        <div><p className="eyebrow">GET PAID BACK</p><h3>Receiving details</h3><p className="muted">Shared with room members only after you are selected and choose to share it.</p></div>
        <div className="segmented"><button type="button" className={form.method === 'AANI' ? 'active' : ''} onClick={() => set('method', 'AANI')}>Aani</button><button type="button" className={form.method === 'BANK' ? 'active' : ''} onClick={() => set('method', 'BANK')}>Bank account</button></div>
        <label>Account holder<input value={form.holder} onChange={e => set('holder', e.target.value)} placeholder={form.name || 'Your name'} /></label>
        {form.method === 'BANK' && <label>Bank name<input value={form.bank} onChange={e => set('bank', e.target.value)} /></label>}
        <label>{form.method === 'AANI' ? 'UAE mobile registered with Aani' : 'IBAN / account number'}<input value={form.identifier} onChange={e => set('identifier', e.target.value)} placeholder={form.method === 'AANI' ? '050 123 4567' : 'AE…'} /></label>
      </section>
      {message && <p className="form-message" role="alert">{message}</p>}
      <div className="form-actions"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</button></div>
    </form>
  </Page>;
}

function Page({ title, subtitle, onBack, actions, children }) {
  return <main className="app-shell">
    <header className="topbar"><button className="wordmark" onClick={onBack}><span>FR</span> FOOD RUN</button><div className="top-actions"><LanguageToggle />{actions}</div></header>
    <div className="page-heading">{onBack && <button className="back" onClick={onBack}>{uiLanguage === 'ar' ? 'رجوع ←' : '← Back'}</button>}<p className="eyebrow">FOOD RUN / TOGETHER</p><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {children}
  </main>;
}

function Home({ data, setPage, openRoom, allowRoomCreation = true }) {
  const { home, rooms, sessions, online } = data;
  const roomCards = Object.values(sessions).map(session => ({ session, reply: rooms[session.roomId] }));
  return <Page title={`Good food, ${home.profile.name?.split(' ')[0] || 'together'}.`} subtitle="Start a table or jump back into today’s order." actions={<><button className="icon-button" aria-label="Notifications" onClick={() => Notification.requestPermission()}>◔</button><button className="profile-chip" onClick={() => setPage('profile')}><Avatar small profile={home.profile} />{home.profile.name || 'Complete profile'}</button></>}>
    <section className="hero card"><div><p className="eyebrow">A TABLE FOR EVERYONE</p><h2>One room. The whole crew.</h2><p>Everyone joins live, the wheel picks who orders, and every item and amount stays together.</p><div className="hero-actions"><button className="primary light" disabled={!allowRoomCreation} onClick={() => setPage('create')}>Create a room</button><button className="secondary light" onClick={() => setPage('join')}>Join with code</button><button className="secondary light" onClick={() => setPage('restaurants')}>Restaurants & menus</button></div>{!allowRoomCreation && <p className="form-message">New room creation is temporarily disabled by the administrator.</p>}</div><div className="hero-art"><span>🥡</span><span>🍜</span><span>🥗</span></div></section>
    <AppDownloads />
    {home.invitations.length > 0 && <section><div className="section-title"><div><p className="eyebrow">YOU’RE INVITED</p><h2>Join the table</h2></div><span>{home.invitations.length}</span></div><div className="grid two">{home.invitations.map(invite => <article className="card invitation" key={invite.id}><span className="status live">Invitation</span><h3>{invite.roomName}</h3><p>{invite.invitedBy} invited you to order #{invite.orderNumber}.</p><button className="primary" onClick={async () => { const reply = await data.send('IDENTITY', { identity: { action: 'ACCEPT_INVITE', invitationId: invite.id } }); if (reply?.room) openRoom(reply.room.id); }}>Join room</button></article>)}</div></section>}
    <section><div className="section-title"><div><p className="eyebrow">YOUR TABLES</p><h2>Live rooms</h2></div><span>{roomCards.length}</span></div>
      {roomCards.length ? <div className="grid two">{roomCards.map(({ session, reply }) => {
        const room = reply?.room, member = room?.members.find(m => m.id === session.memberId), winner = room?.members.find(m => m.id === (room.payerId || room.spin?.winnerId));
        const receipt = reply?.receipts.find(r => r.memberId === session.memberId);
        return <article className="card room-card" key={session.roomId} onClick={() => openRoom(session.roomId)}><div className="room-card-top"><span className={`status ${online[session.roomId] ? 'live' : ''}`}>{online[session.roomId] ? '● Live' : 'Offline'}</span><span>#{room?.orderNumber || 1}</span></div><h3>{session.roomName}</h3><p>{room ? phaseLabel[room.phase] : 'Connecting…'}</p>{winner && <div className="winner-row"><span className="avatar initials small">{initials(winner.name)}</span><span><b>{winner.name}</b><small>{room.payerId ? ' is ordering' : ' was selected'}</small></span></div>}{receipt && ['REVIEW','PLACED','FULFILLED'].includes(room.phase) && <div className="amount-row"><span>Your total</span><b>{receipt.totalText}</b></div>}<button className="secondary">{room?.phase === 'LOBBY' && !member?.participating ? 'Join this order' : 'Open room'} →</button></article>;
      })}</div> : <div className="card empty"><span>🥢</span><h3>No saved rooms yet</h3><p>Create a room and invite your people, or join with a six-digit code.</p></div>}
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
          fees: { delivery: amount(form.delivery || '0', currency), service: amount(form.service || '0', currency), discount: amount(form.discount || '0', currency), proportionalDelivery: form.proportionalDelivery },
        });
      }
      if (reply?.room) openRoom(reply.room.id);
    } catch (error) { setMessage(error.message); }
  };
  return <Page title={mode === 'join' ? 'Join your people' : 'Create a room'} subtitle={mode === 'join' ? 'Open an invitation link, scan its QR, or enter the six-digit code.' : 'Choose the restaurant now, or let everyone vote after joining the room.'} onBack={onBack}>
    <form className="card create-form stack" onSubmit={submit}>
      {mode === 'join' ? <label>Six-digit room code<input inputMode="numeric" pattern="[0-9]{6}" value={form.code} onChange={e => setForm({ ...form, code: e.target.value.replace(/\D/g, '').slice(0, 6) })} required autoFocus /></label> : <>
        <label>Room name<input value={form.room} onChange={e => setForm({ ...form, room: e.target.value })} placeholder="Friday lunch club" required autoFocus /></label>
        {restaurants.length > 0 && <label>{tx('Saved restaurant', 'المطعم المحفوظ')}<select value={form.restaurantId} onChange={e => chooseRestaurant(e.target.value)}><option value="">{tx('Quick open order', 'طلب مفتوح سريع')}</option>{restaurants.map(restaurant => <option value={restaurant.id} key={restaurant.id}>{localizedName(restaurant)}{restaurant.branchName ? ` · ${restaurant.branchName}` : ''} · {restaurant.menu.items.length} {tx('items', 'صنفاً')}</option>)}</select></label>}
        {!chosen && <div className="form-grid two"><label>Restaurant / order name<input value={form.restaurant} onChange={e => setForm({ ...form, restaurant: e.target.value })} placeholder="Today’s food order" required /></label><label>Restaurant phone<input type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="+971…" required /></label></div>}
        {chosen && <div className="selected-restaurant"><span><b>{localizedName(chosen)}</b><small>{chosen.menu.items.length ? `${chosen.menu.items.length} ${tx('saved menu items and prices', 'صنفاً محفوظاً بأسعاره')}` : tx('Open order for custom items', 'طلب مفتوح للأصناف المخصصة')}</small></span><strong>{chosen.currency}</strong></div>}
        <div className="segmented restaurant-choice"><button type="button" className={!form.restaurantPoll ? 'active' : ''} onClick={() => setForm({ ...form, restaurantPoll: false })}>Use this restaurant</button><button type="button" className={form.restaurantPoll ? 'active' : ''} onClick={() => setForm({ ...form, restaurantPoll: true })}>Start a room poll</button></div>
        <p className="field-help">{form.restaurantPoll ? 'Members vote live in the room. You finish the poll before sandwich ordering starts.' : 'The selected restaurant menu opens immediately for everyone in the room.'}</p>
        <div className="segmented delivery-choice"><button type="button" className={!form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: false })}>Pickup</button><button type="button" className={form.deliveryMode ? 'active' : ''} onClick={() => setForm({ ...form, deliveryMode: true })}>Delivery</button></div>
        {form.deliveryMode && <label>Delivery address and contact<input value={form.destination} onChange={e => setForm({ ...form, destination: e.target.value })} required /></label>}
        <div className="form-grid three"><label>Delivery fee · {currency}<input inputMode="decimal" value={form.delivery} onChange={e => setForm({ ...form, delivery: e.target.value })} /></label><label>Service fee · {currency}<input inputMode="decimal" value={form.service} onChange={e => setForm({ ...form, service: e.target.value })} /></label><label>Shared discount · {currency}<input inputMode="decimal" value={form.discount} onChange={e => setForm({ ...form, discount: e.target.value })} /></label></div>
        <label className="check"><input type="checkbox" checked={form.proportionalDelivery} onChange={e => setForm({ ...form, proportionalDelivery: e.target.checked })} /> Split delivery by each person’s food total <span>Leave off to split the delivery fee equally between people who ordered food.</span></label>
      </>}
      {message && <p className="form-message" role="alert">{message}</p>}
      <button className="primary" disabled={data.busy || !profile.name || !profile.phone}>{data.busy ? 'Connecting…' : mode === 'join' ? 'Request to join' : 'Create room'}</button>
      {(!profile.name || !profile.phone) && <p className="form-message">Complete your name and phone in your profile first.</p>}
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

function OrderLine({ line, label, currency, canPrice, onPrice, onEdit, onRemove }) {
  const lineTotal = line.amount ?? (line.unitPrice == null ? null : line.unitPrice * line.quantity);
  return <div className="order-line"><div><b>{line.quantity} × {label || line.description}</b>{line.notes && <small>{line.notes}</small>}</div><div>{lineTotal == null ? <span className="status">Awaiting price</span> : <b>{money(lineTotal, currency)}</b>}{canPrice && <button type="button" className="link" onClick={onPrice}>{line.unitPrice == null ? 'Add price' : 'Change'}</button>}{onEdit && <button type="button" className="link" onClick={onEdit}>Edit</button>}{onRemove && <button type="button" className="link danger" onClick={onRemove}>Remove</button>}</div></div>;
}

function OrderProgress({ room, receipts }) {
  const total = receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const paid = receipts.reduce((sum, receipt) => sum + receipt.paid, 0);
  const index = room.phase === 'LOBBY' ? (room.restaurantPollOpen ? 1 : 2) : ['PREPARING_SPIN', 'SPINNING', 'ACCEPTING'].includes(room.phase) ? 3 : ['COLLECTING', 'REVIEW'].includes(room.phase) ? 4 : 5;
  return <section className="card order-progress"><div className="progress-steps">{['Join', 'Restaurant', 'Sandwiches', 'Pick payer', 'Confirm', 'Settle'].map((title, step) => <div className={`${step < index ? 'done' : ''} ${step === index ? 'current' : ''}`} key={title}><span>{step < index ? '✓' : step + 1}</span><b>{title}</b></div>)}</div>{receipts.length > 0 && <div className="progress-money"><span><small>Total food order</small><b>{money(total, room.restaurant.currency)}</b></span><span><small>Member payments confirmed</small><b>{money(paid, room.restaurant.currency)}</b></span><span><small>Still to settle</small><b>{money(receipts.filter(receipt => receipt.memberId !== room.payerId).reduce((sum, receipt) => sum + Math.max(0, receipt.balance), 0), room.restaurant.currency)}</b></span></div>}</section>;
}

function MenuItemForm({ room, editing, onSave, onCancel }) {
  const available = room.restaurant.menu.items.filter(item => item.available);
  const initialItem = editing ? available.find(item => item.id === editing.itemId) : available[0];
  const [choice, setChoice] = useState({ itemId: initialItem?.id || '', variantId: editing?.variantId || initialItem?.variants[0]?.id || '', optionIds: editing?.optionIds || [], quantity: String(editing?.quantity || 1), notes: editing?.notes || '' });
  const [message, setMessage] = useState('');
  const menuItem = available.find(item => item.id === choice.itemId);
  const groups = menuItem ? room.restaurant.menu.optionGroups.filter(group => menuItem.optionGroupIds.includes(group.id)) : [];
  const selectItem = itemId => { const next = available.find(item => item.id === itemId); setChoice({ itemId, variantId: next?.variants[0]?.id || '', optionIds: [], quantity: '1', notes: '' }); setMessage(''); };
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
    if (saved) { setChoice(old => ({ ...old, quantity: '1', notes: '', optionIds: [] })); setMessage(''); }
  };
  if (!available.length) return null;
  return <article className="card">
    <p className="eyebrow">{editing ? tx('EDIT SANDWICH', 'تعديل السندويش') : tx('SANDWICH MENU', 'قائمة السندويشات')}</p>
    <h2>{editing ? tx('Update your item', 'عدّل الصنف') : `${tx('Choose from', 'اختر من')} ${localizedName(room.restaurant)}`}</h2>
    <form className="stack" onSubmit={submit}>
      <label>{tx('Menu item', 'الصنف')}<select value={choice.itemId} onChange={event => selectItem(event.target.value)}>{room.restaurant.menu.categories.map(category => <optgroup label={localizedName(category)} key={category.id}>{available.filter(item => item.categoryId === category.id).map(item => <option value={item.id} key={item.id}>{localizedName(item)} · {money(item.basePriceMinor, room.restaurant.currency)}</option>)}</optgroup>)}</select></label>
      {localizedDescription(menuItem) && <p className="muted">{localizedDescription(menuItem)}</p>}
      {menuItem?.variants.length > 0 && <div><b className="field-label">{tx('Bread / size', 'الخبز أو الحجم')}</b><div className="choice-grid">{menuItem.variants.map(variant => <button type="button" className={choice.variantId === variant.id ? 'selected' : ''} key={variant.id} onClick={() => setChoice({ ...choice, variantId: variant.id })}>{localizedName(variant)}<small>{money(variant.priceMinor, room.restaurant.currency)}</small></button>)}</div></div>}
      {groups.map(group => <div key={group.id}><b className="field-label">{localizedName(group)} <small>{tx('Choose', 'اختر')} {group.minSelections}–{group.maxSelections}</small></b><div className="choice-grid">{group.options.map(option => <button type="button" className={choice.optionIds.includes(option.id) ? 'selected' : ''} key={option.id} onClick={() => toggleOption(group, option.id)}>{localizedName(option)}<small>{option.priceDeltaMinor ? `+ ${money(option.priceDeltaMinor, room.restaurant.currency)}` : tx('Included', 'مشمول')}</small></button>)}</div></div>)}
      <div className="form-grid two"><label>{tx('Quantity', 'الكمية')}<input type="number" min="1" max="99" value={choice.quantity} onChange={e => setChoice({ ...choice, quantity: e.target.value })} /></label><label>{tx('Notes', 'ملاحظات')}<input value={choice.notes} onChange={e => setChoice({ ...choice, notes: e.target.value })} placeholder={tx('No onions, extra sauce', 'بدون بصل، صوص إضافي')} /></label></div>
      {message && <p className="form-message">{message}</p>}
      <div className="hero-actions"><button className="primary">{editing ? tx('Update my order', 'تحديث طلبي') : tx('Add sandwich', 'إضافة سندويش')}</button>{editing && <button type="button" className="secondary" onClick={onCancel}>{tx('Cancel edit', 'إلغاء التعديل')}</button>}</div>
    </form>
  </article>;
}

function MemberOrderPanel({ room, cart, receipt, onSave, onSubmit }) {
  const [editingId, setEditingId] = useState('');
  const [custom, setCustom] = useState({ description: '', quantity: '1', notes: '' });
  const editing = cart.lines.find(line => line.id === editingId);
  const edit = line => {
    setEditingId(line.id);
    if (line.description) setCustom({ description: line.description, quantity: String(line.quantity), notes: line.notes || '' });
  };
  const cancel = () => { setEditingId(''); setCustom({ description: '', quantity: '1', notes: '' }); };
  const saveLine = async line => {
    const lines = editingId ? cart.lines.map(current => current.id === editingId ? line : current) : [...cart.lines, line];
    const result = await onSave(lines);
    if (result) cancel();
    return result;
  };
  const saveCustom = event => {
    event.preventDefault();
    return saveLine({ id: editingId || uid(), itemId: '', quantity: Number(custom.quantity), variantId: null, optionIds: [], notes: custom.notes.trim(), description: custom.description.trim(), unitPrice: null });
  };
  return <section className="stack order-editor">
    <article className="card early-order"><p className="eyebrow">YOUR SANDWICHES</p><h2>Add now. Change any time before totals.</h2><p className="muted">Your order stays editable before, during, and after the spin. Saving an edit keeps it in this room after refresh.</p></article>
    {room.restaurant.menu.items.length > 0 && (!editing || !editing.description) && <MenuItemForm key={editing?.id || 'new-menu'} room={room} editing={editing} onSave={saveLine} onCancel={cancel} />}
    {room.restaurant.openOrdering && (!editing || editing.description) && <article className="card"><p className="eyebrow">{editing ? 'EDIT CUSTOM ITEM' : 'CUSTOM SANDWICH'}</p><h2>{editing ? 'Update your item' : 'Add an item one by one'}</h2><form className="item-form" onSubmit={saveCustom}><label>Food item<input value={custom.description} onChange={e => setCustom({ ...custom, description: e.target.value })} placeholder="Falafel sandwich" required /></label><label>Qty<input inputMode="numeric" min="1" max="99" type="number" value={custom.quantity} onChange={e => setCustom({ ...custom, quantity: e.target.value })} required /></label><label className="notes">Notes / extras<input value={custom.notes} onChange={e => setCustom({ ...custom, notes: e.target.value })} placeholder="No onions, extra sauce" /></label><div className="hero-actions"><button className="primary">{editing ? 'Update my order' : 'Add custom item'}</button>{editing && <button className="secondary" type="button" onClick={cancel}>Cancel edit</button>}</div></form></article>}
    <article className="card"><div className="section-title compact"><div><p className="eyebrow">MY ORDER</p><h2>{cart.lines.length ? `${cart.lines.length} item${cart.lines.length === 1 ? '' : 's'}` : 'Nothing added yet'}</h2></div>{receipt && <b>{receipt.totalText}</b>}</div>{cart.lines.map(line => <OrderLine key={line.id} label={cartLineDescription(room, line)} line={line} currency={room.restaurant.currency} onEdit={() => edit(line)} onRemove={() => onSave(cart.lines.filter(value => value.id !== line.id))} />)}<button className="primary wide" onClick={onSubmit}>{cart.submitted ? 'Save my updated order' : cart.lines.length ? 'Submit my food order' : 'No food this time'}</button>{cart.submitted && <p className="success-message">✓ Saved. You can still edit and submit changes before totals are reviewed.</p>}</article>
  </section>;
}

function FeeEditor({ room, data }) {
  const currency = room.restaurant.currency;
  const [fees, setFees] = useState({ delivery: minorInput(room.fees.delivery, currency), service: minorInput(room.fees.service, currency), discount: minorInput(room.fees.discount, currency), proportionalDelivery: room.fees.proportionalDelivery });
  const [message, setMessage] = useState('');
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      await data.send('SET_FEES', { fees: { delivery: amount(fees.delivery || '0', currency), service: amount(fees.service || '0', currency), discount: amount(fees.discount || '0', currency), proportionalDelivery: fees.proportionalDelivery }, text: 'Updated delivery, service, and discount allocation' }, room.id);
    } catch (error) { setMessage(error.message); }
  };
  return <article className="card"><p className="eyebrow">FEES & EXACT SPLIT</p><h2>Cover the complete restaurant bill</h2><p className="muted">Food Run allocates every minor unit so all member receipts equal the final food, delivery, service, tax, and discount total.</p><form className="stack" onSubmit={save}><div className="form-grid three"><label>Delivery fee<input inputMode="decimal" value={fees.delivery} onChange={e => setFees({ ...fees, delivery: e.target.value })} /></label><label>Service fee<input inputMode="decimal" value={fees.service} onChange={e => setFees({ ...fees, service: e.target.value })} /></label><label>Discount<input inputMode="decimal" value={fees.discount} onChange={e => setFees({ ...fees, discount: e.target.value })} /></label></div><label className="check"><input type="checkbox" checked={fees.proportionalDelivery} onChange={e => setFees({ ...fees, proportionalDelivery: e.target.checked })} /> Split delivery by food total <span>Turn off for an equal split between everyone with food.</span></label>{message && <p className="form-message">{message}</p>}<button className="secondary">Update fees and reopen totals</button></form></article>;
}

function RestaurantOrderCard({ room, receipts, data, finish = false }) {
  const [copied, setCopied] = useState(false);
  const orderText = combinedOrderText(room, receipts);
  const copy = async () => { await copyText(orderText); setCopied(true); setTimeout(() => setCopied(false), 2500); };
  const contact = room.restaurant.contact.phoneE164 || room.restaurant.contact.whatsappE164;
  const whatsApp = `https://wa.me/?text=${encodeURIComponent(orderText)}`;
  return <article className="card restaurant-order-card"><p className="eyebrow">SELECTED TO ORDER</p><div className="selected-payer"><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><div><h2>{room.members.find(member => member.id === room.payerId)?.name}</h2><p>Collects the final list, places the order, and confirms payments.</p></div></div><pre>{orderText}</pre><div className="hero-actions"><button className="primary" type="button" onClick={copy}>{copied ? '✓ Copied — paste to restaurant' : finish ? 'Finish ordering & copy list' : 'Copy restaurant-ready list'}</button><a className="secondary action-link" href={whatsApp} target="_blank" rel="noreferrer">Share via WhatsApp</a>{contact && <a className="secondary action-link" href={`tel:${contact.replace(/[^+\d]/g, '')}`}>Call restaurant</a>}</div><p className="fine">WhatsApp opens the prepared order; choose the restaurant chat, a group, or another contact.</p></article>;
}

function ReceiptCard({ room, receipt, own = false }) {
  return <article className={`receipt-card ${own ? 'own' : ''}`}><div className="receipt-heading"><span><b>{receipt.name}{own ? ' · You' : ''}</b><small>{receipt.lines.length} line{receipt.lines.length === 1 ? '' : 's'}</small></span><strong>{receipt.totalText}</strong></div>{receipt.lines.map((line, index) => <OrderLine key={`${line.description}:${index}`} line={line} currency={receipt.currency} />)}<div className="fee-breakdown"><span>Food <b>{money(receipt.food, receipt.currency)}</b></span><span>Delivery <b>{money(receipt.delivery, receipt.currency)}</b></span><span>Service / adjustment <b>{money(receipt.service, receipt.currency)}</b></span><span>Discount <b>− {money(receipt.discount, receipt.currency)}</b></span><span>Tax <b>{money(receipt.tax, receipt.currency)}</b></span></div><div className="receipt-balance"><span>Paid <b>{money(receipt.paid, receipt.currency)}</b></span><span>{receipt.balance < 0 ? 'Refund due' : 'To pay'} <strong>{money(Math.abs(receipt.balance), receipt.currency)}</strong></span></div></article>;
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
  return <article className="card wallet-card"><p className="eyebrow">{payer ? 'ROOM WALLET' : 'MY WALLET'}</p><h2>{payer ? 'Every share in one place' : 'Your order and payment'}</h2><div className="progress-money wallet-summary">{payer ? <><span><small>Restaurant total</small><b>{money(restaurantTotal, currency)}</b></span><span><small>Your own share</small><b>{money(receipts.find(receipt => receipt.memberId === room.payerId)?.total || 0, currency)}</b></span><span><small>Confirmed from others</small><b>{money(confirmed, currency)}</b></span><span><small>Members still owe</small><b>{money(remaining, currency)}</b></span>{refunds > 0 && <span><small>Refunds you owe</small><b>{money(refunds, currency)}</b></span>}</> : <><span><small>My order</small><b>{money(own.total, currency)}</b></span><span><small>Confirmed paid</small><b>{money(own.paid, currency)}</b></span><span><small>{own.balance < 0 ? 'Owed back to me' : 'I need to pay'}</small><b>{money(Math.abs(own.balance), currency)}</b></span></>}</div>
    {payer && <div className="wallet-people">{receipts.map(receipt => { const claim = pending.find(transfer => transfer.memberId === receipt.memberId); return <div className="transfer-row" key={receipt.memberId}><span><b>{receipt.name}{receipt.memberId === me.id ? ' · You' : ''}</b><small>Order {money(receipt.total, currency)} · confirmed {money(receipt.paid, currency)}{claim ? ` · ${money(claim.amount, currency)} awaiting confirmation` : ''}</small></span><strong>{receipt.memberId === room.payerId ? 'Own share' : receipt.balance < 0 ? `Refund ${money(-receipt.balance, currency)}` : receipt.balance === 0 ? 'Settled' : `Owes ${money(receipt.balance, currency)}`}</strong></div>; })}</div>}
    {!payer && pending.some(transfer => transfer.memberId === me.id) && <p className="success-message">Your payment is marked sent and awaits confirmation from the selected payer.</p>}
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
  const declarePayment = event => { event.preventDefault(); data.send('DECLARE_TRANSFER', { amount: amount(payment.value, currency), text: payment.reference.trim() }, room.id); };
  const reject = transfer => data.send('REJECT_TRANSFER', { transferId: transfer.id, text: 'Payment was not received or the details do not match.' }, room.id);
  const refund = receipt => {
    const value = window.prompt(`Refund amount for ${receipt.name}`, minorInput(-receipt.balance, currency));
    if (!value) return;
    const reference = window.prompt('Refund reference or cash note');
    if (reference) data.send('DECLARE_REFUND', { memberId: receipt.memberId, amount: amount(value, currency), text: reference }, room.id);
  };
  return <>
    <WalletPanel room={room} receipts={reply.receipts} me={me} payer={payer} />
    <article className="card placed-banner"><p className="eyebrow">RESTAURANT STATUS</p><h2>{room.restaurantPaid ? 'Restaurant payment recorded' : 'Order announced as placed'}</h2><p>{room.restaurantReference}</p></article>
    <article className="card"><div className="section-title compact"><div><p className="eyebrow">RECEIPT</p><h2>{payer ? 'Complete order breakdown' : 'Your total, paid, and remaining'}</h2></div>{myReceipt && <button type="button" className="secondary" onClick={() => copyText(receiptText(room, myReceipt))}>Copy my receipt</button>}</div>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}{room.account && <div className="pay-to"><span>Pay to</span><b>{room.account.holder} · {room.account.bank}</b><code>{room.account.identifier}</code></div>}</article>
    {payer && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} />}
    {payer && <article className="card stack"><p className="eyebrow">RESTAURANT & FINAL BILL</p><h2>{money(total, currency)}</h2><p className="muted">Recording a payment tracks it in Food Run; it does not move money.</p>{!room.restaurantPaid && <button className="primary wide" onClick={() => data.send('PAY_RESTAURANT', { amount: total }, room.id)}>Confirm restaurant paid · {money(total, currency)}</button>}<form className="form-grid adjustment-form" onSubmit={event => { event.preventDefault(); const raw = adjustment.value.trim(); const minor = amount(raw.replace(/^-/, ''), currency) * (raw.startsWith('-') ? -1 : 1); data.send('ADJUST_BILL', { amount: minor, text: adjustment.reason.trim() }, room.id); }}><label>Final bill adjustment<input value={adjustment.value} onChange={e => setAdjustment({ ...adjustment, value: e.target.value })} placeholder="-5.00 or 5.00" /></label><label>Reason<input value={adjustment.reason} onChange={e => setAdjustment({ ...adjustment, reason: e.target.value })} placeholder="Restaurant discount" /></label><button className="secondary">Update final bill</button></form>{room.phase === 'PLACED' && <button className="secondary wide" onClick={() => data.send('FULFILL', {}, room.id)}>Food collected / delivered</button>}</article>}
    {!payer && myReceipt && room.restaurantPaid && myReceipt.balance > 0 && !ownPending && <article className="card notice-card"><p className="eyebrow">PAY YOUR SHARE</p><h2>{myReceipt.balanceText} remaining</h2><form className="stack" onSubmit={declarePayment}><label>Amount sent<input inputMode="decimal" value={payment.value} onChange={e => setPayment({ ...payment, value: e.target.value })} required /></label><label>Transfer reference / cash note<input value={payment.reference} onChange={e => setPayment({ ...payment, reference: e.target.value })} required /></label><button className="primary">I sent my payment</button></form></article>}
    {!payer && myReceipt?.balance === 0 && <article className="card settled-card"><span>✓</span><div><h2>Paid and settled</h2><p>Your selected orderer confirmed your payment.</p></div></article>}
    {room.billRevision > 1 && !room.adjustmentApprovals.includes(me.id) && <button className="primary wide" onClick={() => data.send('APPROVE_ADJUSTMENT', { expectedRevision: room.billRevision }, room.id)}>Approve revised final bill</button>}
    {room.transfers.length > 0 && <article className="card"><p className="eyebrow">PAYMENT ACTIVITY</p><h2>Sent and confirmed</h2>{room.transfers.map(transfer => { const member = room.members.find(value => value.id === transfer.memberId); const declared = String(transfer.status).toLowerCase() === 'declared'; return <div className="transfer-row" key={transfer.id}><span><b>{transfer.refund ? 'Refund' : 'Payment'} · {member?.name}</b><small>{transfer.reference} · {String(transfer.status).toLowerCase()}</small></span><strong>{money(transfer.amount, currency)}</strong>{declared && payer && !transfer.refund && <span className="transfer-actions"><button className="primary" onClick={() => data.send('CONFIRM_TRANSFER', { transferId: transfer.id }, room.id)}>Confirm received</button><button className="secondary" onClick={() => reject(transfer)}>Reject</button></span>}{declared && transfer.refund && transfer.memberId === me.id && <span className="transfer-actions"><button className="primary" onClick={() => data.send('CONFIRM_REFUND', { transferId: transfer.id }, room.id)}>Confirm refund</button><button className="secondary" onClick={() => reject(transfer)}>Reject</button></span>}</div>; })}</article>}
    {payer && room.restaurantPaid && reply.receipts.filter(receipt => receipt.balance < 0 && !pending.some(transfer => transfer.memberId === receipt.memberId)).map(receipt => <button className="secondary wide" key={receipt.memberId} onClick={() => refund(receipt)}>Record refund to {receipt.name} · {money(-receipt.balance, currency)}</button>)}
    {owner && room.phase === 'FULFILLED' && <article className="card complete-card"><h2>Finish this order</h2><p>{reply.progress?.canArchive ? 'Every payment and refund is settled.' : reply.progress?.archiveBlocker || 'Waiting for all payments to settle.'}</p><button className="primary wide" disabled={!reply.progress?.canArchive} onClick={() => data.send('ARCHIVE', { text: 'Order completed and settled' }, room.id)}>Complete and archive order</button></article>}
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
  return <section className="card room-invite"><div className="invite-qr"><QRCodeSVG value={link} size={164} level="M" marginSize={2} title={`Join ${room.name}`} /></div><div className="invite-details"><p className="eyebrow">INVITE TO THIS ROOM</p><h3>Scan, tap, or enter the code</h3><strong className="invite-code">{room.code}</strong><p className="muted">The link remembers this room’s internet or local server and opens the join screen with the code filled in.</p><div className="hero-actions"><button className="primary" onClick={() => copy(link, 'link')}>{copied === 'link' ? '✓ Link copied' : 'Copy invitation link'}</button><button className="secondary" onClick={() => copy(room.code, 'code')}>{copied === 'code' ? '✓ Code copied' : 'Copy code'}</button>{navigator.share && <button className="secondary" onClick={() => navigator.share({ title: `Join ${room.name} on Food Run`, text: `Room code: ${room.code}`, url: link })}>Share</button>}</div></div></section>;
}

function RoomScreen({ data, roomId, onBack }) {
  const reply = data.rooms[roomId], session = data.sessions[roomId], room = reply?.room;
  const [price, setPrice] = useState({ memberId: '', lineId: '', value: '' });
  const [reference, setReference] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  if (!room || !session) return <Page title="Connecting to room…" onBack={onBack}><div className="card empty"><div className="spinner" /><p>Waiting for the latest room state.</p></div></Page>;
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
  const startNextOrder = () => data.send('NEXT_ORDER', { restaurant: room.restaurant, restaurants: room.restaurantOptions || [room.restaurant], expectedNames: [], flag: room.deliveryMode, destination: room.destination, deadline: 0, fees: room.fees }, room.id);
  return <Page title={room.name} subtitle={`Order #${room.orderNumber} · ${phaseLabel[room.phase]} · code ${room.code}`} onBack={onBack} actions={<span className={`status ${data.online[room.id] ? 'live' : ''}`}>{data.online[room.id] ? '● Live' : 'Offline'}</span>}>
    <OrderProgress room={room} receipts={reply.receipts} />
    <div className="room-layout">
      <section className="room-main stack">
        {['PREPARING_SPIN','SPINNING','ACCEPTING'].includes(room.phase) && <article className="card selection-card">
          {room.phase === 'PREPARING_SPIN' && <><div className="spinner" /><h2>Getting everyone in sync…</h2><p>Every participating device is joining the live selection.</p></>}
          {room.phase === 'SPINNING' && <><p className="eyebrow">LIVE SELECTION</p><h2>Who will order?</h2><LiveSelectionWheel key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active /></>}
          {room.phase === 'ACCEPTING' && <><LiveSelectionWheel key={room.spin.id} spin={room.spin} members={room.members} serverTime={reply.serverTime} active={false} /><WinnerReveal winner={winner} selected={selected} me={me} room={room} data={data} /></>}
        </article>}
        {!me.approved && <article className="card notice-card"><h2>Waiting for approval</h2><p>The organizer will approve your request before you can join this order.</p></article>}
        {me.approved && room.phase === 'LOBBY' && <><article className="card"><div className="section-title compact"><div><p className="eyebrow">WHO’S IN?</p><h2>Join today’s order</h2></div><span>{orderingMembers.length}</span></div>{!me.guest && <div className="hero-actions"><button className={me.participating ? 'secondary' : 'primary'} onClick={() => data.send('PARTICIPATE', { flag: !me.participating }, room.id)}>{me.participating ? 'Skip this order' : 'Join this order'}</button></div>}<p className="muted">Once approved and joined, you are ready automatically. Add sandwiches now; no extra ready step is needed.</p>{owner && !room.restaurantPollOpen && <button className="primary wide" disabled={!orderingMembers.length} onClick={() => data.send('PREPARE_SPIN', {}, room.id)}>Spin to choose the payer</button>}</article>{room.restaurantPollOpen && <RestaurantPoll room={room} me={me} owner={owner} data={data} />}</>}
        {me.approved && !me.guest && me.participating && orderingOpen && <MemberOrderPanel key={`${room.orderNumber}:${room.restaurant.id}`} room={room} cart={myCart} receipt={myReceipt} onSave={saveCart} onSubmit={() => data.send('SUBMIT_CART', { expectedRevision: myCart.revision }, room.id)} />}
        {me.approved && room.phase === 'COLLECTING' && <>
          {payer && <article className="card"><p className="eyebrow">PRICE CUSTOM ITEMS</p><h2>Menu prices are already saved</h2><p className="muted">Only open-order items need a unit price.</p>{room.carts.flatMap(cart => cart.lines.filter(line => line.description).map(line => ({ cart, line }))).map(({ cart, line }) => <OrderLine key={line.id} label={`${room.members.find(member => member.id === cart.memberId)?.name} · ${line.description}`} line={line} currency={room.restaurant.currency} canPrice onPrice={() => setPrice({ memberId: cart.memberId, lineId: line.id, value: line.unitPrice == null ? '' : minorInput(line.unitPrice, room.restaurant.currency) })} />)}{price.lineId && <form className="price-form" onSubmit={async e => { e.preventDefault(); const result = await data.send('PRICE_ITEM', { memberId: price.memberId, text: price.lineId, amount: amount(price.value, room.restaurant.currency) }, room.id); if (result) setPrice({ memberId: '', lineId: '', value: '' }); }}><label>Unit price<input autoFocus inputMode="decimal" value={price.value} onChange={e => setPrice({ ...price, value: e.target.value })} /></label><button className="primary">Save price</button></form>}</article>}
          {payer && !room.account && <article className="card notice-card"><h2>Share your receiving details</h2><p>Use the Aani or bank details saved in your profile so people know where to pay.</p><button className="primary" disabled={!data.home.profile.payment} onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>{data.home.profile.payment ? 'Share saved payment method' : 'Add payment details in profile'}</button></article>}
          {(owner || payer) && <FeeEditor key={`${room.quoteRevision}:${room.fees.delivery}:${room.fees.service}:${room.fees.discount}`} room={room} data={data} />}
          {payer && reply.receipts.some(receipt => receipt.lines.length) && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} finish />}
          {owner && <article className="card next-step"><h2>Finish collecting</h2><p>{reply.progress?.canReview ? 'Every person submitted, every custom item is priced, and payment details are ready.' : reply.progress?.reviewBlocker || 'Waiting for everyone to submit.'}</p><button className="primary wide" disabled={!reply.progress?.canReview} onClick={() => data.send('REVIEW', {}, room.id)}>Review everyone’s totals</button></article>}
        </>}
        {room.phase === 'REVIEW' && <>
          <WalletPanel room={room} receipts={reply.receipts} me={me} payer={payer} />
          <article className="card"><p className="eyebrow">CHECK YOUR SHARE</p><h2>Confirm totals</h2>{reply.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={receipt.memberId} />)}{myCart.confirmedQuote !== room.quoteRevision ? <button className="primary wide" onClick={() => data.send('CONFIRM_QUOTE', { expectedRevision: room.quoteRevision }, room.id)}>Confirm my total and recipient</button> : <p className="success-message">✓ Your total and payment recipient are confirmed.</p>}{payer && <RestaurantOrderCard room={room} receipts={reply.receipts} data={data} finish />}{payer && <form className="place-form" onSubmit={e => { e.preventDefault(); data.send('PLACE', { text: reference }, room.id); }}><label>Restaurant confirmation / ETA<input value={reference} onChange={e => setReference(e.target.value)} placeholder="Confirmed · ready in 30 minutes" required /></label><button className="primary" disabled={!allConfirmed}>Announce order placed</button></form>}{!allConfirmed && <p className="muted">Waiting for {orderingMembers.filter(member => room.carts.find(cart => cart.memberId === member.id)?.confirmedQuote !== room.quoteRevision).map(member => member.name).join(', ')} to confirm.</p>}</article>
          {(owner || payer) && <FeeEditor key={`${room.quoteRevision}:${room.fees.delivery}:${room.fees.service}:${room.fees.discount}`} room={room} data={data} />}
        </>}
        {['PLACED','FULFILLED'].includes(room.phase) && <SettlementPanel room={room} reply={reply} me={me} owner={owner} payer={payer} data={data} />}
        {['ARCHIVED','CANCELLED'].includes(room.phase) && <article className="card empty"><span>✓</span><h2>{room.phase === 'ARCHIVED' ? 'Order complete' : 'Order cancelled'}</h2><p>The room, final receipts, restaurant, and prices stay saved for the next meal.</p>{owner && <button className="primary" onClick={startNextOrder}>Start next order</button>}</article>}
        {reply.history?.length > 0 && <details className="card history-card"><summary>Past orders and receipts</summary>{reply.history.map(order => <div className="past-order" key={order.number}><div><b>Order #{order.number} · {order.restaurantName}</b><small>{new Date(order.completedAt).toLocaleString()}</small></div>{order.receipts.map(receipt => <ReceiptCard room={room} receipt={receipt} own={receipt.memberId === me.id} key={`${order.number}:${receipt.memberId}`} />)}</div>)}</details>}
      </section>
      <aside className="room-side stack">
        <RoomInviteCard room={room} hub={data.hub} />
        {room.payerId && <section className="card payer-side"><p className="eyebrow">ORDERING PERSON</p><span className="avatar initials">{initials(room.members.find(member => member.id === room.payerId)?.name)}</span><h3>{room.members.find(member => member.id === room.payerId)?.name}</h3><p>{payer ? 'You are placing the restaurant order and collecting payments.' : 'Selected to place the order and collect payments.'}</p></section>}
        <section className="card"><div className="section-title compact"><div><p className="eyebrow">AT THE TABLE</p><h3>{room.members.filter(member => !member.removed).length} people</h3></div>{owner && <button className="icon-button" onClick={() => setInviteOpen(!inviteOpen)}>＋</button>}</div>{room.members.filter(member => !member.removed).map(member => <div className="member" key={member.id}><span className="avatar initials small">{initials(member.name)}</span><span><b>{member.name}{member.id === me.id ? ' · You' : ''}</b><small>{member.id === room.payerId ? 'Selected to order' : member.id === room.ownerId ? 'Organizer' : !member.approved ? 'Waiting for approval' : room.carts.find(cart => cart.memberId === member.id)?.submitted ? 'Food submitted' : member.participating ? 'Joined automatically' : 'Skipping'}</small></span>{owner && !member.approved && <button className="link" onClick={() => data.send('APPROVE', { memberId: member.id }, room.id)}>Approve</button>}</div>)}{inviteOpen && <div className="invite-list"><p>Invite registered people</p>{data.home.people.map(person => <button className="person-button" key={person.userId} onClick={() => data.send('IDENTITY', { identity: { action: 'INVITE', userId: person.userId } }, room.id)}><span className="avatar initials small">{initials(person.name)}</span>{person.name}<b>Invite</b></button>)}</div>}</section>
        <section className="card room-tools"><p className="eyebrow">ROOM TOOLS</p><button className="secondary wide" onClick={saveCurrentRestaurant}>Save restaurant & prices</button>{payer && ['COLLECTING','REVIEW'].includes(room.phase) && data.home.profile.payment && <button className="secondary wide" onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>Use saved payment details</button>}{owner && ['LOBBY','COLLECTING','REVIEW','ACCEPTING'].includes(room.phase) && <button className="link danger wide" onClick={() => { const reason = window.prompt('Why are you cancelling this order?'); if (reason) data.send('CANCEL', { text: reason }, room.id); }}>Cancel today’s order</button>}</section>
        {selected && <section className="card winner-mini"><p className="eyebrow">SELECTED</p><h3>{winner.name}</h3><p>Sandwich entry remains open while the payer confirms.</p></section>}
      </aside>
    </div>
  </Page>;
}

async function adminRequest(path, token = '', body) {
  const response = await fetch(`${PUBLIC_API_URL}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json().catch(() => ({}));
  if (!response.ok) throw Error(value.error || 'Admin request failed.');
  return value;
}

function AdminApp() {
  const [token, setToken] = useState(() => sessionStorage.getItem('foodrun-admin-token') || '');
  const [credentials, setCredentials] = useState({ username: 'Karam', password: '' });
  const [dashboard, setDashboard] = useState(null);
  const [tab, setTab] = useState('overview');
  const [message, setMessage] = useState('');
  const refresh = async activeToken => {
    try { setDashboard(await adminRequest('/admin/dashboard', activeToken || token)); setMessage(''); }
    catch (error) { setMessage(error.message); if (/expired|sign in/i.test(error.message)) { sessionStorage.removeItem('foodrun-admin-token'); setToken(''); } }
  };
  useEffect(() => { if (token) refresh(token); }, [token]);
  const login = async event => {
    event.preventDefault(); setMessage('');
    try { const result = await adminRequest('/admin/login', '', credentials); sessionStorage.setItem('foodrun-admin-token', result.token); setToken(result.token); setCredentials(old => ({ ...old, password: '' })); }
    catch (error) { setMessage(error.message); }
  };
  const mutate = async (path, body) => { try { await adminRequest(path, token, body); await refresh(); } catch (error) { setMessage(error.message); } };
  const saveRestaurant = restaurant => mutate('/admin/restaurant', { action: 'save', restaurant });
  if (!token) return <main className="center-shell admin-login"><section className="card setup-card"><div className="brand-mark">FR</div><p className="eyebrow">FOOD RUN ADMIN</p><h1>Administration</h1><p className="muted">Server-protected access for users, rooms, wallets, orders, and restaurant menus.</p><form className="stack" onSubmit={login}><label>Admin user<input value={credentials.username} onChange={event => setCredentials({ ...credentials, username: event.target.value })} autoComplete="username" required /></label><label>Password<input type="password" value={credentials.password} onChange={event => setCredentials({ ...credentials, password: event.target.value })} autoComplete="current-password" required /></label>{message && <p className="form-message">{message}</p>}<button className="primary">Sign in</button></form></section></main>;
  if (!dashboard) return <div className="splash"><div className="brand-mark">FR</div><div className="spinner" /><p>{message || 'Loading admin data…'}</p></div>;
  const totals = dashboard.rooms.reduce((sum, room) => sum + room.totalMinor, 0);
  return <main className="admin-shell">
    <header className="admin-topbar"><div><span className="brand-mark">FR</span><b>Food Run Admin</b></div><div><button className="secondary" onClick={() => refresh()}>Refresh</button><button className="link" onClick={() => { sessionStorage.removeItem('foodrun-admin-token'); setToken(''); }}>Sign out</button></div></header>
    <nav className="admin-tabs">{['overview','users','rooms','orders','wallets','restaurants','settings'].map(value => <button className={tab === value ? 'active' : ''} onClick={() => setTab(value)} key={value}>{value[0].toUpperCase() + value.slice(1)}</button>)}</nav>
    {message && <div className="banner error">{message}</div>}
    <section className="admin-content">
      {tab === 'overview' && <><div className="admin-metrics"><article><small>Registered users</small><b>{dashboard.users.length}</b></article><article><small>Rooms</small><b>{dashboard.rooms.length}</b></article><article><small>Saved orders</small><b>{dashboard.archivedOrders.length}</b></article><article><small>Current order value</small><b>{money(totals, 'AED')}</b></article></div><div className="card"><h2>Current activity</h2>{dashboard.rooms.slice(0, 10).map(room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} key={room.id} />)}</div></>}
      {tab === 'users' && <div className="card admin-table"><h2>Users</h2>{dashboard.users.map(user => <div className="admin-row admin-user-row" key={user.id}><span><b>{user.name || 'Incomplete profile'}</b><small>{user.phone || user.id} · {user.language === 'ar' ? 'Arabic' : 'English'} · {user.discoverable ? 'Discoverable' : 'Private'}</small>{user.paymentIdentifier && <small>{user.paymentMethod === 'AANI' ? 'Aani' : user.paymentBank || 'Bank'} · {user.paymentHolder} · {user.paymentIdentifier}</small>}</span><span className={`status ${user.disabled ? '' : 'live'}`}>{user.disabled ? 'Disabled' : 'Active'}</span><button className={user.disabled ? 'secondary' : 'link danger'} onClick={() => mutate('/admin/user', { userId: user.id, disabled: !user.disabled })}>{user.disabled ? 'Enable' : 'Disable'}</button></div>)}</div>}
      {tab === 'rooms' && <div className="card"><h2>Live and saved rooms</h2>{dashboard.rooms.map(room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} key={room.id} />)}</div>}
      {tab === 'orders' && <div className="card"><h2>Order history</h2>{dashboard.archivedOrders.length ? dashboard.archivedOrders.map((room, index) => <AdminRoomRow room={room} key={`${room.id}-${room.orderNumber}-${index}`} />) : <p className="muted">No archived orders yet.</p>}</div>}
      {tab === 'wallets' && <div className="card"><h2>Wallet and settlement totals</h2>{dashboard.rooms.map(room => <section className="admin-wallet-room" key={room.id}><div className="admin-row wallet-admin"><span><b>{room.name} · #{room.orderNumber}</b><small>{room.payer ? `Payer: ${room.payer}` : 'Payer not selected'}</small></span><span><small>Total</small><b>{money(room.totalMinor, room.currency)}</b></span><span><small>Confirmed</small><b>{money(room.confirmedPaidMinor, room.currency)}</b></span><span><small>Outstanding</small><b>{money(room.outstandingMinor, room.currency)}</b></span></div>{room.wallets.map(wallet => <div className="admin-wallet-person" key={wallet.memberId}><b>{wallet.name}</b><span>Order {money(wallet.totalMinor, room.currency)}</span><span>Paid {money(wallet.paidMinor, room.currency)}</span><span>{wallet.balanceMinor < 0 ? 'Refund' : 'Due'} {money(Math.abs(wallet.balanceMinor), room.currency)}</span></div>)}</section>)}</div>}
      {tab === 'restaurants' && <AdminRestaurants restaurants={dashboard.restaurants} save={saveRestaurant} remove={restaurantId => mutate('/admin/restaurant', { action: 'delete', restaurantId })} />}
      {tab === 'settings' && <AdminSettingsPanel settings={dashboard.settings} save={settings => mutate('/admin/settings', settings)} />}
    </section>
  </main>;
}

function AdminRoomRow({ room, onCancel }) {
  const active = !['ARCHIVED','CANCELLED'].includes(room.phase);
  return <div className="admin-row"><span><b>{room.name} · {room.code}</b><small>{room.restaurant} · Order #{room.orderNumber} · {room.members} members</small></span><span className={`status ${active ? 'live' : ''}`}>{room.phase}</span><b>{money(room.totalMinor, room.currency)}</b>{onCancel && active && <button className="link danger" onClick={onCancel}>Cancel room</button>}</div>;
}

function AdminMoneyInput({ value, onCommit, label }) {
  const [text, setText] = useState(() => minorInput(value));
  useEffect(() => setText(minorInput(value)), [value]);
  const commit = () => {
    try { const next = amount(text || '0', 'AED'); onCommit(next); setText(minorInput(next)); }
    catch { setText(minorInput(value)); }
  };
  return <input inputMode="decimal" value={text} onChange={event => setText(event.target.value)} onBlur={commit} onKeyDown={event => { if (event.key === 'Enter') event.currentTarget.blur(); }} aria-label={label} />;
}

function AdminRestaurants({ restaurants, save, remove }) {
  const [editing, setEditing] = useState(restaurants[0] ? clone(restaurants[0]) : blankRestaurant());
  useEffect(() => { const latest = restaurants.find(value => value.id === editing.id); if (latest) setEditing(clone(latest)); }, [restaurants]);
  const changeItem = (id, key, value) => setEditing(old => ({ ...old, menu: { ...old.menu, items: old.menu.items.map(item => item.id === id ? { ...item, [key]: value } : item) } }));
  const changeVariant = (itemId, variantId, key, value) => setEditing(old => ({ ...old, menu: { ...old.menu, items: old.menu.items.map(item => item.id === itemId ? { ...item, variants: item.variants.map(variant => variant.id === variantId ? { ...variant, [key]: value } : variant) } : item) } }));
  const changeOption = (groupId, optionId, key, value) => setEditing(old => ({ ...old, menu: { ...old.menu, optionGroups: old.menu.optionGroups.map(group => group.id === groupId ? { ...group, options: group.options.map(option => option.id === optionId ? { ...option, [key]: value } : option) } : group) } }));
  const addItem = () => {
    let categories = editing.menu.categories;
    if (!categories.length) categories = [{ id: uid(), name: 'Sandwiches', nameAr: 'السندويشات', sortOrder: 0 }];
    setEditing(old => ({ ...old, openOrdering: false, menu: { ...old.menu, categories, items: [...old.menu.items, { id: uid(), categoryId: categories[0].id, name: 'New item', nameAr: 'صنف جديد', description: '', descriptionAr: '', basePriceMinor: 0, available: true, variants: [], optionGroupIds: [] }] } }));
  };
  return <div className="admin-restaurant-layout">
    <aside className="card admin-restaurant-list"><button type="button" className="primary wide" onClick={() => setEditing(blankRestaurant())}>Add restaurant</button>{restaurants.map(restaurant => <button type="button" className={editing.id === restaurant.id ? 'active' : ''} onClick={() => setEditing(clone(restaurant))} key={restaurant.id}><b>{restaurant.name}</b><small>{restaurant.menu.items.length} items</small></button>)}</aside>
    <section className="card stack">
      <div className="section-title compact"><h2>Restaurant and menu</h2>{restaurants.some(value => value.id === editing.id) && <button type="button" className="link danger" onClick={() => remove(editing.id)}>Delete restaurant</button>}</div>
      <div className="form-grid two"><label>English name<input value={editing.name} onChange={event => setEditing({ ...editing, name: event.target.value })} /></label><label>Arabic name<input dir="rtl" value={editing.nameAr || ''} onChange={event => setEditing({ ...editing, nameAr: event.target.value })} /></label><label>UAE phone<input value={editing.contact.phoneE164 || ''} onChange={event => setEditing({ ...editing, contact: { ...editing.contact, phoneE164: event.target.value || null } })} /></label><label>Default delivery fee · AED<AdminMoneyInput value={editing.pricing.defaultDeliveryFeeMinor} onCommit={value => setEditing({ ...editing, pricing: { ...editing.pricing, defaultDeliveryFeeMinor: value } })} label="Default delivery fee in AED" /></label></div>
      <div className="section-title compact"><h3>Menu prices</h3><button type="button" className="secondary" onClick={addItem}>Add menu item</button></div>
      {editing.menu.items.map(item => <section className="admin-menu-block" key={item.id}><div className="admin-menu-item"><input value={item.name} onChange={event => changeItem(item.id, 'name', event.target.value)} aria-label="English item name" /><input dir="rtl" value={item.nameAr || ''} onChange={event => changeItem(item.id, 'nameAr', event.target.value)} aria-label="Arabic item name" /><AdminMoneyInput value={item.basePriceMinor} onCommit={value => changeItem(item.id, 'basePriceMinor', value)} label={`${item.name} base price in AED`} /><button type="button" className="link danger" onClick={() => setEditing(old => ({ ...old, menu: { ...old.menu, items: old.menu.items.filter(value => value.id !== item.id) } }))}>Remove</button></div>{item.variants.map(variant => <div className="admin-price-row" key={variant.id}><small>Variant</small><input value={variant.name} onChange={event => changeVariant(item.id, variant.id, 'name', event.target.value)} /><input dir="rtl" value={variant.nameAr || ''} onChange={event => changeVariant(item.id, variant.id, 'nameAr', event.target.value)} /><AdminMoneyInput value={variant.priceMinor} onCommit={value => changeVariant(item.id, variant.id, 'priceMinor', value)} label={`${variant.name} price in AED`} /></div>)}</section>)}
      {editing.menu.optionGroups.flatMap(group => group.options.map(option => <div className="admin-price-row" key={option.id}><small>{group.name}</small><input value={option.name} onChange={event => changeOption(group.id, option.id, 'name', event.target.value)} /><input dir="rtl" value={option.nameAr || ''} onChange={event => changeOption(group.id, option.id, 'nameAr', event.target.value)} /><AdminMoneyInput value={option.priceDeltaMinor} onCommit={value => changeOption(group.id, option.id, 'priceDeltaMinor', value)} label={`${option.name} price in AED`} /></div>))}
      <button type="button" className="primary wide" onClick={() => save(normalizeRestaurant(editing))}>Save restaurant and all prices</button>
    </section>
  </div>;
}

function AdminSettingsPanel({ settings, save }) {
  const [form, setForm] = useState(settings);
  return <section className="card stack admin-settings"><h2>Website options</h2><label className="check"><input type="checkbox" checked={form.registrationsEnabled} onChange={event => setForm({ ...form, registrationsEnabled: event.target.checked })} /> Allow new user registration</label><label className="check"><input type="checkbox" checked={form.roomCreationEnabled} onChange={event => setForm({ ...form, roomCreationEnabled: event.target.checked })} /> Allow new room creation</label><label>Maintenance message<textarea value={form.maintenanceMessage} onChange={event => setForm({ ...form, maintenanceMessage: event.target.value })} maxLength="500" /></label><button className="primary" onClick={() => save(form)}>Save website options</button></section>;
}

function FoodRunClient() {
  const data = useFoodRun();
  const [siteConfig, setSiteConfig] = useState({ registrationsEnabled: true, roomCreationEnabled: true, maintenanceMessage: '' });
  const inviteCode = useMemo(() => new URLSearchParams(window.location.search).get('room')?.replace(/\D/g, '').slice(0, 6) || '', []);
  const [page, setPage] = useState(inviteCode ? 'join' : 'home');
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
    if (restoredRoom.current || inviteCode || !data.user || !data.hub) return;
    let saved;
    try { saved = JSON.parse(localStorage.getItem(ACTIVE_ROOM_KEY) || 'null'); } catch { saved = null; }
    if (saved?.userId === data.user.uid && saved?.hub === data.hub && data.sessions[saved.roomId]) {
      setRoomId(saved.roomId); setPage('room'); restoredRoom.current = true;
    } else if (Object.keys(data.sessions).length > 0) restoredRoom.current = true;
  }, [data.user, data.hub, data.sessions, inviteCode]);
  const alerts = useMemo(() => <>{siteConfig.maintenanceMessage && <div className="banner notice">{siteConfig.maintenanceMessage}</div>}{data.error && <div className="banner error" role="alert">{data.error}{data.hasPending && <button onClick={data.retry}>Retry saved request</button>}</div>}{data.notice && <div className="banner notice">{data.notice}<button aria-label="Close" onClick={() => data.setNotice('')}>×</button></div>}</>, [siteConfig.maintenanceMessage, data.error, data.hasPending, data.notice]);
  if (!data.authReady || (data.user && data.hub && !data.home && !data.error)) return <><div className="splash"><div className="brand-mark">FR</div><div className="spinner" /><p>Setting the table…</p></div>{alerts}</>;
  if (!data.user) return <AuthScreen ready={data.authReady} allowRegistration={siteConfig.registrationsEnabled} />;
  if (!data.hub || (!data.home && data.error)) return <><HubScreen current={data.hub} connect={data.connect} error={data.error} />{alerts}</>;
  const home = data.home;
  const profileMissing = !home.profile.name || !home.profile.phone;
  let content;
  if (page === 'downloads') content = <Page title="Get Food Run" subtitle="Install the mobile app and keep your table close." onBack={() => setPage('home')}><AppDownloads /></Page>;
  else if (page === 'profile' || profileMissing) content = <ProfileScreen profile={home.profile} busy={data.busy} send={data.send} onBack={() => setPage('home')} />;
  else if (page === 'restaurants') content = <RestaurantLibraryScreen onBack={() => setPage('home')} />;
  else if (page === 'create' || page === 'join') content = <CreateRoom data={data} mode={page} inviteCode={inviteCode} onBack={() => setPage('home')} openRoom={openRoom} />;
  else if (page === 'room') content = <RoomScreen data={data} roomId={roomId} onBack={closeRoom} />;
  else content = <Home data={data} setPage={setPage} openRoom={openRoom} allowRoomCreation={siteConfig.roomCreationEnabled} />;
  return <>{alerts}{content}<footer><span>Food Run</span><button onClick={() => setPage('restaurants')}>Restaurants & menus</button><button onClick={() => setPage('downloads')}>Get the apps</button><button onClick={() => setPage('profile')}>Profile</button><button onClick={() => data.connect('')}>Switch room server</button><button onClick={() => signOut(auth)}>Sign out</button></footer></>;
}

export default function FoodRunApp() {
  const [language, setLanguage] = useState(uiLanguage);
  uiLanguage = language;
  updateLanguage = value => { localStorage.setItem(LANGUAGE_KEY, value); setLanguage(value); };
  useEffect(() => {
    document.documentElement.lang = language;
    document.documentElement.dir = language === 'ar' ? 'rtl' : 'ltr';
  }, [language]);
  return window.location.pathname.replace(/\/+$/, '') === '/admin' ? <AdminApp /> : <FoodRunClient />;
}
