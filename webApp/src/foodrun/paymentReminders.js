import { settlementOpen } from './roomLifecycle.js';
export const reminderCooldown = 24 * 60 * 60 * 1000;
export const reminderKey = (room, memberId) => `${room.id}:${room.orderNumber}:${memberId}`;
export function canRemindPayment(room, actorId, receipt) {
  return room.payerId === actorId && receipt.memberId !== actorId && receipt.balance > 0 && room.restaurantPaid &&
    settlementOpen(room) &&
    room.members.some(member => member.id === receipt.memberId && member.approved && !member.removed && !member.guest && member.participating) &&
    !room.transfers.some(transfer => transfer.memberId === receipt.memberId && String(transfer.status).toLowerCase() === 'declared');
}
