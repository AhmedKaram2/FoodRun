import { t } from './i18n.js';
import PhoneInput from './PhoneInput.jsx';

export default function PaymentFields({ form, set, name = '', required = false, currency = 'AED' }) {
  return <>
    <div className="segmented"><button type="button" disabled={currency !== 'AED'} className={form.method === 'AANI' ? 'active' : ''} onClick={() => set('method', 'AANI')}>{t('Aani')}</button><button type="button" className={form.method === 'BANK' ? 'active' : ''} onClick={() => set('method', 'BANK')}>{t('Bank account')}</button></div>
    <label>{t('Account holder')}<input value={form.holder} onChange={e => set('holder', e.target.value)} placeholder={name || t('Your name')} maxLength={160} /></label>
    {form.method === 'BANK' && <label>{t('Bank name')}<input value={form.bank} onChange={e => set('bank', e.target.value)} required={required || !!form.iban.trim()} maxLength={160} /></label>}
    {form.method === 'AANI' ? <PhoneInput label={t('UAE mobile registered with Aani')} value={form.aaniPhone} onChange={value => set('aaniPhone', value)} required={required} uaeOnly />
      : <label>{t('IBAN')}<input dir="ltr" value={form.iban} onChange={e => set('iban', e.target.value)} placeholder="GB82…" required={required} /></label>}
  </>;
}
