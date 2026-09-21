import { amount } from './client.js';
import { t } from './i18n.js';

export function splitEqually(total, userIds) {
  if (!Number.isSafeInteger(total) || total < 0 || !userIds.length || new Set(userIds).size !== userIds.length) throw Error(t('Choose people and enter a valid receipt total.'));
  const sorted = [...userIds].sort();
  const base = Math.floor(total / sorted.length), remainder = total % sorted.length;
  return Object.fromEntries(sorted.map((id, index) => [id, base + (index < remainder ? 1 : 0)]));
}
export function paymentRoomPayload({ name, restaurant, details, photo, total, people, shares, account, ownerId }) {
  if (!name.trim() || !details.trim()) throw Error(t('Enter a room name and order details.'));
  if (!account) throw Error(t('Add your receiving details in your profile first.'));
  if (people.length < 2 || people.length > 30 || !people.some(person => person.userId === ownerId) || new Set(people.map(p => p.userId)).size !== people.length) throw Error(t('Choose yourself and 1–29 different people.'));
  const values = people.map(person => {
    const draft = shares[person.userId] || {};
    const value = amount(draft.amount || '0', 'AED'), received = amount(draft.received || '0', 'AED');
    if (received > value || person.userId === ownerId && received !== 0) throw Error(t('Received payments must not exceed a person’s share.'));
    return { userId: person.userId, description: draft.description?.trim() || t('Food order'), amount: value, received };
  });
  const receiptTotal = amount(total, 'AED');
  if (receiptTotal <= 0 || values.reduce((sum, share) => sum + share.amount, 0) !== receiptTotal) throw Error(t('The shares must add up to the receipt total.'));
  return { text: name.trim(), name: restaurant.trim() || name.trim(), amount: receiptTotal, account,
    paymentRoom: { details: { orderDetails: details.trim(), receiptPhoto: photo }, shares: values } };
}
export async function receiptPhotoData(file) {
  if (!['image/jpeg','image/png','image/webp'].includes(file.type) || file.size > 10 * 1024 * 1024) throw Error(t('Choose a JPEG, PNG or WebP image under 10 MB.'));
  const bitmap = await createImageBitmap(file);
  try {
    const scale = Math.min(1, 1600 / Math.max(bitmap.width, bitmap.height));
    const canvas = document.createElement('canvas');
    canvas.width = Math.max(1, Math.round(bitmap.width * scale)); canvas.height = Math.max(1, Math.round(bitmap.height * scale));
    const context = canvas.getContext('2d'); context.fillStyle = '#fff'; context.fillRect(0, 0, canvas.width, canvas.height);
    context.drawImage(bitmap, 0, 0, canvas.width, canvas.height);
    for (const quality of [0.88, 0.75, 0.6, 0.45]) {
      const photo = canvas.toDataURL('image/jpeg', quality);
      if (photo.length <= 600000) return photo;
    }
    throw Error(t('The receipt photo is too large. Choose a smaller photo.'));
  } finally { bitmap.close(); }
}
