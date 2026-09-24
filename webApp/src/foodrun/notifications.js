export async function notificationRequest(hub, identityToken, fields = {}, signal) {
  const response = await fetch(`${hub}/notifications`, { method: 'POST', headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ identityToken, ...fields }), credentials: 'omit', redirect: 'error', signal: signal || AbortSignal.timeout(20000) });
  const result = await response.json();
  if (!response.ok || !result.ok) throw Error(result.error || 'Notifications could not be updated.');
  return result;
}
export function notificationTarget(item, action, reply, memberId) {
  const room = reply?.room;
  if (!room || room.id !== item.roomId || room.orderNumber !== item.orderNumber) return 'expired';
  if (!(item.actions || []).some(value => value.id === action) && action !== 'open') return 'expired';
  if (['order', 'copy', 'share'].includes(action)) return room.payerId === memberId && ['COLLECTING', 'REVIEW', 'PLACED', 'FULFILLED'].includes(room.phase) ? 'order' : 'expired';
  if (action === 'confirm') return room.payerId === memberId && room.transfers.some(value => value.id === item.transferId && String(value.status).toLowerCase() === 'declared' && !value.refund) ? 'confirm' : 'expired';
  if (action === 'pay') return room.payerId !== memberId && ['PLACED', 'FULFILLED'].includes(room.phase) ? 'pay' : 'open';
  if (action === 'accept') return room.phase === 'ACCEPTING' && room.spin?.winnerId === memberId ? 'accept' : 'expired';
  return 'open';
}
