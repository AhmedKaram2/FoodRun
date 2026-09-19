import { useMemo, useState } from 'react';
import {
  createUserWithEmailAndPassword,
  sendPasswordResetEmail,
  signInWithEmailAndPassword,
  signInWithPopup,
  signOut,
  updateProfile,
} from 'firebase/auth';
import { auth, googleProvider } from '../firebase';
import { amount, hubAddress, money, photoData } from './client';
import { useFoodRun } from './useFoodRun';

const phaseLabel = {
  LOBBY: 'Gathering', PREPARING_SPIN: 'Getting ready', SPINNING: 'Selecting',
  ACCEPTING: 'Waiting for acceptance', COLLECTING: 'Collecting food', REVIEW: 'Confirming totals',
  PLACED: 'Order placed', FULFILLED: 'Food arrived', ARCHIVED: 'Complete', CANCELLED: 'Cancelled',
};

const ANDROID_DOWNLOAD_URL = 'https://github.com/AhmedKaram2/FoodRun/releases/download/v1.1/FoodRun-Android-1.1.apk';
const IOS_STORE_URL = import.meta.env.VITE_FOODRUN_IOS_URL?.trim() || '';

function initials(name = '') {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]).join('').toUpperCase() || 'FR';
}

function Avatar({ profile, small = false }) {
  return profile?.photo
    ? <img className={`avatar ${small ? 'small' : ''}`} src={profile.photo} alt="" />
    : <span className={`avatar initials ${small ? 'small' : ''}`}>{initials(profile?.name)}</span>;
}

function AppDownloads({ compact = false }) {
  const [iosHelp, setIosHelp] = useState(false);
  return <section id="app-downloads" className={`app-downloads ${compact ? 'compact' : ''}`}>
    <div className="download-heading">
      <div><p className="eyebrow">FOOD RUN ON YOUR PHONE</p><h2>Take the table with you.</h2></div>
      <p>Use the same account and join Internet Rooms from Android, iPhone, or the web.</p>
    </div>
    <div className="download-grid">
      <article className="download-card">
        <span className="platform-icon android" aria-hidden="true">◆</span>
        <div><strong>Android app</strong><small>Version 1.1 · Android 8+</small></div>
        <a className="primary store-button" href={ANDROID_DOWNLOAD_URL}>Download APK</a>
      </article>
      <article className="download-card">
        <span className="platform-icon apple" aria-hidden="true">●</span>
        <div><strong>iPhone app</strong><small>{IOS_STORE_URL ? 'Available for iPhone and iPad' : 'Install from Safari · iOS 17+'}</small></div>
        {IOS_STORE_URL
          ? <a className="secondary store-button" href={IOS_STORE_URL}>Open App Store</a>
          : <button className="secondary store-button" onClick={() => setIosHelp(value => !value)}>Install on iPhone</button>}
      </article>
    </div>
    {iosHelp && <div className="ios-install" role="status"><b>On iPhone or iPad:</b> open this page in Safari, tap the Share button, then choose <b>Add to Home Screen</b> and <b>Add</b>.</div>}
  </section>;
}

function AuthScreen({ ready }) {
  const [register, setRegister] = useState(false);
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async event => {
    event.preventDefault(); setBusy(true); setMessage('');
    try {
      if (register) {
        const result = await createUserWithEmailAndPassword(auth, email.trim(), password);
        await updateProfile(result.user, { displayName: name.trim() });
      } else await signInWithEmailAndPassword(auth, email.trim(), password);
    } catch (error) { setMessage(error.message.replace(/^Firebase: /, '')); }
    finally { setBusy(false); }
  };
  const reset = async () => {
    if (!email.trim()) return setMessage('Enter your email first.');
    try { await sendPasswordResetEmail(auth, email.trim()); setMessage('Password reset email requested.'); }
    catch (error) { setMessage(error.message.replace(/^Firebase: /, '')); }
  };
  return <main className="auth-shell">
    <section className="auth-story">
      <div className="brand-mark">FR</div>
      <p className="eyebrow">FOOD RUN / TOGETHER</p>
      <h1>Good food.<br />Better together.</h1>
      <p>Pick who will order, collect everyone’s food live, and settle every share without the group-chat chaos.</p>
      <div className="story-steps"><span>01 Join</span><span>02 Select</span><span>03 Order</span><span>04 Settle</span></div>
      <AppDownloads compact />
    </section>
    <section className="auth-card card">
      <p className="eyebrow">YOUR TABLE AWAITS</p>
      <h2>{register ? 'Create your profile' : 'Welcome back'}</h2>
      <p className="muted">Use the same account you already use in Intrvioo.</p>
      <button className="google" disabled={!ready || busy} onClick={() => signInWithPopup(auth, googleProvider).catch(e => setMessage(e.message))}>Continue with Google</button>
      <div className="or"><span />or<span /></div>
      <form onSubmit={submit} className="stack">
        {register && <label>Name<input value={name} onChange={e => setName(e.target.value)} required /></label>}
        <label>Email<input type="email" value={email} onChange={e => setEmail(e.target.value)} required /></label>
        <label>Password<input type="password" minLength="6" value={password} onChange={e => setPassword(e.target.value)} required /></label>
        {message && <p className="form-message" role="alert">{message}</p>}
        <button className="primary" disabled={!ready || busy}>{busy ? 'One moment…' : register ? 'Create account' : 'Sign in'}</button>
      </form>
      {!register && <button className="link" onClick={reset}>Forgot password?</button>}
      <button className="link switch" onClick={() => { setRegister(!register); setMessage(''); }}>{register ? 'Already registered? Sign in' : 'New to Food Run? Create account'}</button>
    </section>
  </main>;
}

function HubScreen({ current, connect, error }) {
  const [address, setAddress] = useState(current || 'https://192.168.1.20:8443');
  const [message, setMessage] = useState('');
  const publicApi = import.meta.env.VITE_FOODRUN_API_URL?.trim().replace(/\/$/, '');
  const submit = event => {
    event.preventDefault();
    try { connect(hubAddress(address)); setMessage(''); }
    catch (e) { setMessage(e.message); }
  };
  return <main className="center-shell"><section className="card setup-card">
    <div className="brand-mark">FR</div><p className="eyebrow">CONNECT YOUR TABLE</p>
    <h1>Where should this room live?</h1>
    <p className="muted">Use a nearby hub to keep live traffic on your local network, or use the internet API so everyone can join from anywhere.</p>
    <div className="mode-cards">
      <button type="button" className="mode-card" onClick={() => setAddress('https://192.168.1.20:8443')}>
        <strong>Nearby hub</strong><span>Lowest cloud use</span>
      </button>
      <button type="button" className="mode-card" disabled={!publicApi} onClick={() => publicApi && connect(publicApi)}>
        <strong>Internet room</strong><span>{publicApi ? 'Join from any network' : 'Set VITE_FOODRUN_API_URL'}</span>
      </button>
    </div>
    <form className="stack" onSubmit={submit}>
      <label>Nearby hub address<input value={address} onChange={e => setAddress(e.target.value)} placeholder="https://192.168.1.20:8443" /></label>
      {(message || error) && <p className="form-message" role="alert">{message || error}</p>}
      <button className="primary">Connect to nearby hub</button>
    </form>
    <p className="fine">Food Run remembers the API used to create each room. Android, iOS, and web use the same command API and event-driven WebSocket. Firebase holds the shared account and asynchronous backup.</p>
  </section></main>;
}

function ProfileScreen({ profile, busy, send, onBack }) {
  const [form, setForm] = useState({
    name: profile?.name || auth.currentUser?.displayName || '', phone: profile?.phone || '', photo: profile?.photo || '',
    discoverable: profile?.discoverable ?? true, method: profile?.payment?.method || 'AANI',
    holder: profile?.payment?.holder || '', bank: profile?.payment?.bank || '', identifier: profile?.payment?.identifier || '',
  });
  const set = (key, value) => setForm(old => ({ ...old, [key]: value }));
  const save = async event => {
    event.preventDefault();
    const payment = form.identifier.trim() ? {
      id: profile?.payment?.id || crypto.randomUUID(), holder: form.holder.trim() || form.name.trim(),
      bank: form.method === 'AANI' ? 'Aani' : form.bank.trim(), identifier: form.identifier.trim(),
      currency: 'AED', version: profile?.payment?.version || 1, method: form.method,
    } : null;
    const reply = await send('IDENTITY', { identity: { action: 'SAVE_PROFILE', profile: {
      userId: '', name: form.name.trim(), phone: form.phone.trim(), photo: form.photo,
      payment, discoverable: form.discoverable,
    } } });
    if (reply) onBack();
  };
  return <Page title="Your profile" subtitle="The details your people need when you’re selected." onBack={onBack}>
    <form className="profile-grid" onSubmit={save}>
      <section className="card profile-photo">
        <Avatar profile={{ name: form.name, photo: form.photo }} />
        <label className="upload">Choose photo<input type="file" accept="image/jpeg,image/png,image/webp" onChange={async e => e.target.files[0] && set('photo', await photoData(e.target.files[0]))} /></label>
        {form.photo && <button type="button" className="link" onClick={() => set('photo', '')}>Remove photo</button>}
      </section>
      <section className="card stack">
        <h3>About you</h3>
        <label>Profile name<input value={form.name} onChange={e => set('name', e.target.value)} required maxLength="160" /></label>
        <label>Phone with country code<input type="tel" value={form.phone} onChange={e => set('phone', e.target.value)} required placeholder="+971 50 123 4567" /></label>
        <label className="check"><input type="checkbox" checked={form.discoverable} onChange={e => set('discoverable', e.target.checked)} /> Let people on this hub invite me</label>
      </section>
      <section className="card stack payment-card">
        <div><p className="eyebrow">GET PAID BACK</p><h3>Receiving details</h3><p className="muted">Shared with room members only after you are selected and choose to share it.</p></div>
        <div className="segmented"><button type="button" className={form.method === 'AANI' ? 'active' : ''} onClick={() => set('method', 'AANI')}>Aani</button><button type="button" className={form.method === 'BANK' ? 'active' : ''} onClick={() => set('method', 'BANK')}>Bank account</button></div>
        <label>Account holder<input value={form.holder} onChange={e => set('holder', e.target.value)} placeholder={form.name || 'Your name'} /></label>
        {form.method === 'BANK' && <label>Bank name<input value={form.bank} onChange={e => set('bank', e.target.value)} /></label>}
        <label>{form.method === 'AANI' ? 'Aani phone number' : 'IBAN / account number'}<input value={form.identifier} onChange={e => set('identifier', e.target.value)} placeholder={form.method === 'AANI' ? '+971501234567' : 'AE…'} /></label>
      </section>
      <div className="form-actions"><button className="primary" disabled={busy}>{busy ? 'Saving…' : 'Save profile'}</button></div>
    </form>
  </Page>;
}

function Page({ title, subtitle, onBack, actions, children }) {
  return <main className="app-shell">
    <header className="topbar"><button className="wordmark" onClick={onBack}><span>FR</span> FOOD RUN</button><div className="top-actions">{actions}</div></header>
    <div className="page-heading">{onBack && <button className="back" onClick={onBack}>← Back</button>}<p className="eyebrow">FOOD RUN / TOGETHER</p><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    {children}
  </main>;
}

function Home({ data, setPage, openRoom }) {
  const { home, rooms, sessions, online } = data;
  const roomCards = Object.values(sessions).map(session => ({ session, reply: rooms[session.roomId] }));
  return <Page title={`Good food, ${home.profile.name?.split(' ')[0] || 'together'}.`} subtitle="Start a table or jump back into today’s order." actions={<><button className="icon-button" aria-label="Notifications" onClick={() => Notification.requestPermission()}>◔</button><button className="profile-chip" onClick={() => setPage('profile')}><Avatar small profile={home.profile} />{home.profile.name || 'Complete profile'}</button></>}>
    <section className="hero card"><div><p className="eyebrow">A TABLE FOR EVERYONE</p><h2>One room. The whole crew.</h2><p>Everyone joins live, the wheel picks who orders, and every item and amount stays together.</p><div className="hero-actions"><button className="primary light" onClick={() => setPage('create')}>Create a room</button><button className="secondary light" onClick={() => setPage('join')}>Join with code</button></div></div><div className="hero-art"><span>🥡</span><span>🍜</span><span>🥗</span></div></section>
    <AppDownloads />
    {home.invitations.length > 0 && <section><div className="section-title"><div><p className="eyebrow">YOU’RE INVITED</p><h2>Join the table</h2></div><span>{home.invitations.length}</span></div><div className="grid two">{home.invitations.map(invite => <article className="card invitation" key={invite.id}><span className="status live">Invitation</span><h3>{invite.roomName}</h3><p>{invite.invitedBy} invited you to order #{invite.orderNumber}.</p><button className="primary" onClick={async () => { const reply = await data.send('IDENTITY', { identity: { action: 'ACCEPT_INVITE', invitationId: invite.id } }); if (reply?.room) openRoom(reply.room.id); }}>Join room</button></article>)}</div></section>}
    <section><div className="section-title"><div><p className="eyebrow">YOUR TABLES</p><h2>Live rooms</h2></div><span>{roomCards.length}</span></div>
      {roomCards.length ? <div className="grid two">{roomCards.map(({ session, reply }) => {
        const room = reply?.room, member = room?.members.find(m => m.id === session.memberId), winner = room?.members.find(m => m.id === (room.payerId || room.spin?.winnerId));
        const receipt = reply?.receipts.find(r => r.memberId === session.memberId);
        return <article className="card room-card" key={session.roomId} onClick={() => openRoom(session.roomId)}><div className="room-card-top"><span className={`status ${online[session.roomId] ? 'live' : ''}`}>{online[session.roomId] ? '● Live' : 'Offline'}</span><span>#{room?.orderNumber || 1}</span></div><h3>{session.roomName}</h3><p>{room ? phaseLabel[room.phase] : 'Connecting…'}</p>{winner && <div className="winner-row"><span className="avatar initials small">{initials(winner.name)}</span><span><b>{winner.name}</b><small>{room.payerId ? ' is ordering' : ' was selected'}</small></span></div>}{receipt && ['REVIEW','PLACED','FULFILLED'].includes(room.phase) && <div className="amount-row"><span>Your total</span><b>{receipt.totalText}</b></div>}<button className="secondary">{room?.phase === 'LOBBY' && !member?.participating ? 'Join this order' : 'Open room'} →</button></article>;
      })}</div> : <div className="card empty"><span>🥢</span><h3>No saved rooms yet</h3><p>Create a room and invite your people, or join with a six-digit code.</p></div>}
    </section>
  </Page>;
}

function CreateRoom({ data, mode, onBack, openRoom }) {
  const profile = data.home.profile;
  const [form, setForm] = useState({ room: '', restaurant: '', phone: '', code: '' });
  const submit = async event => {
    event.preventDefault();
    let reply;
    if (mode === 'join') reply = await data.send('JOIN', { code: form.code.trim(), name: profile.name.trim() });
    else reply = await data.send('CREATE', {
      name: profile.name.trim(), text: form.room.trim(), restaurant: {
        id: crypto.randomUUID(), name: form.restaurant.trim(), branchName: '', currency: 'AED',
        contact: { phoneE164: form.phone.trim() || null, whatsappE164: null, address: null },
        pricing: { taxTreatment: 'INCLUDED', taxRateBasisPoints: null, defaultDeliveryFeeMinor: 0, defaultServiceFeeMinor: 0, minimumOrderMinor: 0 },
        notes: '', menu: { categories: [], optionGroups: [], items: [] }, openOrdering: true,
      }, expectedNames: [], flag: false, destination: '', deadline: 0,
      fees: { delivery: 0, service: 0, discount: 0, proportionalDelivery: false },
    });
    if (reply?.room) openRoom(reply.room.id);
  };
  return <Page title={mode === 'join' ? 'Join your people' : 'Create a room'} subtitle={mode === 'join' ? 'Enter the code shared by your organizer.' : 'Everyone can type their own food. The selected person adds prices later.'} onBack={onBack}>
    <form className="card create-form stack" onSubmit={submit}>
      {mode === 'join' ? <label>Six-digit room code<input inputMode="numeric" pattern="[0-9]{6}" value={form.code} onChange={e => setForm({ ...form, code: e.target.value.replace(/\D/g, '').slice(0, 6) })} required autoFocus /></label> : <>
        <label>Room name<input value={form.room} onChange={e => setForm({ ...form, room: e.target.value })} placeholder="Friday lunch club" required autoFocus /></label>
        <label>Restaurant / order name<input value={form.restaurant} onChange={e => setForm({ ...form, restaurant: e.target.value })} placeholder="Today’s food order" required /></label>
        <label>Restaurant phone<input type="tel" value={form.phone} onChange={e => setForm({ ...form, phone: e.target.value })} placeholder="+971…" required /></label>
      </>}
      <button className="primary" disabled={data.busy || !profile.name || !profile.phone}>{data.busy ? 'Connecting…' : mode === 'join' ? 'Request to join' : 'Create room'}</button>
      {(!profile.name || !profile.phone) && <p className="form-message">Complete your name and phone in your profile first.</p>}
    </form>
  </Page>;
}

function OrderLine({ line, currency, canPrice, onPrice, onRemove }) {
  return <div className="order-line"><div><b>{line.quantity} × {line.description}</b>{line.notes && <small>{line.notes}</small>}</div><div>{line.unitPrice == null ? <span className="status">Awaiting price</span> : <b>{money(line.unitPrice * line.quantity, currency)}</b>}{canPrice && <button className="link" onClick={onPrice}>{line.unitPrice == null ? 'Add price' : 'Change'}</button>}{onRemove && <button className="link danger" onClick={onRemove}>Remove</button>}</div></div>;
}

function RoomScreen({ data, roomId, onBack }) {
  const reply = data.rooms[roomId], session = data.sessions[roomId], room = reply?.room;
  const [item, setItem] = useState({ description: '', quantity: '1', notes: '' });
  const [price, setPrice] = useState({ memberId: '', lineId: '', value: '' });
  const [reference, setReference] = useState('');
  const [inviteOpen, setInviteOpen] = useState(false);
  if (!room || !session) return <Page title="Connecting to room…" onBack={onBack}><div className="card empty"><div className="spinner" /><p>Waiting for the latest room state.</p></div></Page>;
  const me = room.members.find(member => member.id === session.memberId);
  const owner = room.ownerId === me.id, payer = room.payerId === me.id;
  const orderingMembers = room.members.filter(member => member.approved && !member.removed && !member.guest && member.participating);
  const myCart = room.carts.find(cart => cart.memberId === me.id) || { memberId: me.id, revision: 0, lines: [], submitted: false, confirmedQuote: -1 };
  const myReceipt = reply.receipts.find(receipt => receipt.memberId === me.id);
  const total = reply.receipts.reduce((sum, receipt) => sum + receipt.total, 0);
  const winner = room.members.find(member => member.id === room.spin?.winnerId);
  const saveCart = lines => data.send('CART', { cart: { ...myCart, lines }, expectedRevision: myCart.revision }, room.id);
  const addItem = event => { event.preventDefault(); const line = { id: crypto.randomUUID(), itemId: '', quantity: Number(item.quantity), variantId: null, optionIds: [], notes: item.notes.trim(), description: item.description.trim(), unitPrice: null }; saveCart([...myCart.lines, line]).then(reply => reply && setItem({ description: '', quantity: '1', notes: '' })); };
  const selected = winner && room.phase === 'ACCEPTING';
  const ring = room.phase === 'SPINNING' ? { '--rotation': `${room.spin.turns * 360 + Math.max(0, room.spin.memberIds.indexOf(room.spin.winnerId)) * (360 / room.spin.memberIds.length)}deg`, '--duration': `${room.spin.duration}ms` } : {};
  return <Page title={room.name} subtitle={`Order #${room.orderNumber} · ${phaseLabel[room.phase]} · code ${room.code}`} onBack={onBack} actions={<span className={`status ${data.online[room.id] ? 'live' : ''}`}>{data.online[room.id] ? '● Live' : 'Offline'}</span>}>
    <div className="room-layout">
      <section className="room-main stack">
        {['PREPARING_SPIN','SPINNING','ACCEPTING'].includes(room.phase) && <article className="card selection-card">
          {room.phase === 'PREPARING_SPIN' && <><div className="spinner" /><h2>Getting everyone in sync…</h2><p>Every participating device is joining the live selection.</p></>}
          {room.phase === 'SPINNING' && <><div className="wheel" style={ring}>{room.spin.memberIds.map((id, index) => <span style={{ transform: `rotate(${index * 360 / room.spin.memberIds.length}deg) translateY(-92px)` }} key={id}>{initials(room.members.find(m => m.id === id)?.name)}</span>)}</div><h2>Who will order?</h2></>}
          {room.phase === 'ACCEPTING' && <><div className="winner-burst">🎉</div><p className="eyebrow">TODAY’S PICK</p><h2>{winner.name} is ordering!</h2><p>Everyone sees the same result live.</p>{winner.id === me.id && <div className="hero-actions"><button className="primary" onClick={() => data.send('ACCEPT_DUTY', {}, room.id)}>I’ll take care of it</button><button className="secondary" onClick={() => data.send('DECLINE_DUTY', { text: 'Unavailable this time' }, room.id)}>I can’t this time</button></div>}</>}
        </article>}
        {!me.approved && <article className="card notice-card"><h2>Waiting for approval</h2><p>The organizer will approve your request before you can join this order.</p></article>}
        {me.approved && room.phase === 'LOBBY' && <article className="card">
          <div className="section-title"><div><p className="eyebrow">WHO’S IN?</p><h2>Ready for today?</h2></div><span>{orderingMembers.length}</span></div>
          {!me.guest && <div className="hero-actions"><button className={me.participating ? 'secondary' : 'primary'} onClick={() => data.send('PARTICIPATE', { flag: !me.participating }, room.id)}>{me.participating ? 'Skip this order' : 'Join this order'}</button>{me.participating && <button className={me.ready ? 'secondary' : 'primary'} disabled={me.ready} onClick={() => data.send('READY', { flag: true, eligible: true }, room.id)}>{me.ready ? '✓ Ready' : 'I’m ready'}</button>}</div>}
          {owner && <button className="primary wide" disabled={!orderingMembers.length || !orderingMembers.every(m => m.ready)} onClick={() => data.send('PREPARE_SPIN', {}, room.id)}>Spin together</button>}
        </article>}
        {me.approved && room.phase === 'COLLECTING' && <>
          <article className="card"><p className="eyebrow">YOUR FOOD</p><h2>Add items one by one</h2><form className="item-form" onSubmit={addItem}><label>Food item<input value={item.description} onChange={e => setItem({ ...item, description: e.target.value })} placeholder="Chicken shawarma" required /></label><label>Qty<input inputMode="numeric" min="1" max="99" type="number" value={item.quantity} onChange={e => setItem({ ...item, quantity: e.target.value })} required /></label><label className="notes">Notes / extras<input value={item.notes} onChange={e => setItem({ ...item, notes: e.target.value })} placeholder="No onions, extra sauce" /></label><button className="primary">Add item</button></form></article>
          <article className="card"><div className="section-title"><div><p className="eyebrow">MY ORDER</p><h2>{myCart.lines.length ? `${myCart.lines.length} item${myCart.lines.length === 1 ? '' : 's'}` : 'Nothing added yet'}</h2></div>{myReceipt && <b>{myReceipt.totalText}</b>}</div>{myCart.lines.map(line => <OrderLine key={line.id} line={line} currency={room.restaurant.currency} onRemove={() => saveCart(myCart.lines.filter(value => value.id !== line.id))} />)}<button className="primary wide" onClick={() => data.send('SUBMIT_CART', { expectedRevision: myCart.revision }, room.id)}>{myCart.submitted ? 'Submit changes again' : myCart.lines.length ? 'Submit my food order' : 'No food this time'}</button></article>
          {payer && <article className="card"><p className="eyebrow">PRICE THE ORDER</p><h2>Enter each item’s unit price</h2>{room.carts.flatMap(cart => cart.lines.map(line => ({ cart, line }))).map(({ cart, line }) => <OrderLine key={line.id} line={line} currency={room.restaurant.currency} canPrice onPrice={() => setPrice({ memberId: cart.memberId, lineId: line.id, value: line.unitPrice == null ? '' : String(line.unitPrice / 100) })} />)}{price.lineId && <form className="price-form" onSubmit={async e => { e.preventDefault(); const result = await data.send('PRICE_ITEM', { memberId: price.memberId, text: price.lineId, amount: amount(price.value, room.restaurant.currency) }, room.id); if (result) setPrice({ memberId: '', lineId: '', value: '' }); }}><label>Unit price<input autoFocus inputMode="decimal" value={price.value} onChange={e => setPrice({ ...price, value: e.target.value })} /></label><button className="primary">Save price</button></form>}</article>}
          {payer && !room.account && <article className="card notice-card"><h2>Share your receiving details</h2><p>Use the Aani or bank details saved in your profile so people know where to pay.</p><button className="primary" disabled={!data.home.profile.payment} onClick={() => data.send('SHARE_ACCOUNT', { account: { ...data.home.profile.payment, currency: room.restaurant.currency } }, room.id)}>{data.home.profile.payment ? 'Share saved payment method' : 'Add payment details in profile'}</button></article>}
          {owner && <button className="primary wide" disabled={!reply.progress?.canReview} onClick={() => data.send('REVIEW', {}, room.id)}>Review everyone’s totals</button>}
        </>}
        {room.phase === 'REVIEW' && <article className="card"><p className="eyebrow">CHECK YOUR SHARE</p><h2>Confirm totals</h2>{reply.receipts.map(receipt => <div className="receipt" key={receipt.memberId}><div><b>{receipt.name}</b><span>{receipt.lines.map(line => `${line.quantity} × ${line.description}`).join(', ') || 'No food'}</span></div><strong>{receipt.totalText}</strong></div>)}{myCart.confirmedQuote !== room.quoteRevision && <button className="primary wide" onClick={() => data.send('CONFIRM_QUOTE', { expectedRevision: room.quoteRevision }, room.id)}>Confirm my total and recipient</button>}{payer && <form className="place-form" onSubmit={e => { e.preventDefault(); data.send('PLACE', { text: reference }, room.id); }}><label>Restaurant confirmation / ETA<input value={reference} onChange={e => setReference(e.target.value)} placeholder="Confirmed · ready in 30 minutes" required /></label><button className="primary" disabled={!orderingMembers.every(member => room.carts.find(cart => cart.memberId === member.id)?.confirmedQuote === room.quoteRevision)}>Order placed</button></form>}</article>}
        {['PLACED','FULFILLED'].includes(room.phase) && <article className="card"><p className="eyebrow">YOUR BILL</p><h2>{myReceipt?.totalText || money(0, room.restaurant.currency)}</h2>{myReceipt?.lines.map(line => <OrderLine key={line.description} line={{ ...line, unitPrice: line.amount / line.quantity }} currency={room.restaurant.currency} />)}{room.account && <div className="pay-to"><span>Pay to</span><b>{room.account.holder} · {room.account.bank}</b><code>{room.account.identifier}</code></div>}{payer && !room.restaurantPaid && <button className="primary wide" onClick={() => data.send('PAY_RESTAURANT', { amount: total }, room.id)}>Confirm restaurant paid · {money(total, room.restaurant.currency)}</button>}{payer && room.phase === 'PLACED' && <button className="secondary wide" onClick={() => data.send('FULFILL', {}, room.id)}>Food collected / delivered</button>}</article>}
        {['ARCHIVED','CANCELLED'].includes(room.phase) && <article className="card empty"><span>✓</span><h2>{room.phase === 'ARCHIVED' ? 'Order complete' : 'Order cancelled'}</h2><p>The room stays saved for the next meal.</p></article>}
      </section>
      <aside className="room-side stack">
        <section className="card room-code"><span>ROOM CODE</span><strong>{room.code}</strong><button className="secondary" onClick={() => navigator.clipboard.writeText(room.code)}>Copy code</button></section>
        <section className="card"><div className="section-title"><div><p className="eyebrow">AT THE TABLE</p><h3>{room.members.filter(m => !m.removed).length} people</h3></div>{owner && <button className="icon-button" onClick={() => setInviteOpen(!inviteOpen)}>＋</button>}</div>{room.members.filter(m => !m.removed).map(member => <div className="member" key={member.id}><span className="avatar initials small">{initials(member.name)}</span><span><b>{member.name}{member.id === me.id ? ' · You' : ''}</b><small>{member.id === room.ownerId ? 'Organizer' : member.id === room.payerId ? 'Selected to order' : !member.approved ? 'Waiting for approval' : member.ready ? 'Ready' : member.participating ? 'Joined' : 'Skipping'}</small></span>{owner && !member.approved && <button className="link" onClick={() => data.send('APPROVE', { memberId: member.id }, room.id)}>Approve</button>}</div>)}
          {inviteOpen && <div className="invite-list"><p>Invite registered people</p>{data.home.people.map(person => <button className="person-button" key={person.userId} onClick={() => data.send('IDENTITY', { identity: { action: 'INVITE', userId: person.userId } }, room.id)}><span className="avatar initials small">{initials(person.name)}</span>{person.name}<b>Invite</b></button>)}</div>}
        </section>
        {selected && <section className="card winner-mini"><p className="eyebrow">SELECTED</p><h3>{winner.name}</h3><p>Waiting for acceptance before food entry opens.</p></section>}
      </aside>
    </div>
  </Page>;
}

export default function FoodRunApp() {
  const data = useFoodRun();
  const [page, setPage] = useState('home');
  const [roomId, setRoomId] = useState('');
  const openRoom = id => { setRoomId(id); setPage('room'); };
  const alerts = useMemo(() => <>{data.error && <div className="banner error" role="alert">{data.error}{data.hasPending && <button onClick={data.retry}>Retry saved request</button>}</div>}{data.notice && <div className="banner notice">{data.notice}<button aria-label="Close" onClick={() => data.setNotice('')}>×</button></div>}</>, [data.error, data.hasPending, data.notice]);
  if (!data.authReady || (data.user && data.hub && !data.home && !data.error)) return <><div className="splash"><div className="brand-mark">FR</div><div className="spinner" /><p>Setting the table…</p></div>{alerts}</>;
  if (!data.user) return <AuthScreen ready={data.authReady} />;
  if (!data.hub || (!data.home && data.error)) return <><HubScreen current={data.hub} connect={data.connect} error={data.error} />{alerts}</>;
  const home = data.home;
  const profileMissing = !home.profile.name || !home.profile.phone;
  let content;
  if (page === 'downloads') content = <Page title="Get Food Run" subtitle="Install the mobile app and keep your table close." onBack={() => setPage('home')}><AppDownloads /></Page>;
  else if (page === 'profile' || profileMissing) content = <ProfileScreen profile={home.profile} busy={data.busy} send={data.send} onBack={() => setPage('home')} />;
  else if (page === 'create' || page === 'join') content = <CreateRoom data={data} mode={page} onBack={() => setPage('home')} openRoom={openRoom} />;
  else if (page === 'room') content = <RoomScreen data={data} roomId={roomId} onBack={() => setPage('home')} />;
  else content = <Home data={data} setPage={setPage} openRoom={openRoom} />;
  return <>{alerts}{content}<footer><span>Food Run</span><button onClick={() => setPage('downloads')}>Get the apps</button><button onClick={() => setPage('profile')}>Profile</button><button onClick={() => data.connect('')}>Switch room server</button><button onClick={() => signOut(auth)}>Sign out</button></footer></>;
}
