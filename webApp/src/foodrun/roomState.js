// Keep downloaded history while accepting only authoritative, current room state.
export function mergeRoomReply(previous, next, olderPage = false) {
  if (!previous?.room || previous.room.id !== next.room?.id || previous.memberId !== next.memberId) return next;
  if (next.room.revision < previous.room.revision) return previous;
  const deleted = new Set(next.deletedHistoryNumbers || []);
  const history = [...new Map([...(previous.history || []), ...(next.history || [])].map(order => [order.number, order])).values()].filter(order => !deleted.has(order.number)).sort((a, b) => b.number - a.number);
  const sameOrder = previous.room.orderNumber === next.room.orderNumber;
  const keepOffset = !deleted.size && !olderPage && sameOrder && (previous.historyNextOffset === -1 || previous.historyNextOffset > next.historyNextOffset);
  return { ...next, history, historyNextOffset: keepOffset ? previous.historyNextOffset : next.historyNextOffset };
}
