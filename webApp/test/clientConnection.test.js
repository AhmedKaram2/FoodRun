import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from '../src/foodrun/client.js';

const hub = 'https://example.test';
const signIn = { kind: 'IDENTITY', identity: { action: 'FIREBASE_SIGN_IN' } };
const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
function network(t) {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const calls = [];
  t.mock.method(globalThis, 'fetch', (url, options) => new Promise((resolve, reject) => {
    calls.push({ url, ...options, resolve });
    options.signal.addEventListener('abort', () => reject(options.signal.reason), { once: true });
  }));
  return calls;
}

test('internet room bootstrap survives a one-minute cold start instead of failing at 20 seconds', async t => {
  const calls = network(t); const abort = new AbortController();
  const pending = request(hub, signIn, abort.signal);
  t.mock.timers.tick(65000); await flush();
  assert.equal(calls[0].signal.aborted, false);
  calls[0].resolve(new Response(JSON.stringify({ ok: true, identityToken: 'test', home: {} })));
  assert.equal((await pending).identityToken, 'test');
  t.mock.timers.tick(90000); assert.equal(calls[0].signal.aborted, false);
});

test('bootstrap timeout remains bounded and retryable even when the caller supplies a signal', async t => {
  const calls = network(t); const abort = new AbortController();
  const pending = request(hub, signIn, abort.signal);
  const rejected = assert.rejects(pending, error => error.code === 'TIMEOUT' && !error.definitive && !error.message.includes('signal timed out'));
  t.mock.timers.tick(90000); await rejected;
  assert.equal(calls.length, 1); assert.equal(abort.signal.aborted, false);
});

test('room and payment mutations retain the 20-second deadline and are never automatically replayed', async t => {
  const calls = network(t);
  for (const kind of ['CREATE', 'RECORD_PAYMENT', 'SNAPSHOT']) {
    const pending = request(hub, { kind });
    const rejected = assert.rejects(pending, error => error.code === 'TIMEOUT' && !error.definitive);
    t.mock.timers.tick(20000); await rejected;
  }
  assert.equal(calls.length, 3);
});

test('caller cancellation takes priority over the timeout and aborts the in-flight fetch', async t => {
  const calls = network(t); const abort = new AbortController();
  const pending = request(hub, signIn, abort.signal);
  const rejected = assert.rejects(pending, error => error.name === 'AbortError');
  abort.abort(); await rejected;
  assert.equal(calls[0].signal.aborted, true);
  t.mock.timers.tick(90000); assert.equal(calls.length, 1);
  await assert.rejects(request(hub, signIn, abort.signal), error => error.name === 'AbortError');
  assert.equal(calls.length, 1);
});

test('timeout while reading a response body is reported as a retryable timeout', async t => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  t.mock.method(globalThis, 'fetch', async (_, { signal }) => ({ ok: true, status: 200,
    json: () => new Promise((_, reject) => signal.addEventListener('abort', () => reject(signal.reason), { once: true })),
  }));
  const pending = request(hub, signIn); const rejected = assert.rejects(pending, error => error.code === 'TIMEOUT' && !error.definitive);
  await flush(); t.mock.timers.tick(90000); await rejected;
});
