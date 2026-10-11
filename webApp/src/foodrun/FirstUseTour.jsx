import {useState} from 'react';
import GroupSheet from './GroupSheet.jsx';
import steps from './firstUseTour.json';
import {t,tf} from './i18n.js';
export default function FirstUseTour({visible,onComplete}) {
 const [index,setIndex]=useState(0),[dismissed,setDismissed]=useState(false);
 if(!visible||dismissed)return null;
 const finish=()=>{setDismissed(true);onComplete();},step=steps[index];
 return <GroupSheet title={t('A quick tour')} onClose={finish} tx={en=>t(en)}>
  <div className="first-use-tour" data-testid="first-use-tour">
   <div className="tour-progress" role="progressbar" aria-label={t('Tour progress')} aria-valuemin={1} aria-valuemax={steps.length} aria-valuenow={index+1}>{steps.map((value,i)=><i key={value.id} className={i<=index?'active':''}/>)}</div>
   <p className="muted">{tf('Step {step} of {count}',{step:index+1,count:steps.length})}</p>
   <div className="tour-art" aria-hidden="true" key={step.id}>{step.emoji}</div>
   <div className="tour-copy" aria-live="polite"><h3>{t(step.title)}</h3><p>{t(step.body)}</p></div>
   <div className="tour-actions">{index>0&&<button className="secondary" onClick={()=>setIndex(index-1)}>{t('Back')}</button>}<button className="primary" onClick={()=>index===steps.length-1?finish():setIndex(index+1)}>{t(index===steps.length-1?'Get started':'Next')}</button></div>
   <button className="link" onClick={finish}>{t('Skip tour')}</button>
  </div>
 </GroupSheet>;
}
