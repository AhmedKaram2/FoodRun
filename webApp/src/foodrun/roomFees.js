import { amount } from './client.js';
import { t } from './i18n.js';

export function roomFeeUpdate(room, draft) {
  if (!['included', 'added'].includes(draft.taxTreatment)) throw Error(t('Choose whether tax is included in the prices or added to the bill.'));
  const taxRateBasisPoints = draft.taxTreatment === 'added' ? amount(draft.taxRate, 'AED') : null;
  if (taxRateBasisPoints > 10000) throw Error(t('Tax must be between 0 and 100%.'));
  const currency = room.restaurant.currency;
  return {
    fees: { delivery: room.deliveryMode ? 0 : amount(draft.delivery || '0', currency), automaticDelivery: room.deliveryMode,
      service: amount(draft.service || '0', currency), discount: amount(draft.discount || '0', currency), proportionalDelivery: draft.proportionalDelivery },
    restaurant: { ...room.restaurant, pricing: { ...room.restaurant.pricing, taxTreatment: draft.taxTreatment, taxRateBasisPoints } },
    text: 'Confirmed fees, discount and tax for this order',
  };
}
