import { money } from './client.js';
import { t } from './i18n.js';
import { halfItemsOpen } from './halfItems.js';

export default function HalfItemOffers({ room, me, data, describe }) {
  const offers = room.halfItemOffers || [];
  if (!offers.length || !me.approved) return null;
  const open = halfItemsOpen(room);
  return <section className="card half-item-offers" aria-label={t('Share half an item')} aria-live="polite">
    <p className="eyebrow">{t('Share half an item')}</p>
    {offers.map(offer => {
      const requester = room.members.find(member => member.id === offer.memberId)?.name || '';
      const recipient = room.members.find(member => member.id === offer.acceptedById)?.name || '';
      const yours = offer.memberId === me.id, accepted = !!offer.acceptedById;
      return <article className="half-item-offer" key={offer.id}>
        <h3>{accepted ? t('Shared: half each') : open ? t('Another half is available') : t('Full item assigned to requester')}</h3>
        <b>{describe(offer.line)}</b>{offer.line.notes && <p>{offer.line.notes}</p>}
        <p>{accepted ? `${requester} ½ + ${recipient} ½` : `${requester} · ${t('If nobody accepts, the requester keeps the whole item and pays the full price.')}`}</p>
        {offer.line.amount > 0 && <p>{t('Food share')}: <bdi>{money(!open && !accepted ? offer.line.amount : accepted && yours ? offer.line.amount - Math.floor(offer.line.amount / 2) : Math.floor(offer.line.amount / 2), room.restaurant.currency)}</bdi> · {t('Delivery and fees are shown separately in your receipt.')}</p>}
        <div className="hero-actions">
          {open && !accepted && !yours && !me.guest && me.participating && <button className="primary" disabled={data.busy} onClick={() => data.send('ACCEPT_HALF_ITEM', { text: offer.id }, room.id)}>{t('Take the other half')}</button>}
          {open && (yours || offer.acceptedById === me.id) && <button className="secondary" disabled={data.busy} onClick={() => data.send('CANCEL_HALF_ITEM', { text: offer.id }, room.id)}>{t(yours ? 'Keep the whole item' : 'Release my half')}</button>}
        </div>
      </article>;
    })}
  </section>;
}
