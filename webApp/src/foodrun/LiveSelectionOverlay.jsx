import SelectionCountdown from './SelectionCountdown.jsx';
import SelectionCelebration from './SelectionCelebration.jsx';
import { useEffect, useRef, useState } from 'react';
import GroupSheet from './GroupSheet.jsx';
import { getLanguage, t } from './i18n.js';
const tx = (en,ar) => getLanguage().startsWith('ar') ? (t(en) === en ? ar : t(en)) : en;
export default function LiveSelectionOverlay({ data, renderWheel }) {
  const [now,setNow] = useState(Date.now()), [dismissed,setDismissed] = useState('');
  const candidate = Object.values(data.rooms || {}).filter(reply => {
    const room = reply.room, session = data.sessions?.[room?.id];
    return room?.spin && data.online?.[room.id] && session && ['SPINNING','ACCEPTING','COLLECTING'].includes(room.phase) &&
      room.members.some(member => member.id === session.memberId && member.approved && !member.removed);
  }).sort((a,b) => b.room.spin.startAt-a.room.spin.startAt)[0];
  const clock = useRef({reply:null,offset:0});
  if(candidate && clock.current.reply !== candidate) clock.current={reply:candidate,offset:(Number(candidate.serverTime) || Date.now())-Date.now()};
  const room = candidate?.room, spin = room?.spin, end = spin ? spin.startAt+spin.duration : 0;
  const time = now+clock.current.offset, ended = time >= end, visible = !!spin && time < end+6000 && dismissed !== spin.id;
  const countdown = spin && time < spin.startAt ? Math.min(3,Math.max(1,Math.floor((time-spin.startAt+3000)/1000)+1)) : 0;
  useEffect(() => { setNow(Date.now()); const timer=setInterval(() => setNow(Date.now()),200); return () => clearInterval(timer); },[spin?.id]);
  if(!visible) return null;
  return <GroupSheet fullScreen title={room.name} busy={!ended} onClose={() => setDismissed(spin.id)} tx={tx}>
    <div className="selection-heading"><h1 className="selection-title" aria-live="polite">{ended ? tx('We have our person!','تم اختيار مسؤول الطلب!') : tx('Who’s ordering today?','مين هيطلب لنا النهارده؟')}</h1></div>
    <div className="selection-animation">{countdown ? <SelectionCountdown number={countdown} ready={tx('Everyone’s here. Ready?','كلنا جاهزين للاختيار؟')} subtitle={tx('The selection starts together.','هنبدأ الاختيار مع بعض.')}/> : renderWheel({style:room.selectionStyle,spin,members:room.members,serverTime:candidate.serverTime,active:!ended})}</div>
    <div className="selection-result">{ended && <><p className="selection-winner" aria-live="polite">🎉 {room.members.find(member => member.id === spin.winnerId)?.name}</p><SelectionCelebration/><button className="primary" onClick={() => setDismissed(spin.id)}>{tx('Continue','متابعة')}</button></>}</div>
  </GroupSheet>;
}
