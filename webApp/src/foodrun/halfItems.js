import { menuLineTotal } from './cartEditing.js';

export const halfItemsOpen = room => ['LOBBY', 'PREPARING_SPIN', 'SPINNING', 'ACCEPTING', 'COLLECTING'].includes(room.phase)
  && !room.restaurantPollOpen && (!room.deadline || Date.now() <= room.deadline);

export const portionQuantity = line => line.halfShare ? '½' : line.quantity;
export const restaurantQuantity = line => line.restaurantQuantity ?? line.quantity;

export async function removeHalfItem(room, cart, line, send) {
  const offer = (room.halfItemOffers || []).find(offer => line.receivedHalf
    ? `half:${offer.id}` === line.id && offer.acceptedById === cart.memberId
    : offer.memberId === cart.memberId && offer.lineId === line.id);
  if (!offer || !halfItemsOpen(room)) return null;
  const cancelled = await send('CANCEL_HALF_ITEM', { text: offer.id }, room.id);
  if (!cancelled?.ok || line.receivedHalf) return cancelled;
  const current = cancelled.room?.carts?.find(value => value.memberId === cart.memberId);
  if (!current) throw Error('Reconnect to this order before making changes.');
  return send('CART', { cart: { ...current, lines: current.lines.filter(value => value.id !== line.id) }, expectedRevision: current.revision }, room.id);
}

export function halfItemCartLines(room, cart) {
  const offers = room.halfItemOffers || [];
  const own = cart.lines.map(line => {
    const amount = menuLineTotal(room.restaurant, line);
    const shared = offers.some(offer => offer.memberId === cart.memberId && offer.lineId === line.id && offer.acceptedById);
    return { ...line, halfShare: shared, amount: shared && amount != null ? amount - Math.floor(amount / 2) : amount };
  });
  const received = offers.filter(offer => offer.acceptedById === cart.memberId).map(offer => ({
    ...offer.line, id: `half:${offer.id}`, halfShare: true, amount: Math.floor(offer.line.amount / 2), receivedHalf: true,
  }));
  return [...own, ...received];
}
