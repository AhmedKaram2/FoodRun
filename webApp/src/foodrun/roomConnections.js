export const ongoingRoom = phase => !['ARCHIVED', 'CANCELLED'].includes(phase);

// Memberships retain history; ongoing rooms and archived unpaid bills keep a socket.
// Reconcile individual rooms so closing one does not reconnect every other room.
export function roomConnections({ watch, load, onReply, onStatus, getSnapshot = () => null }) {
  const entries = new Map();
  function remove(id) { const old = entries.get(id); if (old) { entries.delete(id); old.stop?.(); } }
  return {
    sync(sessions) {
      for (const id of entries.keys()) if (!sessions[id]) remove(id);
      for (const session of Object.values(sessions)) {
        const mode = ongoingRoom(session.phase) || session.paymentsPending ? 'live' : 'history';
        const old = entries.get(session.roomId);
        const key = `${session.token}:${session.memberId}:${mode}:${mode === 'history' ? session.orderNumber : ''}`;
        if (old?.key === key) continue;
        remove(session.roomId);
        const entry = { key };
        entries.set(session.roomId, entry);
        const current = () => entries.get(session.roomId) === entry;
        const receive = reply => { if (current()) onReply(session, reply); };
        if (mode === 'live') entry.stop = watch(session, receive, (...args) => { if (current()) onStatus(session, ...args); });
        else {
          const cached = getSnapshot(session.roomId);
          if (cached?.memberId === session.memberId && cached.room?.phase === session.phase && cached.room?.orderNumber === session.orderNumber) { onStatus(session, true, ''); continue; }
          const abort = new AbortController(); entry.stop = () => abort.abort();
          load(session, abort.signal).then(reply => { if (current()) { onStatus(session, true, ''); receive(reply); } }).catch(error => {
            if (current() && !abort.signal.aborted) { remove(session.roomId); onStatus(session, false, error.message, error); }
          });
        }
      }
    },
    close() { for (const id of [...entries.keys()]) remove(id); },
  };
}
