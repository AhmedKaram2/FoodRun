import { useState } from 'react';
import { money } from './client.js';
import { t, tf } from './i18n.js';
import ServiceSupportNote from './ServiceSupportNote.jsx';

const labels = { EXCLUDE: 'Exclude me from selection · AED 10', HALF_CHANCE: 'Reduce my chance by 50% · AED 5' };
const statusLabels = { REQUESTED: 'Waiting for owner approval', AWAITING_PAYMENT: 'Approved · pay the room owner',
  PAYMENT_DECLARED: 'Waiting for owner to confirm payment', ACTIVE: 'Active for this order', REJECTED: 'Request declined' };

export default function WheelProtection({ room, me, data }) {
  const [reference, setReference] = useState('');
  const owner = room.ownerId === me.id;
  if (!me.approved || me.guest || room.paymentRoom) return null;
  const requests = room.wheelProtections || [];
  const mine = requests.findLast(request => request.memberId === me.id);
  const canRequest = !owner && me.participating && me.eligible && room.phase === 'LOBBY' && (!mine || mine.status === 'REJECTED');
  const canManage = room.phase === 'LOBBY';
  const canConfirm = canManage || room.phase === 'ARCHIVED' && room.autoArchivedAt > 0;
  const send = (kind, request, fields = {}) => data.send(kind, { transferId: request.id, ...fields }, room.id);
  if (!canManage && !requests.length) return null;
  return <article className="card stack wheel-protection">
    <div><p className="eyebrow">{t('WHEEL OPTIONS')}</p><h3>{t('Please don’t pick me')}</h3></div>
    <p className="field-help">{t('For this order only. The room owner approves first, then confirms receiving your payment. You still order food and pay your share.')}</p>
    <ServiceSupportNote />
    {canRequest && <div className="wheel-protection-options">{Object.entries(labels).map(([plan, label]) => <button type="button" className="secondary" key={plan} disabled={data.busy || !data.online[room.id]} onClick={() => data.send('REQUEST_WHEEL_PROTECTION', { text: plan }, room.id)}>{t(label)}</button>)}</div>}
    {(owner ? requests.filter(request => request.status !== 'REJECTED') : requests.filter(request => request.memberId === me.id || request.status === 'ACTIVE')).map(request => <section className="wheel-protection-request" key={request.id}>
      <div className="section-title compact"><b>{room.members.find(member => member.id === request.memberId)?.name}</b><strong><bdi>{money(request.amount, 'AED')}</bdi></strong></div>
      <p>{t(labels[request.plan])}</p><small role="status">{t(statusLabels[request.status])}</small>
      {request.reference && <p className="field-help">{request.reference}</p>}
      {canManage && owner && ['REQUESTED', 'AWAITING_PAYMENT'].includes(request.status) && <div className="hero-actions">{request.status === 'REQUESTED' && <button className="primary" type="button" disabled={data.busy} onClick={() => send('REVIEW_WHEEL_PROTECTION', request, { flag: true })}>{t('Approve request')}</button>}<button className="secondary" type="button" disabled={data.busy} onClick={() => send('REVIEW_WHEEL_PROTECTION', request, { flag: false })}>{t('Decline')}</button></div>}
      {canManage && !owner && request.memberId === me.id && request.status === 'REQUESTED' && <button type="button" className="link" disabled={data.busy} onClick={() => send('REVIEW_WHEEL_PROTECTION', request, { flag: false })}>{t('Cancel request')}</button>}
      {canManage && request.memberId === me.id && request.status === 'AWAITING_PAYMENT' && <form className="stack" onSubmit={event => { event.preventDefault(); if (!data.busy) send('DECLARE_WHEEL_PAYMENT', request, { amount: request.amount, text: reference.trim() || 'Paid the room owner' }); }}>
        <p>{tf('Pay {amount} to {name} outside Food Run, then mark it paid.', { amount: money(request.amount, 'AED'), name: room.members.find(member => member.id === request.recipientId)?.name || t('Room owner') })}</p>
        {(room.wheelProtectionAccounts || []).map(account => <div className="payment-details" key={account.id}><b>{account.holder} · {account.bank}</b><code dir="ltr">{account.identifier}</code></div>)}
        <label>{t('Payment note (optional)')}<input value={reference} onChange={event => setReference(event.target.value)} maxLength={160} disabled={data.busy} placeholder={t('Bank transfer or cash')} /></label>
        <div className="hero-actions"><button className="primary" disabled={data.busy}>{t('I paid the room owner')}</button><button className="secondary" type="button" disabled={data.busy} onClick={() => send('REVIEW_WHEEL_PROTECTION', request, { flag: false })}>{t('Cancel request')}</button></div>
      </form>}
      {canConfirm && owner && request.status === 'PAYMENT_DECLARED' && <div className="hero-actions"><button type="button" className="primary" disabled={data.busy} onClick={() => send('CONFIRM_WHEEL_PAYMENT', request, { flag: true, amount: request.amount })}>{t(canManage ? 'Confirm received & activate' : 'Confirm received')}</button><button type="button" className="secondary" disabled={data.busy} onClick={() => send('CONFIRM_WHEEL_PAYMENT', request, { flag: false, amount: request.amount })}>{t('Not received')}</button></div>}
      {!canManage && request.status !== 'ACTIVE' && <p className="field-help">{t('Selection has started. This request is no longer payable.')}</p>}
    </section>)}
    <p className="fine">{t('Pay only after approval. The fee is separate from your food bill. At least one eligible member must keep their normal chance.')}</p>
  </article>;
}
