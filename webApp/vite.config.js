import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

function offlineShell() {
  return {
    name: 'foodrun-offline-shell',
    generateBundle(_, bundle) {
      const files = new Set(['/', '/index.html', '/manifest.json', '/foodrun-icon-192.png']);
      const add = chunk => {
        const path = '/' + chunk.fileName;
        if (files.has(path)) return;
        files.add(path);
        for (const name of chunk.imports || []) if (bundle[name]) add(bundle[name]);
      };
      for (const item of Object.values(bundle)) {
        if (item.type === 'chunk' && (item.isEntry || item.name === 'ReceiptArchiveScreen')) add(item);
        if (item.fileName.endsWith('.css')) files.add('/' + item.fileName);
      }
      const version = [...files].sort().join('|');
      // Hashed asset paths change on every code change; old caches are removed on activation.
      this.emitFile({ type: 'asset', fileName: 'sw.js', source: `
const CACHE = 'foodrun-shell-' + ${JSON.stringify(version)};
const FILES = ${JSON.stringify([...files])};
self.addEventListener('install', event => { event.waitUntil(caches.open(CACHE).then(cache => cache.addAll(FILES))); });
self.addEventListener('activate', event => { event.waitUntil(caches.keys().then(keys => Promise.all(keys.filter(key => key.startsWith('foodrun-shell-') && key !== CACHE).map(key => caches.delete(key)))).then(() => self.clients.claim())); });
self.addEventListener('fetch', event => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin) return;
  if (event.request.mode === 'navigate') {
    event.respondWith(fetch(event.request).catch(() => caches.match('/index.html')));
  } else if (FILES.includes(url.pathname) || /^\\/assets\\/[^/]+\\.(js|css)$/.test(url.pathname)) {
    event.respondWith(caches.open(CACHE).then(async cache => {
      const saved = await cache.match(event.request);
      if (saved) return saved;
      const response = await fetch(event.request);
      if (response.ok) await cache.put(event.request, response.clone());
      return response;
    }));
  }
});` });
    },
  };
}
export default defineConfig({
  plugins: [react(), offlineShell()],
  server: { host: '0.0.0.0' },
  build: { rollupOptions: { output: { manualChunks(id) {
    if (id.includes('node_modules/@firebase/auth')) return 'firebase-auth';
    if (id.includes('node_modules/@firebase')) return 'firebase-core';
    if (id.includes('node_modules/react')) return 'react-vendor';
  } } } },
});
