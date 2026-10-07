import test from 'node:test';
import assert from 'node:assert/strict';
import { myWalletBalances, walletAvailable, walletPaymentGroups, walletTotals, roomWalletPending, roomCashReceived } from '../src/foodrun/wallet.js';

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
test('wallet allocation clears the customer debt while cash remains collectible from the holder', () => {
  const room = { payerId:'payer', walletPayments:[
    {id:'a',memberId:'alice',amount:1500,status:'OWING'}, {id:'b',memberId:'bob',amount:2500,status:'SENT'}, {id:'c',memberId:'alice',amount:300,status:'SETTLED'},
  ], transfers:[{id:'wallet-a',status:'CONFIRMED'},{id:'wallet-b',status:'CONFIRMED'},{id:'wallet-c',status:'CONFIRMED'}] };
  const receipts = [{memberId:'payer',paid:0},{memberId:'alice',paid:2000,balance:0},{memberId:'bob',paid:2500,balance:0}];
  assert.equal(roomWalletPending(room), 4000);
  assert.equal(roomWalletPending(room,'alice'), 1500);
  assert.equal(roomCashReceived(room,receipts),500);
  const confirmed = {...room,walletPayments:room.walletPayments.map(value=>({...value,status:'SETTLED'}))};
  assert.equal(roomCashReceived(confirmed,receipts),4500);
  assert.equal(roomWalletPending(confirmed),0);
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
