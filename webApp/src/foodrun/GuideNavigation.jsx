import { createContext, useContext } from 'react';
import { t } from './i18n.js';

export const GuideNavigation = createContext(null);

export function GuideLink({ className = '' }) {
  const navigation = useContext(GuideNavigation);
  if (navigation?.isOpen) return null;
  return <a className={`secondary guide-link ${className}`} href="?guide=1" aria-label={t('How to use Food Run')}
    onClick={event => { if (navigation && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey && event.button === 0) { event.preventDefault(); navigation.open(); } }}>
    <span className="guide-link-icon" aria-hidden="true">?</span><span>{t('How to use Food Run')}</span>
  </a>;
}
