import { useEffect, useState } from 'react';
import { t } from './i18n.js';

export function blockRemaining(until, serverTime, receivedAt, now = Date.now()) {
  return Math.max(0, Math.ceil((until - (serverTime + now - receivedAt)) / 1000));
}
export function formatBlockTime(seconds) {
  const days = Math.floor(seconds / 86400), hours = Math.floor(seconds % 86400 / 3600);
  const minutes = Math.floor(seconds % 3600 / 60), rest = seconds % 60;
  return `${days ? `${days} ${t('days')} · ` : ''}${[hours, minutes, rest].map(value => String(value).padStart(2,'0')).join(':')}`;
}
export default function BlockedNotice({ block, retry, onBack, inline = false }) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const timer = setInterval(() => setNow(Date.now()), 1000); return () => clearInterval(timer); }, []);
  const remaining = blockRemaining(block.until, block.serverTime, block.receivedAt, now);
  const content = <section className="card stack blocked-account" role="alert"><h1>{t(block.removed ? 'Account removed' : 'Room access blocked')}</h1>
    {block.durationHours > 0 && <p>{t('Blocked duration')} · {block.durationHours} {t('hours')}</p>}
    {block.until > 0 ? <><strong className="block-timer" role="timer" aria-label={t('Time remaining')} dir="ltr">{formatBlockTime(remaining)}</strong><p>{remaining ? `${t('Blocked until')} ${new Date(block.until).toLocaleString()}` : t('The block period ended. Reconnect to continue.')}</p></> : <p>{t('Until unblocked by admin')}</p>}
    {block.reason && <p>{t('Reason')}: {block.reason}</p>}<button className="primary" onClick={retry}>{t('Check access again')}</button>{onBack && <button className="secondary" onClick={onBack}>{t('Back to my account')}</button>}
  </section>;
  return inline ? content : <main className="center-shell">{content}</main>;
}
