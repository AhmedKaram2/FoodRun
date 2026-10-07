import test from 'node:test';
import assert from 'node:assert/strict';
import { combinePhone, countries, splitPhone } from '../src/foodrun/phoneNumbers.js';
import { paymentAccount, uaePhone } from '../src/foodrun/paymentDetails.js';

test('selected country combines local numbers including Arabic and Persian digits', () => {
  assert.equal(combinePhone('EG', '٠١٠١٢٣٤٥٦٧٨'), '+201012345678');
  assert.equal(combinePhone('AE', '۰۵۰۱۲۳۴۵۶۷'), '+971501234567');
  assert.equal(combinePhone('GB', '02079460000'), '+442079460000');
  assert.equal(combinePhone('IT', '0212345678'), '+390212345678');
  assert.equal(combinePhone('AE', ''), '');
  assert.equal(new Set(countries.map(value => value.region)).size, countries.length);
});
test('existing international phones select their country and do not duplicate the prefix', () => {
  assert.deepEqual(splitPhone('+201012345678'), { region: 'EG', national: '1012345678' });
  assert.deepEqual(splitPhone('00971501234567'), { region: 'AE', national: '501234567' });
  assert.deepEqual(splitPhone('+14161234567', 'CA'), { region: 'CA', national: '4161234567' });
  assert.equal(combinePhone('EG', '+971501234567'), '+971501234567');
});
test('Aani normalizes valid mobile formats and rejects foreign numbers and landlines', () => {
  for (const value of ['050 123 4567', '50 123 4567', '+971 (0)50 123 4567', '00971 50 123 4567', '٠٥٠ ١٢٣ ٤٥٦٧', '+٩٧١ ٥٠ ١٢٣ ٤٥٦٧', '۰۵۰۱۲۳۴۵۶۷']) assert.equal(uaePhone(value, true), '+971501234567');
  assert.throws(() => uaePhone('+201012345678', true), /UAE mobile/);
  assert.throws(() => uaePhone('+97141234567', true), /UAE mobile/);
  assert.equal(paymentAccount({ method: 'AANI', aaniPhone: '٠٥٠١٢٣٤٥٦٧', holder: '', iban: '', bank: '' }, null, 'Google User', true).identifier, '+971501234567');
});
