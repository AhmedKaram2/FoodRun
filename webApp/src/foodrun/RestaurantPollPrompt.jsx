import { useEffect, useRef, useState } from 'react';
import { t } from './i18n.js';

export default function RestaurantPollPrompt({ room, me, data, language = 'en' }) {
  const dialog = useRef(null);
  const pending = useRef(false);
  const [dismissed, setDismissed] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const voted = room.restaurantVotes?.some(vote => vote.memberId === me.id);
  const open = room.phase === 'LOBBY' && room.restaurantPollOpen && me.approved && !me.guest && !me.removed && me.participating && !voted && !dismissed;
  useEffect(() => {
    const node = dialog.current;
    if (!open) { node?.close(); return; }
    const previousFocus = document.activeElement;
    const overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    node.showModal();
    return () => {
      node.close(); document.body.style.overflow = overflow;
      if (previousFocus?.isConnected) previousFocus.focus();
    };
  }, [open]);
  const vote = async restaurantId => {
    if (pending.current) return;
    pending.current = true; setSaving(true); setError('');
    try {
      if (await data.send('VOTE_RESTAURANT', { text: restaurantId }, room.id)) setDismissed(true);
      else setError(t('Your vote was not saved. Please try again.'));
    } catch (error) { setError(error.message); }
    finally { pending.current = false; setSaving(false); }
  };
  return <dialog ref={dialog} className="card poll-dialog" aria-labelledby="poll-dialog-title" onCancel={event => { event.preventDefault(); if (!saving) setDismissed(true); }}>
    <h2 id="poll-dialog-title">{t('Choose a restaurant')}</h2>
    <p>{t('The restaurant poll is open. Choose where you would like to order from.')}</p>
    <div className="poll-dialog-options">{(room.restaurantOptions || []).map(restaurant => <button key={restaurant.id} className="secondary" disabled={saving || data.busy} onClick={() => vote(restaurant.id)}>
      {language === 'ar' && restaurant.nameAr ? restaurant.nameAr : restaurant.name}
    </button>)}</div>
    {error && <p role="alert" className="form-message">{data.error || error}</p>}
    <button className="link" disabled={saving} onClick={() => setDismissed(true)}>{t('Choose later')}</button>
  </dialog>;
}
