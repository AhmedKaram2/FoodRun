import { useEffect, useState } from 'react';
import { t } from './i18n.js';

export default function FeedbackBanner({ feedback, onDismiss, retry, busy }) {
  const [hovered, setHovered] = useState(false), [focused, setFocused] = useState(false);
  useEffect(() => {
    if (!feedback || hovered || focused) return;
    const timer = setTimeout(() => onDismiss(feedback.id), feedback.duration);
    return () => clearTimeout(timer);
  }, [feedback?.id, hovered, focused, onDismiss]);
  if (!feedback) return null;
  return <div className={`feedback-banner ${feedback.isError ? 'feedback-error' : 'feedback-success'}`} role={feedback.isError ? 'alert' : 'status'}
    onMouseEnter={() => setHovered(true)} onMouseLeave={() => setHovered(false)} onFocus={() => setFocused(true)}
    onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setFocused(false); }}>
    <span className="feedback-icon" aria-hidden="true">{feedback.isError ? '!' : '✓'}</span>
    <div className="feedback-body"><strong>{t(feedback.isError ? 'Action failed' : 'Done')}</strong><p>{feedback.message}</p>
      {feedback.isError && retry && <button type="button" className="feedback-retry" disabled={busy} onClick={retry}>{t('Retry saved request')}</button>}
    </div>
    <button type="button" className="feedback-close" aria-label={t('Close')} onClick={() => onDismiss(feedback.id)}>×</button>
  </div>;
}
