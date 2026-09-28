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

export function restaurantOrderText(room, receipts, language = 'en') {
  const quantityText = quantity => language === 'ar'
    ? String(quantity).replace(/\d/g, digit => '٠١٢٣٤٥٦٧٨٩'[Number(digit)])
    : String(quantity);
  const lines = groupedOrderLines(room, receipts, language).map(line => {
    return `${quantityText(line.quantity)} ${line.description}${line.notes ? ` — ${line.notes}` : ''}`;
  });
  const address = (room.deliveryMode ? room.destination : room.restaurant.contact?.address)?.trim()
    || (language === 'ar' ? 'العنوان يحدد لاحقاً' : 'Address to be confirmed');
  const total = receipts.flatMap(receipt => receipt.lines).reduce((sum, line) => sum + line.quantity, 0);
  const addressLabel = language === 'ar' ? 'العنوان' : 'Address';
  const totalLabel = language === 'ar' ? 'إجمالي السندويشات' : 'Total sandwiches';
  return [`${addressLabel}: ${address}`, '', ...lines, `${totalLabel}: ${quantityText(total)}`].join('\n');
}
