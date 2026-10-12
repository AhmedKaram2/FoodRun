import Illustration from './Illustration.jsx';
import { useRef, useState } from 'react';
import { getLanguage, t } from './i18n.js';
import GroupSheet from './GroupSheet.jsx';
import { addQuickName, groupParticipants, quickChoiceGroups, emptyQuickChoice } from './quickChoice.js';
import './quickChoice.css';
const tx = (en,ar) => getLanguage().startsWith('ar') ? (t(en) === en ? ar : t(en)) : en;

export default function QuickChoice({ home, startQuickChoice, onBack }) {
  const [state,setState] = useState(emptyQuickChoice);
  const [sheet,setSheet] = useState(''), [search,setSearch] = useState(''), [name,setName] = useState(''), [error,setError] = useState('');
  const [spinning,setSpinning] = useState(false), pending = useRef(false);
  const groups = quickChoiceGroups(home), included = state.people.filter(person => person.active);
  const purpose = state.purpose.trim() || tx('Food pickup','إحضار الطعام');
  const group = groups.find(value => `${value.ownerId}:${value.group.id}` === state.groupId);
  const save = value => { setState(value); setError(''); };
  const edit = value => { if(pending.current) return; setSheet(value); setSearch(''); setError(''); setName(''); };
  const close = () => { setSheet(''); setError(''); };
  const start = async () => {
    if(pending.current || !included.length) return;
    pending.current=true;setSpinning(true);setError('');
    try { await startQuickChoice({action:'start',purpose:state.purpose,style:state.style,groupId:state.groupId,audience:state.audience,people:included.map(({id,name})=>({id,name}))}); }
    catch(failure) { setError(failure.message); }
    finally { pending.current=false;setSpinning(false); }
  };
  const add = event => {
    event.preventDefault();
    try { save(addQuickName(state,name,crypto.randomUUID()));setName('');setSheet('people'); }
    catch(failure) { setError(t(failure.message)); }
  };
  const title = sheet === 'groups' ? tx('Choose group','اختيار مجموعة') : sheet === 'settings' ? tx('Purpose & animation','السبب وطريقة العرض') : tx('Participants','المشاركون');
  return <main className="app-shell quick-choice">
    <div className="section-title illustrated-heading"><Illustration kind="wheel"/><div><p className="eyebrow">{tx('FOR ANY OCCASION','لأي مناسبة')}</p><h1>{tx('Quick Choice','الاختيار السريع')}</h1></div><button className="secondary" onClick={onBack}>{t('Home')}</button></div>
    <p>{tx('Choose someone for anything. Everyone included has an equal chance.','اختَر شخصاً لأي سبب. لكل المشاركين فرصة متساوية.')}</p>
    <div className="quick-choice-layout">
      <section className="card quick-choice-stage stack">
        <button className="quick-purpose" disabled={spinning} onClick={() => edit('settings')} aria-label={tx('Edit purpose and animation','تعديل السبب وطريقة العرض')}><span aria-hidden="true">✦</span><span><small>{tx('Choosing for','سبب الاختيار')}</small><b>{purpose}</b></span><span aria-hidden="true">✎</span></button>
        <div className="quick-choice-empty"><span>✦</span><h2>{tx('Who will it be?','مَن سيكون الاختيار؟')}</h2><p>{purpose}</p></div>
        <p className="quick-count">{included.length} / {state.people.length} · {tx('Included','المشاركون')}{group ? ` · ${group.group.name}` : ''}</p>
        {!included.length && <p className="muted">{tx('Add names or choose a group to get started.','أضف أسماء أو اختَر مجموعة للبدء.')}</p>}
        <button className="primary wide" disabled={spinning || !included.length} onClick={start}>{spinning ? t('Choosing…') : tx('Pick someone','اختيار شخص')}</button>
        {error && !sheet && <p role="alert" className="form-message">{error}</p>}
      </section>
      <aside className="card stack quick-choice-tools"><h2>{tx('Participants','المشاركون')}</h2><p>{tx('Changes here only affect this picker. Your group stays the same.','التغييرات هنا تخص هذا الاختيار فقط. تبقى مجموعتك كما هي.')}</p>
        <button className="primary" disabled={spinning} onClick={() => edit('groups')}>{tx('Choose group','اختيار مجموعة')}</button>
        <button className="secondary" disabled={spinning} onClick={() => edit('people')}>{tx('People','الأشخاص')} · {state.people.length}</button>
        <button className="secondary" disabled={spinning} onClick={() => edit('add')}>{t('Add person')}</button>
      </aside>
    </div>
    {sheet && <GroupSheet title={title} onClose={close} tx={tx}>
      {sheet === 'settings' && <>
        <label>{tx('Choosing for','سبب الاختيار')}<input value={state.purpose} maxLength={160} placeholder={tx('e.g. food, a game, a task or a prize','مثلاً: الطعام، لعبة، مهمة أو جائزة')} onChange={event => save({...state,purpose:event.target.value})}/></label>
        <button className="secondary" onClick={() => save({...state,purpose:''})}>{tx('Use food default','استخدام الطعام كاختيار افتراضي')}</button>
        <p>{t('Selection animation')}</p><div className="segmented">{[['names',t('Running names')],['wheel',t('Wheel')]].map(([value,label]) => <button key={value} className={state.style === value ? 'active' : ''} aria-pressed={state.style === value} onClick={() => save({...state,style:value})}>{label}</button>)}</div>
        <p>{tx('Who sees this choice?','مَن يشاهد الاختيار؟')}</p><div className="stack">{[['participants',tx('Included group members','المشاركون من المجموعة')],['all',tx('Everyone online','جميع المتصلين الآن')]].map(([value,label]) => <label className="check" key={value}><input type="radio" name="quick-audience" checked={state.audience===value} onChange={()=>save({...state,audience:value})}/>{label}</label>)}</div>
        <p className="muted">{tx('Group members see it automatically. Choose everyone online to share custom names with others.','يرى أعضاء المجموعة الاختيار تلقائياً. اختَر جميع المتصلين لمشاركة الأسماء اليدوية مع الآخرين.')}</p>
      </>}
      {sheet === 'groups' && <>
        <p>{tx('All group members will be included. You can exclude anyone afterward.','سيشارك جميع أعضاء المجموعة. يمكنك استبعاد أي شخص بعد ذلك.')}</p>
        <label>{t('Search groups')}<input type="search" value={search} placeholder={tx('Search by group name…','ابحث باسم المجموعة…')} onChange={event => setSearch(event.target.value)}/></label>
        {!groups.length && <p>{tx('Create or join a group first, or add names yourself.','أنشئ مجموعة أو انضم إلى مجموعة، أو أضف الأسماء بنفسك.')}</p>}
        {groups.filter(value => value.group.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())).map(value => {
          const id = `${value.ownerId}:${value.group.id}`;
          return <button key={id} className="quick-group" onClick={() => { save({...state,people:groupParticipants(value),groupId:id});edit('people'); }}><span><b>{value.group.name}</b><small>{groupParticipants(value).length} · {tx('People','الأشخاص')}</small></span><span>{state.groupId === id ? '✓' : '+'}</span></button>;
        })}
      </>}
      {sheet === 'add' && <form className="stack" onSubmit={add}><label>{t('Name')}<input value={name} maxLength={80} placeholder={tx('Enter a participant’s name','أدخل اسم المشارك')} onChange={event => {setName(event.target.value);setError('');}}/></label><button className="primary">{t('Add person')}</button></form>}
      {sheet === 'people' && <>
        <div className="hero-actions"><button className="primary" onClick={() => edit('add')}>{t('Add person')}</button><button className="secondary" onClick={() => save({...state,people:state.people.map(person => ({...person,active:true}))})}>{tx('Include everyone','مشاركة الجميع')}</button></div>
        <label>{tx('Search names','البحث في الأسماء')}<input type="search" value={search} placeholder={tx('Search by name…','ابحث بالاسم…')} onChange={event => setSearch(event.target.value)}/></label>
        {state.people.filter(person => person.name.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase())).map(person => <div className="quick-person" key={person.id}><label className="check"><input type="checkbox" checked={person.active} onChange={() => save({...state,people:state.people.map(value => value.id === person.id ? {...value,active:!value.active} : value)})}/><span><b>{person.name}</b><small>{tx(person.active ? 'Included' : 'Excluded',person.active ? 'مشارك' : 'مستبعد')}</small></span></label><button className="icon-button danger" aria-label={`${t('Remove')} ${person.name}`} onClick={() => save({...state,people:state.people.filter(value => value.id !== person.id)})}>×</button></div>)}
        {!state.people.length && <p>{tx('No participants yet','لا يوجد مشاركون بعد')}</p>}
      </>}
      {error && <p role="alert" className="form-message">{error}</p>}<button className="secondary" onClick={close}>{t('Done')}</button>
    </GroupSheet>}
  </main>;
}
