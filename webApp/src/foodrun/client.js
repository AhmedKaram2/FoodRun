import { t } from './i18n.js';
export const CURRENCIES = ['AED', 'EGP', 'USD', 'EUR', 'GBP', 'SAR', 'QAR', 'KWD', 'BHD', 'OMR', 'JOD', 'IQD', 'MAD', 'TND', 'TRY', 'INR', 'PKR', 'BDT', 'PHP', 'CAD', 'AUD', 'NZD', 'JPY', 'CNY', 'KRW', 'ZAR'];
export function currencyDigits(currency = 'AED') {
  if (!CURRENCIES.includes(currency)) throw Error(t('Unsupported currency.'));
  return ['KWD', 'BHD', 'OMR', 'JOD', 'IQD', 'TND'].includes(currency) ? 3 : ['JPY', 'KRW'].includes(currency) ? 0 : 2;
}
export function minorInput(value = 0, currency = 'AED') { const digits = currencyDigits(currency); return (value / 10 ** digits).toFixed(digits); }
export function money(value = 0, currency = 'AED') {
  const digits = currencyDigits(currency);
  return `${currency} ${(value / 10 ** digits).toFixed(digits)}`;
}
export function amount(text, currency = 'AED') {
  const digits = currencyDigits(currency);
  if (!new RegExp(`^\\d{1,9}${digits ? `(\\.\\d{1,${digits}})?` : ''}$`).test(String(text))) throw Error(t("Enter a valid price using digits and a decimal point."));
  const [whole, fraction = ''] = String(text).split('.');
  const result = Number(whole) * 10 ** digits + Number(fraction.padEnd(digits, '0'));
  if (!Number.isSafeInteger(result) || result > 100000000) throw Error(t("That amount is too large."));
  return result;
}
export function hubAddress(input) {
  const url = new URL(input);
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw Error('Use the hub HTTPS address, for example https://192.168.1.20:8443.');
  return url.origin;
}
export function normalizeReply(reply) {
  const receipt = value => ({ ...value, totalText: money(value.total, value.currency), balanceText: money(value.balance, value.currency) });
  return { ...reply, receipts: (reply.receipts || []).map(receipt), history: (reply.history || []).map(order => ({ ...order, receipts: (order.receipts || []).map(receipt) })) };
}
export async function request(hub, command, signal) {
  // A sleeping internet hub can need about a minute to start. Only account
  // connection gets this longer deadline; order/payment commands are not retried.
  const timeoutMs = command.kind === 'COMMAND_STATUS' || command.kind === 'IDENTITY' && command.identity?.action === 'FIREBASE_SIGN_IN' ? 90000 : 20000;
  const abort = new AbortController();
  const cancel = () => abort.abort(signal.reason);
  if (signal?.aborted) cancel();
  else signal?.addEventListener('abort', cancel, { once: true });
  const timer = setTimeout(() => abort.abort(new DOMException("The order server took too long to respond.", 'TimeoutError')), timeoutMs);
  try {
    abort.signal.throwIfAborted();
    const response = await fetch(`${hub}/command`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(command), signal: abort.signal, credentials: 'omit', redirect: 'error' });
    const reply = await response.json().catch(error => { if (abort.signal.aborted) throw error; return null; });
    abort.signal.throwIfAborted();
    if (!response.ok || !reply?.ok) {
      const definitive = response.status >= 400 && response.status < 500 && ![408, 429].includes(response.status) || response.ok && reply?.code && reply.code !== 'HUB_UNAVAILABLE';
      throw Object.assign(Error(reply?.error || `Hub request failed (${response.status}). Check your connection and retry.`), { definitive: !!definitive, code: reply?.code, accessBlock: reply?.accessBlock, serverTime: reply?.serverTime });
    }
    return normalizeReply(reply);
  } catch (error) {
    if (signal?.aborted) throw signal.reason;
    if (abort.signal.aborted) throw Object.assign(Error(t("The order server took too long to respond. Please try again.")), { code: 'TIMEOUT', definitive: false });
    throw error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', cancel);
  }
}
export function command(kind, fields = {}) { return { protocolVersion: 1, commandId: crypto.randomUUID(), kind, ...fields, selectionDetails: true, visualSelectionDetails: true, liveRoomDetails: true, multiplePaymentDetails: true, wheelProtectionDetails: true, autoArchiveDetails: true, walletDetails: true, halfItemDetails: true, friendsDetails: true, friendMembershipDetails: true, notificationPreferencesDetails:true }; }
export function watch(hub, payload, onReply, onStatus, { events = globalThis.window,
  visibility = globalThis.document, isOnline = () => globalThis.navigator?.onLine !== false } = {}) {
  let socket, stopped = false, timer, watchdog, retry = 1000;
  const closeSocket = () => {
    clearTimeout(watchdog);
    const old = socket; socket = null;
    if (old) { old.onopen = old.onmessage = old.onclose = old.onerror = null; old.close(); }
  };
  const reconnect = (delay = retry) => {
    closeSocket(); clearTimeout(timer);
    if (!stopped && isOnline()) {
      timer = setTimeout(connect, delay); retry = Math.min(retry * 2, 30000);
    }
  };
  const armWatchdog = () => {
    clearTimeout(watchdog);
    // Application heartbeats detect sockets left open after a browser/network suspension.
    watchdog = setTimeout(() => { onStatus(false, ''); reconnect(); }, 60000);
  };
  const connect = () => {
    if (stopped || !isOnline()) return;
    clearTimeout(timer); closeSocket();
    const current = socket = new WebSocket(hub.replace(/^https:/, 'wss:') + '/events');
    watchdog = setTimeout(() => { onStatus(false, ''); reconnect(); }, 90000);
    current.onopen = () => { if (socket === current) { current.send(JSON.stringify({ ...payload, text: 'live-check-v1' })); armWatchdog(); } };
    current.onmessage = event => {
      if (socket !== current || stopped) return;
      try {
        const reply = JSON.parse(event.data);
        if (!reply.ok) {
          onStatus(false, reply.error, reply);
          if (reply.code === 'HUB_UNAVAILABLE') reconnect();
          else { stopped = true; closeSocket(); }
          return;
        }
        retry = 1000; armWatchdog(); onStatus(true, '');
        if (reply.code === 'LIVE') return;
        onReply(normalizeReply(reply));
        if (['ARCHIVED', 'CANCELLED'].includes(reply.room?.phase) && !reply.room?.paymentsPending) { stopped = true; closeSocket(); }
      } catch { onStatus(false, 'The hub returned an unreadable update.'); reconnect(); }
    };
    current.onclose = () => { if (socket === current) { onStatus(false, ''); reconnect(); } };
    current.onerror = () => { if (socket === current) { onStatus(false, ''); reconnect(); } };
  };
  const resume = () => { if (!stopped) { retry = 1000; connect(); } };
  const foreground = () => { if (visibility.visibilityState === 'visible') resume(); };
  const offline = () => { closeSocket(); clearTimeout(timer); onStatus(false, ''); };
  events?.addEventListener('online', resume); events?.addEventListener('offline', offline);
  visibility?.addEventListener('visibilitychange', foreground);
  connect();
  return () => {
    stopped = true; clearTimeout(timer); closeSocket();
    events?.removeEventListener('online', resume); events?.removeEventListener('offline', offline);
    visibility?.removeEventListener('visibilitychange', foreground);
  };
}
export async function photoData(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) throw Error(t("Choose a JPEG, PNG or WebP image under 10 MB."));
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 256;
  const size = Math.min(bitmap.width, bitmap.height);
  canvas.getContext('2d').drawImage(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size, 0, 0, 256, 256);
  bitmap.close(); return canvas.toDataURL('image/jpeg', 0.8);
}
