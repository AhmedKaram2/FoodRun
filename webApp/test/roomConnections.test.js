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

test('terminal room socket closes without scheduling automatic reconnect', () => {
  const originalSocket = globalThis.WebSocket, originalTimeout = globalThis.setTimeout;
  let socket, reconnects = 0, replies = 0;
  try {
    globalThis.WebSocket = class { constructor() { socket = this; } send() {} close() { this.onclose?.(); } };
    globalThis.setTimeout = () => { reconnects++; return 0; };
    const stop = watch('https://example.test', {}, () => replies++, () => {});
    socket.onmessage({ data: JSON.stringify({ ok: true, room: { phase: 'ARCHIVED' } }) });
    assert.equal(replies, 1); assert.equal(reconnects, 0); stop();
  } finally { globalThis.WebSocket = originalSocket; globalThis.setTimeout = originalTimeout; }
});
