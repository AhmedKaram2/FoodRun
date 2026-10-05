import test from 'node:test';
import assert from 'node:assert/strict';
import { accountConnection } from '../src/foodrun/accountConnection.js';

const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
function fixture(t, signIn, options = {}) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const events = new EventTarget(), visibility = new EventTarget(); visibility.visibilityState = 'visible';
  const states = [], errors = [], replies = [];
  let online = true, subscriptions = 0, peak = 0;
  const stop = accountConnection({ events, visibility, isOnline: () => online, signIn,
    onStatus: value => states.push(value), onError: error => errors.push(error),
    onConnected: reply => { replies.push(reply); peak = Math.max(peak, ++subscriptions); return () => subscriptions--; },
    ...options,
  });
  t.after(stop);
  return { events, visibility, states, errors, replies, stop, setOnline: value => { online = value; },
    get subscriptions() { return subscriptions; }, get peak() { return peak; } };
}

test('initial timeout reconnects automatically and refresh retains exactly one HOME subscription', async t => {
  let calls = 0;
  const f = fixture(t, async () => { if (++calls === 1) throw Object.assign(Error('signal timed out'), { code: 'TIMEOUT' }); return { identityToken: 'test' }; });
  await flush();
  assert.equal(f.states.at(-1), 'retrying'); assert.equal(f.errors.length, 0);
  t.mock.timers.tick(1000); await flush();
  assert.equal(calls, 2); assert.equal(f.states.at(-1), 'connected'); assert.equal(f.subscriptions, 1);
  t.mock.timers.tick(50 * 60 * 1000); await flush();
  assert.equal(calls, 3); assert.equal(f.subscriptions, 1); assert.equal(f.peak, 1);
  f.stop(); assert.equal(f.subscriptions, 0);
});

test('repeated transport failures back off to 30 seconds and do not produce repeated failure alerts', async t => {
  let calls = 0;
  const f = fixture(t, async () => { calls++; throw new TypeError('Failed to fetch'); });
  await flush();
  for (const delay of [1000, 2000, 4000, 8000, 16000, 30000, 30000]) {
    const previous = calls;
    t.mock.timers.tick(delay - 1); await flush(); assert.equal(calls, previous);
    t.mock.timers.tick(1); await flush(); assert.equal(calls, previous + 1);
  }
  assert.equal(f.errors.length, 0); assert.equal(f.subscriptions, 0);
});

test('returning online cancels retry delay and duplicate online/foreground events do not overlap requests', async t => {
  let calls = 0, resolve;
  const f = fixture(t, () => { calls++; if (calls === 1) return Promise.reject(new TypeError('Failed to fetch')); return new Promise(done => { resolve = done; }); });
  await flush();
  f.events.dispatchEvent(new Event('online'));
  f.events.dispatchEvent(new Event('online')); f.visibility.dispatchEvent(new Event('visibilitychange'));
  assert.equal(calls, 2);
  t.mock.timers.tick(1000); await flush(); assert.equal(calls, 2);
  resolve({}); await flush();
  assert.equal(f.subscriptions, 1);
  f.events.dispatchEvent(new Event('online')); assert.equal(calls, 2);
});

test('offline failures wait for network recovery instead of repeatedly sending requests', async t => {
  let calls = 0;
  const f = fixture(t, async () => { calls++; throw new TypeError('Failed to fetch'); });
  f.setOnline(false); await flush();
  assert.equal(f.states.at(-1), 'offline');
  t.mock.timers.tick(5 * 60 * 1000); await flush(); assert.equal(calls, 1);
  f.setOnline(true); f.events.dispatchEvent(new Event('online')); await flush(); assert.equal(calls, 2);
});

test('a foreground event resumes a delayed connection but hidden-page events do not', async t => {
  let calls = 0;
  const f = fixture(t, async () => { calls++; throw new TypeError('Failed to fetch'); });
  await flush();
  f.visibility.visibilityState = 'hidden'; f.visibility.dispatchEvent(new Event('visibilitychange')); assert.equal(calls, 1);
  f.visibility.visibilityState = 'visible'; f.visibility.dispatchEvent(new Event('visibilitychange')); await flush(); assert.equal(calls, 2);
});

test('permission, blocked account and expired-session errors stop automatic attempts', async t => {
  for (const error of [Object.assign(Error('denied'), { definitive: true }), Object.assign(Error('blocked'), { accessBlock: {} }), Object.assign(Error('sign in again'), { code: 'REAUTH_REQUIRED' }), Object.assign(Error('disabled'), { code: 'auth/user-disabled' })]) {
    let calls = 0;
    const f = fixture(t, async () => { calls++; throw error; });
    await flush(); t.mock.timers.tick(60 * 60 * 1000); await flush();
    f.events.dispatchEvent(new Event('online')); f.visibility.dispatchEvent(new Event('visibilitychange')); await flush();
    assert.equal(calls, 1); assert.equal(f.errors[0], error); assert.equal(f.states.at(-1), 'failed'); f.stop();
    t.mock.timers.reset();
  }
});

test('Firebase token network failures are recovered before room bootstrap', async t => {
  let calls = 0;
  const f = fixture(t, async () => { if (++calls === 1) throw Object.assign(Error('network unavailable'), { code: 'auth/network-request-failed' }); return {}; });
  await flush(); t.mock.timers.tick(1000); await flush();
  assert.equal(calls, 2); assert.equal(f.states.at(-1), 'connected'); assert.equal(f.errors.length, 0);
});

test('switching server or signing out aborts bootstrap and ignores even a late successful reply', async t => {
  let signal, resolve;
  const f = fixture(t, value => { signal = value; return new Promise(done => { resolve = done; }); });
  f.stop(); assert.equal(signal.aborted, true);
  resolve({ identityToken: 'stale' }); await flush();
  t.mock.timers.tick(60 * 60 * 1000); f.events.dispatchEvent(new Event('online')); await flush();
  assert.equal(f.replies.length, 0); assert.equal(f.subscriptions, 0); assert.deepEqual(f.states, ['connecting']);
});

test('closing while retry is scheduled removes timers and recovery listeners', async t => {
  let calls = 0;
  const f = fixture(t, async () => { calls++; throw new TypeError('Failed to fetch'); });
  await flush(); f.stop(); t.mock.timers.tick(60 * 60 * 1000);
  f.events.dispatchEvent(new Event('online')); f.visibility.dispatchEvent(new Event('visibilitychange')); await flush();
  assert.equal(calls, 1);
});
