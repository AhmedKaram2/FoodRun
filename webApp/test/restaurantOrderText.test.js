import test from 'node:test';
import assert from 'node:assert/strict';
import { groupedOrderLines, restaurantOrderText } from '../src/foodrun/restaurantOrderText.js';
import { setLanguage, t } from '../src/foodrun/i18n.js';

const room = { restaurant: { name: 'Kitchen', nameAr: 'المطعم', menu: {
  items: [{ id: 'meal', name: 'Beans', nameAr: 'فول', variants: [{ id: 'size', name: 'Large', nameAr: 'كبير' }] }],
  optionGroups: [{ options: [{ id: 'extra', name: 'Salad', nameAr: 'سلطة' }] }],
} }, deliveryMode: true, destination: 'Office 12', restaurantReference: '30 minutes' };
const line = { itemId: 'meal', variantId: 'size', optionIds: ['extra'], description: 'Beans · Large · Salad', quantity: 1, amount: 300, notes: 'No salt / بدون ملح' };
const receipts = [{ lines: [line, { ...line, quantity: 2, amount: 600 }, { ...line, notes: 'Extra lemon' }, { description: 'Custom food', quantity: 2, amount: 400, notes: 'Keep this note' }] }];

test('restaurant order text defaults to Arabic even when the website is English', () => {
  setLanguage('en');
  const text = restaurantOrderText(room, receipts);
  assert.ok(text.startsWith('العنوان:'));
  assert.ok(text.includes('٣ فول · كبير · سلطة'));
  assert.equal(t('Order sent'), 'Order sent');
});

test('copy language controls names, sizes, extras and quantities without changing website language', () => {
  setLanguage('en');
  const arabic = restaurantOrderText(room, receipts, 'ar');
  assert.ok(arabic.includes('٣ فول · كبير · سلطة — No salt / بدون ملح'));
  assert.ok(arabic.includes('١ فول · كبير · سلطة — Extra lemon'));
  assert.ok(arabic.includes('٢ Custom food — Keep this note'));
  assert.ok(arabic.startsWith('العنوان: Office 12'));
  assert.ok(arabic.endsWith('إجمالي السندويشات: ٦'));
  assert.ok(!arabic.includes('30 minutes') && !arabic.includes('المطعم'));
  assert.equal(t('Order sent'), 'Order sent');
  setLanguage('ar');
  const english = restaurantOrderText(room, receipts, 'en');
  assert.ok(english.startsWith('Address: Office 12'));
  assert.ok(english.includes('3 Beans · Large · Salad — No salt / بدون ملح'));
  assert.ok(english.endsWith('Total sandwiches: 6'));
  assert.ok(!english.includes('Kitchen') && !english.includes('Expected delivery'));
  assert.notEqual(t('Order sent'), 'Order sent');
  setLanguage('en');
});

test('pickup uses the restaurant address and custom notes remain readable', () => {
  const fallback = { ...room, restaurant: { ...room.restaurant, nameAr: '' }, deliveryMode: false, restaurantReference: '' };
  const text = restaurantOrderText(fallback, [{ lines: [{ description: 'User item', quantity: 1, amount: 300, notes: 'User note' }] }], 'ar');
  assert.ok(text.startsWith('العنوان: العنوان يحدد لاحقاً'));
  assert.ok(text.includes('١ User item — User note'));
  assert.ok(text.endsWith('إجمالي السندويشات: ١'));
  assert.ok(!text.includes('undefined'));
});

test('a number in an item name stays separate from the quantity in Arabic and English', () => {
  for (const description of ['٢ طعمية', '2 Falafel', '۲ طعمية', 'Falafel 12-pack', '7UP', '١/٢ دجاج']) {
    const numbered = [{ lines: [{ description, quantity: 1, amount: 300 }] }];
    assert.ok(restaurantOrderText(room, numbered, 'ar').includes(`\n${description} — الكمية: ١\n`));
    assert.ok(restaurantOrderText(room, numbered, 'en').includes(`\n${description} — Quantity: 1\n`));
  }
});

test('numbered menu names keep grouped quantities, amounts and separate notes', () => {
  const numberedRoom = { ...room, restaurant: { ...room.restaurant, menu: {
    ...room.restaurant.menu,
    items: [{ id: 'meal', name: '2 Falafel', nameAr: '٢ طعمية', variants: [] }],
  } } };
  const item = { itemId: 'meal', description: '2 Falafel', quantity: 1, amount: 300, notes: 'بدون سلطة' };
  const numbered = [{ lines: [item] }, { lines: [{ ...item, quantity: 2, amount: 600 }, { ...item, notes: 'Extra lemon' }] }];
  const arabic = restaurantOrderText(numberedRoom, numbered, 'ar');
  assert.ok(arabic.includes('\n٢ طعمية — الكمية: ٣ — بدون سلطة\n'));
  assert.ok(arabic.includes('\n٢ طعمية — الكمية: ١ — Extra lemon\n'));
  assert.ok(arabic.endsWith('إجمالي السندويشات: ٤'));
  const english = restaurantOrderText(numberedRoom, numbered, 'en');
  assert.ok(english.includes('\n2 Falafel — Quantity: 3 — بدون سلطة\n'));
  assert.ok(english.endsWith('Total sandwiches: 4'));
  assert.deepEqual(groupedOrderLines(numberedRoom, numbered, 'ar').map(({ quantity, amount }) => ({ quantity, amount })),
    [{ quantity: 3, amount: 900 }, { quantity: 1, amount: 300 }]);
});
