import { useEffect, useState } from 'react';
import { amount, money, minorInput, CURRENCIES } from './client.js';
import { getLanguage, t } from './i18n.js';
import { myWalletBalances, walletAvailable, walletPaymentGroups, walletTotals } from './wallet.js';

const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;
const uid = data => data.home.profile.userId || data.user?.uid;

function ReceivingMethods({ methods, selected, onSelect }) {
  if (!methods.length) return <p className="field-help">{tx('This person has no receiving method in this currency. Choose another person or currency.', 'هذا الشخص ليس لديه وسيلة استلام بهذه العملة. اختر شخصاً أو عملة أخرى.')}</p>;
  return <div className="stack wallet-methods"><label>{t('Payment method')}<select value={selected} onChange={event => onSelect(event.target.value)}>{methods.map(method => <option key={method.id} value={method.id}>{method.bank} · {method.identifier}</option>)}</select></label>{methods.filter(method => method.id === selected).map(method => <div className="wallet-method" key={method.id}><b>{method.holder} · {method.bank}</b><code dir="ltr">{method.identifier}</code><small>{method.currency}</small></div>)}</div>;
}

function TopUp({ data, onClose }) {
  const initial = data.walletTopUpRequest;
  const [value, setValue] = useState(() => initial ? minorInput(initial.amount, initial.currency) : '100'), [currency, setCurrency] = useState(initial?.currency || data.home.profile.payment?.currency || 'AED');
  const [search, setSearch] = useState(''), [people, setPeople] = useState([]), [person, setPerson] = useState(null);
  const [recipient, setRecipient] = useState(null), [accountId, setAccountId] = useState(''), [note, setNote] = useState('');
  const [loading, setLoading] = useState(false), [error, setError] = useState('');
  const [searched, setSearched] = useState(false);
  useEffect(() => {
    const query = search.trim();
    setPeople([]); setSearched(false); setError(''); setLoading(false);
    if (!query) return;
    const controller = new AbortController();
    const timer = setTimeout(async () => {
      setLoading(true);
      try { const reply = await data.walletQuery('WALLET_PEOPLE', { text: query }, controller.signal); if (!controller.signal.aborted) { setPeople(reply.walletPeople || []); setSearched(true); } }
      catch (failure) { if (!controller.signal.aborted) setError(failure.message); }
      finally { if (!controller.signal.aborted) setLoading(false); }
    }, 250);
    return () => { clearTimeout(timer); controller.abort(); };
  }, [search, data.identityToken]);
  useEffect(() => {
    if (!person) return;
    const controller = new AbortController(); setRecipient(null); setAccountId(''); setLoading(true); setError('');
    data.walletQuery('WALLET_RECIPIENT', { userId: person.userId, currency }, controller.signal).then(reply => {
      if (controller.signal.aborted) return;
      setRecipient(reply.walletRecipient); setAccountId(reply.walletRecipient?.accounts[0]?.id || '');
    }).catch(failure => { if(!controller.signal.aborted) setError(failure.message); }).finally(() => { if(!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [person?.userId, currency, data.identityToken]);
  const submit = async event => {
    event.preventDefault(); setError('');
    try {
      const minor = amount(value, currency);
      if(minor <= 0) throw Error(tx('Enter a positive top-up amount.', 'أدخل مبلغ شحن أكبر من صفر.'));
      if(await data.send('WALLET_TOP_UP', { amount: minor, currency, userId: person.userId, accountId, text: note })) {
        data.setNotice(tx('Top-up pending. Your balance increases after the holder confirms receipt.', 'الشحن قيد التأكيد. يزداد رصيدك بعد تأكيد الشخص استلام المبلغ.')); onClose();
      }
    } catch(failure) { setError(failure.message); }
  };
  return <form className="card stack wallet-top-up" onSubmit={submit}>
    <div className="section-title compact"><h3>{tx('Charge my wallet', 'شحن محفظتي')}</h3><button type="button" className="secondary" onClick={onClose}>{t('Cancel')}</button></div>
    <div className="form-grid two"><label>{tx('Top-up amount', 'مبلغ الشحن')}<input inputMode="decimal" value={value} onChange={event => setValue(event.target.value)} required /></label><label>{t('Currency')}<select value={currency} onChange={event => setCurrency(event.target.value)}>{CURRENCIES.map(item => <option key={item}>{item}</option>)}</select></label></div>
    <label>{tx('Choose who will hold your money', 'اختر الشخص الذي سيحتفظ بأموالك')}<input type="search" value={search} autoCapitalize="none" autoCorrect="off" spellCheck={false} maxLength={254} onChange={event => { setSearch(event.target.value); setPeople([]); setPerson(null); setRecipient(null); setAccountId(''); }} placeholder={tx('Search by name or email', 'ابحث بالاسم أو البريد الإلكتروني')} /></label>
    {!search.trim() && <p className="field-help">{tx('Start typing a name or email to find someone.', 'ابدأ بكتابة اسم أو بريد إلكتروني للبحث عن شخص.')}</p>}
    {search.trim() && people.length > 0 && <div className="wallet-people" role="group" aria-label={tx('Search results', 'نتائج البحث')}>{people.map(value => <button type="button" className={`secondary ${person?.userId === value.userId ? 'selected' : ''}`} aria-pressed={person?.userId === value.userId} key={value.userId} onClick={() => setPerson(value)}>{value.name}</button>)}</div>}
    {search.trim() && searched && !loading && !people.length && <p role="status">{tx('No matching users.', 'لا يوجد مستخدمون مطابقون.')}</p>}
    {loading && <p role="status">{t('Loading…')}</p>}
    {recipient && <><h4>{recipient.person.name}</h4><ReceivingMethods methods={recipient.accounts} selected={accountId} onSelect={setAccountId} /><p className="field-help">{tx('Send the money using this method, then mark it sent. Confirming here does not transfer money from your bank.', 'أرسل المبلغ باستخدام وسيلة الاستلام، ثم أكد الإرسال. هذا الزر لا يحوّل الأموال من حسابك البنكي.')}</p><label>{t('Payment note (optional)')}<input value={note} onChange={event => setNote(event.target.value)} maxLength={160} /></label><button className="primary" disabled={data.busy || loading || !accountId}>{tx('I sent the top-up', 'أرسلت مبلغ الشحن')}</button></>}
    {error && <p role="alert" className="form-message">{t(error)}</p>}
  </form>;
}

function BatchPayment({ group, data }) {
  const [expanded, setExpanded] = useState(false), [recipient, setRecipient] = useState(null), [accountId, setAccountId] = useState(''), [note, setNote] = useState(''), [error, setError] = useState('');
  useEffect(() => {
    if(!expanded) return;
    const controller = new AbortController(); setError('');
    data.walletQuery('WALLET_RECIPIENT', { userId: group.recipientId, currency: group.currency }, controller.signal).then(reply => { if (!controller.signal.aborted) { setRecipient(reply.walletRecipient); setAccountId(reply.walletRecipient?.accounts[0]?.id || ''); } }).catch(failure => { if(!controller.signal.aborted) setError(failure.message); });
    return () => controller.abort();
  }, [expanded, group.recipientId, group.currency]);
  return <article className="wallet-item stack"><div className="section-title compact"><b>{group.recipientName}</b><strong>{money(group.amount, group.currency)}</strong></div>
    <p>{tx('One transfer covers these wallet payments:', 'تحويل واحد يغطي دفعات المحافظ التالية:')}</p>
    {group.payments.map(payment => <small key={payment.id}>{payment.customerName} · {payment.roomName} #{payment.orderNumber} · {money(payment.amount, payment.currency)}</small>)}
    {!expanded ? <button className="primary" onClick={() => setExpanded(true)}>{tx('Pay full group amount', 'دفع كامل المبلغ المجمع')}</button> : <form className="stack" onSubmit={async event => {
      event.preventDefault();
      if(await data.send('WALLET_DECLARE_BATCH', { userId: group.recipientId, currency: group.currency, amount: group.amount, accountId, text: note })) { setExpanded(false); data.setNotice(tx('Awaiting recipient confirmation for all payments in this group.', 'بانتظار تأكيد المستلم لجميع دفعات هذه المجموعة.')); }
    }}>{recipient && <ReceivingMethods methods={recipient.accounts} selected={accountId} onSelect={setAccountId} />}<label>{t('Payment note (optional)')}<input value={note} maxLength={160} onChange={event => setNote(event.target.value)} /></label><p className="field-help">{tx('Send the full amount first. All listed payments settle together when the recipient confirms receipt.', 'أرسل كامل المبلغ أولاً. تتم تسوية جميع الدفعات المعروضة معاً بعد تأكيد المستلم.')}</p><div className="hero-actions"><button className="primary" disabled={data.busy || !accountId}>{tx('I sent the full amount', 'أرسلت كامل المبلغ')}</button><button type="button" className="secondary" onClick={() => setExpanded(false)}>{t('Cancel')}</button></div></form>}
    {error && <p role="alert">{t(error)}</p>}
  </article>;
}

export default function WalletFunds({ data }) {
  const [charging, setCharging] = useState(!!data.walletTopUpRequest);
  const wallet = data.home.wallet, userId = uid(data);
  if(!wallet) return null;
  const mine = myWalletBalances(wallet, userId), held = wallet.balances.filter(value => value.holderId === userId && value.customerId !== userId);
  const groups = walletPaymentGroups(wallet, userId), topUps = wallet.topUps || [], batches = wallet.batches || [];
  const obligations = wallet.payments.filter(value => value.holderId === userId && value.status !== 'SETTLED');
  const received = wallet.payments.filter(value => value.recipientId === userId && value.status !== 'SETTLED');
  const review = (kind, id, received) => data.send(kind, { transferId: id, flag: received });
  return <section className="card stack wallet-funds" aria-label={tx('Wallet balance', 'رصيد المحفظة')}>
    {!charging && <><div className="section-title"><h2>{tx('Wallet balance', 'رصيد المحفظة')}</h2><button className="primary" disabled={data.busy} onClick={() => setCharging(true)}>{tx('Charge wallet', 'شحن المحفظة')}</button></div>
    <div className="wallet-balance-totals">{(mine.length ? walletTotals(mine) : [[data.home.profile.payment?.currency || 'AED', 0]]).map(([currency, value]) => <strong key={currency}>{money(value, currency)}</strong>)}</div>
    {mine.length > 0 && <p className="field-help">{tx('Available to spend. Your money is held by these people:', 'الرصيد المتاح للاستخدام. أموالك موجودة لدى هؤلاء الأشخاص:')}</p>}
    {!mine.length && <p className="field-help">{tx('Your balance increases after the holder confirms a top-up.', 'يزداد رصيدك بعد تأكيد الشخص استلام مبلغ الشحن.')}</p>}
    {mine.map(value => <div className="wallet-balance-row" key={`${value.holderId}:${value.currency}`}><span>{tx('Held by', 'لدى')} <b>{value.holderName}</b></span><strong>{money(value.available, value.currency)}</strong></div>)}</>}
    {charging && <TopUp data={data} onClose={() => { setCharging(false); data.clearWalletTopUp?.(); }} />}
    {topUps.filter(value => value.status === 'PENDING').map(value => <article className="wallet-item stack" key={value.id}><div className="section-title compact"><b>{value.customerId === userId ? `${tx('Top-up sent to', 'شحن مرسل إلى')} ${value.holderName}` : `${tx('Top-up from', 'شحن من')} ${value.customerName}`}</b><strong>{money(value.amount, value.currency)}</strong></div><small>{value.account.bank} · <bdi>{value.account.identifier}</bdi></small>{value.note && <p>{value.note}</p>}<span className="status">{t('Awaiting confirmation')}</span>{value.holderId === userId && <><p>{tx('Check your bank or cash receipt before confirming. This credits the sender’s wallet.', 'تحقق من وصول المبلغ إلى حسابك أو استلام النقد قبل التأكيد. سيُضاف المبلغ إلى محفظة المرسل.')}</p><div className="hero-actions"><button className="primary" disabled={data.busy} onClick={() => review('WALLET_REVIEW_TOP_UP', value.id, true)}>{t('Confirm received')}</button><button className="secondary" disabled={data.busy} onClick={() => review('WALLET_REVIEW_TOP_UP', value.id, false)}>{t('Not received')}</button></div></>}</article>)}
    {(held.length > 0 || obligations.length > 0) && <section className="stack"><h3>{tx('Money I hold for others', 'أموال الآخرين الموجودة لدي')}</h3>{held.map(value => <div className="wallet-balance-row" key={`${value.customerId}:${value.currency}`}><span>{value.customerName} · {tx('Available', 'متاح')}</span><b>{money(value.available, value.currency)}</b></div>)}<p className="field-help">{tx('Keep available funds for their owners. The payments below are already deducted from their wallets and must be sent to the order recipients.', 'احتفظ بالأرصدة المتاحة لأصحابها. الدفعات التالية خُصمت بالفعل من محافظهم ويجب إرسالها لمستلمي الطلبات.')}</p>{groups.map(group => <BatchPayment key={group.key} group={group} data={data} />)}</section>}
    {received.filter(value => value.status === 'OWING').map(value => <div className="wallet-item" key={value.id}><b>{money(value.amount, value.currency)}</b><p>{value.holderName} · {tx('will pay for', 'سيدفع عن')} {value.customerName} · {value.roomName} #{value.orderNumber}</p></div>)}
    {batches.filter(value => value.status === 'PENDING').map(value => <article className="wallet-item stack" key={value.id}><div className="section-title compact"><b>{value.recipientId === userId ? value.holderName : value.recipientName}</b><strong>{money(value.amount, value.currency)}</strong></div><span>{value.paymentIds.length} {tx('wallet payments', 'دفعات محافظ')} · {t('Awaiting confirmation')}</span>{wallet.payments.filter(payment => value.paymentIds.includes(payment.id)).map(payment => <small key={payment.id}>{payment.customerName} · {payment.roomName} #{payment.orderNumber} · {money(payment.amount, payment.currency)}</small>)}<small>{value.account.bank} · <bdi>{value.account.identifier}</bdi></small>{value.note && <p>{value.note}</p>}{value.recipientId === userId && <><p>{tx('Confirm only when the full amount reaches you. This settles every listed payment.', 'أكد فقط بعد استلام كامل المبلغ. سيتم تسوية جميع الدفعات المعروضة.')}</p><div className="hero-actions"><button className="primary" disabled={data.busy} onClick={() => review('WALLET_REVIEW_BATCH', value.id, true)}>{tx('Confirm full amount received', 'تأكيد استلام كامل المبلغ')}</button><button className="secondary" disabled={data.busy} onClick={() => review('WALLET_REVIEW_BATCH', value.id, false)}>{t('Not received')}</button></div></>}</article>)}
    <details><summary>{tx('Wallet activity', 'حركة المحفظة')}</summary>{topUps.filter(value => value.status !== 'PENDING').slice(0, 30).map(value => <div className="wallet-item" key={value.id}><b>{money(value.amount, value.currency)}</b> · {value.customerId === userId ? value.holderName : value.customerName}<p>{tx(value.status === 'CONFIRMED' ? 'Top-up confirmed' : 'Top-up not received', value.status === 'CONFIRMED' ? 'تم تأكيد الشحن' : 'لم يتم استلام الشحن')}</p><small>{new Date(value.createdAt).toLocaleString()}</small></div>)}{wallet.payments.filter(value => value.customerId === userId).slice(0, 30).map(value => <div className="wallet-item" key={value.id}><b>−{money(value.amount, value.currency)}</b><p>{value.roomName} #{value.orderNumber} · {value.holderName} → {value.recipientName}</p><small>{tx(value.status === 'SETTLED' ? 'Cash settled' : value.status === 'SENT' ? 'Holder sent cash; awaiting recipient' : 'Wallet paid; holder still needs to send cash', value.status === 'SETTLED' ? 'تمت تسوية النقد' : value.status === 'SENT' ? 'أرسل الشخص النقد؛ بانتظار المستلم' : 'تم الدفع بالمحفظة؛ على الشخص إرسال النقد')}</small></div>)}</details>
  </section>;
}

export function WalletPaymentOption({ data, room, receipt, canPay }) {
  const wallet = data.home?.wallet, userId = data.home ? uid(data) : '';
  const available = walletAvailable(wallet, userId, receipt.currency);
  const paid = (room.walletPayments || []).filter(value => value.memberId === receipt.memberId);
  return <div className="wallet-order-payment stack">
    {canPay && <><p>{tx('Pay directly below, or use your wallet balance.', 'ادفع مباشرة بالأسفل، أو استخدم رصيد محفظتك.')}</p><button className="secondary" disabled={data.busy || available < receipt.balance} onClick={async () => {
      if(await data.send('PAY_WITH_WALLET', { amount: receipt.balance }, room.id)) data.setNotice(tx('Wallet paid. The cash holder is responsible for the transfer.', 'تم الدفع بالمحفظة. الشخص الذي يحتفظ بالنقد مسؤول عن التحويل.'));
    }}>{tx('Pay with wallet', 'الدفع بالمحفظة')} · {money(receipt.balance, receipt.currency)}</button><small>{tx('Available wallet balance', 'رصيد المحفظة المتاح')}: {money(available, receipt.currency)}</small>{available < receipt.balance && <small>{tx('Top up to cover this payment, or pay directly.', 'اشحن محفظتك لتغطية المبلغ، أو ادفع مباشرة.')}</small>}</>}
    {canPay && available < receipt.balance && data.openWallet && <button className="secondary" onClick={() => data.openWallet({ amount: receipt.balance - available, currency: receipt.currency })}>{tx('Top up', 'اشحن')} · {money(receipt.balance - available, receipt.currency)}</button>}
    {paid.map(value => <p className="field-help" key={value.id}>{money(value.amount, value.currency)} · {value.holderName} → {value.recipientName} · {tx(value.status === 'SETTLED' ? 'Cash settled' : value.status === 'SENT' ? 'Awaiting cash receipt confirmation' : 'Wallet paid; cash holder needs to pay', value.status === 'SETTLED' ? 'تمت تسوية النقد' : value.status === 'SENT' ? 'بانتظار تأكيد استلام النقد' : 'تم الدفع بالمحفظة؛ على حامل النقد الدفع')}</p>)}
  </div>;
}
