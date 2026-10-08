import PagedList, { matchesSearch } from './PagedList.jsx';
import MobilePageLayout from './MobilePageLayout.jsx';
import { BrandLogo, BrandMark } from './Brand.jsx';
import PaymentFields from './PaymentFields.jsx';
import { paymentDraft, paymentAccount, internationalPhone } from './paymentDetails.js';
import { photoData, CURRENCIES } from './client';
import { canAccessAdmin } from './adminAccess';
import { cleanupSelection, requireCleanupPreview } from './adminCleanup.js';
import './adminCleanup.css';
import { t } from './i18n.js';
import MenuEditor, { MenuMoneyInput } from './MenuEditor';
import { useState, useEffect, useRef } from 'react';
import { money } from './client';
import { PUBLIC_API_URL, normalizeRestaurant, blankRestaurant, changeRestaurantCurrency, clone, LanguageToggle } from './FoodRunApp';

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

export default function AdminApp({ language, user, onBack, onSupport }) {
  const [dashboard, setDashboard] = useState(null);
  const [tab, setTab] = useState('overview');
  const [search, setSearch] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const refresh = async () => {
    try { setDashboard(await adminRequest('/admin/dashboard', user)); setMessage(''); }
    catch (error) { setDashboard(null); setMessage(error.message); }
  };
  useEffect(() => { refresh(); }, [user]);
  const mutate = async (path, body) => { setBusy(true); try { const result = await adminRequest(path, user, body); await refresh(); return result; } catch (error) { setMessage(error.message); return null; } finally { setBusy(false); } };
  const saveRestaurant = restaurant => mutate('/admin/restaurant', { action: 'save', restaurant });
  if (!dashboard) return <div className="splash"><BrandMark /><p role="status">{message || t("Loading admin data…")}</p>{message && <button className="secondary" onClick={refresh}>{t("Retry")}</button>}<button className="link" onClick={onBack}>{t("Home")}</button></div>;
  const totals = Object.entries(dashboard.rooms.reduce((values, room) => ({ ...values, [room.currency]: (values[room.currency] || 0) + room.totalMinor }), {}));
  return <MobilePageLayout pageKey={tab}><main className="admin-shell">
    <header className="admin-topbar"><div><BrandLogo /><b>{t("Intrvioo Admin")}</b></div><div><LanguageToggle /><button className="secondary" onClick={() => refresh()}>{t("Refresh")}</button><button className="link" onClick={onBack}>{t("Home")}</button></div></header>
    <nav className="admin-tabs">{['overview','users','rooms','orders','wallets','restaurants','requests','cleanup','settings'].map(value => <button className={tab === value ? 'active' : ''} onClick={() => { setTab(value); setSearch(''); }} key={value}>{t(value[0].toUpperCase() + value.slice(1))}</button>)}</nav>
    <label className="admin-search">{t("Search")} · {t(tab[0].toUpperCase() + tab.slice(1))}<input type="search" value={search} onChange={event => setSearch(event.target.value)} placeholder={t("Name, email, room code or keyword")} /></label>
    {message && <div className="banner error">{message}</div>}
    <fieldset disabled={busy} className="admin-content room-fieldset">
      {tab === 'overview' && <><div className="admin-metrics"><article><small>{t("Registered users")}</small><b>{dashboard.users.length}</b></article><article><small>{t("Rooms")}</small><b>{dashboard.rooms.length}</b></article><article><small>{t("Saved orders")}</small><b>{dashboard.archivedOrders.length}</b></article><article><small>{t("Current order value")}</small>{totals.length ? totals.map(([currency, total]) => <b key={currency}>{money(total, currency)}</b>) : <b>{money(0, 'AED')}</b>}</article></div><div className="card"><h2>{t("Current activity")}</h2><PagedList items={dashboard.rooms.filter(room => matchesSearch(room,search))} resetKey={search}>{room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} key={room.id} />}</PagedList></div></>}
      {tab === 'users' && <AdminUsers users={dashboard.users} externalSearch={search} currentUserId={user.uid} rooms={dashboard.rooms} mutate={mutate} busy={busy} onSupport={async userId => { const reply = await mutate('/admin/support/start', { userId }); if(reply?.identityToken) onSupport?.(reply); }} />}
      {tab === 'rooms' && <><details className="card admin-room-cleanup"><summary>{t('Clear rooms by date')}</summary><AdminCleanup request={(path, body) => adminRequest(path, user, body)} onChanged={refresh} initialFilter="date" search={search} roomsOnly /></details><div className="card"><h2>{t("Live and saved rooms")}</h2><PagedList items={dashboard.rooms.filter(room => matchesSearch(room,search))} resetKey={search}>{room => <AdminRoomRow room={room} onCancel={() => mutate('/admin/room', { roomId: room.id, action: 'cancel' })} onDelete={() => {
        const confirmation = window.prompt(t('Delete this room and its history? Enter the room code:') + ' ' + room.code);
        if (confirmation === room.code) mutate('/admin/room', { roomId: room.id, action: 'delete', expectedRevision: room.revision, confirmation });
      }} key={room.id} />}</PagedList></div></>}
      {tab === 'requests' && <AdminBlockRequests requests={(dashboard.blockRequests || []).filter(value => matchesSearch(value,search))} mutate={mutate} busy={busy} />}
      {tab === 'cleanup' && <AdminCleanup request={(path, body) => adminRequest(path, user, body)} onChanged={refresh} search={search} />}
      {tab === 'orders' && <div className="card"><h2>{t("Order history")}</h2><PagedList items={dashboard.archivedOrders.filter(room => matchesSearch(room,search))} resetKey={search} empty={<p className="muted">{t("No archived orders yet.")}</p>}>{(room, index) => <AdminRoomRow room={room} key={`${room.id}-${room.orderNumber}-${index}`} />}</PagedList></div>}
      {tab === 'wallets' && <div className="card"><h2>{t("Wallet and settlement totals")}</h2><PagedList items={dashboard.rooms.filter(room => matchesSearch(room,search))} resetKey={search}>{room => <section className="admin-wallet-room" key={room.id}><div className="admin-row wallet-admin"><span><b>{room.name} · #{room.orderNumber}</b><small>{room.payer ? `Payer: ${room.payer}` : t("Payer not selected")}</small></span><span><small>{t("Total")}</small><b>{money(room.totalMinor, room.currency)}</b></span><span><small>{t("Confirmed")}</small><b>{money(room.confirmedPaidMinor, room.currency)}</b></span><span><small>{t("Outstanding")}</small><b>{money(room.outstandingMinor, room.currency)}</b></span></div>{room.wallets.map(wallet => <div className="admin-wallet-person" key={wallet.memberId}><b>{wallet.name}</b><span>{t("Order")}{money(wallet.totalMinor, room.currency)}</span><span>{t("Paid")}{money(wallet.paidMinor, room.currency)}</span><span>{wallet.balanceMinor < 0 ? 'Refund' : 'Due'} {money(Math.abs(wallet.balanceMinor), room.currency)}</span></div>)}</section>}</PagedList></div>}
      {tab === 'restaurants' && <AdminRestaurants language={language} restaurants={dashboard.restaurants} search={search} save={saveRestaurant} remove={restaurantId => mutate('/admin/restaurant', { action: 'delete', restaurantId })} />}
      {tab === 'settings' && <AdminSettingsPanel settings={dashboard.settings} search={search} save={settings => mutate('/admin/settings', settings)} />}
    </fieldset>
  </main></MobilePageLayout>;
}

function AdminRoomRow({ room, onCancel, onDelete }) {
  const active = !['ARCHIVED','CANCELLED'].includes(room.phase);
  const cancellable = active && !['PLACED', 'FULFILLED'].includes(room.phase);
  return <div className="admin-row"><span><b>{room.name} · {room.code}</b><small>{room.restaurant}{t("· Order #")}{room.orderNumber} · {room.members}{t("members")}</small></span><span className={`status ${active ? 'live' : ''}`}>{room.phase}</span><b>{money(room.totalMinor, room.currency)}</b>{onCancel && cancellable && <button className="link danger" onClick={onCancel}>{t("Cancel room")}</button>}{onDelete && room.canDelete && <button className="link danger" onClick={onDelete}>{t("Delete room")}</button>}</div>;
}

function AdminRestaurants({ restaurants, save, remove, language, search = '' }) {
  const [message, setMessage] = useState('');
  const [editing, setEditing] = useState(restaurants[0] ? clone(restaurants[0]) : blankRestaurant());
  useEffect(() => { const latest = restaurants.find(value => value.id === editing.id); if (latest) setEditing(clone(latest)); }, [restaurants]);
  return <div className="admin-restaurant-layout">
    <aside className="card admin-restaurant-list"><button type="button" className="primary wide" onClick={() => setEditing(blankRestaurant())}>{t("Add restaurant")}</button><PagedList items={restaurants.filter(value => matchesSearch(value,search))} resetKey={search}>{restaurant => <button type="button" className={editing.id === restaurant.id ? 'active' : ''} onClick={() => setEditing(clone(restaurant))} key={restaurant.id}><b>{restaurant.name}</b><small>{restaurant.menu.items.length}{t("items")}</small></button>}</PagedList></aside>
    <section className="card stack">
      <div className="section-title compact"><h2>{t("Restaurant and menu")}</h2>{restaurants.some(value => value.id === editing.id) && <button type="button" className="link danger" onClick={() => remove(editing.id)}>{t("Delete restaurant")}</button>}</div>
      <div className="form-grid two"><label>{t("English name")}<input value={editing.name} onChange={event => setEditing({ ...editing, name: event.target.value })} /></label><label>{t("Arabic name")}<input dir="rtl" value={editing.nameAr || ''} onChange={event => setEditing({ ...editing, nameAr: event.target.value })} /></label><label>{t("Phone with country code")}<input placeholder="+20 10 1234 5678" value={editing.contact.phoneE164 || ''} onChange={event => setEditing({ ...editing, contact: { ...editing.contact, phoneE164: event.target.value || null } })} /></label><label>{t("Currency")}<select value={editing.currency} onChange={event => setEditing(changeRestaurantCurrency(editing, event.target.value))}>{CURRENCIES.map(currency => <option key={currency}>{currency}</option>)}</select></label><label>{t(`Default delivery fee · ${editing.currency}`)}<MenuMoneyInput currency={editing.currency} value={editing.pricing.defaultDeliveryFeeMinor} onChange={value => setEditing({ ...editing, pricing: { ...editing.pricing, defaultDeliveryFeeMinor: value } })} label={`Default delivery fee in ${editing.currency}`} /></label></div>
      <div className="section-title compact"><h3>{t("Menu prices")}</h3></div>
      {message && <p role="alert" className="form-message">{message}</p>}
      <MenuEditor menu={editing.menu} language={language} currency={editing.currency} onChange={menu => setEditing(old => ({ ...old, menu }))} />
      <button type="button" className="primary wide" onClick={event => { const invalid = [...event.currentTarget.closest('section').querySelectorAll('input')].find(input => !input.checkValidity()); if (invalid) { let node = invalid.parentElement; while (node) { if (node.tagName === 'DETAILS') node.open = true; node = node.parentElement; } invalid.reportValidity(); return; } try { save(normalizeRestaurant(editing)); setMessage(''); } catch (error) { setMessage(error.message); } }}>{t("Save restaurant and all prices")}</button>
    </section>
  </div>;
}

function AdminSettingsPanel({ settings, save, search = '' }) {
  const [form, setForm] = useState(settings);
  return <section className="card stack admin-settings"><h2>{t("Website options")}</h2><div className="stack" hidden={!matchesSearch([t("Allow new user registration"),settings.registrationsEnabled],search)}><label className="check"><input type="checkbox" checked={form.registrationsEnabled} onChange={event => setForm({ ...form, registrationsEnabled: event.target.checked })} />{t("Allow new user registration")}</label></div><div className="stack" hidden={!matchesSearch([t("Allow new room creation"),settings.roomCreationEnabled],search)}><label className="check"><input type="checkbox" checked={form.roomCreationEnabled} onChange={event => setForm({ ...form, roomCreationEnabled: event.target.checked })} />{t("Allow new room creation")}</label></div><div className="stack" hidden={!matchesSearch([t("Maintenance message"),settings.maintenanceMessage],search)}><label>{t("Maintenance message")}<textarea value={form.maintenanceMessage} onChange={event => setForm({ ...form, maintenanceMessage: event.target.value })} maxLength="500" /></label></div><button className="primary" onClick={() => save(form)}>{t("Save website options")}</button></section>;
}

export function AdminUsers({ users, currentUserId, rooms = [], mutate, busy, onSupport, externalSearch }) {
  const [search, setSearch] = useState('');
  const [create, setCreate] = useState({ name: '', email: '', password: '', phone: '', language: 'en' });
  return <div className="stack">
    <details className="card"><summary>{t('Add user')}</summary><form className="stack" onSubmit={async event => {
      event.preventDefault(); const result = await mutate('/admin/user', { ...create, action: 'create' });
      if (result) setCreate({ name: '', email: '', password: '', phone: '', language: 'en' });
    }}><p>{t('Create an email account. Share the temporary password privately; the user can reset it from sign-in.')}</p><div className="form-grid two">
      <label>{t('Name')}<input required maxLength={160} value={create.name} onChange={e => setCreate({ ...create, name: e.target.value })} /></label>
      <label>{t('Email')}<input type="email" required autoComplete="off" value={create.email} onChange={e => setCreate({ ...create, email: e.target.value })} /></label>
      <label>{t('Phone with country code')}<input type="tel" placeholder="+20 10 1234 5678" required value={create.phone} onChange={e => setCreate({ ...create, phone: e.target.value })} /></label>
      <label>{t('Temporary password')}<input type="password" required minLength={8} maxLength={128} autoComplete="new-password" value={create.password} onChange={e => setCreate({ ...create, password: e.target.value })} /></label>
    </div><button className="primary" disabled={busy}>{t('Create user')}</button></form></details>
    <section className="card stack"><h2>{t('Users')}</h2>{externalSearch === undefined && <label>{t('Search users')}<input type="search" value={search} onChange={e => setSearch(e.target.value)} placeholder={t("Name, email or phone")} /></label>}
      <PagedList items={users.filter(person => matchesSearch([person.name,person.email,person.phone,person.id],externalSearch ?? search))} resetKey={externalSearch ?? search}>{person => <AdminUserCard key={person.id} person={person} self={person.id === currentUserId} rooms={rooms} mutate={mutate} busy={busy} onSupport={onSupport} />}</PagedList>
    </section>
  </div>;
}
function AdminUserCard({ person, self, rooms, mutate, busy, onSupport }) {
  const [duration, setDuration] = useState('24'), [reason, setReason] = useState('');
  const [scopeRoomId, setScopeRoomId] = useState('');
  const source = person.profile || { name: person.name || '', phone: person.phone || '', photo: '', language: person.language || 'en', discoverable: person.discoverable ?? true };
  const draft = () => ({ ...source, ...paymentDraft(source.payment) });
  const [form, setForm] = useState(draft);
  const [editing, setEditing] = useState(false), [message, setMessage] = useState('');
  useEffect(() => { setForm(draft()); }, [person]);
  const set = (key, value) => setForm(old => ({ ...old, [key]: value }));
  const save = async event => {
    event.preventDefault(); setMessage('');
    try {
      const profile = { ...source, name: form.name.trim(), phone: internationalPhone(form.phone), photo: form.photo,
        discoverable: form.discoverable, language: form.language, payment: paymentAccount(form, source.payment, form.name) };
      const result = await mutate('/admin/user', { userId: person.id, action: 'profile', profile });
      if (result) { setEditing(false); setMessage(t('Profile and payment details saved.')); }
    } catch (error) { setMessage(error.message); }
  };
  return <article className="admin-person stack"><div className="section-title"><div><b>{person.name || t('Incomplete profile')}</b><small>{person.phone || person.id}</small></div><span className={`status ${person.disabled ? '' : 'live'}`}>{t(person.removed ? 'Removed' : person.disabled ? 'Blocked' : 'Active')}</span></div>
    {person.email && <small><bdi>{person.email}</bdi></small>}
    {!self && !person.disabled && !person.removed && onSupport && <button type="button" className="primary" disabled={busy} onClick={() => onSupport(person.id)}>{t('Sign in as this user')}</button>}
    {!person.removed && <><button className="secondary" type="button" onClick={() => setEditing(!editing)}>{t(editing ? 'Close editor' : 'Edit user details')}</button>
      {editing && <form className="stack admin-profile-editor" onSubmit={save}>
        <div className="form-grid two">
          <label>{t('Profile name')}<input required maxLength={160} value={form.name} onChange={e => set('name', e.target.value)} /></label>
          <label>{t('Phone with country code')}<input required type="tel" placeholder="+20 10 1234 5678" value={form.phone} onChange={e => set('phone', e.target.value)} /></label>
          <label>{t('Language')}<select value={form.language} onChange={e => set('language', e.target.value)}><option value="en">English</option><option value="ar">العربية</option></select></label>
          <label className="check"><input type="checkbox" checked={form.discoverable} onChange={e => set('discoverable', e.target.checked)} />{t('Let people on this hub invite me')}</label>
        </div>
        <div className="admin-profile-photo">{form.photo && <img src={form.photo} alt={form.name} />}<label>{t('Choose photo')}<input type="file" accept="image/jpeg,image/png,image/webp" onChange={async e => { const file = e.target.files[0]; if (!file) return; try { set('photo', await photoData(file)); } catch (error) { setMessage(error.message); } }} /></label>{form.photo && <button type="button" className="link" onClick={() => set('photo', '')}>{t('Remove photo')}</button>}</div>
        <label>{t('Photo URL (optional)')}<input type="url" value={form.photo?.startsWith('data:') ? '' : form.photo || ''} onChange={e => set('photo', e.target.value)} placeholder="https://…" /></label>
        <h3>{t('Receiving details')}</h3><PaymentFields form={form} set={set} name={form.name} />
        <p className="field-help">{t('Changes update the user profile and their active rooms.')}</p>
        <button className="primary" disabled={busy}>{t('Save user details')}</button>
      </form>}
    </>}
    {message && <p role="status">{message}</p>}
    {person.disabled && <p className="admin-block-status">{person.blockedUntil ? `${t('Blocked until')} ${new Date(person.blockedUntil).toLocaleString()}` : t('Until unblocked by admin')}{person.blockReason && <> · {person.blockReason}</>}</p>}
    {Object.entries(person.roomBlocks || {}).map(([roomId, block]) => <div className="admin-block-status" key={roomId}>{rooms.find(room => room.id === roomId)?.name || roomId} · {block.until ? new Date(block.until).toLocaleString() : t('Until unblocked by admin')} · {block.reason}<button className="secondary" onClick={() => mutate('/admin/user', { userId: person.id, action: 'unblock', scopeRoomId: roomId })}>{t('Unblock now')}</button></div>)}
    {self ? <p>{t('Your administrator account')}</p> : person.removed ? <button className="secondary" onClick={() => mutate('/admin/user', { userId: person.id, action: 'restore' })}>{t('Restore Intrvioo access')}</button> : <>
      {person.disabled ? <button className="secondary" onClick={() => mutate('/admin/user', { userId: person.id, action: 'unblock' })}>{t('Unblock now')}</button> : <form className="stack" onSubmit={event => { event.preventDefault(); mutate('/admin/user', { userId: person.id, action: 'block', durationHours: Number(duration), reason, scopeRoomId }); }}>
        <label>{t('Block scope')}<select value={scopeRoomId} onChange={e => setScopeRoomId(e.target.value)}><option value="">{t('All rooms')}</option>{rooms.map(room => <option key={room.id} value={room.id}>{room.name}</option>)}</select></label>
        <div className="form-grid two"><label>{t('Block duration')}<select value={duration} onChange={e => setDuration(e.target.value)}>{[[1,'1 hour'],[6,'6 hours'],[24,'1 day'],[72,'3 days'],[168,'7 days'],[720,'30 days'],[0,'Until unblocked by admin']].map(([value,label]) => <option key={value} value={value}>{t(label)}</option>)}</select></label>
        <label>{t('Reason')}<input required minLength={5} maxLength={300} value={reason} onChange={e => setReason(e.target.value)} /></label></div><button disabled={busy} className="secondary danger">{t('Block user')}</button>
      </form>}
      <button className="link danger" onClick={() => { if (window.confirm(t('Remove this user from Intrvioo? Their shared Google/email account stays active. Active rooms must be finished first.') + '\n' + person.name)) mutate('/admin/user', { userId: person.id, action: 'remove', confirmation: person.id, reason }); }}>{t('Remove from Intrvioo')}</button>
    </>}
  </article>;
}
export function AdminBlockRequests({ requests, mutate, busy }) {
  return <section className="card stack"><h2>{t('Block requests')}</h2><p>{t('Room owners request a block for their room. The user can still sign in. Only an admin can approve the request.')}</p>{!requests.length && <p>{t('No block requests.')}</p>}<PagedList items={requests}>{request => <article key={request.id} className="admin-person stack"><b>{request.userName} · {request.durationHours} {t('hours')}</b><small>{request.requesterName} · {request.roomName} · {new Date(request.createdAt).toLocaleString()}</small><p>{request.reason}</p><span className="status">{t(request.status)}</span>{request.status === 'pending' && <div className="hero-actions"><button disabled={busy} className="primary" onClick={() => mutate('/admin/block-request', { requestId: request.id, action: 'approve' })}>{t('Approve block')}</button><button disabled={busy} className="secondary" onClick={() => mutate('/admin/block-request', { requestId: request.id, action: 'reject' })}>{t('Reject request')}</button></div>}</article>}</PagedList></section>;
}
export function AdminCleanup({ request, onChanged, initialFilter = 'age', roomsOnly = false, search = '' }) {
  const [scope, setScope] = useState('closedRooms'), [days, setDays] = useState('30');
  const [filter, setFilter] = useState(initialFilter), [fromDate, setFromDate] = useState(''), [toDate, setToDate] = useState('');
  const [preview, setPreview] = useState(null), [confirmation, setConfirmation] = useState(''), [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const requestVersion = useRef(0);
  const timeZone = Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Dubai';
  let selection = null, validation = '';
  try { selection = cleanupSelection({ scope, filter, days, fromDate, toDate, timeZone }); } catch (error) { validation = error.message; }
  const resetPreview = () => { requestVersion.current++; setPreview(null); setConfirmation(''); setMessage(''); };
  const run = async remove => {
    if (busy || !selection || remove && search.trim()) return;
    const version = ++requestVersion.current;
    setBusy(true); setMessage('');
    try {
      if (remove) { requireCleanupPreview(preview, selection); if (confirmation !== 'DELETE') throw Error('Type DELETE to confirm this cleanup.'); }
      const result = await request('/admin/cleanup/' + (remove ? 'delete' : 'preview'), { ...selection, ...(remove ? { previewToken: preview.previewToken, confirmation } : {}) });
      if (version !== requestVersion.current) return;
      if (remove) { setPreview(null); setConfirmation(''); await onChanged(); setMessage(`${result.removedCount} ${t('records removed')}`); }
      else { setPreview(requireCleanupPreview(result, selection)); setConfirmation(''); }
    } catch (error) { if (version === requestVersion.current) { setMessage(t(error.message)); setPreview(null); setConfirmation(''); } } finally { setBusy(false); }
  };
  return <section className="card stack admin-cleanup"><h2>{t(roomsOnly ? 'Clear rooms by date' : 'Clear old website data')}</h2><p>{t('Delete cancelled or fully settled rooms, or old order history. Active orders and unsettled payments are protected. Downloaded copies and external backups are not erased.')}</p>
    <fieldset disabled={busy} className="room-fieldset form-grid two">
      {!roomsOnly && <label>{t('Data to clear')}<select value={scope} onChange={e => { setScope(e.target.value); resetPreview(); }}><option value="closedRooms">{t('Closed rooms and their history')}</option><option value="history">{t('Previous order history only')}</option></select></label>}
      <label>{t('Time filter')}<select value={filter} onChange={e => { setFilter(e.target.value); resetPreview(); }}><option value="age">{t('Older than a number of days')}</option><option value="date">{t('Date range')}</option></select></label>
      {filter === 'date' ? <><label>{t('Start date')}<input type="date" value={fromDate} max={toDate || undefined} onChange={e => { setFromDate(e.target.value); resetPreview(); }} required /></label><label>{t('End date')}<input type="date" value={toDate} min={fromDate || undefined} onChange={e => { setToDate(e.target.value); resetPreview(); }} required /></label></> : <label>{t('Older than (days)')}<input type="number" min={0} max={3650} value={days} onChange={e => { setDays(e.target.value); resetPreview(); }} /></label>}
    </fieldset>
    {filter === 'date' && <p className="fine">{t('Based on last room activity. Both dates are included. Time zone:')} <b dir="ltr">{timeZone}</b></p>}
    {validation && (filter === 'age' || fromDate && toDate) && <p className="form-message" role="alert">{t(validation)}</p>}
    <button disabled={busy || !selection} className="secondary" onClick={() => run(false)}>{t('Preview cleanup')}</button>
    {preview && <div className="stack"><b>{preview.count} {t('records selected')}</b><div className="admin-cleanup-targets"><PagedList items={preview.targets.filter(room => matchesSearch(room,search))} resetKey={search}>{room => <article key={`${room.id}:${room.orderNumber}`}><span><b>{room.name} · #{room.orderNumber}</b><small>{room.restaurant} · {t(room.phase || '')}</small></span>{room.updatedAt != null && <time dateTime={new Date(room.updatedAt).toISOString()}>{new Date(room.updatedAt).toLocaleString(undefined, { timeZone })}</time>}</article>}</PagedList></div>{preview.count === 0 && <p className="muted">{t('No rooms or orders match this selection.')}</p>}{preview.count > 0 && <>{search.trim() && <p>{t('Clear search to review the full cleanup selection before deleting.')}</p>}<label>{t('Type DELETE to confirm')}<input disabled={busy} value={confirmation} onChange={e => setConfirmation(e.target.value)} autoComplete="off" dir="ltr" /></label><button disabled={busy || !selection || confirmation !== 'DELETE' || !!search.trim()} className="primary danger" onClick={() => run(true)}>{t('Delete selected data')}</button></>}</div>}
    {message && <p role="status">{message}</p>}
  </section>;
}
