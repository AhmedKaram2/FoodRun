export function settlementOpen(room) {
  return ['PLACED', 'FULFILLED'].includes(room.phase) ||
    room.phase === 'ARCHIVED' && room.autoArchivedAt > 0 && ['PLACED', 'FULFILLED'].includes(room.autoArchiveFrom);
}
