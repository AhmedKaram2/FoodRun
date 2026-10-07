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
  const timeoutMs = command.kind === 'IDENTITY' && command.identity?.action === 'FIREBASE_SIGN_IN' ? 90000 : 20000;
  const abort = new AbortController();
  const cancel = () => abort.abort(signal.reason);
  if (signal?.aborted) cancel();
  else signal?.addEventListener('abort', cancel, { once: true });
  const timer = setTimeout(() => abort.abort(new DOMException('The room server took too long to respond.', 'TimeoutError')), timeoutMs);
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
    if (abort.signal.aborted) throw Object.assign(Error(t('The room server took too long to respond. Please try again.')), { code: 'TIMEOUT', definitive: false });
    throw error;
  } finally {
    clearTimeout(timer);
    signal?.removeEventListener('abort', cancel);
  }
}
export function command(kind, fields = {}) { return { protocolVersion: 1, commandId: crypto.randomUUID(), kind, ...fields, selectionDetails: true, visualSelectionDetails: true, liveRoomDetails: true, multiplePaymentDetails: true, wheelProtectionDetails: true, autoArchiveDetails: true, walletDetails: true, halfItemDetails: true, friendsDetails: true, friendMembershipDetails: true }; }
export function watch(hub, payload, onReply, onStatus) {
  let socket, stopped = false, timer, retry = 1000;
  const connect = () => {
    if (stopped) return;
    socket = new WebSocket(hub.replace(/^https:/, 'wss:') + '/events');
    socket.onopen = () => socket.send(JSON.stringify(payload));
    socket.onmessage = event => {
      try {
        const reply = JSON.parse(event.data);
        if (!reply.ok) { onStatus(false, reply.error, reply); stopped = true; socket.close(); return; }
        retry = 1000; onStatus(true, ''); onReply(normalizeReply(reply));
        if (['ARCHIVED', 'CANCELLED'].includes(reply.room?.phase) && !reply.room?.paymentsPending) { stopped = true; socket.close(); }
      } catch { onStatus(false, 'The hub returned an unreadable update.'); }
    };
    socket.onclose = () => { onStatus(false, ''); if (!stopped) { timer = setTimeout(connect, retry); retry = Math.min(retry * 2, 30000); } };
    socket.onerror = () => socket.close();
  };
  connect();
  return () => { stopped = true; clearTimeout(timer); socket?.close(); };
}
export async function photoData(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) throw Error(t("Choose a JPEG, PNG or WebP image under 10 MB."));
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 256;
  const size = Math.min(bitmap.width, bitmap.height);
  canvas.getContext('2d').drawImage(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size, 0, 0, 256, 256);
  bitmap.close(); return canvas.toDataURL('image/jpeg', 0.8);
}
