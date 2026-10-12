import test from 'node:test';
import assert from 'node:assert/strict';
import { paymentConfirmationDeadline } from '../src/foodrun/paymentConfirmation.js';

test('direct sent payments display their twelve-hour deadline', () => {
  const sent={id:'transfer',status:'declared',createdAt:1000};
  assert.equal(paymentConfirmationDeadline(sent),43_201_000);
  for(const changes of [{refund:true},{id:'wallet-own-batch'},{id:'wallet-payment'},{status:'confirmed'},{status:'rejected'},{createdAt:0},{createdAt:'invalid'}]) {
    assert.equal(paymentConfirmationDeadline({...sent,...changes}),null);
  }
  assert.equal(paymentConfirmationDeadline(null),null);
});
