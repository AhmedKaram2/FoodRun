/* Shared with the offline service worker; FCM data messages use the standard Push API. */
self.addEventListener('push', event => {
  let data;
  try { data = event.data.json().data; } catch { return; }
  if (data?.foodrun !== '1') return;
  let item;
  try { item = JSON.parse(data.payload); } catch { return; }
  if (!/^[a-f0-9]{40}$/.test(item.id)) return;
  event.waitUntil((async () => {
    await self.registration.showNotification(item.title, {
      body: item.body, icon: '/foodrun-icon-192.png', tag: item.id,
      data: { id: item.id }, actions: (item.actions || []).slice(0, 2).map(a => ({ action: a.id, title: a.title })),
    });
    const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    clients.forEach(client => client.postMessage({ type: 'foodrun-notifications-updated' }));
  })());
});
self.addEventListener('notificationclick', event => {
  const id = event.notification.data?.id;
  if (!/^[a-f0-9]{40}$/.test(id || '')) return;
  event.notification.close();
  const action = ['open', 'order', 'copy', 'share', 'pay', 'confirm', 'accept'].includes(event.action) ? event.action : 'open';
  const url = new URL('/', self.location.origin);
  url.searchParams.set('notification', id); url.searchParams.set('action', action);
  event.waitUntil((async () => {
    const clients = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
    const client = clients.find(value => new URL(value.url).origin === url.origin);
    if (client) { await client.navigate(url.href); await client.focus(); }
    else await self.clients.openWindow(url.href);
  })());
});
