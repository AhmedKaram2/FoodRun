import { t } from './i18n.js';
import { reminderKey } from './paymentReminders.js';
import { useFeedback } from './useFeedback.js';
import { useCallback, useEffect, useRef, useState, useMemo } from 'react';
import { onAuthStateChanged, signOut } from 'firebase/auth';
import { auth } from '../firebase';
import { command, request, watch } from './client';
import { mergeRoomReply } from './roomState';
import { roomConnections } from './roomConnections';
import { accountConnection } from './accountConnection.js';
import { readReceiptArchive, saveReceiptArchive, clearReceiptArchive, clearUserReceiptArchives } from './offlineReceipts';

const publicHub = import.meta.env.VITE_FOODRUN_API_URL?.trim().replace(/\/$/, '') || 'https://foodrun-api-q6b9.onrender.com';
function invitedHub() {
  try {
    const value = new URLSearchParams(window.location.search).get('hub');
    const url = value ? new URL(value) : null;
    return url?.protocol === 'https:' ? url.origin : '';
  } catch { return ''; }
}
function sessionStorageKey(userId, hub) { return `foodrun-sessions-v1:${userId}:${hub}`; }

export function useFoodRun({ supportSession = null, onSupportEnd } = {}) {
  const [accessBlock, setAccessBlock] = useState(null);
  const [joinBlock, setJoinBlock] = useState(null);
  const [roomBlocks, setRoomBlocks] = useState({});
  const readBlock = reply => {
    if(reply?.code === 'SUPPORT_ENDED') { onSupportEnd?.(); return; }
    if (reply?.code === 'REAUTH_REQUIRED') {
      setNotice(t('Please sign in again to reconnect your FoodRun account.'));
      if(supportSession) onSupportEnd?.(); else signOut(auth).catch(() => setError(t('Please sign out and sign in again.')));
      return;
    }
    if (reply?.accessBlock) {
      const block = { ...reply.accessBlock, serverTime: reply.serverTime || Date.now(), receivedAt: Date.now() };
      setAccessBlock(block); setRoomBlocks(old => ({ ...old, [block.roomId || '*']: block }));
    }
    if (reply?.home) setRoomBlocks(Object.fromEntries(Object.entries(reply.home.roomAccessBlocks || {}).map(([id, block]) => [id, { ...block, serverTime: reply.serverTime || Date.now(), receivedAt: Date.now() }])));
  };
  const [firebaseUser, setUser] = useState(null), [authReady, setAuthReady] = useState(false);
  const user = useMemo(() => supportSession ? { uid: supportSession.home.profile.userId, email: '', emailVerified: false, displayName: supportSession.home.profile.name } : firebaseUser, [firebaseUser, supportSession]);
  const [hub, setHub] = useState(() => invitedHub() || localStorage.getItem('foodrun-hub') || import.meta.env.VITE_FOODRUN_HUB_URL || publicHub);
  const [hubRevision, setHubRevision] = useState(0);
  const [connectionState, setConnectionState] = useState('');
  const [home, setHome] = useState(null), [rooms, setRooms] = useState({}), [online, setOnline] = useState({});
  const { error, setError, notice, setNotice, feedback, dismissFeedback } = useFeedback();
  const [busy, setBusy] = useState(false);
  const [paymentReminderTimes, setPaymentReminderTimes] = useState({});
  const [paymentReminderStates, setPaymentReminderStates] = useState({});
  const [sessionScope, setSessionScope] = useState('');
  const [offlineReceipts, setOfflineReceipts] = useState([]);
  const [identityToken, setIdentityToken] = useState(''), [sessions, setSessions] = useState({});
  const inFlight = useRef(false);
  const authUser = useRef(null);
  const seen = useRef(new Set()), pending = useRef(null), alive = useRef(0), roomRef = useRef({});
  const alert = useCallback((id, title, body) => {
    if (seen.current.has(id)) return;
    seen.current.add(id); setNotice(`${title} ${body}`);
    try { if ('Notification' in window && Notification.permission === 'granted') /* The server delivers system notifications through Firebase. */ void 0; } catch { /* In-app notice remains available on browsers without notification constructors. */ }
  }, []);
  useEffect(() => onAuthStateChanged(auth, next => {
    if(!next) onSupportEnd?.();
    if (authUser.current && authUser.current !== next?.uid) {
      ++alive.current;
      clearUserReceiptArchives(authUser.current).catch(() => {});
    }
    authUser.current = next?.uid;
    setUser(next); setAuthReady(true);
  }), []);
  useEffect(() => {
    const epoch = ++alive.current; setSessionScope(''); setHome(null); setRooms({}); roomRef.current = {}; setSessions({}); setOnline({}); setBusy(false); inFlight.current = false; setIdentityToken(''); setAccessBlock(null); setRoomBlocks({}); setJoinBlock(null); setPaymentReminderTimes({}); setPaymentReminderStates({}); seen.current.clear(); pending.current = null;
    setConnectionState(''); setError('');
    if (!user || !hub) return;
    try {
      pending.current = supportSession ? null : JSON.parse(sessionStorage.getItem(`foodrun-pending:${user.uid}:${hub}`) || 'null');
      if (pending.current) setError(t("A saved request needs confirmation. Retry it before making another change."));
    } catch { pending.current = null; }
    try { if(!supportSession) setSessions(JSON.parse(localStorage.getItem(sessionStorageKey(user.uid, hub)) || '{}')); } catch { setSessions({}); }
    let cancelled = false;
    const current = () => !cancelled && epoch === alive.current;
    const stop = accountConnection({
      signIn: async signal => {
        if(supportSession) return request(hub, command('HOME', { identityToken: supportSession.identityToken }), signal).then(reply => ({ ...reply, identityToken: supportSession.identityToken }));
        const firebaseToken = await user.getIdToken();
        signal.throwIfAborted();
        return request(hub, command('IDENTITY', { identity: { action: 'FIREBASE_SIGN_IN', firebaseToken } }), signal);
      },
      onStatus: state => { if (current()) setConnectionState(state); },
      onConnected: reply => {
        if (!current()) return;
        readBlock(reply); setAccessBlock(null); setIdentityToken(reply.identityToken); setSessionScope(sessionStorageKey(user.uid, hub)); setHome(reply.home); if (!pending.current) setError('');
        setSessions(Object.fromEntries(reply.home.rooms.map(room => [room.roomId, room])));
        return watch(hub, command('HOME', { identityToken: reply.identityToken }), next => {
          if (!current()) return;
          readBlock(next); setHome(next.home);
          setSessions(Object.fromEntries(next.home.rooms.map(room => [room.roomId, room])));
          next.home.invitations.forEach(invite => alert(`invite:${invite.id}`, `You're invited to ${invite.roomName}.`, t("Join from your home screen.")));
        }, (connected, reason, statusReply) => { if (!current()) return; readBlock(statusReply); setOnline(old => ({ ...old, home: connected })); if (reason) setError(reason); });
      },
      onError: error => { if (current()) { readBlock(error); setError(error.message); } },
    });
    return () => { cancelled = true; stop(); };
  }, [user, hub, hubRevision, alert, supportSession]);
  useEffect(() => {
    let cancelled = false;
    setOfflineReceipts([]);
    if (user && hub && !supportSession) readReceiptArchive(user.uid, hub).then(value => { if (!cancelled) setOfflineReceipts(value); }).catch(() => {});
    return () => { cancelled = true; };
  }, [user?.uid, hub]);
  const cacheReceipt = reply => {
    if(supportSession) return;
    const epoch = alive.current;
    saveReceiptArchive(user.uid, hub, reply).then(value => { if (epoch === alive.current) setOfflineReceipts(value); }).catch(() => setNotice(t("Receipts could not be saved offline. Check browser storage permissions."), true));
  };
  useEffect(() => {
    if (!user || !hub || sessionScope !== sessionStorageKey(user.uid, hub)) return;
    if(!supportSession) localStorage.setItem(sessionStorageKey(user.uid, hub), JSON.stringify(sessions));
  }, [user, hub, sessions, sessionScope]);
  const connections = useRef(null);
  const sessionKey = Object.values(sessions).map(s => `${s.roomId}:${s.token}:${s.memberId}:${s.phase}:${s.orderNumber}`).sort().join('|');
  useEffect(() => {
    if (!hub || !user || !identityToken || sessionScope !== sessionStorageKey(user.uid, hub)) return;
    const epoch = alive.current;
    let stopped = false;
    const manager = roomConnections({
      getSnapshot: id => roomRef.current[id],
      watch: (session, receive, status) => watch(hub, command('SNAPSHOT', { roomId: session.roomId, token: session.token }), receive, status),
      load: (session, signal) => request(hub, command('SNAPSHOT', { roomId: session.roomId, token: session.token }), signal),
      onReply: (session, reply) => {
      if (stopped || epoch !== alive.current) return;
      const r = reply.room;
      if (!r || r.id !== session.roomId || reply.memberId !== session.memberId) return;
      const previous = roomRef.current[r.id];
      if (previous?.room.revision > r.revision) return;
      roomRef.current = { ...roomRef.current, [r.id]: mergeRoomReply(previous, reply) }; setRooms(roomRef.current);
      if (!previous || previous.room.revision !== r.revision) cacheReceipt(roomRef.current[r.id]);
      if (r.phase === 'ACCEPTING' && r.spin?.winnerId === session.memberId) alert(`spin:${r.spin.id}`, t("You're selected!"), `Join ${r.name} and accept to collect everyone's food.`);
      if (r.phase === 'COLLECTING' && !r.spin && r.payerId === session.memberId) alert(`selected:${r.id}:${r.orderNumber}`, t("You're selected!"), r.name);
      if (r.phase === 'PLACED' && previous?.room.phase !== 'PLACED') alert(`placed:${r.id}:${r.orderNumber}`, t("The restaurant order was placed."), `${r.restaurant.name} · ${r.restaurantReference}`);
      const currentReceipt = reply.receipts.find(receipt => receipt.memberId === session.memberId);
      const previousReceipt = previous?.receipts?.find(receipt => receipt.memberId === session.memberId);
      if (currentReceipt?.balance === 0 && previousReceipt?.balance > 0) alert(`paid:${r.id}:${r.orderNumber}:${currentReceipt.revision}`, t("Payment confirmed."), `Your ${currentReceipt.totalText} share is settled.`);
      if (r.phase === 'PREPARING_SPIN' && !r.preparedIds.includes(session.memberId) && r.members.some(m => m.id === session.memberId && m.approved && m.participating && !m.guest)) {
        request(hub, command('ACK_SPIN', { roomId: r.id, token: session.token, expectedOrderNumber: r.orderNumber, text: r.preparationId })).catch(e => setError(e.message));
      }
    }, onStatus: (session, connected, reason, statusReply) => { if (!stopped && epoch === alive.current) { readBlock(statusReply); setOnline(old => ({ ...old, [session.roomId]: connected })); if(reason) setError(reason); } } });
    connections.current = manager; manager.sync(sessions);
    return () => { stopped = true; manager.close(); if (connections.current === manager) connections.current = null; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hub, user?.uid, identityToken, sessionScope, alert]);
  useEffect(() => { connections.current?.sync(sessions); }, [sessionKey]);
  useEffect(() => { roomRef.current = {}; }, [hub, user?.uid]);
  const pendingKey = user && hub ? `foodrun-pending:${user.uid}:${hub}` : '';
  const savePending = payload => {
    // Persist before sending, so refresh cannot turn a lost acknowledgement into a second order/payment.
    if(!supportSession) {
      if (payload) sessionStorage.setItem(pendingKey, JSON.stringify(payload));
      else sessionStorage.removeItem(pendingKey);
    }
    pending.current = payload;
  };
  const accept = (reply, olderPage = false) => {
    if (reply.home) setHome(reply.home);
    if (!reply.room) return;
    const r = reply.room;
    if (reply.token) setSessions(old => ({ ...old, [r.id]: { roomId: r.id, roomName: r.name, memberId: reply.memberId, token: reply.token } }));
    roomRef.current = { ...roomRef.current, [r.id]: mergeRoomReply(roomRef.current[r.id], reply, olderPage) };
    setRooms(roomRef.current);
    cacheReceipt(roomRef.current[r.id]);
  };
  const execute = async (payload, { retrying = false, olderPage = false } = {}) => {
    if (inFlight.current) return null;
    if (pending.current && !retrying) { setError(t("Retry the saved request before making another change.")); return null; }
    const epoch = alive.current;
    inFlight.current = true; setBusy(true); setError('');
    try {
      if (!['IDENTITY', 'SNAPSHOT', 'WALLET_PEOPLE', 'WALLET_RECIPIENT', 'FRIEND_LOOKUP'].includes(payload.kind)) savePending(payload);
      const reply = await request(hub, payload);
      if (epoch !== alive.current) return null;
      savePending(null); accept(reply, olderPage);
      if (payload.kind === 'REMIND_PAYMENT') {
        const key = reminderKey({ id: payload.roomId, orderNumber: payload.expectedOrderNumber }, payload.memberId);
        const state = reply.code === 'REMINDER_NOTIFIED' ? 'notified' : reply.code === 'REMINDER_SENT' ? 'sent' : reply.code === 'REMINDER_FAILED' ? 'failed' : 'pending';
        setPaymentReminderStates(old => ({ ...old, [key]: state }));
        if (state !== 'failed') setPaymentReminderTimes(old => ({ ...old, [key]: Date.now() }));
        if (state === 'failed') setError(t('Email could not be sent. Please try again.'));
        else setNotice(t(state === 'notified' ? 'Reminder sent in the app; recipient emails are disabled.' : state === 'sent' ? 'Email reminder sent' : 'Sending email reminder…'));
      }
      if (payload.kind === 'RECORD_PAYMENT') setNotice(t('Payment recorded.'));
      if (['CONFIRM_TRANSFER', 'CONFIRM_REFUND'].includes(payload.kind)) setNotice(t('Payment confirmed.'));
      return reply;
    } catch (e) {
      if (epoch !== alive.current) return null;
      readBlock(e);
      if (e.accessBlock && ['JOIN', 'CREATE'].includes(payload.kind)) setJoinBlock({ ...e.accessBlock, serverTime: e.serverTime || Date.now(), receivedAt: Date.now() });
      if (e.definitive) savePending(null);
      if (payload.kind === 'REMIND_PAYMENT' && e.code === 'REMINDER_EMAIL_REQUIRED') {
        setError(''); return { ok: false, code: e.code, error: e.message };
      }
      setError(e.message + (pending.current ? ' Retry the saved request to confirm its result.' : ''));
      return null;
    } finally {
      if (epoch === alive.current) { inFlight.current = false; setBusy(false); }
    }
  };
  const send = async (kind, fields = {}, roomId = '') => {
    const session = sessions[roomId], room = roomRef.current[roomId]?.room;
    if (roomId && (!session || !room)) { setError(t("Reconnect to this room before making changes.")); return null; }
    const payload = command(kind, { identityToken, ...(session ? { roomId, token: session.token, expectedRevision: room.revision, expectedOrderNumber: room.orderNumber } : {}), ...fields });
    return execute(payload);
  };
  const retry = () => pending.current ? execute(pending.current, { retrying: true }) : null;
  const loadOlderHistory = roomId => {
    const session = sessions[roomId], offset = roomRef.current[roomId]?.historyNextOffset;
    if (!session || offset == null || offset < 0) return null;
    return execute(command('SNAPSHOT', { roomId, token: session.token, historyOffset: offset }), { olderPage: true });
  };
  const connect = address => {
    if (address) localStorage.setItem('foodrun-hub', address); else localStorage.removeItem('foodrun-hub');
    setError(''); setJoinBlock(null); setHub(address); setHubRevision(value => value + 1);
  };
  const clearOfflineReceipts = async () => {
    try { await clearReceiptArchive(user.uid, hub); setOfflineReceipts([]); }
    catch { setError(t("Could not remove downloaded receipts. Check browser storage permissions.")); }
  };
  const checkPaymentReminder = async (roomId, memberId) => {
    const room = roomRef.current[roomId]?.room, session = sessions[roomId];
    if (!room || !session) return;
    const epoch = alive.current, key = reminderKey(room, memberId);
    try {
      const reply = await request(hub, command('PAYMENT_REMINDER_STATUS', { roomId, token: session.token, memberId, expectedOrderNumber: room.orderNumber }));
      if (epoch !== alive.current) return;
      const state = reply.code === 'REMINDER_NOTIFIED' ? 'notified' : reply.code === 'REMINDER_SENT' ? 'sent' : reply.code === 'REMINDER_PENDING' ? 'pending' : 'failed';
      setPaymentReminderStates(old => ({ ...old, [key]: state }));
      if (state === 'sent') setNotice(t('Email reminder sent'));
      if (state === 'failed') { setPaymentReminderTimes(old => { const next = { ...old }; delete next[key]; return next; }); setError(t('Email could not be sent. Please try again.')); }
    } catch { /* A later status check can recover after a network interruption. */ }
  };
  const walletQuery = (kind, fields = {}, signal) => request(hub, command(kind, { identityToken, ...fields }), signal);
  return { walletQuery, identityToken, accessBlock, roomBlocks, joinBlock, clearJoinBlock: () => setJoinBlock(null), user, authReady, hub, connectionState, home, rooms, sessions, online, error, setError, notice, setNotice, feedback, dismissFeedback, busy, send, retry, loadOlderHistory, hasPending: !!pending.current, connect, offlineReceipts, clearOfflineReceipts, paymentReminderTimes, paymentReminderStates, checkPaymentReminder };
}
