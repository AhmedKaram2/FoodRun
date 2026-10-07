import { useRef, useState } from 'react';
import { getLanguage, t } from './i18n.js';
const tx = (en, ar) => getLanguage() === 'ar' ? ar : en;

export default function FriendGroups({ data, onBack }) {
  const blank = () => ({ id: crypto.randomUUID(), name: '', favourite: true, members: [] });
  const [group, setGroup] = useState(blank), [email, setEmail] = useState(''), [busy, setBusy] = useState(false), [message, setMessage] = useState('');
  const queryVersion = useRef(0);
  const edit = value => { ++queryVersion.current; setGroup(value); setEmail(''); setMessage(''); setBusy(false); };
  const add = async event => {
    event.preventDefault(); const version = ++queryVersion.current; const value = email.trim().toLowerCase(); setBusy(true); setMessage('');
    try {
      const reply = await data.walletQuery('FRIEND_LOOKUP', { text: value });
      if(version !== queryVersion.current) return;
      if(!reply.friendContact) throw Error(tx('Could not look up this email.', 'تعذر البحث عن هذا البريد.'));
      setGroup(old => ({ ...old, members: [...old.members.filter(member => member.email !== value), reply.friendContact] })); setEmail('');
    } catch(error) { if(version === queryVersion.current) setMessage(error.message); }
    finally { if(version === queryVersion.current) setBusy(false); }
  };
  return <main className="page-shell"><div className="section-title"><h1>{tx('Friend groups', 'مجموعات الأصدقاء')}</h1><button className="secondary" onClick={onBack}>{t('Home')}</button></div>
    <p>{tx('Save a favourite group, then select it when creating a room to email everyone an invitation.', 'احفظ مجموعة مفضلة ثم اخترها عند إنشاء غرفة لإرسال دعوة بالبريد لجميع أعضائها.')}</p>
    <div className="stack">
      <section className="card stack"><h2>{tx('Your groups', 'مجموعاتك')}</h2>{(data.home.friendGroups || []).map(value => <article key={value.id} className="wallet-item"><b>{value.name} {value.favourite ? '★' : ''}</b><p>{value.members.length} {tx('friends', 'أصدقاء')}</p><button className="secondary" disabled={data.busy} onClick={() => edit(value)}>{t('Edit')}</button><button className="link danger" disabled={data.busy} onClick={async () => { const reply = await data.send('DELETE_FRIEND_GROUP', { friendGroupId: value.id }); if(reply && group.id === value.id) edit(blank()); }}>{t('Delete')}</button></article>)}<button className="secondary" onClick={() => edit(blank())}>{tx('New group', 'مجموعة جديدة')}</button></section>
      <section className="card stack"><h2>{tx('Edit group', 'تعديل المجموعة')}</h2><label>{tx('Group name', 'اسم المجموعة')}<input value={group.name} maxLength={160} onChange={event => setGroup({ ...group, name: event.target.value })} /></label><label className="check"><input type="checkbox" checked={group.favourite} onChange={event => setGroup({ ...group, favourite: event.target.checked })} />{tx('Favourite group', 'مجموعة مفضلة')}</label>
        <form className="stack" onSubmit={add}><label>{t('Email')}<input type="email" required value={email} maxLength={254} onChange={event => { ++queryVersion.current; setBusy(false); setEmail(event.target.value); }} /></label><button className="secondary" disabled={busy || group.members.length >= 30}>{tx('Add by email', 'إضافة بالبريد')}</button></form>
        {group.members.map(member => <div className="wallet-item" key={member.email}><b>{member.name || member.email}</b><p><bdi>{member.email}</bdi> · {member.userId ? tx('Registered user', 'مستخدم مسجل') : tx('Sign-up invitation will be emailed when saved', 'تُرسل دعوة التسجيل بالبريد عند الحفظ')}</p><button className="link danger" onClick={() => setGroup({ ...group, members: group.members.filter(value => value.email !== member.email) })}>{t('Remove')}</button></div>)}
        {message && <p role="alert">{message}</p>}<button className="primary" disabled={data.busy || busy || !group.name.trim() || !group.members.length} onClick={async () => { const reply = await data.send('SAVE_FRIEND_GROUP', { friendGroup: group }); if(reply) { edit(blank()); data.setNotice(tx('Friend group saved.', 'تم حفظ مجموعة الأصدقاء.')); } }}>{t('Save')}</button>
      </section>
    </div>
  </main>;
}
