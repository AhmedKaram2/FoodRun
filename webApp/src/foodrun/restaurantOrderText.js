import { t, tf } from './i18n.js';

const name = (value, language) => language === 'ar' && value?.nameAr ? value.nameAr : value?.name || '';

export function groupedOrderLines(room, receipts, language = 'en') {
  const grouped = new Map();
  receipts.flatMap(receipt => receipt.lines).forEach(line => {
    const item = room.restaurant.menu.items.find(value => value.id === line.itemId);
    const variant = item?.variants.find(value => value.id === line.variantId);
    const options = room.restaurant.menu.optionGroups.flatMap(group => group.options).filter(value => (line.optionIds || []).includes(value.id));
    const description = item ? [name(item, language), variant && name(variant, language), ...options.map(value => name(value, language))].filter(Boolean).join(' · ') : line.description;
    const key = `${description}\u0000${line.notes || ''}`;
    const previous = grouped.get(key) || { ...line, description, quantity: 0, amount: 0 };
    grouped.set(key, { ...previous, quantity: previous.quantity + line.quantity, amount: previous.amount + line.amount });
  });
  return [...grouped.values()];
}

export function restaurantOrderText(room, receipts, language = 'en', expectedArrival = room.restaurantReference) {
  const lines = groupedOrderLines(room, receipts, language).map(line => {
    const quantity = language === 'ar' ? String(line.quantity).replace(/\d/g, digit => '٠١٢٣٤٥٦٧٨٩'[Number(digit)]) : String(line.quantity);
    return `${quantity} ${line.description}${line.notes ? ` — ${line.notes}` : ''}`;
  });
  return [name(room.restaurant, language), room.deliveryMode
    ? `${t('Delivery', language)}: ${room.destination || t('Address to be confirmed', language)}` : t('Pickup', language),
    tf('Expected delivery / pickup: {time}', { time: expectedArrival?.trim() || t('To be confirmed by restaurant', language) }, language), '', ...lines].join('\n');
}
