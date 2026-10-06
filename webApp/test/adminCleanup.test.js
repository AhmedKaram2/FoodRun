import test from 'node:test';
import assert from 'node:assert/strict';
import { cleanupSelection, requireCleanupPreview } from '../src/foodrun/adminCleanup.js';

const form = { scope: 'closedRooms', filter: 'date', days: '30', fromDate: '2026-10-01', toDate: '2026-10-02', timeZone: 'Asia/Dubai' };
const preview = selection => ({ ...selection, count: 1, targets: [{ id: 'room-1' }], previewToken: 'sample-preview' });

test('calendar dates and timezone are preserved without also filtering by age', () => {
  assert.deepEqual(cleanupSelection(form), { scope: 'closedRooms', olderThanDays: 0, fromDate: '2026-10-01', toDate: '2026-10-02', timeZone: 'Asia/Dubai' });
  assert.doesNotThrow(() => cleanupSelection({ ...form, fromDate: '2028-02-29', toDate: '2028-02-29' }));
  assert.deepEqual(cleanupSelection({ ...form, filter: 'age', days: '7' }), { scope: 'closedRooms', olderThanDays: 7 });
});

test('missing, invalid, or reversed ranges cannot become a broad cleanup', () => {
  for (const change of [{ fromDate: '' }, { toDate: '' }, { fromDate: '2026-02-30' }, { toDate: '2026-13-01' }, { fromDate: '2026-10-03' }, { timeZone: '' }, { filter: 'unknown' }, { scope: 'all' }]) assert.throws(() => cleanupSelection({ ...form, ...change }));
  for (const days of ['', '-1', '1.5', '3651', 'no']) assert.throws(() => cleanupSelection({ ...form, filter: 'age', days }));
});

test('legacy servers and changed filters cannot authorize date-range deletion', () => {
  const selection = cleanupSelection(form), value = preview(selection);
  assert.equal(requireCleanupPreview(value, selection), value);
  for (const change of [{ fromDate: undefined }, { toDate: undefined }, { timeZone: undefined }, { scope: 'history' }, { olderThanDays: 30 }, { fromDate: '2026-09-01' }, { timeZone: 'UTC' }, { previewToken: '' }]) assert.throws(() => requireCleanupPreview({ ...value, ...change }, selection));
  const age = cleanupSelection({ ...form, filter: 'age' });
  assert.doesNotThrow(() => requireCleanupPreview(preview(age), age));
});
