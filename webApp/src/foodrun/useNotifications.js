import { useCallback, useEffect, useRef, useState } from 'react';
import app from '../firebase';
import { notificationRequest } from './notifications.js';

export function useNotifications({ hub, identityToken, user, language = 'en' }) {
  const [items, setItems] = useState([]), [error, setError] = useState(''), [available, setAvailable] = useState(false), [enabled, setEnabled] = useState(false);
  const epoch = useRef(0), pushToken = useRef('');
  const call = useCallback(async fields => {
    const generation = epoch.current;
    const value = await notificationRequest(hub, identityToken, fields);
    if (generation === epoch.current) { setItems(value.notifications || []); setAvailable(value.pushAvailable); setError(''); }
    return value;
  }, [hub, identityToken]);
  const refresh = useCallback(() => { if (identityToken) call({}).catch(() => {}); }, [identityToken, call]);
  const enable = useCallback(async (ask = true) => {
    const generation = epoch.current;
    try {
      if (!identityToken) return;
      if (!import.meta.env.VITE_FIREBASE_VAPID_KEY) { if (ask) throw Error('Browser push setup is being completed. Your updates are available in this inbox.'); return; }
      if (!('Notification' in window) || !('serviceWorker' in navigator)) throw Error('Notifications are unavailable in this browser.');
      const permission = ask ? await Notification.requestPermission() : Notification.permission;
      if (permission !== 'granted') { if (ask) throw Error('Enable notifications in browser settings.'); return; }
      const { getMessaging, getToken, isSupported } = await import('firebase/messaging');
      if (!(await isSupported())) throw Error('Notifications are unavailable in this browser.');
      const registration = await navigator.serviceWorker.register(import.meta.env.PROD ? '/sw.js' : '/notification-worker.js');
      await navigator.serviceWorker.ready;
      const token = await getToken(getMessaging(app), { serviceWorkerRegistration: registration, ...(import.meta.env.VITE_FIREBASE_VAPID_KEY ? { vapidKey: import.meta.env.VITE_FIREBASE_VAPID_KEY } : {}) });
      if (generation !== epoch.current) return;
      let installationId = localStorage.getItem('foodrun-push-installation');
      if (!installationId) { installationId = crypto.randomUUID(); localStorage.setItem('foodrun-push-installation', installationId); }
      await call({ action: 'register', token, installationId, platform: 'web', language });
      if (generation !== epoch.current) return;
      pushToken.current = token; setEnabled(true); localStorage.setItem('foodrun-push-enabled', 'true');
    } catch (failure) { if (generation === epoch.current) setError(failure.message); }
  }, [identityToken, call, language]);
  const disable = useCallback(async () => {
    ++epoch.current;
    const failures = [];
    try {
      if (pushToken.current) await call({ action: 'unregister', token: pushToken.current });
    } catch (failure) { failures.push(failure.message); }
    try {
      const { deleteToken, getMessaging } = await import('firebase/messaging');
      await deleteToken(getMessaging(app));
    } catch (failure) { failures.push(failure.message); }
    try {
      const registration = await navigator.serviceWorker.getRegistration();
      if (registration) (await registration.getNotifications()).forEach(value => value.close());
    } catch (failure) { failures.push(failure.message); }
    pushToken.current = ''; setEnabled(false); localStorage.removeItem('foodrun-push-enabled');
    if (failures.length) setError(failures.join(' '));
  }, [call]);
  useEffect(() => {
    ++epoch.current; setItems([]); setError(''); setAvailable(false); setEnabled(false);
    if (!identityToken || !user?.uid) return;
    refresh();
    if (localStorage.getItem('foodrun-push-enabled') === 'true') enable(false);
    const interval = setInterval(refresh, 15000);
    const message = event => { if (event.data?.type === 'foodrun-notifications-updated') refresh(); };
    navigator.serviceWorker?.addEventListener('message', message);
    window.addEventListener('focus', refresh);
    return () => {
      ++epoch.current; clearInterval(interval); window.removeEventListener('focus', refresh); navigator.serviceWorker?.removeEventListener('message', message);
    };
  }, [hub, identityToken, user?.uid, refresh, enable]);
  return { items, error, available, configured: !!import.meta.env.VITE_FIREBASE_VAPID_KEY, enabled, enable, disable, refresh, read: id => call({ action: 'read', notificationId: id }).catch(failure => setError(failure.message)) };
}
