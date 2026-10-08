import test from 'node:test';
import assert from 'node:assert/strict';
import { roomConnections } from '../src/foodrun/roomConnections.js';
import { watch } from '../src/foodrun/client.js';
test('a refreshed account membership replaces its subscription and ignores old device updates', () => {
  const watches = [], received = [];
  const manager = roomConnections({watch:(session,reply)=>{const entry={session,reply,closed:false};watches.push(entry);return()=>{entry.closed=true;};},load:async()=>({}),onReply:(session,reply)=>received.push({session,reply}),onStatus:()=>{}});
  const old = {roomId:'room',memberId:'duplicate',token:'old',phase:'PLACED',orderNumber:1};
  manager.sync({room:old});
  const current = {...old,memberId:'canonical',token:'new'};
  manager.sync({room:current});
  assert.equal(watches[0].closed,true);
  watches[0].reply({room:{revision:99},memberId:'duplicate'});
  watches[1].reply({room:{revision:3},memberId:'canonical'});
  assert.equal(received.length,1);assert.equal(received[0].session.memberId,'canonical');manager.close();
});
test('automatically archived unpaid rooms stay live until payment settlement completes', async () => {
  let opened = 0, stopped = 0, loaded = 0;
  const manager = roomConnections({ watch: () => { opened++; return () => stopped++; }, load: async () => { loaded++; return {}; }, onReply: () => {}, onStatus: () => {} });
  const session = { roomId: 'bill', memberId: 'me', token: 'saved', phase: 'ARCHIVED', orderNumber: 1, paymentsPending: true };
  manager.sync({ bill: session });
  assert.equal(opened, 1); assert.equal(loaded, 0);
  manager.sync({ bill: { ...session } }); assert.equal(opened, 1);
  manager.sync({ bill: { ...session, paymentsPending: false } }); await Promise.resolve();
  assert.equal(stopped, 1); assert.equal(loaded, 1); manager.close();
});

test('only ongoing rooms stay live; closing, reopening and removing do not reconnect unaffected rooms', async () => {
  const opened = [], stopped = [], loaded = [], received = [];
  const manager = roomConnections({
    watch: s => { opened.push(s.roomId); return () => stopped.push(s.roomId); },
    load: async s => { loaded.push(s.roomId); return { room: { id: s.roomId } }; },
    onReply: (s, reply) => received.push(reply.room.id), onStatus: () => {},
  });
  const session = (roomId, phase, orderNumber = 1) => ({ roomId, phase, orderNumber, token: roomId, memberId: 'me' });
  let rooms = { a: session('a', 'COLLECTING'), b: session('b', 'FULFILLED'), c: session('c', 'ARCHIVED'), d: session('d', 'CANCELLED') };
  manager.sync(rooms); await Promise.resolve();
  assert.deepEqual(opened, ['a', 'b']); assert.deepEqual(loaded, ['c', 'd']);
  manager.sync({ ...rooms }); assert.equal(opened.length, 2); assert.equal(loaded.length, 2);
  rooms = { ...rooms, a: session('a', 'ARCHIVED') }; manager.sync(rooms); await Promise.resolve();
  assert.deepEqual(stopped, ['a']); assert.deepEqual(loaded, ['c', 'd', 'a']); assert.equal(opened.length, 2);
  rooms = { ...rooms, a: session('a', 'LOBBY', 2) }; manager.sync(rooms);
  assert.deepEqual(opened, ['a', 'b', 'a']);
  delete rooms.b; manager.sync(rooms); assert.deepEqual(stopped, ['a', 'b']);
  manager.close(); assert.deepEqual(stopped, ['a', 'b', 'a']);
});

test('cached completed history is reused and stale HTTP responses are ignored after reopening', async () => {
  let resolve, calls = 0; const received = [];
  const manager = roomConnections({
    watch: () => () => {}, load: () => { calls++; return new Promise(done => { resolve = done; }); },
    getSnapshot: id => id === 'cached' ? { memberId: 'me', room: { phase: 'ARCHIVED', orderNumber: 1 } } : null,
    onReply: (_, reply) => received.push(reply), onStatus: () => {},
  });
  const cached = { roomId: 'cached', memberId: 'me', token: 'x', phase: 'ARCHIVED', orderNumber: 1 };
  const old = { ...cached, roomId: 'old' };
  manager.sync({ cached, old }); assert.equal(calls, 1);
  manager.sync({ cached, old: { ...old, phase: 'LOBBY', orderNumber: 2 } });
  resolve({ room: { phase: 'ARCHIVED' } }); await Promise.resolve();
  assert.equal(received.length, 0); manager.close();
});

function socketFixture(t) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const original = globalThis.WebSocket, sockets = [], replies = [], statuses = [];
  const events = new EventTarget(), visibility = new EventTarget(); visibility.visibilityState = 'visible';
  let online = true;
  globalThis.WebSocket = class { constructor() { sockets.push(this); } send(value) { this.payload = JSON.parse(value); } close() { this.closed = true; this.onclose?.(); } };
  const stop = watch('https://example.test', { kind: 'SNAPSHOT', token: 'test-session' }, reply => replies.push(reply), (...args) => statuses.push(args), { events, visibility, isOnline: () => online });
  t.after(() => { stop(); globalThis.WebSocket = original; });
  return { sockets, replies, statuses, events, visibility, stop, setOnline: value => { online = value; } };
}
const update = (socket, reply) => socket.onmessage({ data: JSON.stringify(reply) });
test('terminal room socket closes without scheduling automatic reconnect', t => {
  const f = socketFixture(t);
  update(f.sockets[0], { ok: true, room: { phase: 'ARCHIVED' } });
  t.mock.timers.tick(120000);
  assert.equal(f.replies.length, 1); assert.equal(f.sockets.length, 1); assert.equal(f.sockets[0].closed, true);
});
test('returning to the browser reconnects read subscriptions and ignores old socket messages', t => {
  const f = socketFixture(t), old = f.sockets[0], late = old.onmessage;
  f.visibility.visibilityState = 'hidden'; f.visibility.dispatchEvent(new Event('visibilitychange')); assert.equal(f.sockets.length, 1);
  f.visibility.visibilityState = 'visible'; f.visibility.dispatchEvent(new Event('visibilitychange'));
  assert.equal(old.closed, true); assert.equal(f.sockets.length, 2);
  late({ data: JSON.stringify({ ok: true, room: { revision: 1 } }) });
  update(f.sockets[1], { ok: true, room: { revision: 9 } });
  assert.equal(f.replies.length, 1); assert.equal(f.replies[0].room.revision, 9);
});
test('server handover retries a temporarily unavailable subscription instead of leaving it permanently stale', t => {
  const f = socketFixture(t);
  update(f.sockets[0], { ok: false, code: 'HUB_UNAVAILABLE', error: 'reconnect' });
  t.mock.timers.tick(1000); assert.equal(f.sockets.length, 2);
  f.sockets[1].onopen(); assert.equal(f.sockets[1].payload.kind, 'SNAPSHOT');
  update(f.sockets[1], { ok: true, room: { revision: 20 } });
  assert.equal(f.replies[0].room.revision, 20);
});
test('heartbeats keep an idle connection live without replacing data, while a silent socket is recovered', t => {
  const f = socketFixture(t); f.sockets[0].onopen();
  update(f.sockets[0], { ok: true, room: { revision: 4 } });
  for(let i = 0; i < 8; i++) { t.mock.timers.tick(15000); update(f.sockets[0], { ok: true, code: 'LIVE' }); }
  assert.equal(f.sockets.length, 1); assert.equal(f.replies.length, 1);
  t.mock.timers.tick(60000); t.mock.timers.tick(1000);
  assert.equal(f.sockets.length, 2); assert.equal(f.sockets[0].closed, true);
});
test('offline waits for network recovery, and permission errors remain definitive', t => {
  const f = socketFixture(t); f.setOnline(false); f.events.dispatchEvent(new Event('offline'));
  t.mock.timers.tick(120000); assert.equal(f.sockets.length, 1);
  f.setOnline(true); f.events.dispatchEvent(new Event('online')); assert.equal(f.sockets.length, 2);
  update(f.sockets[1], { ok: false, code: 'ROOM_BLOCKED' });
  f.events.dispatchEvent(new Event('online')); f.visibility.dispatchEvent(new Event('visibilitychange'));
  t.mock.timers.tick(120000); assert.equal(f.sockets.length, 2);
});
