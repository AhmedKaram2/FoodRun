import { t } from './i18n.js';
export function selectedPollRestaurants(restaurants, ids) {
  const uniqueIds = [...new Set(ids)];
  if (uniqueIds.length < 2 || uniqueIds.length > 12) throw Error(t('Choose between 2 and 12 restaurants for the poll.'));
  const selected = uniqueIds.map(id => restaurants.find(restaurant => restaurant.id === id));
  if (selected.some(restaurant => !restaurant)) throw Error(t('A selected restaurant is no longer available. Update the poll choices.'));
  if (selected.some(restaurant => restaurant.currency !== selected[0].currency)) throw Error(t('Poll restaurants must use the same currency.'));
  return selected;
}
