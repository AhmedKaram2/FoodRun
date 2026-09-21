import { useState } from 'react';
import { Page } from './FoodRunApp.jsx';
import { amount, money } from './client.js';
import { paymentRoomPayload, receiptPhotoData, splitEqually } from './paymentRoom.js';
import { t } from './i18n.js';

export function CreatePaymentRoom({ data, onBack, openRoom, openProfile }) {
  const owner = { userId: data.user.uid, name: data.home.profile.name };
  const [selected, setSelected] = useState([]), [search, setSearch] = useState(''), [shares, setShares] = useState({});
  const [form, setForm] = useState({ name: '', restaurant: '', details: '', photo: '', total: '' });
  const [error, setError] = useState(''), [uploading, setUploading] = useState(false), [saving, setSaving] = useState(false);
  const people = [owner, ...data.home.people.filter(person => selected.includes(person.userId) && person.userId !== owner.userId)];
  const updateShare = (id, field, value) => setShares(old => ({ ...old, [id]: { ...old[id], [field]: value } }));
  const upload = async event => {
    const file = event.target.files?.[0]; if (!file) return;
    setUploading(true); setError('');
    try { const photo = await receiptPhotoData(file); setForm(old => ({ ...old, photo })); } catch (e) { setError(e.message); } finally { setUploading(false); }
  };
  const submit = async event => {
    event.preventDefault(); if (saving || uploading || data.busy) return;
    setSaving(true); setError('');
    try {
      const payload = paymentRoomPayload({ ...form, people, shares, account: data.home.profile.payment, ownerId: owner.userId });
      const reply = await data.send('CREATE_PAYMENT_ROOM', payload);
      if (reply?.room) openRoom(reply.room.id);
    } catch (e) { setError(e.message); } finally { setSaving(false); }
  };
  return <Page title={t('Payment room')} subtitle={t('Already ordered? Add the bill and settle everyone’s share.')} onBack={onBack}>
    <form className="stack" onSubmit={submit}><fieldset className="room-fieldset stack" disabled={saving || data.busy}>
      <section className="card stack"><h2>{t('Order details')}</h2>
        <label>{t('Room name')}<input required maxLength={160} value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} /></label>
        <label>{t('Restaurant')}<input maxLength={160} value={form.restaurant} onChange={e => setForm({ ...form, restaurant: e.target.value })} /></label>
        <label>{t('What did you order?')}<textarea required maxLength={4000} value={form.details} onChange={e => setForm({ ...form, details: e.target.value })} /></label>
        <label>{t('Receipt photo (optional)')}<input type="file" accept="image/jpeg,image/png,image/webp" disabled={uploading} onChange={upload} /></label>
        {uploading && <p role="status">{t('Preparing receipt photo…')}</p>}
        {form.photo && <><img className="receipt-photo" src={form.photo} alt={t('Receipt photo')} /><button className="link" type="button" onClick={() => setForm({ ...form, photo: '' })}>{t('Remove photo')}</button></>}
      </section>
      <section className="card stack"><h2>{t('Add people')}</h2><p>{t('Selected people will see this room and their balance in their wallet.')}</p>
        <label>{t('Search users')}<input type="search" value={search} onChange={e => setSearch(e.target.value)} /></label>
        <div className="payment-people-list">{data.home.people.filter(person => person.userId !== owner.userId && person.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())).map(person => <label className="payment-person-choice" key={person.userId}><input type="checkbox" checked={selected.includes(person.userId)} onChange={e => setSelected(old => e.target.checked ? [...old, person.userId] : old.filter(id => id !== person.userId))} />{person.name}</label>)}</div>
        {!data.home.people.length && <p>{t('No users are available yet. Ask them to sign in and enable visibility in the users list.')}</p>}
      </section>
      <section className="card stack"><h2>{t('Split the bill')}</h2><p>{t('Enter final amounts including tax, fees and discounts. Your own share can be zero.')}</p>
        <label>{t('Receipt total')} · AED<input required inputMode="decimal" value={form.total} onChange={e => setForm({ ...form, total: e.target.value })} /></label>
        <button type="button" className="secondary" onClick={() => { try { const values = splitEqually(amount(form.total, 'AED'), people.map(person => person.userId)); setShares(old => Object.fromEntries(people.map(person => [person.userId, { ...old[person.userId], amount: (values[person.userId] / 100).toFixed(2) }]))); setError(''); } catch (e) { setError(e.message); } }}>{t('Split equally')}</button>
        {people.map(person => <div className="card stack" key={person.userId}><h3>{person.name}{person.userId === owner.userId ? t(' · You') : ''}</h3>
          <label>{t('Their order')}<input maxLength={160} value={shares[person.userId]?.description || ''} onChange={e => updateShare(person.userId, 'description', e.target.value)} placeholder={t('Food order')} /></label>
          <div className="form-grid two"><label>{t('Share')} · AED<input inputMode="decimal" required value={shares[person.userId]?.amount || ''} onChange={e => updateShare(person.userId, 'amount', e.target.value)} /></label>
          {person.userId !== owner.userId && <label>{t('Already received')} · AED<input inputMode="decimal" value={shares[person.userId]?.received || ''} placeholder="0.00" onChange={e => updateShare(person.userId, 'received', e.target.value)} /></label>}</div>
        </div>)}
      </section>
      {!data.home.profile.payment && <section className="card"><p>{t('Add your receiving details in your profile first.')}</p><button type="button" onClick={openProfile}>{t('Profile')}</button></section>}
      <p className="fine">{t('Creating this room records that you already paid the restaurant. It does not move money.')}</p>
      {error && <p className="form-message" role="alert">{error}</p>}
      <button className="primary wide" disabled={uploading || !data.home.profile.payment}>{t('Create payment room')}</button>
    </fieldset></form>
  </Page>;
}

export function PaymentReceipt({ room, data, payer }) {
  const [error, setError] = useState(''), [uploading, setUploading] = useState(false);
  const details = room.paymentRoom;
  return <article className="card stack"><p className="eyebrow">{t('PAYMENT ROOM')}</p><h2>{room.restaurant.name}</h2><p className="preserve-lines">{details.orderDetails}</p>
    {details.receiptPhoto && <details><summary>{t('View receipt photo')}</summary><a href={details.receiptPhoto} download="receipt.jpg"><img className="receipt-photo" src={details.receiptPhoto} alt={t('Receipt photo')} /></a></details>}
    {payer && room.phase === 'FULFILLED' && <label>{t('Add or replace receipt photo')}<input type="file" accept="image/jpeg,image/png,image/webp" disabled={uploading || data.busy} onChange={async event => { const file = event.target.files?.[0]; if (!file) return; setUploading(true); setError(''); const revision = room.revision; try { const photo = await receiptPhotoData(file); await data.send('UPDATE_PAYMENT_RECEIPT', { expectedRevision: revision, paymentRoom: { details: { ...details, receiptPhoto: photo }, shares: [] } }, room.id); } catch (e) { setError(e.message); } finally { setUploading(false); } }} /></label>}
    {error && <p role="alert">{error}</p>}
  </article>;
}

export function PaymentShareEditor({ room, receipts, data }) {
  const [draft, setDraft] = useState(null), [error, setError] = useState('');
  return <article className="card stack"><h2>{t('Everyone’s shares')}</h2><p>{t('Updated totals apply automatically. No new approval is needed.')}</p>{receipts.map(receipt => <section key={receipt.memberId}>
    <div className="order-line"><b>{receipt.name}</b><span>{money(receipt.total, room.restaurant.currency)} <button className="link" onClick={() => { setDraft({ memberId: receipt.memberId, amount: (receipt.food / 100).toFixed(2), description: receipt.lines[0]?.description || t('Food order'), revision: room.revision }); setError(''); }}>{t('Edit share')}</button></span></div>
    {draft?.memberId === receipt.memberId && <form className="stack" onSubmit={async e => { e.preventDefault(); try { const reply = await data.send('UPDATE_PAYMENT_SHARE', { memberId: draft.memberId, amount: amount(draft.amount, room.restaurant.currency), text: draft.description, expectedRevision: draft.revision }, room.id); if (reply) setDraft(null); } catch (e) { setError(e.message); } }}>
      <label>{t('Their order')}<input required maxLength={160} value={draft.description} onChange={e => setDraft({ ...draft, description: e.target.value })} /></label>
      <label>{t('Share')} · AED<input inputMode="decimal" required value={draft.amount} onChange={e => setDraft({ ...draft, amount: e.target.value })} /></label>
      <div className="hero-actions"><button className="primary" disabled={data.busy}>{t('Save share')}</button><button type="button" className="secondary" onClick={() => setDraft(null)}>{t('Cancel')}</button></div>{error && <p role="alert">{error}</p>}
    </form>}
  </section>)}</article>;
}

export function RecordPayment({ room, receipt, data }) {
  const [value, setValue] = useState(''), [reference, setReference] = useState(''), [error, setError] = useState('');
  if (receipt.memberId === room.payerId || receipt.balance <= 0 || room.transfers.some(transfer => transfer.memberId === receipt.memberId && transfer.status === 'declared')) return null;
  return <details><summary>{t('Record payment received')}</summary><form className="stack" onSubmit={async e => { e.preventDefault(); try { const reply = await data.send('RECORD_PAYMENT', { memberId: receipt.memberId, amount: amount(value, room.restaurant.currency), text: reference.trim() }, room.id); if (reply) { setValue(''); setReference(''); setError(''); } } catch (e) { setError(e.message); } }}>
    <label>{t('Amount received')} · AED<input required inputMode="decimal" value={value} onChange={e => setValue(e.target.value)} /></label>
    <label>{t('Payment reference or cash note')}<input required maxLength={160} value={reference} onChange={e => setReference(e.target.value)} /></label>
    <button className="secondary" disabled={data.busy}>{t('Confirm received')}</button>{error && <p role="alert">{error}</p>}
  </form></details>;
}
