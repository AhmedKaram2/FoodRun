import { t, tf } from './i18n.js';
import { money } from './client.js';
import Illustration from './Illustration.jsx';
import PagedList from './PagedList.jsx';
import './HomeDashboard.css';

export function savedRoomCards(data) {
  return Object.values(data.sessions || {}).map(session => ({ session, reply: data.rooms?.[session.roomId] }))
    .sort((a,b) => (b.reply?.room?.createdAt || 0) - (a.reply?.room?.createdAt || 0));
}
function RoomTile({ entry, data, openRoom, phaseName }) {
  const { session, reply }=entry, room=reply?.room;
  const person=room?.members?.find(member => member.id === (room.payerId || room.spin?.winnerId));
  return <button type="button" className="home-room-tile" disabled={data.busy} aria-label={`${t('Open room')} · ${session.roomName}`} onClick={()=>openRoom(session.roomId)}>
    <div className="room-card-top"><Illustration kind="room"/><span className={`status ${data.online?.[session.roomId] ? 'live' : ''}`}>{data.online?.[session.roomId] ? t('● Live') : t('Offline')}</span><span>#{room?.orderNumber || 1}</span></div>
    <strong>{session.roomName}</strong><small>{room ? phaseName(room.phase) : t('Connecting…')}</small>
    {person && <small>{tf('{name} is selected to order',{name:person.name})}</small>}
  </button>;
}
export function RoomsOverview({ data, openRoom, phaseName, all=false, onViewAll }) {
  const entries=savedRoomCards(data), continuing=entries.find(({reply})=>reply?.room && !['FULFILLED','ARCHIVED','CANCELLED'].includes(reply.room.phase));
  const recent=entries.filter(entry=>entry !== continuing).slice(0,6);
  const shown=all ? entries : recent;
  return <section className="home-rooms" aria-label={t('Rooms')}>
    <div className="section-title compact"><h2>{t(all ? 'Rooms' : 'Recent rooms')}</h2>{!all && <button type="button" className="link" disabled={data.busy} onClick={onViewAll} aria-label={t('View all rooms')}>{t('View all')} →</button>}</div>
    {shown.length ? all ? <div className="home-room-grid"><PagedList items={shown} pageSize={12}>{entry=><RoomTile key={entry.session.roomId} entry={entry} data={data} openRoom={openRoom} phaseName={phaseName}/>}</PagedList></div> : <div className="home-room-strip" data-home-rooms>{shown.map(entry=><RoomTile key={entry.session.roomId} entry={entry} data={data} openRoom={openRoom} phaseName={phaseName}/>)}</div>
      : !entries.length && <div className="home-room-empty"><Illustration kind="room"/><p>{t('Create or join a room above. Your rooms will appear here.')}</p></div>}
  </section>;
}
export default function HomeDashboard({ data, setPage, openRoom, openRestaurants, phaseName, allowRoomCreation=true }) {
  const continuing=savedRoomCards(data).find(({reply})=>reply?.room && !['FULFILLED','ARCHIVED','CANCELLED'].includes(reply.room.phase));
  const periods=['week','month'].map(period=>({period,rows:data.home.spending?.periods.filter(value=>value.period===period) || []}));
  const features=[
    {id:'quick-choice',title:'Quick Choice',art:'wheel',tone:'sky',run:()=>setPage('quick-choice')},
    {id:'payment-create',title:'Payment room',art:'wallet',tone:'honey',run:()=>setPage('payment-create'),disabled:!allowRoomCreation},
    {id:'wallet',title:'Wallet',art:'wallet',tone:'mint',run:()=>setPage('wallet')},
    {id:'menus',title:'Menus',art:'food',tone:'coral',run:()=>openRestaurants()},
    {id:'friends',title:'Groups',art:'friends',tone:'sky',run:()=>setPage('friends')},
    {id:'restaurant-create',title:'Add restaurant',art:'room',tone:'honey',run:()=>setPage('restaurant-create')},
  ];
  return <div className="home-overview">
    <div className="home-create-actions"><button className="primary" disabled={data.busy || !allowRoomCreation} onClick={()=>setPage('create')}>{t('Create a room')}</button><button className="secondary" disabled={data.busy} onClick={()=>setPage('join')}>{t('Join a room')}</button></div>
    {continuing && <section className="card continue-order"><Illustration kind="food"/><div><small>{t('Your current order')}</small><h3>{continuing.session.roomName}</h3><p>{phaseName(continuing.reply.room.phase)}</p></div><button className="secondary" disabled={data.busy} onClick={()=>openRoom(continuing.session.roomId)}>{t('Continue order')} →</button></section>}
    <section aria-label={t('Quick actions')}><h2>{t('Quick actions')}</h2><div className="home-feature-grid">{features.map(feature=><button key={feature.id} data-home-feature={feature.id} className={`home-feature-tile ${feature.tone}`} disabled={data.busy || feature.disabled} onClick={feature.run}><Illustration kind={feature.art}/><strong>{t(feature.title)}</strong></button>)}</div></section>
    <RoomsOverview data={data} openRoom={openRoom} phaseName={phaseName} onViewAll={()=>setPage('rooms')}/>
    <section className="home-spending" aria-label={t('My spending')}><div className="section-title compact"><h2>{t('My spending')}</h2><button type="button" className="link" disabled={data.busy} aria-label={t('View spending')} onClick={()=>setPage('wallet-spending')}>{t('Details')} →</button></div>
      {data.home.spending ? <div className="home-spending-grid">{periods.map(({period,rows})=><button key={period} className={`home-spending-tile ${period==='week' ? 'honey':'mint'}`} disabled={data.busy} onClick={()=>setPage('wallet-spending')}><span>{t(period==='week' ? 'This week':'This month')}</span>{rows.map(row=><strong key={row.currency}>{money(row.total,row.currency)}</strong>)}<small>{t('Net spending')}</small></button>)}</div>:<p className="muted">{t('Refresh to load spending statistics.')}</p>}
    </section>
  </div>;
}
