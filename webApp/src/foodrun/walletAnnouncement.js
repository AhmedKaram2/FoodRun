const seen = new Set();
export const walletAnnouncementKey = userId => `intrvioo-wallet-announcement-v1:${encodeURIComponent(userId)}`;
export function walletAnnouncementSeen(userId, storage) {
  if (!userId || seen.has(userId)) return true;
  try { return (storage || globalThis.localStorage)?.getItem(walletAnnouncementKey(userId)) === 'seen'; }
  catch { return false; }
}
export function dismissWalletAnnouncement(userId, storage) {
  if (!userId) return;
  seen.add(userId);
  try { (storage || globalThis.localStorage)?.setItem(walletAnnouncementKey(userId), 'seen'); }
  catch { /* Keep the dismissal for this visit when device storage is unavailable. */ }
}
