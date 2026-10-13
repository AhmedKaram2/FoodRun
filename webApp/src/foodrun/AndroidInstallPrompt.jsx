import { useEffect, useId, useState } from 'react';

const SEEN_KEY = 'foodrun-android-install-prompt-v1';
let shownThisSession = false;

export default function AndroidInstallPrompt({ downloadUrl, tx }) {
  const title = useId();
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    const agent = navigator.userAgent;
    if (!/Android/i.test(agent) || /\bwv\b/i.test(agent) || window.matchMedia('(display-mode: standalone)').matches || new URLSearchParams(window.location.search).has('nativeSignIn') || shownThisSession) return;
    try { if (localStorage.getItem(SEEN_KEY)) return; } catch { /* Session memory still prevents repeats when storage is unavailable. */ }
    let timer;
    const show = () => {
      if (shownThisSession) return;
      try { if (localStorage.getItem(SEEN_KEY)) return; } catch { /* Use session memory if storage is unavailable. */ }
      if (document.hidden || document.querySelector('[role="dialog"], dialog[open]')) {
        timer = setTimeout(show, 1500);
        return;
      }
      shownThisSession = true;
      try { localStorage.setItem(SEEN_KEY, 'seen'); } catch { /* The prompt remains optional without persistent storage. */ }
      setVisible(true);
    };
    timer = setTimeout(show, 2500);
    return () => clearTimeout(timer);
  }, []);

  if (!visible) return null;
  return <aside className="android-install-prompt" aria-labelledby={title} aria-live="polite">
    <div><strong id={title}>{tx('Get the FoodRun app', 'تحميل تطبيق FoodRun')}</strong><p>{tx('Keep your rooms close on Android.', 'تابع غرفك بسهولة على أندرويد.')}</p></div>
    <a className="primary" href={downloadUrl} onClick={() => setVisible(false)}>{tx('Download APK', 'تنزيل تطبيق أندرويد')}</a>
    <button type="button" className="icon-button" aria-label={tx('Close', 'إغلاق')} onClick={() => setVisible(false)}>×</button>
  </aside>;
}
