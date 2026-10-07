// Calling codes and national prefixes from google/libphonenumber, fetched 2026-10-07.
// https://github.com/google/libphonenumber/blob/master/resources/PhoneNumberMetadata.xml
import codes from './countryCodes.json' with { type: 'json' };
const en = new Intl.DisplayNames(['en'], { type: 'region' }), ar = new Intl.DisplayNames(['ar'], { type: 'region' });
export const countries = codes.map(value => ({ ...value, name: en.of(value.region), nameAr: ar.of(value.region) }));
export const asciiDigits = value => String(value || '').replace(/[٠-٩]/g, digit => String(digit.charCodeAt(0) - 0x0660))
  .replace(/[۰-۹]/g, digit => String(digit.charCodeAt(0) - 0x06f0));
const country = region => countries.find(value => value.region === region) || countries.find(value => value.region === 'AE');
export function splitPhone(value, defaultRegion = 'AE') {
  const raw = asciiDigits(value).trim(), digits = raw.replace(/\D/g, '');
  const full = raw.startsWith('00') ? digits.slice(2) : digits;
  const preferred = country(defaultRegion);
  if (raw.startsWith('+') || raw.startsWith('00')) {
    const selected = full.startsWith(preferred.code) ? preferred : [...countries].sort((a, b) => b.code.length - a.code.length).find(value => full.startsWith(value.code));
    if (selected) return { region: selected.region, national: full.slice(selected.code.length) };
  }
  if (full.startsWith(preferred.code) && full.length >= 11) return { region: preferred.region, national: full.slice(preferred.code.length) };
  return { region: preferred.region, national: raw };
}
export function combinePhone(region, value) {
  const raw = asciiDigits(value).trim();
  if (!raw) return '';
  if (raw.startsWith('+') || raw.startsWith('00')) return '+' + raw.replace(/\D/g, '').replace(/^00/, '');
  const selected = country(region);
  let local = raw.replace(/\D/g, '');
  if (local.startsWith(selected.code) && local.length >= 11) local = local.slice(selected.code.length);
  else if (selected.nationalPrefix && local.startsWith(selected.nationalPrefix)) local = local.slice(selected.nationalPrefix.length);
  return '+' + selected.code + local;
}
