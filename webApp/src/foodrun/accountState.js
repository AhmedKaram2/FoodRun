// HTTP acknowledgements and socket snapshots can arrive in a different order.
export function newestAccountReply(previous, incoming) {
  const before = Number(previous?.serverTime) || 0, after = Number(incoming?.serverTime) || 0;
  if (previous?.home?.profile?.userId === incoming?.home?.profile?.userId && before > 0 && after > 0 && after < before) return previous;
  return incoming;
}
