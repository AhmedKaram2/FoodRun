import { t } from './i18n.js';

export function navigateApp(page) {
  window.dispatchEvent(new CustomEvent('foodrun-open-page', { detail: page }));
}
export default function AppNavigation() {
  return <nav className="app-navigation" aria-label={t('Main navigation')}>
    <button type="button" className="app-navigation-link" onClick={() => navigateApp('home')}>
      <svg aria-hidden="true" viewBox="0 0 24 24"><path d="M3 10 12 3l9 7v10H15v-7H9v7H3Z"/></svg><span>{t('Home')}</span>
    </button>
    <button type="button" className="app-navigation-link" onClick={() => navigateApp('profile')}>
      <svg aria-hidden="true" viewBox="0 0 24 24"><circle cx="12" cy="7" r="4"/><path d="M4 21v-2a8 8 0 0 1 16 0v2"/></svg><span>{t('Profile')}</span>
    </button>
  </nav>;
}
