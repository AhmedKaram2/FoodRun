// Bank transfers use an IBAN; Aani uses a UAE mobile alias. Never reuse one as the other.
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
