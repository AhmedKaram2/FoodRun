import React, { useState } from 'react';
import { t, getLanguage } from './i18n.js';
import { notificationTarget } from './notifications.js';
import { restaurantOrderText } from './restaurantOrderText.js';
import { money } from './client.js';
const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;

export function NotificationCenter({ notifications, onOpen, onBack }) {
  return <main className="page-shell stack notification-center"><div className="section-title"><h1>{t('Notifications')}</h1><button className="secondary" onClick={onBack}>{t('Back')}</button></div>
    <section className="card stack"><p>{tx('Orders, payments, and the next action for your table.', 'الطلبات والمدفوعات والخطوة التالية لغرفتك.')}</p>
      <div className="hero-actions">{notifications.configured === false ? <p>{tx("Browser push setup is being completed. Your updates are available in this inbox.", "جارٍ استكمال إشعارات المتصفح. تحديثاتك متاحة في صندوق الإشعارات هنا.")}</p> : <button className="primary" onClick={() => notifications.enable()}>{notifications.enabled ? tx('Notifications enabled', 'الإشعارات مفعلة') : t('Enable notifications')}</button>}{notifications.enabled && <button className="secondary" onClick={notifications.disable}>{tx('Turn off device notifications', 'إيقاف إشعارات هذا الجهاز')}</button>}</div>
      {notifications.error && <p role="alert">{notifications.error}</p>}</section>
    {notifications.items.length === 0 && <section className="card"><p>{tx('Your order and payment updates will appear here.', 'ستظهر تحديثات الطلبات والمدفوعات هنا.')}</p></section>}
    {notifications.items.map(item => <article className="card stack" key={item.id}><div className="section-title"><h2>{item.title}</h2>{!item.read && <span className="status live">{tx('New', 'جديد')}</span>}</div><p>{item.body}</p><small>{new Date(item.createdAt).toLocaleString(getLanguage() === 'ar' ? 'ar-AE' : 'en-AE')}</small><div className="hero-actions">{item.actions.map(action => <button className="secondary" key={action.id} onClick={() => onOpen(item, action.id)}>{action.title}</button>)}<button className="link" onClick={() => notifications.read(item.id)}>{tx('Mark read', 'تحديد كمقروء')}</button></div></article>)}
  </main>;
}

export function NotificationActionCard({ selected, data, onClose }) {
  const [copyLanguage, setCopyLanguage] = useState(() => ['en', 'ar'].includes(localStorage.getItem('foodrun-copy-language-v1')) ? localStorage.getItem('foodrun-copy-language-v1') : getLanguage());
  const [status, setStatus] = useState('');
  const { item, action } = selected, reply = data.rooms[item.roomId], room = reply?.room, memberId = data.sessions[item.roomId]?.memberId;
  const target = notificationTarget(item, action, reply, memberId);
  const live = !!data.online[item.roomId] && !data.busy;
  const text = room ? restaurantOrderText(room, reply.receipts, copyLanguage) : '';
  const transfer = room?.transfers.find(value => value.id === item.transferId);
  const copy = async () => { try { await navigator.clipboard.writeText(text); setStatus(tx('Order copied', 'تم نسخ الطلب')); } catch { setStatus(tx('Copy is unavailable. Select the order text below.', 'النسخ غير متاح. حدد نص الطلب أدناه.')); } };
  const share = async () => { try { if (navigator.share) await navigator.share({ text }); else await copy(); } catch (error) { if (error.name !== 'AbortError') setStatus(error.message); } };
  const send = async (kind, fields = {}) => { if (await data.send(kind, fields, room.id)) onClose(); };
  const payment = () => { document.querySelector('#room-payment')?.scrollIntoView({ behavior: 'smooth', block: 'start' }); onClose(); };
  return <aside className="card stack notification-action" aria-label={t('Notifications')}><div className="section-title"><h2>{item.title}</h2><button className="icon-button" aria-label={t('Close')} onClick={onClose}>×</button></div>
    {!room ? <p>{t('Connecting…')}</p> : target === 'expired' ? <p>{tx('This action is no longer available. See the current room status below.', 'هذا الإجراء لم يعد متاحاً. راجع حالة الغرفة الحالية بالأسفل.')}</p> : <>
      {target === 'order' && <><label>{t('Order list language')}<select value={copyLanguage} onChange={event => { setCopyLanguage(event.target.value); localStorage.setItem('foodrun-copy-language-v1', event.target.value); }}><option value="en">English</option><option value="ar">العربية</option></select></label><pre dir={copyLanguage === 'ar' ? 'rtl' : 'ltr'}>{text}</pre><div className="hero-actions"><button className="secondary" onClick={copy}>{tx('Copy order', 'نسخ الطلب')}</button><button className="secondary" onClick={share}>{tx('Share order', 'مشاركة الطلب')}</button>{['COLLECTING', 'REVIEW'].includes(room.phase) && <button className="primary" disabled={!live || !reply.progress?.canReview} onClick={() => send('PLACE')}>{tx('I sent the full order', 'أرسلت الطلب الكامل')}</button>}</div>{!reply.progress?.canReview && ['COLLECTING', 'REVIEW'].includes(room.phase) && <p>{t(reply.progress?.reviewBlocker || '')}</p>}</>}
      {target === 'confirm' && <><p>{room.members.find(member => member.id === transfer.memberId)?.name} · {money(transfer.amount, room.restaurant.currency)}</p><p>{transfer.reference}</p><p>{tx('Confirm only after checking that the money reached your account.', 'أكد بعد التحقق من وصول المبلغ إلى حسابك.')}</p><button className="primary" disabled={!live} onClick={() => send('CONFIRM_TRANSFER', { transferId: transfer.id })}>{tx('Confirm payment received', 'تأكيد استلام الدفعة')}</button></>}
      {target === 'pay' && <button className="primary" onClick={payment}>{tx('Open my payment', 'فتح دفعتي')}</button>}
      {target === 'accept' && <button className="primary" disabled={!live} onClick={() => send('ACCEPT_DUTY')}>{tx('Accept selection', 'قبول الاختيار')}</button>}
      {target === 'open' && <button className="secondary" onClick={onClose}>{tx('Continue in room', 'متابعة في الغرفة')}</button>}
    </>}{status && <p role="status">{status}</p>}
  </aside>;
}
