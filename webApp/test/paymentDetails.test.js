import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentDraft, paymentAccount, profilePaymentAccounts, profileWithPaymentAccount, roomPaymentAccounts } from '../src/foodrun/paymentDetails.js';

const bank = { id: 'bank', holder: 'Test', bank: 'Bank', identifier: 'AE070331234567890123456', currency: 'AED', version: 1, method: 'BANK' };
const aani = { id: 'aani', holder: 'Test', bank: 'Aani', identifier: '+971501234567', currency: 'AED', version: 1, method: 'AANI' };
test('adding and editing receiving methods preserve other methods and profile data', () => {
  const profile = { name: 'Test', phone: '+971501234567', payment: bank, favoriteOrders: [{ id: 'favorite' }] };
  const added = profileWithPaymentAccount(profile, aani);
  assert.deepEqual(profilePaymentAccounts(added), [aani, bank]);
  assert.deepEqual(added.favoriteOrders, profile.favoriteOrders);
  const updated = paymentAccount({ ...paymentDraft(aani), aaniPhone: '050 999 9999' }, aani, 'Test', true);
  const edited = profileWithPaymentAccount(added, updated);
  assert.deepEqual(profilePaymentAccounts(edited), [updated, bank]);
  assert.equal(updated.id, aani.id);
  assert.equal(updated.identifier, '+971509999999');
  assert.equal(profilePaymentAccounts(profile)[0].identifier, bank.identifier);
});
test('room methods include the legacy default once and never exceed the saved-method limit', () => {
  assert.deepEqual(roomPaymentAccounts({ account: bank, accounts: [bank, aani] }), [bank, aani]);
  assert.deepEqual(roomPaymentAccounts({ account: bank }), [bank]);
  const profile = { payment: bank, paymentAccounts: Array.from({ length: 9 }, (_, i) => ({ ...bank, id: `bank-${i}` })) };
  assert.throws(() => profileWithPaymentAccount(profile, aani), /10/);
  assert.equal(profilePaymentAccounts(profile).length, 10);
});
