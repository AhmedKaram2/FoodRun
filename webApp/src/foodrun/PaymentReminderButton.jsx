import { t } from './i18n.js';
import { canRemindPayment, reminderCooldown, reminderKey } from './paymentReminders.js';

export function PaymentReminderButton({ room, receipt, memberId, data }) {
  if (!canRemindPayment(room, memberId, receipt)) return null;
  const last = data.paymentReminderTimes?.[reminderKey(room, receipt.memberId)];
  const queued = last !== undefined && Date.now() - last < reminderCooldown;
  return <button type="button" className="secondary" disabled={data.busy || queued || !data.online[room.id]}
    onClick={() => data.send('REMIND_PAYMENT', { memberId: receipt.memberId }, room.id)}>
    {t(queued ? 'Email reminder queued' : 'Send payment reminder')}
  </button>;
}
