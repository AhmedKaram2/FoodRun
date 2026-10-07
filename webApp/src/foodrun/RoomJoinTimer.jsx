import { useEffect, useState } from 'react';
import { getLanguage } from './i18n.js';
export default function RoomJoinTimer({ room, serverTime }) {
  const [remaining, setRemaining] = useState(0);
  useEffect(() => {
    const started = performance.now(), base = Number(serverTime) || Date.now();
    const update = () => setRemaining(Math.max(0, Math.ceil((room.joinDeadlineAt - base - performance.now() + started) / 1000)));
    update(); const timer = setInterval(update, 1000); return () => clearInterval(timer);
  }, [room.joinDeadlineAt, serverTime]);
  if(!room.joinDeadlineAt) return null;
  const ar = getLanguage() === 'ar';
  return <div className="card" role="timer"><b>{ar ? 'مهلة الانضمام' : 'Join window'} · {remaining ? `${Math.floor(remaining / 60)}:${String(remaining % 60).padStart(2, '0')}` : ar ? 'انتهت' : 'Closed'}</b><p>{remaining ? ar ? 'تبدأ العجلة تلقائياً عند انتهاء المهلة.' : 'The shared wheel starts automatically when the timer ends.' : ar ? 'يمكن للأعضاء الحاليين متابعة الطلب. أُغلق الانضمام الجديد.' : 'Existing members can continue. New joins are closed.'}</p></div>;
}
