import React, { useEffect, useRef, useState } from 'react';
import { t, tf } from './i18n.js';
import { runningNameIndex } from './wheel.js';

export default function RunningNames({ spin, members, serverTime, active }) {
  const clock = useRef({ server: Number(serverTime) || Date.now(), monotonic: performance.now() });
  const [now, setNow] = useState(clock.current.server);
  const [reduced, setReduced] = useState(() => window.matchMedia('(prefers-reduced-motion: reduce)').matches);
  useEffect(() => {
    const query = window.matchMedia('(prefers-reduced-motion: reduce)');
    const change = () => setReduced(query.matches);
    query.addEventListener?.('change', change); return () => query.removeEventListener?.('change', change);
  }, []);
  useEffect(() => {
    let frame, timer;
    const tick = () => { const value = clock.current.server + performance.now() - clock.current.monotonic; setNow(value); if(value < spin.startAt + spin.duration) frame = requestAnimationFrame(tick); };
    if (!active) setNow(spin.startAt + spin.duration);
    else if(reduced) timer = setTimeout(() => setNow(spin.startAt + spin.duration), Math.max(0, spin.startAt + spin.duration - now));
    else tick();
    return () => { cancelAnimationFrame(frame); clearTimeout(timer); };
  }, [spin.id, active, reduced]);
  const done = !active || now >= spin.startAt + spin.duration;
  const names = spin.memberIds.map(id => members.find(member => member.id === id)?.name || t('Someone'));
  const index = runningNameIndex(spin, now);
  const shown = reduced && !done ? t('Choosing…') : names[index];
  return <div className={`running-names ${done ? 'finished' : ''}`} role="img" aria-label={done ? tf('{name} was selected', { name: names[index] }) : tf('Selecting one of {count} people', { count: names.length })}>
    <span className="name-runner-dot">{done ? '✓' : '✦'}</span>
    <div className="name-runner-window" aria-hidden="true"><div className="name-runner-neighbor">{!reduced && !done ? names[(index + names.length - 1) % names.length] : ''}</div><div className="name-runner-selected" key={shown}>{shown}</div><div className="name-runner-neighbor">{!reduced && !done ? names[(index + 1) % names.length] : ''}</div></div>
    <p>{done ? t('Selected to order') : t('Who will order?')}</p>
  </div>;
}
