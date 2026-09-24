// Bank transfers use an IBAN; Aani uses a UAE mobile alias. Never reuse one as the other.
export function uaePhone(value, mobileOnly = false) {
  const digits = String(value || '').replace(/\D/g, '');
  const local = digits.startsWith('00971') ? digits.slice(5) : digits.startsWith('971') ? digits.slice(3) : digits.startsWith('0') ? digits.slice(1) : digits;
  const valid = mobileOnly ? /^5\d{8}$/.test(local) : /^(?:[2-9]\d{7}|5\d{8})$/.test(local);
  if (!valid) throw Error(mobileOnly ? 'Enter a UAE mobile number, for example +971 50 123 4567.' : 'Enter a UAE phone number, for example +971 4 123 4567.');
  return `+971${local}`;
}

export function paymentAccount(form, previous, name, required = false) {
  const identifier = form.method === 'AANI' ? form.aaniPhone : form.iban;
  if (!identifier.trim() && !required) return null;
  return {
    id: previous?.id || crypto.randomUUID(), holder: form.holder.trim() || name.trim(),
    bank: form.method === 'AANI' ? 'Aani' : form.bank.trim(),
    identifier: form.method === 'AANI' ? uaePhone(identifier, true) : uaeIban(identifier),
    currency: 'AED', version: previous?.version || 1, method: form.method,
  };
}

export function uaeIban(value) {
  const iban = String(value || '').replace(/\s/g, '').toUpperCase();
  if (!/^AE\d{21}$/.test(iban)) throw Error('Enter a UAE IBAN starting with AE and containing 23 characters.');
  let remainder = 0;
  for (const char of iban.slice(4) + iban.slice(0, 4)) {
    const digits = /[A-Z]/.test(char) ? String(char.charCodeAt(0) - 55) : char;
    for (const digit of digits) remainder = (remainder * 10 + Number(digit)) % 97;
  }
  if (remainder !== 1) throw Error('IBAN checksum is invalid.');
  return iban;
}

export function paymentDraft(account) {
  return {
    method: account?.method || 'AANI', holder: account?.holder || '',
    bank: account?.method === 'AANI' ? '' : account?.bank || '',
    iban: account && account.method !== 'AANI' ? account.identifier : '',
    aaniPhone: account?.method === 'AANI' ? account.identifier : '',
  };
}
