import { useEffect, useId, useState } from 'react';
import { countries, combinePhone, splitPhone } from './phoneNumbers.js';
import { getLanguage, t } from './i18n.js';

export default function PhoneInput({ label, value, onChange, required = false, uaeOnly = false }) {
  const id = useId();
  const [parts, setParts] = useState(() => splitPhone(value));
  useEffect(() => {
    if (combinePhone(parts.region, parts.national) !== value) setParts(splitPhone(value, uaeOnly ? 'AE' : parts.region));
  }, [value]);
  const update = (region, national) => {
    const next = /^(\+|00)/.test(national.trim()) ? splitPhone(national, region) : { region, national };
    if (uaeOnly && next.region !== 'AE') { next.region = 'AE'; next.national = national; }
    setParts(next); onChange(combinePhone(next.region, next.national));
  };
  const choices = uaeOnly ? countries.filter(country => country.region === 'AE') : countries;
  return <fieldset className="phone-number-field"><legend>{label}</legend><div className="phone-number-input" dir="ltr">
    <label className="phone-country" htmlFor={`${id}-country`}><span className="sr-only">{t('Country code')}</span>
      <select id={`${id}-country`} value={uaeOnly ? 'AE' : parts.region} onChange={event => update(event.target.value, parts.national)} autoComplete="country">
        {choices.map(country => <option key={country.region} value={country.region}>{getLanguage() === 'ar' ? country.nameAr : country.name} (+{country.code})</option>)}
      </select>
    </label><label className="phone-national" htmlFor={`${id}-number`}><span className="sr-only">{t('Mobile number')}</span>
      <input id={`${id}-number`} type="tel" inputMode="tel" autoComplete="tel-national" value={parts.national} onChange={event => update(parts.region, event.target.value)} required={required} placeholder={uaeOnly || parts.region === 'AE' ? '50 123 4567' : t('Mobile number')} maxLength={30} />
    </label>
  </div>{uaeOnly && <small>{t('Aani uses a UAE mobile number (+971).')}</small>}</fieldset>;
}
