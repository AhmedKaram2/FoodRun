import test from 'node:test';
import assert from 'node:assert/strict';
import { roomInvitation } from '../src/foodrun/roomInvitation.js';

const room = { name: 'Lunch together', orderNumber: 3, code: '123456', restaurant: { name: 'Sultan', nameAr: 'سلطان' } };
const link = 'https://intrvioo.com/?room=123456';
test('invitations include restaurant, order, code and link in English and Arabic', () => {
  for (const language of ['en', 'ar']) {
    const text = roomInvitation(room, link, language);
    assert.ok(text.includes(language === 'ar' ? 'سلطان' : 'Sultan'));
    assert.ok(text.includes(language === 'ar' ? 'طلب رقم 3' : 'Order #3'));
    assert.ok(text.includes('123456'));
    assert.equal(text.split(link).length, 2);
    assert.ok(text.includes(language === 'ar' ? 'من غير موافقة' : 'No approval needed'));
  }
});
test('open polls show the choices rather than announcing a selected restaurant', () => {
  const text = roomInvitation({ ...room, restaurantPollOpen: true, restaurantOptions: [room.restaurant, { name: 'Kalha' }] }, link);
  assert.ok(text.includes('Restaurant poll: Sultan · Kalha'));
  assert.ok(!text.includes('Restaurant:'));
});
