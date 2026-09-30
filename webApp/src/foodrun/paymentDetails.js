// Bank transfers use an IBAN; Aani uses a UAE mobile alias. Never reuse one as the other.
export function uaePhone(value, mobileOnly = false) {
  const digits = String(value || '').replace(/\D/g, '');
  const local = digits.startsWith('00971') ? digits.slice(5) : digits.startsWith('971') ? digits.slice(3) : digits.startsWith('0') ? digits.slice(1) : digits;
  const valid = mobileOnly ? /^5\d{8}$/.test(local) : /^(?:[2-9]\d{7}|5\d{8})$/.test(local);
  if (!valid) throw Error(mobileOnly ? 'Enter a UAE mobile number, for example +971 50 123 4567.' : 'Enter a UAE phone number, for example +971 4 123 4567.');
  return `+971${local}`;
}
export function internationalPhone(value) {
  const raw = String(value || '').trim()
    .replace(/[٠-٩]/g, digit => String(digit.charCodeAt(0) - 0x0660))
    .replace(/[۰-۹]/g, digit => String(digit.charCodeAt(0) - 0x06f0));
  if (/^0(?!0)/.test(raw)) return uaePhone(raw);
  let digits = raw.replace(/\D/g, '');
  if (digits.startsWith('00')) digits = digits.slice(2);
  if (!raw.startsWith('+') && !raw.startsWith('00') && /^(?:5\d{8}|[2-9]\d{7})$/.test(digits)) return uaePhone(digits);
  if (!/^[1-9]\d{6,14}$/.test(digits)) throw Error('Enter a phone number with country code, for example +20 10 1234 5678.');
  return `+${digits}`;
}

export function paymentAccount(form, previous, name, required = false, currency = previous?.currency || 'AED') {
  const identifier = form.method === 'AANI' ? form.aaniPhone : form.iban;
  if (!identifier.trim() && !required) return null;
  return {
    id: previous?.id || crypto.randomUUID(), holder: form.holder.trim() || name.trim(),
    bank: form.method === 'AANI' ? 'Aani' : form.bank.trim(),
    identifier: form.method === 'AANI' ? uaePhone(identifier, true) : iban(identifier),
    currency: form.method === 'AANI' ? 'AED' : currency, version: previous?.version || 1, method: form.method,
  };
}

export function iban(value) {
  const iban = String(value || '').replace(/\s/g, '').toUpperCase();
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(iban)) throw Error('Enter a valid IBAN with 15 to 34 characters.');
  let remainder = 0;
  for (const char of iban.slice(4) + iban.slice(0, 4)) {
    const digits = /[A-Z]/.test(char) ? String(char.charCodeAt(0) - 55) : char;
    for (const digit of digits) remainder = (remainder * 10 + Number(digit)) % 97;
  }
  if (remainder !== 1) throw Error('IBAN checksum is invalid.');
  return iban;
}
export const uaeIban = iban;

export function paymentDraft(account) {
  return {
    method: account?.method || 'AANI', holder: account?.holder || '',
    bank: account?.method === 'AANI' ? '' : account?.bank || '',
    iban: account && account.method !== 'AANI' ? account.identifier : '',
    aaniPhone: account?.method === 'AANI' ? account.identifier : '',
  };
}
