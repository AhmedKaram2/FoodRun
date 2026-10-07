import { money } from './client.js';
import { getLanguage, t } from './i18n.js';

const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;

export default function WalletCustody({ room, data }) {
  const payments = room.walletPayments || [];
  if (!payments.length) return null;
  const userId = data.home.profile.userId;
  const batchIds = new Set(payments.filter(value => value.status === 'SENT').map(value => value.batchId));
  const batches = (data.home.wallet?.batches || []).filter(value => batchIds.has(value.id) && value.status === 'PENDING' && value.recipientId === userId);
  return <section className="card stack wallet-custody" aria-label={tx('Wallet payment responsibility', 'مسؤولية دفعات المحفظة')}>
    <h3>{tx('Collect wallet payments from', 'استلام دفعات المحفظة من')}</h3>
    {payments.map(payment => <article className="wallet-item stack" key={payment.id}>
      <div className="section-title compact"><b>{payment.holderName}</b><strong>{money(payment.amount, payment.currency)}</strong></div>
      <p>{tx('On behalf of', 'نيابة عن')} <b>{payment.customerName}</b> · {tx('Pay to', 'الدفع إلى')} {payment.recipientName}</p>
      <span className="status">{payment.status === 'SETTLED' ? tx('Receipt confirmed', 'تم تأكيد الاستلام') : payment.status === 'SENT' ? tx('Sent · awaiting recipient confirmation', 'تم الإرسال · بانتظار تأكيد المستلم') : tx('Wallet holder still needs to pay', 'على حامل أموال المحفظة دفع المبلغ')}</span>
    </article>)}
    {batches.map(batch => <article className="wallet-item stack" key={batch.id}>
      <h4>{tx('Payment sent by', 'دفعة مرسلة من')} {batch.holderName} · {money(batch.amount, batch.currency)}</h4>
      {(data.home.wallet?.payments || []).filter(value => batch.paymentIds.includes(value.id)).map(value => <small key={value.id}>{tx('On behalf of', 'نيابة عن')} {value.customerName} · {value.roomName} #{value.orderNumber} · {money(value.amount, value.currency)}</small>)}
      {batch.note && <p>{batch.note}</p>}
      <p>{tx('Confirm after receiving the full grouped amount.', 'أكد بعد استلام كامل المبلغ المجمع.')}</p>
      <div className="hero-actions"><button className="primary" disabled={data.busy} onClick={() => data.send('WALLET_REVIEW_BATCH', { transferId: batch.id, flag: true })}>{tx('Confirm full amount received', 'تأكيد استلام كامل المبلغ')}</button><button className="secondary" disabled={data.busy} onClick={() => data.send('WALLET_REVIEW_BATCH', { transferId: batch.id, flag: false })}>{t('Not received')}</button></div>
    </article>)}
  </section>;
}
