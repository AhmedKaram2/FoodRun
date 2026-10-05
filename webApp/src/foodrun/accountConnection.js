// Reconnect account bootstrap without replaying room or payment changes.
// Only one request and one HOME subscription may belong to this connection.
export function accountConnection({ signIn, onConnected, onStatus, onError,
  events = globalThis.window, visibility = globalThis.document,
  isOnline = () => globalThis.navigator?.onLine !== false }) {
  let stopped = false, definitive = false, connected = false, active = null;
  let timer, retry = 1000, stopWatch = () => {};
  const connect = async () => {
    if (stopped || definitive || active) return;
    clearTimeout(timer);
    if (!isOnline()) { onStatus('offline'); return; }
    const abort = new AbortController(); active = abort;
    onStatus('connecting');
    try {
      const reply = await signIn(abort.signal);
      if (stopped || abort.signal.aborted) return;
      stopWatch();
      stopWatch = onConnected(reply) || (() => {});
      connected = true; retry = 1000; onStatus('connected');
      // Refresh from a successful connection, never alongside a pending retry.
      timer = setTimeout(connect, 50 * 60 * 1000);
    } catch (error) {
      if (stopped || abort.signal.aborted) return;
      connected = false;
      if (error.definitive || error.accessBlock || error.code === 'REAUTH_REQUIRED' || error.code?.startsWith('auth/') && !['auth/network-request-failed', 'auth/too-many-requests'].includes(error.code)) {
        definitive = true; onStatus('failed'); onError(error); return;
      }
      onStatus(isOnline() ? 'retrying' : 'offline');
      if (isOnline()) {
        timer = setTimeout(connect, retry);
        retry = Math.min(retry * 2, 30000);
      }
    } finally {
      if (active === abort) active = null;
    }
  };
  const resume = () => { if (!connected) connect(); };
  const foreground = () => { if (visibility.visibilityState === 'visible') resume(); };
  events?.addEventListener('online', resume);
  visibility?.addEventListener('visibilitychange', foreground);
  connect();
  return () => {
    if (stopped) return;
    stopped = true; active?.abort(); clearTimeout(timer); stopWatch();
    events?.removeEventListener('online', resume);
    visibility?.removeEventListener('visibilitychange', foreground);
  };
}
