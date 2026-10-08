function validDate(value) {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) return false;
  const date = new Date(`${value}T00:00:00Z`);
  return Number.isFinite(date.getTime()) && date.toISOString().slice(0, 10) === value;
}

export function cleanupSelection({ scope, filter, days, fromDate, toDate, timeZone }) {
  if (!['closedRooms', 'history'].includes(scope)) throw Error("Choose orders or order history.");
  if (filter === 'date') {
    if (!validDate(fromDate) || !validDate(toDate)) throw Error('Choose both a valid start date and an end date.');
    if (toDate < fromDate) throw Error('The end date must be on or after the start date.');
    if (!timeZone) throw Error('Choose a valid time zone.');
    return { scope, olderThanDays: 0, fromDate, toDate, timeZone };
  }
  if (filter !== 'age') throw Error('Choose a time filter.');
  if (days === '' || !Number.isInteger(Number(days)) || Number(days) < 0 || Number(days) > 3650) throw Error('Choose an age between 0 and 3650 days.');
  return { scope, olderThanDays: Number(days) };
}

export function requireCleanupPreview(preview, selection) {
  if (!preview || preview.scope !== selection.scope || preview.olderThanDays !== selection.olderThanDays || !preview.previewToken || !Number.isInteger(preview.count) || !Array.isArray(preview.targets)) throw Error('Preview this selection again.');
  // Older servers ignore unknown request fields. Their unfiltered result must
  // never become a deletable date-range preview.
  if (selection.fromDate && (preview.fromDate !== selection.fromDate || preview.toDate !== selection.toDate || preview.timeZone !== selection.timeZone)) throw Error('The server could not verify this date range. Refresh and preview again.');
  return preview;
}
