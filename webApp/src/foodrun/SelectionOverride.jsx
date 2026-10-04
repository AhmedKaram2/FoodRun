import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { canAccessAdmin } from './adminAccess.js';
import { command, request } from './client.js';

export default function SelectionOverride({ room, data, language }) {
  const ar = language === 'ar';
  const tx = (en, arabic) => ar ? arabic : en;
  const allowed = canAccessAdmin(data.user) && room.phase === 'LOBBY' && !room.paymentRoom;
  const [open, setOpen] = useState(false);
  const [unlocked, setUnlocked] = useState(false);
  const [passcode, setPasscode] = useState('');
  const [chosen, setChosen] = useState('');
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const dialog = useRef(null), timer = useRef(null), start = useRef(null), epoch = useRef(0);
  const candidates = room.members.filter(member => member.approved && !member.removed && !member.guest && member.participating && member.eligible);
  const cancelPress = () => { clearTimeout(timer.current); timer.current = null; };
  const close = () => { ++epoch.current; setOpen(false); setUnlocked(false); setPasscode(''); setBusy(false); };
  useEffect(() => {
    if (!allowed) close();
    return () => { ++epoch.current; clearTimeout(timer.current); };
  }, [allowed, data.identityToken]);
  useEffect(() => {
    if (open) dialog.current?.showModal();
    else dialog.current?.close();
  }, [open]);
  const beginPress = () => {
    if (!allowed || data.busy || data.hasPending || timer.current) return;
    timer.current = setTimeout(() => {
      timer.current = null; setMessage(''); setPasscode(''); setUnlocked(false); setOpen(true);
    }, 800);
  };
  const submit = async event => {
    event.preventDefault();
    if (busy || !allowed) return;
    const generation = epoch.current;
    const unlocking = !unlocked;
    setBusy(true); setMessage('');
    try {
      const reply = await request(data.hub, command(unlocking ? 'UNLOCK_SELECTION_OVERRIDE' : 'SET_SELECTION_OVERRIDE', {
        roomId: room.id, token: data.sessions[room.id].token, identityToken: data.identityToken,
        expectedRevision: room.revision, expectedOrderNumber: room.orderNumber,
        ...(unlocking ? { text: passcode } : { memberId: chosen }),
      }));
      if (generation !== epoch.current) return;
      if (unlocking) {
        if (reply.code !== 'SELECTION_OVERRIDE_UNLOCKED') throw Error(tx('Could not unlock this option.', 'تعذر فتح هذا الخيار.'));
        setUnlocked(true); setPasscode('');
      } else {
        if (reply.code !== 'SELECTION_OVERRIDE_SAVED') throw Error(tx('Could not save the selection.', 'تعذر حفظ الاختيار.'));
        setMessage(chosen ? tx('Saved for the next spin. The wheel will select this person once.', 'تم الحفظ للدورة القادمة. ستختار العجلة هذا الشخص مرة واحدة.') : tx('Random selection restored.', 'تمت استعادة الاختيار العشوائي.'));
      }
    } catch (error) { if (generation === epoch.current) setMessage(error.message); }
    finally { if (generation === epoch.current) { setBusy(false); setPasscode(''); } }
  };
  return <>
    <span className={allowed ? 'room-name-hold' : undefined} tabIndex={allowed ? 0 : undefined}
      onPointerDown={event => {
        if (!allowed || event.button !== 0) return;
        event.currentTarget.setPointerCapture(event.pointerId);
        start.current = { x: event.clientX, y: event.clientY }; beginPress();
      }}
      onPointerMove={event => { if (start.current && Math.hypot(event.clientX - start.current.x, event.clientY - start.current.y) > 10) cancelPress(); }}
      onPointerUp={cancelPress} onPointerCancel={cancelPress} onLostPointerCapture={cancelPress}
      onContextMenu={event => { if (allowed) event.preventDefault(); }}
      onKeyDown={event => { if (allowed && [' ', 'Enter'].includes(event.key) && !event.repeat) { event.preventDefault(); beginPress(); } }}
      onKeyUp={cancelPress} onBlur={cancelPress}>{room.name}</span>
    {open && createPortal(<dialog ref={dialog} className="card poll-dialog" onCancel={close} onClose={close} aria-labelledby="selection-override-title" dir={ar ? 'rtl' : 'ltr'}>
      <form className="stack" onSubmit={submit}>
        <h2 id="selection-override-title">{tx('Wheel selection', 'اختيار العجلة')}</h2>
        {unlocked ? <label>{tx('Person for the next spin', 'الشخص للدورة القادمة')}<select value={chosen} onChange={event => setChosen(event.target.value)} disabled={busy}>
          <option value="">{tx('Random selection', 'اختيار عشوائي')}</option>
          {candidates.map(member => <option key={member.id} value={member.id}>{member.name}</option>)}
        </select></label> : <label>{tx('Passcode', 'رمز الدخول')}<input type="password" inputMode="numeric" autoComplete="off" value={passcode} onChange={event => setPasscode(event.target.value)} maxLength={4} required autoFocus disabled={busy} /></label>}
        {message && <p role="status">{message}</p>}
        <div className="button-row"><button type="button" className="secondary" onClick={close}>{tx('Close', 'إغلاق')}</button><button className="primary" disabled={busy || !unlocked && passcode.length !== 4 || unlocked && chosen !== '' && !candidates.some(member => member.id === chosen)}>{unlocked ? tx('Save', 'حفظ') : tx('Unlock', 'فتح')}</button></div>
      </form>
    </dialog>, document.body)}
  </>;
}
