import { canAccessAdmin } from './adminAccess';
import { t } from './i18n.js';
import MenuEditor, { MenuMoneyInput } from './MenuEditor';
import { useState, useEffect } from 'react';
import { money, amount } from './client';
import { PUBLIC_API_URL, normalizeRestaurant, blankRestaurant, clone, uid, minorInput, LanguageToggle } from './FoodRunApp';

async function adminRequest(path, user, body) {
  if (!canAccessAdmin(user)) throw Error(t('This account cannot access administration.'));
  const token = await user.getIdToken();
  const response = await fetch(`${PUBLIC_API_URL}${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }), ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const value = await response.json().catch(() => ({}));
  if (!response.ok) throw Error(value.error || t("Admin request failed."));
  return value;
}

export default function AdminApp({ language, user, onBack }) {
  const [dashboard, setDashboard] = useState(null);
  const [tab, setTab] = useState('overview');
  const [message, setMessage] = useState('');
  const refresh = async () => {
    try { setDashboard(await adminRequest('/admin/dashboard', user)); setMessage(''); }
    catch (error) { setDashboard(null); setMessage(error.message); }
  };
  useEffect(() => { refresh(); }, [user]);
  const mutate = async (path, body) => { try { await adminRequest(path, user, body); await refresh(); } catch (error) { setMessage(error.message); } };
  const saveRestaurant = restaurant => mutate('/admin/restaurant', { action: 'save', restaurant });
  if (!dashboard) return <div className="splash"><div className="brand-mark">FR</div><p role="status">{message || t("Loading admin data…")}</p>{message && <button className="secondary" onClick={refresh}>{t("Retry")}</button>}<button className="link" onClick={onBack}>{t("Home")}</button></div>;
  const totals = dashboard.rooms.reduce((sum, room) => sum + room.totalMinor, 0);
  return <main className="admin-shell">
    <header className="admin-topbar"><div><span className="brand-mark">FR</span><b>{t("Food Run Admin")}</b></div><div><LanguageToggle /><button className="secondary" onClick={() => refresh()}>{t("Refresh")}</button><button className="link" onClick={onBack}>{t("Home")}</button></div></header>
    <nav className="admin-tabs">{['overview','users','rooms','orders','wallets',t("restaurants"),'settings'].map(value => <button className={tab === value ? 'active' : ''} onClick={() => setTab(value)} key={value}>{t(value[0].toUpperCase() + value.slice(1))}</button>)}</nav>
    {message && <div className="banner error">{message}</div>}
    <section className="admin-content">
      {tab === 'overview' && <><div className="admin-metrics"><article><small>{t("Registered users")}</small><b>{dashboard.users.length}</b></article><article><small>{t("Rooms")}</small><b>{dashboard.rooms.length}</b></article><article><small>{t("Saved orders")}</small><b>{dashboard.archivedOrders.length}</b></article><article><small>{t("Current order value")}</small><b>{money(totals, 'AED')}</b></article></div><div className="card"><h2>{t("Current activity")}</h2>{dashboard.rooms.slice(0, 10).map(room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} key={room.id} />)}</div></>}
      {tab === 'users' && <div className="card admin-table"><h2>{t("Users")}</h2>{dashboard.users.map(user => <div className="admin-row admin-user-row" key={user.id}><span><b>{user.name || t("Incomplete profile")}</b><small>{user.phone || user.id} · {user.language === 'ar' ? 'Arabic' : 'English'} · {user.discoverable ? t("Discoverable") : t("Private")}</small>{user.paymentIdentifier && <small>{user.paymentMethod === 'AANI' ? t("Aani") : user.paymentBank || t("Bank")} · {user.paymentHolder} · {user.paymentIdentifier}</small>}</span><span className={`status ${user.disabled ? '' : 'live'}`}>{user.disabled ? t("Disabled") : t("Active")}</span><button className={user.disabled ? 'secondary' : 'link danger'} onClick={() => mutate('/admin/user', { userId: user.id, disabled: !user.disabled })}>{user.disabled ? t("Enable") : t("Disable")}</button></div>)}</div>}
      {tab === 'rooms' && <div className="card"><h2>{t("Live and saved rooms")}</h2>{dashboard.rooms.map(room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} key={room.id} />)}</div>}
      {tab === 'orders' && <div className="card"><h2>{t("Order history")}</h2>{dashboard.archivedOrders.length ? dashboard.archivedOrders.map((room, index) => <AdminRoomRow room={room} key={`${room.id}-${room.orderNumber}-${index}`} />) : <p className="muted">{t("No archived orders yet.")}</p>}</div>}
      {tab === 'wallets' && <div className="card"><h2>{t("Wallet and settlement totals")}</h2>{dashboard.rooms.map(room => <section className="admin-wallet-room" key={room.id}><div className="admin-row wallet-admin"><span><b>{room.name} · #{room.orderNumber}</b><small>{room.payer ? `Payer: ${room.payer}` : t("Payer not selected")}</small></span><span><small>{t("Total")}</small><b>{money(room.totalMinor, room.currency)}</b></span><span><small>{t("Confirmed")}</small><b>{money(room.confirmedPaidMinor, room.currency)}</b></span><span><small>{t("Outstanding")}</small><b>{money(room.outstandingMinor, room.currency)}</b></span></div>{room.wallets.map(wallet => <div className="admin-wallet-person" key={wallet.memberId}><b>{wallet.name}</b><span>{t("Order")}{money(wallet.totalMinor, room.currency)}</span><span>{t("Paid")}{money(wallet.paidMinor, room.currency)}</span><span>{wallet.balanceMinor < 0 ? 'Refund' : 'Due'} {money(Math.abs(wallet.balanceMinor), room.currency)}</span></div>)}</section>)}</div>}
      {tab === t("restaurants") && <AdminRestaurants language={language} restaurants={dashboard.restaurants} save={saveRestaurant} remove={restaurantId => mutate('/admin/restaurant', { action: 'delete', restaurantId })} />}
      {tab === 'settings' && <AdminSettingsPanel settings={dashboard.settings} save={settings => mutate('/admin/settings', settings)} />}
    </section>
  </main>;
}

function AdminRoomRow({ room, onCancel }) {
  const active = !['ARCHIVED','CANCELLED'].includes(room.phase);
  const cancellable = active && !['PLACED', 'FULFILLED'].includes(room.phase);
  return <div className="admin-row"><span><b>{room.name} · {room.code}</b><small>{room.restaurant}{t("· Order #")}{room.orderNumber} · {room.members}{t("members")}</small></span><span className={`status ${active ? 'live' : ''}`}>{room.phase}</span><b>{money(room.totalMinor, room.currency)}</b>{onCancel && cancellable && <button className="link danger" onClick={onCancel}>{t("Cancel room")}</button>}</div>;
}

function AdminRestaurants({ restaurants, save, remove, language }) {
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState(restaurants[0] ? clone(restaurants[0]) : blankRestaurant());
  useEffect(() => { const latest = restaurants.find(value => value.id === editing.id); if (latest) setEditing(clone(latest)); }, [restaurants]);
  const addItem = () => {
    let categories = editing.menu.categories;
    if (!categories.length) categories = [{ id: uid(), name: "Sandwiches", nameAr: 'السندويشات', sortOrder: 0 }];
    setEditing(old => ({ ...old, openOrdering: false, menu: { ...old.menu, categories, items: [...old.menu.items, { id: uid(), categoryId: categories[0].id, name: 'New item', nameAr: 'صنف جديد', description: '', descriptionAr: '', basePriceMinor: 0, available: true, variants: [], optionGroupIds: [] }] } }));
  };
  return <div className="admin-restaurant-layout">
    <aside className="card admin-restaurant-list"><button type="button" className="primary wide" onClick={() => setEditing(blankRestaurant())}>{t("Add restaurant")}</button>{restaurants.map(restaurant => <button type="button" className={editing.id === restaurant.id ? 'active' : ''} onClick={() => setEditing(clone(restaurant))} key={restaurant.id}><b>{restaurant.name}</b><small>{restaurant.menu.items.length}{t("items")}</small></button>)}</aside>
    <section className="card stack">
      <div className="section-title compact"><h2>{t("Restaurant and menu")}</h2>{restaurants.some(value => value.id === editing.id) && <button type="button" className="link danger" onClick={() => remove(editing.id)}>{t("Delete restaurant")}</button>}</div>
      <div className="form-grid two"><label>{t("English name")}<input value={editing.name} onChange={event => setEditing({ ...editing, name: event.target.value })} /></label><label>{t("Arabic name")}<input dir="rtl" value={editing.nameAr || ''} onChange={event => setEditing({ ...editing, nameAr: event.target.value })} /></label><label>{t("UAE phone")}<input value={editing.contact.phoneE164 || ''} onChange={event => setEditing({ ...editing, contact: { ...editing.contact, phoneE164: event.target.value || null } })} /></label><label>{t("Default delivery fee · AED")}<MenuMoneyInput value={editing.pricing.defaultDeliveryFeeMinor} onChange={value => setEditing({ ...editing, pricing: { ...editing.pricing, defaultDeliveryFeeMinor: value } })} label="Default delivery fee in AED" /></label></div>
      <div className="section-title compact"><h3>{t("Menu prices")}</h3><button type="button" className="secondary" onClick={addItem}>{t("Add menu item")}</button></div>
      {message && <p role="alert" className="form-message">{message}</p>}
      <MenuEditor menu={editing.menu} language={language} onChange={menu => setEditing(old => ({ ...old, menu }))} />
      <button type="button" className="primary wide" onClick={event => { const invalid = [...event.currentTarget.closest('section').querySelectorAll('input')].find(input => !input.checkValidity()); if (invalid) { let node = invalid.parentElement; while (node) { if (node.tagName === 'DETAILS') node.open = true; node = node.parentElement; } invalid.reportValidity(); return; } try { save(normalizeRestaurant(editing)); setMessage(''); } catch (error) { setMessage(error.message); } }}>{t("Save restaurant and all prices")}</button>
    </section>
  </div>;
}

function AdminSettingsPanel({ settings, save }) {
  const [form, setForm] = useState(settings);
  return <section className="card stack admin-settings"><h2>{t("Website options")}</h2><label className="check"><input type="checkbox" checked={form.registrationsEnabled} onChange={event => setForm({ ...form, registrationsEnabled: event.target.checked })} />{t("Allow new user registration")}</label><label className="check"><input type="checkbox" checked={form.roomCreationEnabled} onChange={event => setForm({ ...form, roomCreationEnabled: event.target.checked })} />{t("Allow new room creation")}</label><label>{t("Maintenance message")}<textarea value={form.maintenanceMessage} onChange={event => setForm({ ...form, maintenanceMessage: event.target.value })} maxLength="500" /></label><button className="primary" onClick={() => save(form)}>{t("Save website options")}</button></section>;
}
