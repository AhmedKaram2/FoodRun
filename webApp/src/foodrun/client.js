export function money(value = 0, currency = 'AED') {
  const digits = ['KWD', 'BHD', 'OMR'].includes(currency) ? 3 : currency === 'JPY' ? 0 : 2;
  return `${currency} ${(value / 10 ** digits).toFixed(digits)}`;
}
export function amount(text, currency = 'AED') {
  const digits = ['KWD', 'BHD', 'OMR'].includes(currency) ? 3 : currency === 'JPY' ? 0 : 2;
  if (!new RegExp(`^\\d{1,9}${digits ? `(\\.\\d{1,${digits}})?` : ''}$`).test(String(text))) throw Error('Enter a valid price using digits and a decimal point.');
  const [whole, fraction = ''] = String(text).split('.');
  const result = Number(whole) * 10 ** digits + Number(fraction.padEnd(digits, '0'));
  if (!Number.isSafeInteger(result) || result > 100000000) throw Error('That amount is too large.');
  return result;
}
export function hubAddress(input) {
  const url = new URL(input);
  if (url.protocol !== 'https:' || url.username || url.password || url.pathname !== '/' || url.search || url.hash) throw Error('Use the hub HTTPS address, for example https://192.168.1.20:8443.');
  return url.origin;
}
export async function request(hub, command, signal) {
  const response = await fetch(`${hub}/command`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(command), signal: signal || AbortSignal.timeout(20000), credentials: 'omit', redirect: 'error' });
  if (!response.ok) throw Error(`Hub request failed (${response.status}). Check your connection and retry.`);
  const reply = await response.json();
  if (!reply.ok) throw Object.assign(Error(reply.error || 'This action is unavailable.'), { definitive: reply.code !== 'HUB_UNAVAILABLE' });
  return reply;
}
export function command(kind, fields = {}) { return { protocolVersion: 1, commandId: crypto.randomUUID(), kind, ...fields }; }
export function watch(hub, payload, onReply, onStatus) {
  let socket, stopped = false, timer, retry = 1000;
  const connect = () => {
    if (stopped) return;
    socket = new WebSocket(hub.replace(/^https:/, 'wss:') + '/events');
    socket.onopen = () => socket.send(JSON.stringify(payload));
    socket.onmessage = event => {
      try {
        const reply = JSON.parse(event.data);
        if (!reply.ok) { onStatus(false, reply.error); stopped = true; socket.close(); return; }
        retry = 1000; onStatus(true, ''); onReply(reply);
      } catch { onStatus(false, 'The hub returned an unreadable update.'); }
    };
    socket.onclose = () => { onStatus(false, ''); if (!stopped) { timer = setTimeout(connect, retry); retry = Math.min(retry * 2, 30000); } };
    socket.onerror = () => socket.close();
  };
  connect();
  return () => { stopped = true; clearTimeout(timer); socket?.close(); };
}
export async function photoData(file) {
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) throw Error('Choose a JPEG, PNG or WebP image under 10 MB.');
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement('canvas'); canvas.width = canvas.height = 256;
  const size = Math.min(bitmap.width, bitmap.height);
  canvas.getContext('2d').drawImage(bitmap, (bitmap.width - size) / 2, (bitmap.height - size) / 2, size, size, 0, 0, 256, 256);
  bitmap.close(); return canvas.toDataURL('image/jpeg', 0.8);
}
