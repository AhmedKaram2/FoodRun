import { t, tf } from './i18n.js';

export function roomInvitation(room, link, language = 'en') {
  const name = value => language === 'ar' && value.nameAr ? value.nameAr : value.name;
  const restaurant = room.restaurantPollOpen
    ? tf('Restaurant poll: {names}', { names: (room.restaurantOptions || []).map(name).join(' · ') }, language)
    : tf('Restaurant: {name}', { name: name(room.restaurant) }, language);
  return [`Food Run · ${room.name}`, restaurant,
    tf('Order #{number}', { number: room.orderNumber }, language),
    tf('Room code: {code}', { code: room.code }, language),
    t('Join using the link or code. After selection, the room creator or selected person must approve new joiners.', language), link].join('\n');
}
