import { t } from './i18n.js';

export default function PaymentFields({ form, set, name = '', required = false }) {
  return <>
    <div className="segmented"><button type="button" className={form.method === 'AANI' ? 'active' : ''} onClick={() => set('method', 'AANI')}>{t('Aani')}</button><button type="button" className={form.method === 'BANK' ? 'active' : ''} onClick={() => set('method', 'BANK')}>{t('Bank account')}</button></div>
    <label>{t('Account holder')}<input value={form.holder} onChange={e => set('holder', e.target.value)} placeholder={name || t('Your name')} maxLength={160} /></label>
    {form.method === 'BANK' && <label>{t('Bank name')}<input value={form.bank} onChange={e => set('bank', e.target.value)} required={required || !!form.iban.trim()} maxLength={160} /></label>}
    <label>{form.method === 'AANI' ? t('UAE mobile registered with Aani') : t('UAE IBAN')}<input dir="ltr" type={form.method === 'AANI' ? 'tel' : 'text'} value={form.method === 'AANI' ? form.aaniPhone : form.iban} onChange={e => set(form.method === 'AANI' ? 'aaniPhone' : 'iban', e.target.value)} placeholder={form.method === 'AANI' ? '050 123 4567' : 'AE…'} required={required} /></label>
  </>;
}
