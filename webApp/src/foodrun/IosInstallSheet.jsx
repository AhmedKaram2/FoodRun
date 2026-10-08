import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';

export default function IosInstallSheet({ onClose, tx }) {
  const title = useId(), dialog = useRef(null);
  useEffect(() => {
    const previousFocus = document.activeElement, overflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden'; dialog.current?.focus();
    const key = event => {
      if (event.key === 'Escape') { event.preventDefault(); onClose(); }
      if (event.key !== 'Tab') return;
      const controls = [...dialog.current.querySelectorAll('button,a[href]')];
      const first = controls[0], last = controls.at(-1);
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', key);
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown', key); if(previousFocus?.isConnected) previousFocus.focus(); };
  }, [onClose]);
  return createPortal(<div className="ios-install-backdrop" onClick={event => { if(event.target === event.currentTarget) onClose(); }}>
    <section ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={title} className="ios-install-sheet">
      <span className="ios-sheet-handle" aria-hidden="true" />
      <div className="section-title"><h2 id={title}>{tx('FoodRun on your iPhone','FoodRun على الآيفون')}</h2><button className="icon-button" aria-label={tx('Close','إغلاق')} onClick={onClose}>×</button></div>
      <p>{tx('Install the web app from Safari. No App Store download or Apple account is needed.','ثبّت تطبيق الويب من Safari، دون تنزيل من App Store أو الحاجة إلى حساب Apple.')}</p>
      <ol className="ios-install-steps">
        <li><strong>{tx('Open intrvioo.com in Safari.','افتح intrvioo.com في Safari.')}</strong></li>
        <li><strong>{tx('Tap Share, then Add to Home Screen.','اضغط مشاركة، ثم إضافة إلى الشاشة الرئيسية.')}</strong><svg aria-hidden="true" viewBox="0 0 24 24"><path d="M12 15V2m-4 4 4-4 4 4M7 10H4v11h16V10h-3"/></svg></li>
        <li><strong>{tx('Keep Open as Web App enabled if shown, then tap Add.','اترك خيار فتح كتطبيق ويب مفعّلاً إذا ظهر، ثم اضغط إضافة.')}</strong></li>
        <li><strong>{tx('Open FoodRun from your Home Screen and sign in with your usual account.','افتح FoodRun من الشاشة الرئيسية، وسجّل الدخول بحسابك المعتاد.')}</strong></li>
      </ol>
      <p className="ios-install-note">{tx('Your rooms and wallet stay in the same account. To receive push notifications on iOS 16.4 or later, enable them inside the installed app and allow the prompt.','تظل غرفك ومحفظتك مرتبطة بالحساب نفسه. لاستقبال الإشعارات على iOS 16.4 أو أحدث، فعّلها من التطبيق المثبّت واسمح بها عند ظهور الطلب.')}</p>
      <button className="primary wide" onClick={onClose}>{tx('Got it','حسناً')}</button>
    </section>
  </div>,document.body);
}
