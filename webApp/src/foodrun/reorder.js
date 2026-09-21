import { addMenuLine, menuLineTotal } from './cartEditing.js';

const nameKey = value => (value || '').trim().toLowerCase();
export function matchesRestaurant(order, restaurant) {
  return order.restaurantId ? order.restaurantId === restaurant.id
    : !!nameKey(order.restaurantName) && nameKey(order.restaurantName) === nameKey(restaurant.name);
}

// Membership IDs are room-specific; never select another customer's receipt.
export function lastRestaurantOrder(data, restaurant) {
  let latest = null;
  for (const session of Object.values(data.sessions || {})) {
    const reply = data.rooms?.[session.roomId];
    if (!reply || (reply.memberId && reply.memberId !== session.memberId)) continue;
    const room = reply.room;
    const orders = [...(reply.history || [])];
    if (room && ['PLACED', 'FULFILLED', 'ARCHIVED'].includes(room.phase)) {
      orders.push({ number: room.orderNumber, restaurantId: room.restaurant.id,
        restaurantName: room.restaurant.name, completedAt: room.updatedAt, receipts: reply.receipts });
    }
    for (const order of orders) {
      if (!matchesRestaurant(order, restaurant)) continue;
      const receipt = order.receipts?.find(value => value.memberId === session.memberId);
      if (!receipt?.lines?.length) continue;
      if (!latest || order.completedAt > latest.order.completedAt) latest = { order, receipt, roomId: session.roomId };
    }
  }
  return latest;
}

// History is paged newest first. Stop each room as soon as its latest matching
// personal order is found, so returning customers also work beyond page one.
export function nextRestaurantHistoryRoom(data, restaurant) {
  return Object.values(data.sessions || {}).find(session => {
    const reply = data.rooms?.[session.roomId];
    if (!reply || !(reply.historyNextOffset >= 0)) return false;
    return !lastRestaurantOrder({ sessions: { [session.roomId]: session }, rooms: data.rooms }, restaurant);
  })?.roomId || '';
}

// Keep each configuration intact. A changed menu requires a new selection,
// rather than silently dropping a size or an extra from a customer's order.
export function prepareReorder(restaurant, source, createId = () => crypto.randomUUID()) {
  return source.map(saved => {
    const line = { id: createId(), itemId: saved.itemId || '', quantity: saved.quantity,
      variantId: saved.variantId || null, optionIds: [...(saved.optionIds || [])],
      notes: saved.notes || '', description: saved.itemId ? '' : saved.description || '', unitPrice: null };
    const item = restaurant.menu.items.find(value => value.id === line.itemId);
    const label = saved.label || saved.description || item?.name || saved.itemId;
    let issue = '';
    if (!Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 99 || line.notes.length > 500) {
      issue = 'Check the quantity or notes before adding this item.';
    } else if (!line.itemId) {
      if (!restaurant.openOrdering || !line.description || line.variantId || line.optionIds.length) issue = 'Custom items are unavailable on this menu.';
    } else if (!item || item.available === false) {
      issue = 'This item is unavailable.';
    } else if (item.variants.length ? !item.variants.some(value => value.id === line.variantId) : line.variantId !== null) {
      issue = 'This size is unavailable. Choose a new size from the menu.';
    } else {
      const groups = restaurant.menu.optionGroups.filter(group => item.optionGroupIds.includes(group.id));
      const options = groups.flatMap(group => group.options);
      if (new Set(line.optionIds).size !== line.optionIds.length || line.optionIds.some(id => !options.some(option => option.id === id))) {
        issue = 'An extra is unavailable. Choose new extras from the menu.';
      } else if (groups.some(group => {
        const count = line.optionIds.filter(id => group.options.some(option => option.id === id)).length;
        return count < group.minSelections || count > group.maxSelections;
      })) {
        issue = 'The customization requirements have changed. Review this item on the menu.';
      }
    }
    return { line, label, issue, total: issue ? null : menuLineTotal(restaurant, line) };
  });
}

export function mergeReorder(cartLines, entries) {
  const lines = entries.filter(entry => !entry.issue).reduce((lines, entry) => addMenuLine(lines, entry.line), cartLines);
  if (lines.length > 100) throw Error('Your cart can contain up to 100 items. Remove an item before reordering.');
  return lines;
}
