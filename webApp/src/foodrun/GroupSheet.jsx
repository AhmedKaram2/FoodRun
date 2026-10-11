import { useEffect, useId, useRef } from 'react';
import { createPortal } from 'react-dom';
export default function GroupSheet({ title, busy, onClose, children, tx, fullScreen = false }) {
  const label = useId(), dialog = useRef(null), close = useRef(onClose), working = useRef(busy);
  close.current = onClose; working.current = busy;
  useEffect(() => {
    const previous = document.activeElement, overflow = document.body.style.overflow;
    if(fullScreen) dialog.current?.showModal();
    document.body.style.overflow = 'hidden'; dialog.current?.focus();
    const key = event => {
      if(!dialog.current.contains(document.activeElement)) return;
      if(event.key === 'Escape' && !working.current) { event.preventDefault(); close.current(); }
      if(event.key !== 'Tab') return;
      const controls = [...dialog.current.querySelectorAll('button:not(:disabled),input:not(:disabled),a[href]')].filter(node => node.getClientRects().length);
      const first = controls[0], last = controls.at(-1);
      if(!first) { event.preventDefault(); return; }
      if(event.shiftKey && (document.activeElement === first || document.activeElement === dialog.current)) { event.preventDefault(); last.focus(); }
      else if(!event.shiftKey && (document.activeElement === last || document.activeElement === dialog.current)) { event.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown',key);
    return () => { document.body.style.overflow = overflow; document.removeEventListener('keydown',key); if(previous?.isConnected) previous.focus(); };
  },[]);
  const Container = fullScreen ? 'dialog' : 'section';
  return createPortal(<div className={`ios-install-backdrop${fullScreen ? " global-selection-backdrop" : ""}`} onClick={event => { if(event.target === event.currentTarget && !busy) onClose(); }}>
    <Container ref={dialog} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby={label} onCancel={event => { event.preventDefault(); if(!busy) onClose(); }} className={`ios-install-sheet group-sheet stack${fullScreen ? " selection-screen" : ""}`}>
      <span className="ios-sheet-handle" aria-hidden="true"/><div className="section-title"><h2 id={label}>{title}</h2><button className="icon-button" disabled={busy} aria-label={tx('Close','إغلاق')} onClick={onClose}>×</button></div>{children}
    </Container>
  </div>,document.body);
}
