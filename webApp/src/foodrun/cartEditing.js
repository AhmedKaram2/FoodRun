export function addMenuLine(lines, incoming) {
  if (!Number.isInteger(incoming.quantity) || incoming.quantity < 1 || incoming.quantity > 99) throw Error('Choose a quantity between 1 and 99.');
  const same = line => line.itemId === incoming.itemId && (line.variantId || '') === (incoming.variantId || '') &&
    JSON.stringify([...(line.optionIds || [])].sort()) === JSON.stringify([...(incoming.optionIds || [])].sort()) &&
    (line.notes || '') === (incoming.notes || '') && (line.description || '') === (incoming.description || '');
  const existing = lines.find(same);
  if (!existing) return [...lines, incoming];
  if (existing.quantity + incoming.quantity > 99) throw Error('Choose a quantity between 1 and 99.');
  return lines.map(line => line.id === existing.id ? { ...line, quantity: line.quantity + incoming.quantity } : line);
}
export function changeQuantity(lines, id, delta) {
  return lines.flatMap(line => {
    if (line.id !== id) return [line];
    const quantity = line.quantity + delta;
    if (quantity < 0 || quantity > 99) throw Error('Choose a quantity between 1 and 99.');
    return quantity === 0 ? [] : [{ ...line, quantity }];
  });
}

// Price preview uses the same integer minor units and variant/extra rules as Billing.lines.
// Final fees, tax and member totals always come from the server receipt.
export function menuLineTotal(restaurant, line) {
  if (line.description) return line.unitPrice == null ? null : line.unitPrice * line.quantity;
  const item = restaurant.menu.items.find(value => value.id === line.itemId);
  if (!item) return null;
  const variant = item.variants.find(value => value.id === line.variantId);
  if (item.variants.length && !variant) return null;
  const groups = restaurant.menu.optionGroups.filter(group => item.optionGroupIds.includes(group.id));
  const options = groups.flatMap(group => group.options).filter(option => (line.optionIds || []).includes(option.id));
  return (line.unitPrice ?? ((variant?.priceMinor ?? item.basePriceMinor) + options.reduce((sum, option) => sum + option.priceDeltaMinor, 0))) * line.quantity;
}
