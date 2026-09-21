import { useEffect, useMemo, useRef, useState } from 'react';
import { money } from './client.js';
import { t } from './i18n.js';
import { mergeReorder, prepareReorder } from './reorder.js';

export default function ReorderReview({ restaurant, source, cartLines, onSave, onApplied, onCancel }) {
  const entries = useMemo(() => prepareReorder(restaurant, source), [restaurant, source]);
  const available = entries.filter(entry => !entry.issue);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const pending = useRef(false);
  const heading = useRef(null);
  useEffect(() => { heading.current?.focus(); }, []);
  const apply = async () => {
    if (pending.current) return;
    pending.current = true;
    setSaving(true); setError('');
    try {
      const result = await onSave(mergeReorder(cartLines, entries));
      if (result) onApplied(entries.filter(entry => entry.issue));
      else setError(t('Your cart was not updated. Resolve the connection or cart error, then try again.'));
    } catch (error) { setError(t(error.message)); }
    finally { pending.current = false; setSaving(false); }
  };
  return <article className="card reorder-review" aria-labelledby="reorder-review-title">
    <h2 id="reorder-review-title" ref={heading} tabIndex={-1}>{t('Review your reorder')}</h2>
    <p className="muted">{t('Current menu prices are shown. Available items will be added to your cart. Review your cart before submitting.')}</p>
    <ul className="reorder-items">{entries.map(entry => <li key={entry.line.id}>
      <div><b>{entry.line.quantity} × {entry.label}</b>{entry.line.notes && <small>{entry.line.notes}</small>}
        {entry.issue && <p className="form-message">{t(entry.issue)}</p>}</div>
      {!entry.issue && <strong>{entry.total == null ? t('Price to be confirmed') : money(entry.total, restaurant.currency)}</strong>}
    </li>)}</ul>
    {entries.some(entry => entry.issue) && <p>{t('Unavailable items and changed customizations will not be added. Choose replacements from the menu.')}</p>}
    {!!available.length && <p className="price-preview">{t('Food subtotal')}: <strong>{money(available.reduce((sum, entry) => sum + (entry.total ?? 0), 0), restaurant.currency)}</strong>
      <small>{t('Delivery, fees and unpriced custom items are not included.')}</small></p>}
    {error && <p role="alert" className="form-message">{error}</p>}
    <div className="hero-actions"><button className="primary" disabled={saving || !available.length} onClick={apply}>{t(saving ? 'Adding…' : 'Add to cart')}</button>
      <button className="secondary" disabled={saving} onClick={onCancel}>{t('Cancel')}</button></div>
  </article>;
}
