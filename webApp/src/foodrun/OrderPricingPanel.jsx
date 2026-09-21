import { useRef, useState } from 'react';
import { amount, money } from './client.js';
import { menuLineTotal } from './cartEditing.js';
import { t } from './i18n.js';

export default function OrderPricingPanel({ room, data, describeLine }) {
  const [editing, setEditing] = useState(null);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);
  const pending = useRef(false);
  const currency = room.restaurant.currency;
  const edit = (cart, line) => {
    const total = menuLineTotal(room.restaurant, line);
    setEditing({ memberId: cart.memberId, lineId: line.id, revision: room.revision,
      value: total == null ? '' : (total / line.quantity / 100).toFixed(2) });
    setError('');
  };
  const save = async (event, reset = false) => {
    event.preventDefault();
    if (pending.current || !editing) return;
    pending.current = true; setSaving(true); setError('');
    try {
      const result = await data.send('PRICE_ITEM', { memberId: editing.memberId, text: editing.lineId,
        expectedRevision: editing.revision, amount: reset ? 0 : amount(editing.value, currency), flag: reset }, room.id);
      if (result) setEditing(null);
      else setError(t('The price was not saved. Refresh the item and try again.'));
    } catch (error) { setError(error.message); }
    finally { pending.current = false; setSaving(false); }
  };
  return <article className="card order-pricing-panel">
    <p className="eyebrow">{t('SELECTED TO ORDER')}</p><h2>{t('Item prices')}</h2>
    <p className="muted">{t('Set the price per item, including its selected extras. Changes apply only to this order and update everyone’s totals.')}</p>
    <p className="fine">{t('Changing an item’s quantity or customizations resets its adjusted price.')}</p>
    {!room.carts.some(cart => cart.lines.length) && <p>{t('Items will appear here as people add their food.')}</p>}
    {room.carts.filter(cart => room.members.some(member => member.id === cart.memberId && member.approved && !member.guest && !member.removed && member.participating)).map(cart => <section key={cart.memberId}>
      {!!cart.lines.length && <h3>{room.members.find(member => member.id === cart.memberId)?.name}</h3>}
      {cart.lines.map(line => {
        const total = menuLineTotal(room.restaurant, line);
        const selected = editing?.memberId === cart.memberId && editing?.lineId === line.id;
        return <div className="priced-item" key={line.id}>
          <div className="order-line"><div><b>{line.quantity} × {describeLine(line)}</b>{line.notes && <small>{line.notes}</small>}
            {!!line.itemId && line.unitPrice != null && <small>{t('Adjusted price for this order')}</small>}</div>
            <div><b>{total == null ? t('Awaiting price') : money(total, currency)}</b><button className="link" disabled={saving || data.busy} onClick={() => edit(cart, line)}>{t('Edit price')}</button></div></div>
          {selected && <form className="stack price-form" onSubmit={save}>
            <label>{t('Unit price')} · {currency}<input autoFocus inputMode="decimal" value={editing.value} disabled={saving} onChange={event => setEditing({ ...editing, value: event.target.value })} required /></label>
            <div className="hero-actions"><button className="primary" disabled={saving || data.busy}>{t('Save price')}</button>
              {!!line.itemId && line.unitPrice != null && <button type="button" className="secondary" disabled={saving || data.busy} onClick={event => save(event, true)}>{t('Use menu price')}</button>}
              <button className="secondary" type="button" disabled={saving} onClick={() => { setEditing(null); setError(''); }}>{t('Cancel')}</button></div>
            {error && <p role="alert" className="form-message">{data.error || error}</p>}
          </form>}
        </div>;
      })}
    </section>)}
  </article>;
}
