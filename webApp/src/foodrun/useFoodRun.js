import { useCallback, useEffect, useRef, useState } from 'react';
import { onAuthStateChanged } from 'firebase/auth';
import { auth } from '../firebase';
import { command, request, watch } from './client';

const publicHub = import.meta.env.VITE_FOODRUN_API_URL?.trim().replace(/\/$/, '') || '';

export function useFoodRun() {
  const [user, setUser] = useState(null), [authReady, setAuthReady] = useState(false);
  const [hub, setHub] = useState(() => localStorage.getItem('foodrun-hub') || import.meta.env.VITE_FOODRUN_HUB_URL || publicHub);
  const [hubRevision, setHubRevision] = useState(0);
  const [home, setHome] = useState(null), [rooms, setRooms] = useState({}), [online, setOnline] = useState({});
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [busy, setBusy] = useState(false);
  const [identityToken, setIdentityToken] = useState(''), [sessions, setSessions] = useState({});
  const seen = useRef(new Set()), pending = useRef(null), alive = useRef(0), roomRef = useRef({});
  const alert = useCallback((id, title, body) => {
    if (seen.current.has(id)) return;
    seen.current.add(id); setNotice(`${title} ${body}`);
    if ('Notification' in window && Notification.permission === 'granted') new Notification(title, { body, tag: id });
  }, []);
  useEffect(() => onAuthStateChanged(auth, next => { setUser(next); setAuthReady(true); }), []);
  useEffect(() => {
    const epoch = ++alive.current; setHome(null); setRooms({}); setOnline({}); setSessions({}); setIdentityToken(''); seen.current.clear(); pending.current = null;
    if (!user || !hub) return;
    let stop = () => {}, cancelled = false;
    const connect = async () => {
      try {
        const reply = await request(hub, command('IDENTITY', { identity: { action: 'FIREBASE_SIGN_IN', firebaseToken: await user.getIdToken() } }));
        if (cancelled || epoch !== alive.current) return;
        setIdentityToken(reply.identityToken); setHome(reply.home); setError('');
        setSessions(Object.fromEntries(reply.home.rooms.map(room => [room.roomId, room])));
        stop = watch(hub, command('HOME', { identityToken: reply.identityToken }), next => {
          if (cancelled) return;
          setHome(next.home);
          setSessions(old => ({ ...old, ...Object.fromEntries(next.home.rooms.map(room => [room.roomId, room])) }));
          next.home.invitations.forEach(invite => alert(`invite:${invite.id}`, `You're invited to ${invite.roomName}.`, 'Join from your home screen.'));
        }, (connected, reason) => { setOnline(old => ({ ...old, home: connected })); if (reason) setError(reason); });
      } catch (e) { if (!cancelled) setError(hub === publicHub
        ? `Could not connect to the internet room. ${e.message} Retry when your internet connection is available.`
        : `Could not connect to the nearby hub. ${e.message} Open its HTTPS address once to trust its certificate after checking the fingerprint, and allow local-network access.`); }
    };
    connect();
    const refresh = setInterval(() => { stop(); connect(); }, 50 * 60 * 1000);
    return () => { cancelled = true; stop(); clearInterval(refresh); };
  }, [user, hub, hubRevision, alert]);
  const sessionKey = Object.values(sessions).map(s => `${s.roomId}:${s.token}`).sort().join('|');
  useEffect(() => {
    if (!hub) return;
    const stops = Object.values(sessions).map(session => watch(hub, command('SNAPSHOT', { roomId: session.roomId, token: session.token }), reply => {
      const r = reply.room;
      const previous = roomRef.current[r.id];
      if (previous?.room.revision > r.revision) return;
      roomRef.current = { ...roomRef.current, [r.id]: reply }; setRooms(roomRef.current);
      if (r.phase === 'ACCEPTING' && r.spin.winnerId === session.memberId) alert(`spin:${r.spin.id}`, "You're selected!", `Join ${r.name} and accept to collect everyone's food.`);
      if (r.phase === 'PREPARING_SPIN' && !r.preparedIds.includes(session.memberId) && r.members.some(m => m.id === session.memberId && m.approved && m.participating && !m.guest)) {
        request(hub, command('ACK_SPIN', { roomId: r.id, token: session.token, expectedOrderNumber: r.orderNumber, text: r.preparationId })).catch(e => setError(e.message));
      }
    }, connected => setOnline(old => ({ ...old, [session.roomId]: connected }))));
    return () => stops.forEach(stop => stop());
    // sessionKey describes the membership credentials; object replacement must not reconnect every second.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hub, sessionKey, alert]);
  useEffect(() => { roomRef.current = {}; }, [hub, user?.uid]);
  const send = async (kind, fields = {}, roomId = '') => {
    if (busy) return null;
    if (pending.current) throw Error('Retry the saved request before making another change.');
    const session = sessions[roomId], room = roomRef.current[roomId]?.room;
    const payload = command(kind, { identityToken, ...(session ? { roomId, token: session.token, expectedRevision: room.revision, expectedOrderNumber: room.orderNumber } : {}), ...fields });
    setBusy(true); setError('');
    if (kind !== 'IDENTITY') pending.current = payload;
    try {
      const reply = await request(hub, payload);
      pending.current = null;
      if (reply.home) setHome(reply.home);
      if (reply.room) {
        const r = reply.room;
        if (reply.token) setSessions(old => ({ ...old, [r.id]: { roomId: r.id, roomName: r.name, memberId: reply.memberId, token: reply.token } }));
        roomRef.current = { ...roomRef.current, [r.id]: reply }; setRooms(roomRef.current);
      }
      return reply;
    } catch(e) {
      if (e.definitive) pending.current = null;
      setError(e.message + (pending.current ? ' Retry the saved request to confirm its result.' : '')); return null;
    } finally { setBusy(false); }
  };
  const retry = async () => {
    if (!pending.current || busy) return;
    setBusy(true);
    try {
      const reply = await request(hub, pending.current); pending.current = null; setError('');
      if (reply.room) { roomRef.current = { ...roomRef.current, [reply.room.id]: reply }; setRooms(roomRef.current); }
      if (reply.token) setSessions(old => ({ ...old, [reply.room.id]: { roomId: reply.room.id, roomName: reply.room.name, memberId: reply.memberId, token: reply.token } }));
    } catch (e) { if (e.definitive) pending.current = null; setError(e.message); } finally { setBusy(false); }
  };
  const connect = address => {
    if (address) localStorage.setItem('foodrun-hub', address); else localStorage.removeItem('foodrun-hub');
    setError(''); setHub(address); setHubRevision(value => value + 1);
  };
  return { user, authReady, hub, home, rooms, sessions, online, error, setError, notice, setNotice, busy, send, retry, hasPending: !!pending.current, connect };
}
