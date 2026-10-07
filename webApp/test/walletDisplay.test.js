import test from 'node:test';
import assert from 'node:assert/strict';
import { userDashboard } from '../src/foodrun/orderHistory.js';
import { roomCashReceived, walletPaymentGroups } from '../src/foodrun/wallet.js';

function data(balance = 700, status = 'DECLARED', pendingAmount = 700, memberId = 'mohamed') {
  const room = { id:'room', name:'Monday breakfast', orderNumber:1, phase:'FULFILLED', payerId:'baraa', restaurant:{name:'Restaurant'}, members:[{id:'baraa',name:'Baraa'},{id:'mohamed',name:'Mohamed'}],
    transfers:[{id:'sent',memberId:'mohamed',amount:pendingAmount,status}], walletPayments:[] };
  return { sessions:{room:{roomId:'room',memberId}}, rooms:{room:{room,receipts:[{memberId:'mohamed',name:'Mohamed',total:1000,paid:1000-balance,balance,currency:'AED'}]}} };
}
test('sent money leaves the sender payment tasks while the recipient still has confirmation to do', () => {
  const sender = userDashboard(data());
  assert.equal(sender.toPay,0); assert.deepEqual(sender.payEntries,[]);
  assert.equal(sender.paymentHistory[0].pending.amount,700);
  const recipient = userDashboard(data(700,'DECLARED',700,'baraa'));
  assert.equal(recipient.toReceive,700); assert.equal(recipient.receiveEntries[0].pending.amount,700);
  assert.equal(userDashboard(data(700,'REJECTED')).toPay,700);
  assert.equal(userDashboard(data(1000,'DECLARED',700)).toPay,300);
});
test('a wallet claim awaiting approval cannot count as cash received or a transfer to oneself', () => {
  const room = {payerId:'baraa',walletPayments:[{id:'a',holderId:'baraa',recipientId:'baraa',amount:700,status:'OWING'}],transfers:[{id:'wallet-a',amount:700,status:'DECLARED'}]};
  assert.equal(roomCashReceived(room,[{memberId:'mohamed',paid:0}]),0);
  assert.deepEqual(walletPaymentGroups({payments:room.walletPayments},'baraa'),[]);
});
