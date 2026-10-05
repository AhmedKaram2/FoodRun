import { getLanguage } from './i18n.js';
import { minorInput } from './client.js';

export function mealRoomName(now = new Date(), language = getLanguage()) {
  const meal = now.getHours() < 13 ? (language === 'ar' ? 'فطور' : 'Breakfast') : (language === 'ar' ? 'غداء' : 'Lunch');
  const date = new Intl.DateTimeFormat(language === 'ar' ? 'ar-AE' : 'en-GB', { day: '2-digit', month: 'short', year: 'numeric', numberingSystem: 'latn' }).format(now);
  return `${meal} · ${date}`;
}

const key = userId => `foodrun-room-preferences:${userId}`;
export function rememberRoomDefaults(userId, room) {
  if (!userId) return;
  try { localStorage.setItem(key(userId), JSON.stringify({ restaurantId: room.restaurantPollOpen ? '' : room.restaurant.id, destination: room.destination })); } catch { /* Storage is optional. */ }
}
export function roomDefaults(userId, restaurants) {
  let saved = {};
  try { if (userId) saved = JSON.parse(localStorage.getItem(key(userId)) || '{}'); } catch { /* Keep useful defaults without storage. */ }
  const restaurant = restaurants.find(value => value.id === saved?.restaurantId);
  return {
    room: mealRoomName(), restaurantId: restaurant?.id || '',
    destination: typeof saved?.destination === 'string' ? saved.destination : "Mohre, Backside Parking, Security gate, Opposite Suni's Restaurant https://maps.app.goo.gl/cLba7hYb9Rtfqyjr5",
    delivery: restaurant ? minorInput(restaurant.pricing.defaultDeliveryFeeMinor, restaurant.currency) : '0.00',
    service: restaurant ? minorInput(restaurant.pricing.defaultServiceFeeMinor, restaurant.currency) : '0.00',
  };
}
