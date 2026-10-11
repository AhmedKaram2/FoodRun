import {useState} from 'react';
import { money } from './client.js';
import {roomPaymentAccounts} from './paymentDetails.js';
import GroupSheet from './GroupSheet.jsx';
import { getLanguage, t } from './i18n.js';

const tx = (en, ar) => getLanguage().startsWith('ar') ? (t(en) === en ? ar : t(en)) : en;

export default function WalletCustody({ room, data }) {
  const [settling,setSettling]=useState(false),[selected,setSelected]=useState(''),[error,setError]=useState('');
  const payments = (room.walletPayments || []).filter(payment => payment.status !== 'SETTLED');
  if (!payments.length) return null;
  const userId = data.home.profile.userId || data.user?.uid;
  const memberId=data.sessions?.[room.id]?.memberId;
  const mine=payments.filter(payment=>payment.holderId===userId && payment.holderId!==payment.recipientId && payment.status==='OWING');
  const own=(data.rooms?.[room.id]?.receipts || []).find(receipt=>receipt.memberId===memberId);
  const ownAmount=Math.max(0,own?.balance || 0),walletAmount=mine.reduce((total,payment)=>total+payment.amount,0),total=ownAmount+walletAmount;
  const methods=roomPaymentAccounts(room),accountId=methods.some(method=>method.id===selected)?selected:room.account?.id || '';
  const canSettle=mine.length>0 && !!memberId && room.restaurantPaid && !(room.transfers || []).some(transfer=>transfer.memberId===memberId && String(transfer.status).toLowerCase()==='declared');
  const batchIds = new Set(payments.filter(value => value.status === 'SENT').map(value => value.batchId));
  const batches = (data.home.wallet?.batches || []).filter(value => batchIds.has(value.id) && value.status === 'PENDING' && (value.recipientId === userId || value.holderId===userId));
  return <section className="card stack wallet-custody" aria-label={tx('Wallet payment responsibility', 'مسؤولية دفعات المحفظة')}>
    <h3>{tx('Collect wallet payments from', 'استلام دفعات المحفظة من')}</h3>
    {mine.length>0 && <article className="wallet-item stack"><h4>{t('Your total to pay')} · {money(total,room.restaurant.currency)}</h4><p>{t('Your own share')} · {money(ownAmount,room.restaurant.currency)}<br/>{t('On behalf of others')} · {money(walletAmount,room.restaurant.currency)}</p><button className="primary" disabled={data.busy || !canSettle} onClick={()=>{setError('');setSettling(true);}}>{t('Settle all payments')}</button></article>}
    {payments.map(payment => <article className="wallet-item stack" key={payment.id}>
      <div className="section-title compact"><b>{payment.holderName}</b><strong>{money(payment.amount, payment.currency)}</strong></div>
      <p>{tx('On behalf of', 'نيابة عن')} <b>{payment.customerName}</b> · {tx('Pay to', 'الدفع إلى')} {payment.recipientName}</p>
      <span className="status">{payment.status === 'SENT' ? t('Sent · awaiting recipient confirmation') : (room.transfers || []).some(transfer=>transfer.id===`wallet-${payment.id}` && String(transfer.status).toLowerCase()==='declared') ? t('Awaiting wallet approval') : tx('Wallet holder still needs to pay', 'على حامل أموال المحفظة دفع المبلغ')}</span>
    </article>)}
    {batches.map(batch => <article className="wallet-item stack" key={batch.id}>
      <h4>{tx('Payment sent by', 'دفعة مرسلة من')} {batch.holderName} · {money(batch.amount, batch.currency)}</h4>
      {(data.home.wallet?.payments || []).filter(value => batch.paymentIds.includes(value.id)).map(value => <small key={value.id}>{tx('On behalf of', 'نيابة عن')} {value.customerName} · {value.roomName} #{value.orderNumber} · {money(value.amount, value.currency)}</small>)}
      {batch.note && <p>{batch.note}</p>}
      <p>{batch.recipientId===userId ? tx('Confirm after receiving the full grouped amount.', 'أكد بعد استلام كامل المبلغ المجمع.') : t('Awaiting recipient confirmation')}</p>
      {batch.recipientId===userId && <div className="hero-actions"><button className="primary" disabled={data.busy} onClick={() => data.send('WALLET_REVIEW_BATCH', { transferId: batch.id, flag: true })}>{tx('Confirm full amount received', 'تأكيد استلام كامل المبلغ')}</button><button className="secondary" disabled={data.busy} onClick={() => data.send('WALLET_REVIEW_BATCH', { transferId: batch.id, flag: false })}>{t('Not received')}</button></div>}
    </article>)}
    {settling && <GroupSheet title={t('Settle all payments')} busy={data.busy} onClose={()=>setSettling(false)} tx={tx}><form className="stack" onSubmit={async event=>{event.preventDefault();if(data.busy || !canSettle)return;setError('');try{if(await data.send('WALLET_DECLARE_BATCH',{userId:mine[0].recipientId,currency:room.restaurant.currency,amount:total,accountId,flag:true},room.id))setSettling(false);}catch(failure){setError(failure.message);}}}><h3>{money(total,room.restaurant.currency)}</h3><p>{t('Your own share')} · {money(ownAmount,room.restaurant.currency)}<br/>{t('On behalf of others')} · {money(walletAmount,room.restaurant.currency)}</p><label>{t('Payment method')}<select value={accountId} onChange={event=>setSelected(event.target.value)}>{methods.map(method=><option key={method.id} value={method.id}>{method.bank} · {method.identifier}</option>)}</select></label>{methods.filter(method=>method.id===accountId).map(method=><p key={method.id}>{method.holder} · {method.bank}<br/><bdi>{method.identifier}</bdi></p>)}<p>{t('Send the full amount first. The recipient confirms all these payments together.')}</p>{(error || data.error) && <p role="alert">{error || data.error}</p>}<button className="primary" disabled={data.busy || !canSettle || !accountId}>{t('I sent the full amount')}</button></form></GroupSheet>}
  </section>;
}
