import test from 'node:test';
import assert from 'node:assert/strict';
import { mealRoomName, rememberRoomDefaults, roomDefaults } from '../src/foodrun/smartDefaults.js';

test('room names switch at 1 PM local time and localize the date', () => {
  assert.equal(mealRoomName(new Date(2026, 9, 5, 12, 59), 'en'), 'Breakfast · 05 Oct 2026');
  assert.equal(mealRoomName(new Date(2026, 9, 5, 13), 'en'), 'Lunch · 05 Oct 2026');
  assert.equal(mealRoomName(new Date(2026, 9, 5, 13), 'ar'), 'غداء · 05 أكتوبر 2026');
  assert.equal(mealRoomName(new Date(2027, 0, 1, 0), 'en'), 'Breakfast · 01 Jan 2027');
});
test('defaults belong to the current account and use current restaurant prices', () => {
  const values = new Map();
  globalThis.localStorage = { getItem: key => values.get(key) || null, setItem: (key, value) => values.set(key, value) };
  try {
    const restaurant = { id:'r', currency:'JOD', pricing:{defaultDeliveryFeeMinor:1250,defaultServiceFeeMinor:500} };
    rememberRoomDefaults('alice', {restaurant,destination:'Gate 2'});
    const actual = roomDefaults('alice', [restaurant]);
    assert.equal(actual.restaurantId, 'r'); assert.equal(actual.destination, 'Gate 2');
    assert.equal(actual.delivery, '1.250'); assert.equal(actual.service, '0.500');
    assert.equal(roomDefaults('bob', [restaurant]).restaurantId, '');
    assert.equal(roomDefaults('alice', []).restaurantId, '');
  } finally { delete globalThis.localStorage; }
});
test('blocked browser storage does not prevent opening the room form', () => {
  globalThis.localStorage = { getItem: () => { throw Error('Unavailable'); }, setItem: () => { throw Error('Unavailable'); } };
  try {
    assert.doesNotThrow(() => rememberRoomDefaults('alice',{restaurant:{id:'r'},destination:''}));
    assert.ok(roomDefaults('alice',[]).room);
  } finally { delete globalThis.localStorage; }
});
