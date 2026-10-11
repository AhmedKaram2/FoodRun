import { useEffect, useRef, useState } from 'react';
import { getLanguage, t } from './i18n.js';
import PagedList from './PagedList.jsx';
import GroupSheet from './GroupSheet.jsx';
const tx = (en, ar) => getLanguage().startsWith('ar') ? (t(en) === en ? ar : t(en)) : en;
const key = member => member.userId || member.email.toLowerCase();
export default function FriendGroups({ data, onBack }) {
  const blank = () => ({ id:crypto.randomUUID(),name:'',favourite:true,members:[] });
  const [group,setGroup] = useState(blank), [query,setQuery] = useState(''), [results,setResults] = useState([]), [searching,setSearching] = useState(false), [message,setMessage] = useState('');
  const [mode,setMode] = useState(''), [viewKey,setViewKey] = useState(''), [tab,setTab] = useState('owned'), [filter,setFilter] = useState(''), [confirmation,setConfirmation] = useState(''), [saving,setSaving] = useState(false);
  const version = useRef(0), input = useRef(null), pending = useRef(false), identity = useRef(data.user?.uid);
  identity.current = data.user?.uid;
  const owned = data.home.friendGroups || [], joined = data.home.joinedFriendGroups || [];
  const viewed = mode === 'owned' ? owned.find(value => value.id === viewKey) : joined.find(value => `${value.ownerId}:${value.group.id}` === viewKey);
  const details = mode === 'owned' ? viewed : viewed?.group, busy = saving || data.busy;
  const close = () => { if(pending.current) return; ++version.current; setMode(''); setConfirmation(''); setQuery(''); setMessage(''); };
  useEffect(() => { if((mode === 'owned' || mode === 'joined') && !viewed && !pending.current) close(); },[mode,viewed,saving]);
  useEffect(() => { ++version.current; setMode(''); setQuery(''); setResults([]); setConfirmation(''); setMessage(''); },[data.user?.uid]);
  useEffect(() => {
    const current = ++version.current, abort = new AbortController(); setResults([]);
    if(mode !== 'edit' || query.trim().length < 2) { setSearching(false); return; }
    setSearching(true);
    const timer = setTimeout(async () => {
      try { const reply = await data.walletQuery('FRIEND_SEARCH',{text:query.trim()},abort.signal); if(current === version.current) setResults(reply.friendContacts || []); }
      catch(error) { if(!abort.signal.aborted && current === version.current) setMessage(error.message); }
      finally { if(current === version.current) setSearching(false); }
    },300);
    return () => { clearTimeout(timer); abort.abort(); };
  },[query,group.id,mode,data.user?.uid,data.hub]);
  const edit = value => { ++version.current; setGroup({...value,members:[...value.members]}); setQuery(''); setResults([]); setMessage(''); setConfirmation(''); setMode('edit'); };
  const view = (value,kind) => { setViewKey(kind === 'owned' ? value.id : `${value.ownerId}:${value.group.id}`); setMode(kind); setMessage(''); setConfirmation(''); };
  const add = member => { if(group.members.length >= 30) return; setGroup(old => ({...old,members:[...old.members.filter(value => key(value) !== key(member)),member]})); setQuery(''); input.current?.focus(); };
  const invite = async () => { const current = ++version.current; setSearching(true); setMessage(''); try { const reply = await data.walletQuery('FRIEND_LOOKUP',{text:query.trim().toLowerCase()}); if(current === version.current && reply.friendContact) add(reply.friendContact); } catch(error) { if(current === version.current) setMessage(error.message); } finally { if(current === version.current) setSearching(false); } };
  const send = async (kind,payload,notice) => {
    if(pending.current || data.busy) return;
    pending.current = true; setSaving(true); setMessage(''); const user = identity.current;
    try { const ok = await data.send(kind,payload); if(identity.current !== user) return;
      if(ok) { ++version.current; setMode(''); setQuery(''); setConfirmation(''); data.setNotice(notice); }
      else setMessage(tx('Could not save this change. Try again.','تعذّر حفظ التغيير. حاول مرة أخرى.'));
    } catch(error) { if(identity.current === user) setMessage(error.message); }
    finally { pending.current = false; setSaving(false); }
  };
  const options = results.filter(member => !group.members.some(value => key(value) === key(member)));
  const member = (value,removable = false) => <article className="wallet-item group-person" key={key(value)}><div><b>{value.name || value.email}</b><small><bdi>{value.email || tx('App notifications available','إشعارات التطبيق متاحة')}</bdi>{!value.userId && ` · ${tx('Invitation sent when saved','تُرسل الدعوة عند الحفظ')}`}</small></div>{removable && <button disabled={busy} type="button" className="link danger" onClick={() => setGroup(old => ({...old,members:old.members.filter(person => key(person) !== key(value))}))}>{t('Remove')}</button>}</article>;
  const items = (tab === 'owned' ? owned : joined).filter(value => `${value.group?.name || value.name} ${value.ownerName || ''}`.toLocaleLowerCase().includes(filter.trim().toLocaleLowerCase()));
  return <main className="page-shell"><div className="section-title"><h1>{tx('Friend groups','مجموعات الأصدقاء')}</h1><button className="secondary" onClick={onBack}>{t('Home')}</button></div><p>{tx('Choose a saved group when creating a room to invite its members.','اختر مجموعة محفوظة عند إنشاء غرفة لدعوة أعضائها.')}</p>
    <section data-mobile-section="groups" className="card stack"><div className="section-title"><nav className="group-tabs" aria-label={tx('Group lists','قوائم المجموعات')}>{[['owned',tx('Your groups','مجموعاتك')],['joined',tx('Joined groups','المجموعات المنضم إليها')]].map(([value,label]) => <button key={value} className={tab === value ? 'primary' : 'secondary'} aria-pressed={tab === value} onClick={() => setTab(value)}>{label}</button>)}</nav><button className="primary" disabled={busy} onClick={() => edit(blank())}>{tx('New group','مجموعة جديدة')}</button></div>
    <label>{tx('Search groups','البحث في المجموعات')}<input type="search" placeholder={tx('Search by group name…','ابحث باسم المجموعة…')} value={filter} onChange={event => setFilter(event.target.value)}/></label>
    <PagedList items={items} resetKey={`${tab}:${filter}`}>{value => <article className="wallet-item group-person" key={tab === 'owned' ? value.id : `${value.ownerId}:${value.group.id}`}><div><b>{value.group?.name || value.name} {value.favourite ? '★' : ''}</b><small>{tab === 'joined' && `${tx('Created by','أنشأها')} ${value.ownerName} · `}{(value.group?.members || value.members).length} {tx('friends','أصدقاء')}</small></div><div className="group-actions"><button className="secondary" disabled={busy} onClick={() => view(value,tab)}>{tx('View group','عرض المجموعة')}</button>{tab === 'owned' && <button className="link" disabled={busy} onClick={() => edit(value)}>{t('Edit')}</button>}</div></article>}</PagedList></section>
    {!!mode && <GroupSheet title={mode === 'edit' ? tx('Edit group','تعديل المجموعة') : details?.name || tx('Group details','تفاصيل المجموعة')} busy={busy} onClose={close} tx={tx}>
      {mode === 'edit' ? <><label>{tx('Group name','اسم المجموعة')}<input disabled={busy} value={group.name} maxLength={160} onChange={event => setGroup({...group,name:event.target.value})}/></label><label className="check"><input disabled={busy} type="checkbox" checked={group.favourite} onChange={event => setGroup({...group,favourite:event.target.checked})}/>{tx('Favourite group','مجموعة مفضلة')}</label>
        <div className="friend-search"><label>{tx('Find people by name or email','ابحث عن الأشخاص بالاسم أو البريد')}<input disabled={busy} ref={input} type="search" placeholder={tx('Name or email · at least 2 characters','الاسم أو البريد الإلكتروني · حرفان على الأقل')} role="combobox" aria-expanded={query.trim().length >= 2} aria-controls="friend-search-results" autoComplete="off" value={query} maxLength={254} onChange={event => setQuery(event.target.value)} /></label>
        {query.trim().length >= 2 && <div id="friend-search-results" className="friend-search-results" aria-busy={searching}>{searching && <p role="status">{tx('Searching…','جارٍ البحث…')}</p>}{options.map(value => <button type="button" className="friend-search-person" key={key(value)} disabled={busy || group.members.length >= 30} onClick={() => add(value)}><span><b>{value.name || value.email}</b><small><bdi>{value.email}</bdi></small></span><b>＋</b></button>)}{!searching && !options.length && <p>{tx('No new people match this search.','لا يوجد أشخاص جدد مطابقون للبحث.')}</p>}{!searching && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(query.trim()) && !results.some(value => value.email.toLowerCase() === query.trim().toLowerCase()) && <button type="button" className="secondary" disabled={busy || group.members.length >= 30} onClick={invite}>{tx('Invite by email','دعوة بالبريد')} · <bdi>{query.trim()}</bdi></button>}</div>}</div>
        <h3>{tx('Selected members','الأعضاء المحددون')} · {group.members.length}/30</h3><PagedList items={group.members} resetKey={group.id}>{value => member(value,true)}</PagedList><button className="primary" disabled={busy || searching || !group.name.trim()} onClick={() => send('SAVE_FRIEND_GROUP',{friendGroup:{...group,name:group.name.trim()}},tx('Friend group saved.','تم حفظ مجموعة الأصدقاء.'))}>{t('Save')}</button></> : <><p>{mode === 'joined' ? `${tx('Created by','أنشأها')} ${viewed?.ownerName || ''}` : tx('You manage this group.','أنت تدير هذه المجموعة.')}</p><PagedList items={details?.members || []} resetKey={viewKey}>{value => member(value)}</PagedList>
        {confirmation ? <div className="card stack"><p>{mode === 'joined' ? tx('Leave this group? You will no longer receive its room invitations.','هل تريد مغادرة المجموعة؟ لن تصلك دعوات غرفها بعد المغادرة.') : tx('Delete this group?','هل تريد حذف هذه المجموعة؟')}</p><button className="danger" disabled={busy} onClick={() => mode === 'joined' ? send('LEAVE_FRIEND_GROUP',{friendGroupId:viewed.group.id,friendGroupOwnerId:viewed.ownerId},tx('You left the group.','غادرت المجموعة.')) : send('DELETE_FRIEND_GROUP',{friendGroupId:details.id},tx('Group deleted.','تم حذف المجموعة.'))}>{mode === 'joined' ? tx('Confirm leave','تأكيد المغادرة') : tx('Confirm delete','تأكيد الحذف')}</button><button className="secondary" disabled={busy} onClick={() => setConfirmation('')}>{t('Cancel')}</button></div> : <div className="group-actions">{mode === 'owned' && <button className="primary" disabled={busy} onClick={() => edit(details)}>{t('Edit')}</button>}<button className="link danger" disabled={busy} onClick={() => setConfirmation('confirm')}>{mode === 'joined' ? tx('Leave group','مغادرة المجموعة') : tx('Delete group','حذف المجموعة')}</button></div>}</>}
      {busy && <p role="status">{tx('Saving…','جارٍ الحفظ…')}</p>}{(message || data.error) && <p role="alert" className="error">{data.error || message}</p>}<button className="secondary" disabled={busy} onClick={close}>{tx('Back to groups','العودة للمجموعات')}</button>
    </GroupSheet>}
  </main>;
}
