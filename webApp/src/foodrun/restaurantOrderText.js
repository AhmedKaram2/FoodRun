import { restaurantQuantity } from './halfItems.js';
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
    grouped.set(key, { ...previous, halfShare: false, quantity: previous.quantity + restaurantQuantity(line), amount: previous.amount + line.amount });
  });
  return [...grouped.values()].filter(line => line.quantity > 0);
}

export function restaurantOrderText(room, receipts, language = 'ar') {
  const quantityText = quantity => language === 'ar'
    ? String(quantity).replace(/\d/g, digit => '٠١٢٣٤٥٦٧٨٩'[Number(digit)])
    : String(quantity);
  const lines = groupedOrderLines(room, receipts, language).map(line => {
    const quantity = quantityText(line.quantity);
    const itemText = /\p{Nd}/u.test(line.description)
      ? `${line.description} — ${language === 'ar' ? 'الكمية' : 'Quantity'}: ${quantity}`
      : `${quantity} ${line.description}`;
    return `${itemText}${line.notes ? ` — ${line.notes}` : ''}`;
  });
  const address = (room.deliveryMode ? room.destination : room.restaurant.contact?.address)?.trim()
    || (language === 'ar' ? 'العنوان يحدد لاحقاً' : 'Address to be confirmed');
  const total = receipts.flatMap(receipt => receipt.lines).reduce((sum, line) => sum + restaurantQuantity(line), 0);
  const addressLabel = language === 'ar' ? 'العنوان' : 'Address';
  const totalLabel = language === 'ar' ? 'إجمالي السندويشات' : 'Total sandwiches';
  return [`${addressLabel}: ${address}`, '', ...lines, `${totalLabel}: ${quantityText(total)}`].join('\n');
}
