import test from 'node:test';
import assert from 'node:assert/strict';
import { myWalletBalances, walletAvailable, walletPaymentGroups, walletTotals } from '../src/foodrun/wallet.js';

test('available wallet balances stay separated by owner, holder and currency', () => {
  const wallet = { balances: [
    { customerId:'me', holderId:'a', currency:'AED', available:6000 },
    { customerId:'me', holderId:'b', currency:'AED', available:4000 },
    { customerId:'me', holderId:'b', currency:'USD', available:500 },
    { customerId:'other', holderId:'me', currency:'AED', available:9999 },
  ] };
  assert.equal(walletAvailable(wallet, 'me', 'AED'), 10000);
  assert.equal(walletAvailable(wallet, 'me', 'USD'), 500);
  assert.deepEqual(walletTotals(myWalletBalances(wallet, 'me')), [['AED',10000],['USD',500]]);
  assert.equal(walletAvailable(null, 'me', 'AED'), 0);
});
test('grouped payment offers include only outstanding holder debts for the same recipient and currency', () => {
  const payment = { holderId:'holder', recipientId:'payer', recipientName:'Payer', currency:'AED', amount:1500, status:'OWING' };
  const wallet = { payments:[
    { ...payment, id:'a', customerId:'a', roomId:'one' }, { ...payment, id:'b', customerId:'b', roomId:'two', amount:2500 },
    { ...payment, id:'usd', currency:'USD', amount:200 }, { ...payment, id:'other', recipientId:'other' },
    { ...payment, id:'sent', status:'SENT' }, { ...payment, id:'done', status:'SETTLED' }, { ...payment, id:'foreign', holderId:'another' },
  ] };
  const groups = walletPaymentGroups(wallet, 'holder');
  assert.equal(groups.length,3);
  assert.equal(groups.find(value => value.key === 'payer:AED').amount,4000);
  assert.deepEqual(groups.find(value => value.key === 'payer:AED').payments.map(value => value.id), ['a','b']);
});
