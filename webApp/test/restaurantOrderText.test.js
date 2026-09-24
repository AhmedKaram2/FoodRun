import test from 'node:test';
import assert from 'node:assert/strict';
import { restaurantOrderText } from '../src/foodrun/restaurantOrderText.js';
import { setLanguage, t } from '../src/foodrun/i18n.js';

const room = { restaurant: { name: 'Kitchen', nameAr: 'المطعم', menu: {
  items: [{ id: 'meal', name: 'Beans', nameAr: 'فول', variants: [{ id: 'size', name: 'Large', nameAr: 'كبير' }] }],
  optionGroups: [{ options: [{ id: 'extra', name: 'Salad', nameAr: 'سلطة' }] }],
} }, deliveryMode: true, destination: 'Office 12', restaurantReference: '30 minutes' };
const line = { itemId: 'meal', variantId: 'size', optionIds: ['extra'], description: 'Beans · Large · Salad', quantity: 1, amount: 300, notes: 'No salt / بدون ملح' };
const receipts = [{ lines: [line, { ...line, quantity: 2, amount: 600 }, { ...line, notes: 'Extra lemon' }, { description: 'Custom food', quantity: 2, amount: 400, notes: 'Keep this note' }] }];

test('copy language controls names, sizes, extras and quantities without changing website language', () => {
  setLanguage('en');
  const arabic = restaurantOrderText(room, receipts, 'ar');
  assert.ok(arabic.startsWith('المطعم'));
  assert.ok(arabic.includes('٣ فول · كبير · سلطة — No salt / بدون ملح'));
  assert.ok(arabic.includes('١ فول · كبير · سلطة — Extra lemon'));
  assert.ok(arabic.includes('٢ Custom food — Keep this note'));
  assert.ok(arabic.includes('Office 12') && arabic.includes('30 minutes'));
  assert.equal(t('Order sent'), 'Order sent');
  setLanguage('ar');
  const english = restaurantOrderText(room, receipts, 'en');
  assert.ok(english.startsWith('Kitchen\nDelivery: Office 12'));
  assert.ok(english.includes('3 Beans · Large · Salad — No salt / بدون ملح'));
  assert.notEqual(t('Order sent'), 'Order sent');
  setLanguage('en');
});

test('missing translations and custom notes remain readable and copy ETA is optional', () => {
  const fallback = { ...room, restaurant: { ...room.restaurant, nameAr: '' }, deliveryMode: false, restaurantReference: '' };
  const text = restaurantOrderText(fallback, [{ lines: [{ description: 'User item', quantity: 1, amount: 300, notes: 'User note' }] }], 'ar');
  assert.ok(text.startsWith('Kitchen'));
  assert.ok(text.includes('١ User item — User note'));
  assert.ok(!text.includes('undefined'));
});
