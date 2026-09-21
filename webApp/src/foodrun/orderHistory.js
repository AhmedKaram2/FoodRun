import { matchesRestaurant } from './reorder.js';

export function selectionKey(restaurantKey, lines) {
  const normalized = lines.map(line => [
    line.itemId || '', line.variantId || '', JSON.stringify([...(line.optionIds || [])].sort()),
    line.itemId ? '' : (line.description || '').trim().toLowerCase(), String(line.quantity), (line.notes || '').trim().toLowerCase(),
  ].map(value => String(value)));
  return JSON.stringify([String(restaurantKey || '').trim().toLowerCase(), ...normalized.map(line => JSON.stringify(line)).sort()]);
}

export function uniquePreviousOrders(data, restaurant = null) {
  const choices = [];
  Object.values(data.sessions || {}).forEach(session => {
    const history = data.rooms?.[session.roomId]?.history || [];
    history.forEach(order => {
      const receipt = order.receipts?.find(value => value.memberId === session.memberId);
      if (!receipt?.lines?.length) return;
      const restaurantId = order.restaurantId || data.home?.restaurants?.find(value => value.name?.toLowerCase() === order.restaurantName?.toLowerCase())?.id || '';
      if (restaurant && !matchesRestaurant(order, restaurant)) return;
      choices.push({ value: `past|${session.roomId}|${order.number}`, roomId: session.roomId, order, receipt, restaurantId, repeatCount: 1 });
    });
  });
  const unique = new Map();
  choices.forEach(choice => {
    const key = selectionKey(choice.restaurantId || `name:${choice.order.restaurantName}`, choice.receipt.lines);
    const old = unique.get(key);
    if (!old) unique.set(key, { ...choice, key });
    else unique.set(key, { ...(choice.order.completedAt > old.order.completedAt ? choice : old), key, repeatCount: old.repeatCount + 1 });
  });
  return [...unique.values()].sort((a, b) => b.order.completedAt - a.order.completedAt);
}

export function uniqueRoomPreviousOrders(history, memberId, restaurant, matchingOnly = false) {
  const unique = new Map();
  (history || []).forEach(order => {
    const receipt = order.receipts?.find(value => value.memberId === memberId);
    if (!receipt?.lines?.length) return;
    const restaurantId = order.restaurantId || (order.restaurantName?.toLowerCase() === restaurant.name?.toLowerCase() ? restaurant.id : '');
    if (matchingOnly && !matchesRestaurant(order, restaurant)) return;
    const key = selectionKey(restaurantId || `name:${order.restaurantName}`, receipt.lines);
    const choice = { value: `past|room|${order.number}`, order, receipt, restaurantId, key, repeatCount: 1 };
    const old = unique.get(key);
    if (!old) unique.set(key, choice);
    else unique.set(key, { ...(order.completedAt > old.order.completedAt ? choice : old), repeatCount: old.repeatCount + 1 });
  });
  return [...unique.values()].sort((a, b) => b.order.completedAt - a.order.completedAt);
}


export function userDashboard(data) {
  const entries = [], currentOrders = [], paymentHistory = [];
  let toPay = 0, toReceive = 0;
  Object.values(data.sessions || {}).forEach(session => {
    const reply = data.rooms?.[session.roomId], room = reply?.room;
    const orders = [...(reply?.history || [])];
    if (room && ['PLACED', 'FULFILLED', 'ARCHIVED'].includes(room.phase)) orders.unshift({ number: room.orderNumber, restaurantName: room.restaurant?.name || '', completedAt: room.updatedAt, receipts: reply.receipts });
    const seen = new Set();
    for (const order of orders) {
      const receipt = order.receipts?.find(value => value.memberId === session.memberId);
      const key = `${session.roomId}:${order.number}`;
      if (!receipt || seen.has(key)) continue;
      seen.add(key);
      paymentHistory.push({ key, roomId: session.roomId, roomName: room?.name || session.roomName || '', number: order.number, restaurantName: order.restaurantName, at: order.completedAt || 0, receipt });
    }
    if (!room) return;
    currentOrders.push({ room, session, reply });
    if (!['PLACED', 'FULFILLED'].includes(room.phase) || !room.payerId) return;
    const payer = room.payerId === session.memberId;
    const payerName = room.members.find(member => member.id === room.payerId)?.name || 'selected payer';
    if (payer) {
      (reply.receipts || []).filter(receipt => receipt.memberId !== session.memberId && receipt.balance !== 0).forEach(receipt => {
        if (receipt.balance > 0) toReceive += receipt.balance; else toPay += -receipt.balance;
        entries.push({ room, receipt, memberId: session.memberId, roomId: room.id, roomName: room.name, person: receipt.name, personId: receipt.memberId, amount: Math.abs(receipt.balance), currency: receipt.currency, pending: (room.transfers || []).find(value => value.memberId === receipt.memberId && String(value.status).toLowerCase() === 'declared'), kind: receipt.balance > 0 ? 'receive' : 'refund', text: receipt.balance > 0 ? `${receipt.name} needs to pay you` : `Refund ${receipt.name}` });
      });
    } else {
      const receipt = (reply.receipts || []).find(value => value.memberId === session.memberId);
      if (!receipt || receipt.balance === 0) return;
      if (receipt.balance > 0) toPay += receipt.balance; else toReceive += -receipt.balance;
      const recipient = payerName;
      entries.push({ room, receipt, memberId: session.memberId, roomId: room.id, roomName: room.name, person: recipient, personId: room.payerId, amount: Math.abs(receipt.balance), currency: receipt.currency, pending: (room.transfers || []).find(value => value.memberId === session.memberId && String(value.status).toLowerCase() === 'declared'), kind: receipt.balance > 0 ? 'pay' : 'receive', text: receipt.balance > 0 ? `Pay ${recipient}` : `${payerName} needs to refund you` });
    }
  });
  currentOrders.sort((a, b) => (b.room.createdAt || 0) - (a.room.createdAt || 0));
  paymentHistory.sort((a, b) => b.at - a.at);
  return { paymentHistory, toPay, toReceive, entries, payEntries: entries.filter(entry => entry.kind !== 'receive'), receiveEntries: entries.filter(entry => entry.kind === 'receive'), currentOrders };
}
