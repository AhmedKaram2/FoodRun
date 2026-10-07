import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { money } from './client.js';
import { getLanguage, t } from './i18n.js';
const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;
export const walletTransactionTitle = kind => kind === 'TOP_UP' ? tx('Wallet top-up', 'شحن المحفظة') : kind === 'CASH_TRANSFER' ? tx('Holder cash transfer', 'تحويل نقدي من حامل الأموال') : tx('Wallet payment', 'دفعة من المحفظة');
export function walletTransactionStatus(value) {
  const labels = {PENDING:['Awaiting top-up confirmation','بانتظار تأكيد الشحن'],CONFIRMED:['Wallet credited','تم شحن المحفظة'],REJECTED:['Not received','لم يتم الاستلام'],APPROVAL_PENDING:['Awaiting wallet approval','بانتظار الموافقة على دفعة المحفظة'],OWING:['Holder still needs to send cash','على حامل الأموال إرسال المبلغ'],SENT:['Cash sent; awaiting confirmation','أُرسل المبلغ؛ بانتظار التأكيد'],SETTLED:['Cash settled','تمت تسوية المبلغ'],RETURNED:['Payment declined; funds returned','رُفضت الدفعة وأُعيد الرصيد']};
  return tx(...(labels[value] || [value,value]));
}
export default function WalletHistoryDialog({ data, wallet, onClose }) {
  const dialog = useRef(null), generation = useRef(0), controller = useRef(null), query = useRef(data.walletQuery);
  query.current = data.walletQuery;
  const [history, setHistory] = useState(null), [loading, setLoading] = useState(false), [error, setError] = useState('');
  const key = {customerId:wallet.customerId,holderId:wallet.holderId,currency:wallet.currency};
  const load = async (cursor = '') => {
    controller.current?.abort(); const abort = new AbortController(); controller.current = abort;
    const epoch = ++generation.current; setLoading(true); setError(''); if(!cursor) setHistory(null);
    try {
      const reply = await query.current('WALLET_HISTORY', {walletKey:key,currency:key.currency,walletHistoryCursor:cursor}, abort.signal);
      if(abort.signal.aborted || epoch !== generation.current) return;
      if(!reply.walletHistory) throw Error(tx('Could not load wallet transactions.', 'تعذر تحميل معاملات المحفظة.'));
      setHistory(old => ({...reply.walletHistory,transactions:cursor ? [...new Map([...(old?.transactions || []),...reply.walletHistory.transactions].map(value=>[value.id,value])).values()] : reply.walletHistory.transactions}));
    } catch(failure) { if(!abort.signal.aborted && epoch===generation.current) setError(failure.message); }
    finally { if(!abort.signal.aborted && epoch===generation.current) setLoading(false); }
  };
  useEffect(() => { dialog.current?.showModal(); return () => { ++generation.current; controller.current?.abort(); }; }, []);
  useEffect(() => { load(); }, [wallet.customerId,wallet.holderId,wallet.currency,data.identityToken,data.home.wallet]);
  const balance = history?.balance || wallet;
  const date = value => new Date(value).toLocaleString(getLanguage()==='ar'?'ar-AE':'en-GB');
  return createPortal(<dialog ref={dialog} className="card wallet-history-dialog" dir={getLanguage()==='ar'?'rtl':'ltr'} aria-labelledby="wallet-history-title" onCancel={onClose} onClose={onClose}>
    <div className="section-title compact"><h2 id="wallet-history-title">{tx('Wallet transactions','معاملات المحفظة')}</h2><button className="secondary" type="button" onClick={onClose}>{t('Close')}</button></div>
    <p>{balance.customerName} · {tx('Held by','لدى')} {balance.holderName}</p><div className="wallet-history-balance"><span>{tx('Available balance','الرصيد المتاح')}</span><strong>{money(balance.available,balance.currency)}</strong></div>
    <button className="link" disabled={loading} onClick={()=>load()}>{t('Refresh')}</button>
    <div className="stack wallet-history-entries">{(history?.transactions || []).map(value => <article className="wallet-item stack" key={value.id}>
      <div className="section-title compact"><b>{walletTransactionTitle(value.kind)}</b><strong>{value.balanceChange>0 ? '+' : value.balanceChange<0 ? '−' : ''}{money(value.amount,value.currency)}</strong></div>
      <span className="status">{walletTransactionStatus(value.status)}</span><time dateTime={new Date(value.createdAt).toISOString()}>{date(value.createdAt)}</time>
      <p>{tx('From','من')}: {value.fromName}<br/>{tx('To','إلى')}: {value.toName}</p>
      {(value.orders || []).map((order,index)=><p key={index}>{order.roomName} #{order.orderNumber} · {money(order.amount,value.currency)}</p>)}
      {value.account && <small>{value.account.holder} · {value.account.bank}<br/><bdi>{value.account.identifier}</bdi></small>}
      {value.note && <p>{tx('Note','ملاحظة')}: {value.note}</p>}
      {value.resolvedAt>0 && <small>{tx('Reviewed','تمت المراجعة')}: {date(value.resolvedAt)}</small>}
      {value.kind==='CASH_TRANSFER' && <small>{tx('Already deducted from the wallet; this transfer does not deduct it again.','خُصم المبلغ من المحفظة سابقاً؛ لا يخصمه هذا التحويل مرة أخرى.')}</small>}
      {value.status==='RETURNED' && <small>{tx('The reserved amount was returned to this wallet.','أُعيد المبلغ المحجوز إلى هذه المحفظة.')}</small>}
    </article>)}</div>
    {loading && <p role="status">{t('Loading…')}</p>}{error && <p role="alert">{t(error)}</p>}
    {!loading && !error && history && !history.transactions.length && <p>{tx('No transactions yet.','لا توجد معاملات بعد.')}</p>}
    {error && <button className="secondary" disabled={loading} onClick={()=>load(history?.nextCursor || '')}>{t('Retry')}</button>}
    {history?.nextCursor && <button className="secondary wide" disabled={loading} onClick={()=>load(history.nextCursor)}>{tx('Load older transactions','تحميل معاملات أقدم')}</button>}
  </dialog>,document.body);
}
