import { useState } from 'react';
import { CURRENCIES } from './client.js';
import { t } from './i18n.js';
import { paymentAccount, paymentDraft, profilePaymentAccounts, profileWithPaymentAccount, roomPaymentAccounts } from './paymentDetails.js';
import PaymentFields from './PaymentFields.jsx';

export default function PaymentMethodsEditor({ data, room, id = 'payer-account' }) {
  const profile = data.home.profile;
  const saved = profilePaymentAccounts(profile);
  const shared = roomPaymentAccounts(room);
  const all = room ? [...shared, ...saved.filter(account => !shared.some(method => method.id === account.id))] : saved;
  const mergedProfile = { ...profile, paymentAccounts: all };
  const methods = room ? all.filter(account => account.currency === room.restaurant.currency) : all;
  const current = room ? room.account || methods[0] : profile.payment;
  const [editor, setEditor] = useState(() => methods.length ? null : { previous: null, form: { ...paymentDraft(), method: room && room.restaurant.currency !== 'AED' ? 'BANK' : 'AANI', currency: room?.restaurant.currency || 'AED' } });
  const [message, setMessage] = useState('');
  const edit = account => { setMessage(''); setEditor({ previous: account || null, form: { ...paymentDraft(account), currency: room?.restaurant.currency || account?.currency || 'AED', method: account?.method || (room && room.restaurant.currency !== 'AED' ? 'BANK' : 'AANI') } }); };
  const persist = async updated => {
    setMessage('');
    try {
      const reply = await data.send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: { ...updated, userId: '', phone: profile.phone, language: profile.language || 'ar' } } });
      if (reply) { setEditor(null); setMessage(t('Payment methods saved.')); }
      else setMessage(t('Payment details were not saved. Please try again.'));
    } catch (error) { setMessage(t(error.message)); }
  };
  const save = async event => {
    event.preventDefault();
    try {
      const account = paymentAccount(editor.form, editor.previous, profile.name, true, room?.restaurant.currency || editor.form.currency);
      await persist(profileWithPaymentAccount(mergedProfile, account));
    } catch (error) { setMessage(t(error.message)); }
  };
  const remove = async account => {
    const remaining = all.filter(value => value.id !== account.id);
    const previousDefault = profile.payment || room?.account;
    await persist({ ...profile, payment: previousDefault?.id === account.id ? remaining[0] || null : previousDefault || null, paymentAccounts: remaining });
  };
  return <article id={id} className="card stack payment-methods-editor">
    <div className="section-title compact"><h2>{t('Payment')}</h2><div className="hero-actions">
      {current && <button type="button" className="secondary" onClick={() => edit(current)}>{t('Edit')}</button>}
      <button type="button" className="secondary" disabled={all.length >= 10} onClick={() => edit(null)}>{t('Add payment method')}</button>
    </div></div>
    <p className="muted">{t('Save multiple receiving methods. Members can choose how to pay you.')}</p>
    {methods.map(account => <div className="saved-payment-method stack" key={account.id}>
      <div className="section-title compact"><b>{t(account.method === 'AANI' ? 'Aani' : 'Bank account')} · {account.currency}</b>{current?.id === account.id && <span className="status">{t('Default')}</span>}</div>
      <span>{account.holder} · {account.bank}</span><code dir="ltr">{account.identifier}</code>
      <div className="hero-actions"><button type="button" className="secondary" onClick={() => edit(account)}>{t('Edit')}</button>{current?.id !== account.id && <button type="button" className="secondary" onClick={() => persist(profileWithPaymentAccount(mergedProfile, account))}>{t('Use this method')}</button>}<button type="button" className="link danger" onClick={() => remove(account)}>{t('Remove')}</button></div>
    </div>)}
    {editor && <form className="stack payment-method-form" onSubmit={save}>
      <h3>{t(editor.previous ? 'Edit payment method' : 'Add payment method')}</h3>
      {!room && editor.form.method === 'BANK' && <label>{t('Currency')}<select value={editor.form.currency} onChange={event => setEditor(old => ({ ...old, form: { ...old.form, currency: event.target.value } }))}>{CURRENCIES.map(currency => <option key={currency}>{currency}</option>)}</select></label>}
      <PaymentFields form={editor.form} set={(key, value) => setEditor(old => ({ ...old, form: { ...old.form, [key]: value } }))} name={profile.name} required currency={room?.restaurant.currency || 'AED'} />
      <div className="hero-actions"><button className="primary" disabled={data.busy}>{t('Save payment details')}</button><button type="button" className="secondary" onClick={() => setEditor(null)}>{t('Cancel')}</button></div>
    </form>}
    {message && <p className="form-message" role="status">{message}</p>}
  </article>;
}
