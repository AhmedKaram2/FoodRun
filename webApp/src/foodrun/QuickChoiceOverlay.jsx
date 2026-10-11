import SelectionCountdown from './SelectionCountdown.jsx';
import SelectionCelebration from './SelectionCelebration.jsx';
import { useEffect, useState } from 'react';
import GroupSheet from './GroupSheet.jsx';
import {getLanguage,t} from './i18n.js';
const tx=(en,ar)=>getLanguage().startsWith('ar') ? (t(en)===en ? ar : t(en)) : en;
export default function QuickChoiceOverlay({data,renderSelection}) {
  const choice=data.quickSelection;
  const [now,setNow]=useState(Date.now()), [dismissed,setDismissed]=useState(''), [busy,setBusy]=useState(false), [error,setError]=useState('');
  useEffect(()=>{setNow(Date.now());setError('');const timer=setInterval(()=>setNow(Date.now()),100);return()=>clearInterval(timer);},[choice?.id]);
  if(!choice || dismissed===choice.id || now+(choice.serverOffset || 0)>=choice.expiresAt) return null;
  const time=now+(choice.serverOffset || 0), ended=time>=choice.spin.startAt+choice.spin.duration;
  const countdown=time<choice.spin.startAt ? Math.min(3,Math.max(1,Math.floor((time-choice.spin.startAt+3000)/1000)+1)) : 0;
  const owner=choice.ownerId===(data.home?.profile.userId || data.user?.uid), purpose=choice.purpose || tx('Food pickup','إحضار الطعام');
  const run=async action=>{if(busy)return;setBusy(true);setError('');try{await data.startQuickChoice({action,id:choice.id});if(action==='close')setDismissed(choice.id);}catch(failure){setError(failure.message);}finally{setBusy(false);}};
  const close=()=>{if(!ended || busy)return;if(owner)run('close');else setDismissed(choice.id);};
  return <GroupSheet fullScreen title={purpose} busy={!ended || busy} onClose={close} tx={tx}>
    <h1 className="selection-title" aria-live="polite">{ended ? tx('We have our pick!','تم الاختيار!') : tx('Who will it be?','مَن سيكون الاختيار؟')}</h1>
    <p>{choice.ownerName} · {tx('Quick Choice','الاختيار السريع')}</p>
    {countdown ? <SelectionCountdown number={countdown} ready={tx('Everyone’s here. Ready?','كلنا جاهزين للاختيار؟')} subtitle={tx('The selection starts together.','هنبدأ الاختيار مع بعض.')}/> : renderSelection({key:choice.id,style:choice.style,spin:choice.spin,members:choice.people,serverTime:time,active:!ended,general:true,purpose})}
    {ended && <><p className="selection-winner" role="status">🎉 {choice.people.find(person=>person.id===choice.spin.winnerId)?.name}</p><SelectionCelebration/><div className="hero-actions">{owner && <button className="primary" disabled={busy} onClick={()=>run('retry')}>{tx('Try again','حاول مرة أخرى')}</button>}<button className="secondary" disabled={busy} onClick={close}>{t('Close')}</button></div></>}
    {error && <p role="alert" className="form-message">{error}</p>}
  </GroupSheet>;
}
