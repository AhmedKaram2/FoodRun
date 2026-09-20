// Only the signed-in member's receipt is retained. No tokens, carts, bank details,
// photos, member directory or other members' receipts enter this archive.
export function receiptArchive(previous, reply, now = Date.now()) {
  if (!reply?.room || !reply.memberId) return previous;
  const room = reply.room;
  const records = new Map(previous.map(record => [record.id, record]));
  const add = (orderNumber, restaurant, receipts, phase, completedAt) => {
    const receipt = receipts?.find(value => value.memberId === reply.memberId);
    const id = JSON.stringify([room.id, orderNumber]);
    if (!receipt) return;
    const { name, currency, food, delivery, service, tax, discount, adjustment, total, paid, balance, lines } = receipt;
    records.set(id, { id, roomId: room.id, roomName: room.name, orderNumber, restaurant, phase, completedAt,
      savedAt: now, receipt: { name, currency, food, delivery, service, tax, discount, adjustment, total, paid, balance,
        lines: (lines || []).map(({ description, quantity, amount, notes }) => ({ description, quantity, amount, notes })) } });
  };
  (reply.history || []).forEach(order => add(order.number, order.restaurantName, order.receipts, 'ARCHIVED', order.completedAt));
  if (['PLACED', 'FULFILLED', 'ARCHIVED'].includes(room.phase)) add(room.orderNumber, room.restaurant.name, reply.receipts, room.phase, room.updatedAt);
  else records.delete(JSON.stringify([room.id, room.orderNumber]));
  return [...records.values()].sort((a, b) => b.completedAt - a.completedAt || b.savedAt - a.savedAt).slice(0, 200);
}

function database() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open('foodrun-private-receipts', 1);
    request.onupgradeneeded = () => request.result.createObjectStore('archives');
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}
async function transaction(mode, action) {
  const db = await database();
  try {
    return await new Promise((resolve, reject) => {
      const tx = db.transaction('archives', mode);
      const request = action(tx.objectStore('archives'));
      tx.oncomplete = () => resolve(request?.result);
      tx.onerror = () => reject(tx.error);
      tx.onabort = () => reject(tx.error);
    });
  } finally { db.close(); }
}
const scope = (userId, hub) => JSON.stringify([userId, hub]);
let operations = Promise.resolve();
function serialized(action) {
  const next = operations.then(action);
  operations = next.catch(() => {});
  return next;
}
export function readReceiptArchive(userId, hub) {
  return serialized(async () => await transaction('readonly', store => store.get(scope(userId, hub))) || []);
}
export function saveReceiptArchive(userId, hub, reply) {
  return serialized(async () => {
    const previous = await transaction('readonly', store => store.get(scope(userId, hub))) || [];
    const next = receiptArchive(previous, reply);
    await transaction('readwrite', store => store.put(next, scope(userId, hub)));
    return next;
  });
}
export function clearReceiptArchive(userId, hub) {
  return serialized(() => transaction('readwrite', store => store.delete(scope(userId, hub))));
}
export function clearUserReceiptArchives(userId) {
  return serialized(async () => {
    const keys = await transaction('readonly', store => store.getAllKeys());
    for (const key of keys) {
      if (JSON.parse(key)[0] === userId) await transaction('readwrite', store => store.delete(key));
    }
  });
}
