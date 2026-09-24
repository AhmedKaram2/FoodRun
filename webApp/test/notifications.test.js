import test from 'node:test';
import assert from 'node:assert/strict';
import { notificationTarget } from '../src/foodrun/notifications.js';
const item = { roomId: 'room', orderNumber: 3, transferId: 'payment', actions: ['copy','share','order','confirm','pay','accept'].map(id => ({ id })) };
const room = { id: 'room', orderNumber: 3, phase: 'COLLECTING', payerId: 'payer', transfers: [{ id: 'payment', status: 'declared' }] };
test('notification routes reject stale orders, unauthorized payers, and already processed payments', () => {
  assert.equal(notificationTarget(item, 'copy', { room }, 'payer'), 'order');
  assert.equal(notificationTarget(item, 'copy', { room }, 'member'), 'expired');
  assert.equal(notificationTarget(item, 'confirm', { room }, 'payer'), 'confirm');
  assert.equal(notificationTarget(item, 'confirm', { room: { ...room, transfers: [{ id: 'payment', status: 'confirmed' }] } }, 'payer'), 'expired');
  assert.equal(notificationTarget(item, 'copy', { room: { ...room, orderNumber: 4 } }, 'payer'), 'expired');
  assert.equal(notificationTarget(item, 'unknown', { room }, 'payer'), 'expired');
});
test('payment and duty links route only to the appropriate current action', () => {
  assert.equal(notificationTarget(item, 'pay', { room: { ...room, phase: 'PLACED' } }, 'member'), 'pay');
  assert.equal(notificationTarget(item, 'pay', { room: { ...room, phase: 'PLACED' } }, 'payer'), 'open');
  assert.equal(notificationTarget(item, 'accept', { room: { ...room, phase: 'ACCEPTING', spin: { winnerId: 'payer' } } }, 'payer'), 'accept');
  assert.equal(notificationTarget(item, 'accept', { room }, 'payer'), 'expired');
});
