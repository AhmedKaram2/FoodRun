import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { t, tf } from './i18n.js';
import { canRemindPayment, reminderCooldown, reminderKey } from './paymentReminders.js';

export function PaymentReminderButton({ room, receipt, memberId, data }) {
  const [open, setOpen] = useState(false), [email, setEmail] = useState(''), [message, setMessage] = useState('');
  const dialog = useRef(null), sending = useRef(false);
  const checker = useRef(data.checkPaymentReminder); checker.current = data.checkPaymentReminder;
  const key = reminderKey(room, receipt.memberId), state = data.paymentReminderStates?.[key];
  const last = data.paymentReminderTimes?.[key];
  const coolingDown = last !== undefined && Date.now() - last < reminderCooldown && state !== 'failed';
  const eligible = canRemindPayment(room, memberId, receipt);
  useEffect(() => { if (open) dialog.current?.showModal(); else dialog.current?.close(); }, [open]);
  useEffect(() => { if (!eligible) setOpen(false); }, [eligible]);
  useEffect(() => {
    if (state !== 'pending' || !eligible || !data.online[room.id]) return;
    const timer = setInterval(() => checker.current?.(room.id, receipt.memberId), 5000);
    return () => clearInterval(timer);
  }, [state, eligible, data.online[room.id], room.id, receipt.memberId]);
  if (!eligible) return null;
  const send = async (address = '') => {
    if (sending.current || data.busy) return;
    sending.current = true; setMessage('');
    try {
      const reply = await data.send('REMIND_PAYMENT', { memberId: receipt.memberId, ...(address ? { text: address.trim() } : {}) }, room.id);
      if (reply?.code === 'REMINDER_EMAIL_REQUIRED') { setEmail(''); setOpen(true); }
      else if (reply?.ok && reply.code !== 'REMINDER_FAILED') setOpen(false);
      else setMessage(t('Email could not be sent. Please try again.'));
    } finally { sending.current = false; }
  };
  return <>
    <button type="button" className="secondary" disabled={data.busy || coolingDown || !data.online[room.id]} onClick={() => send()}>
      {t(state === 'notified' && coolingDown ? 'Reminder sent in app' : state === 'sent' && coolingDown ? 'Email reminder sent' : state === 'pending' && coolingDown ? 'Sending email reminder…' : 'Send payment reminder')}
    </button>
    {open && createPortal(<dialog ref={dialog} className="card poll-dialog" aria-labelledby={`reminder-email-${receipt.memberId}`} onCancel={event => { if (data.busy) event.preventDefault(); else setOpen(false); }}>
      <form className="stack" onSubmit={event => { event.preventDefault(); send(email); }}>
        <h2 id={`reminder-email-${receipt.memberId}`}>{t('Recipient email')}</h2>
        <p>{tf('Enter an email address for {name} to receive this reminder.', { name: receipt.name })}</p>
        <label>{t('Email address')}<input type="email" value={email} onChange={event => setEmail(event.target.value)} required maxLength={254} autoFocus disabled={data.busy} /></label>
        {(message || data.error) && <p className="form-message" role="alert">{data.error || message}</p>}
        <div className="hero-actions"><button type="button" className="secondary" disabled={data.busy} onClick={() => setOpen(false)}>{t('Cancel')}</button><button className="primary" disabled={data.busy}>{t('Send payment reminder')}</button></div>
      </form>
    </dialog>, document.body)}
  </>;
}
