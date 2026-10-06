import test from 'node:test';
import assert from 'node:assert/strict';
import { walletAnnouncementSeen, dismissWalletAnnouncement, walletAnnouncementKey } from '../src/foodrun/walletAnnouncement.js';

test('wallet announcement dismissal is persisted separately for each account', () => {
  const values = new Map(), storage = { getItem: key => values.get(key), setItem: (key, value) => values.set(key, value) };
  assert.equal(walletAnnouncementSeen('alice', storage), false);
  dismissWalletAnnouncement('alice', storage);
  assert.equal(values.get(walletAnnouncementKey('alice')), 'seen');
  assert.equal(walletAnnouncementSeen('alice', storage), true);
  assert.equal(walletAnnouncementSeen('bob', storage), false);
  values.set(walletAnnouncementKey('returning-user'), 'seen');
  assert.equal(walletAnnouncementSeen('returning-user', storage), true);
  assert.equal(walletAnnouncementSeen('', storage), true);
});

test('blocked device storage keeps Home usable and remembers dismissal for the visit', () => {
  const storage = { getItem() { throw Error('blocked'); }, setItem() { throw Error('blocked'); } };
  assert.equal(walletAnnouncementSeen('blocked-user', storage), false);
  assert.doesNotThrow(() => dismissWalletAnnouncement('blocked-user', storage));
  assert.equal(walletAnnouncementSeen('blocked-user', storage), true);
});
