import { useLayoutEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { getLanguage } from './i18n.js';
export default function MobilePageLayout({ pageKey, children }) {
  const root = useRef(null), panels = useRef([]);
  const [tabs, setTabs] = useState([]), [selected, setSelected] = useState('');
  useLayoutEffect(() => {
    const host = root.current;
    const collect = () => {
      const explicit = [...host.querySelectorAll('[data-mobile-section]')];
      const candidates = explicit.length ? explicit : [...host.querySelectorAll('main .card, main .user-dashboard, main .room-main, main .room-side')];
      const values = candidates.filter(node => !candidates.some(parent => parent !== node && parent.contains(node))).map((node, index) => {
        const title = node.dataset.mobileLabel || node.querySelector('summary,h1,h2,h3,.eyebrow')?.textContent?.trim();
        return title ? { node, id: node.dataset.mobileSection || `section-${index}`, title } : null;
      }).filter(Boolean);
      const activePanels = values.length > 1 ? values : [];
      panels.current.filter(old => !activePanels.some(value => value.node === old.node)).forEach(({node}) => node.removeAttribute("data-mobile-hidden"));
      panels.current = activePanels;
      setTabs(old => JSON.stringify(old) === JSON.stringify(panels.current.map(({id,title}) => ({id,title}))) ? old : panels.current.map(({id,title}) => ({id,title})));
      setSelected(old => panels.current.some(value => value.id === old) ? old : panels.current[0]?.id || '');
    };
    const activateTarget = event => {
      const shortcut = event.target.closest?.('[data-mobile-target]'); if(shortcut) { setSelected(shortcut.dataset.mobileTarget); return; }
      const target = event.type === 'invalid' ? event.target : event.target.closest?.('a[href^="#"]');
      const node = event.type === 'invalid' ? target : target && document.getElementById(target.getAttribute('href').slice(1));
      const panel = panels.current.find(value => value.node.contains(node));
      if(panel) { if(event.type === 'invalid') flushSync(() => setSelected(panel.id)); else setSelected(panel.id); if(panel.node.tagName === 'DETAILS') panel.node.open = true; let parent = node?.parentElement; while(parent) { if(parent.tagName === 'DETAILS') parent.open = true; parent = parent.parentElement; } }
    };
    collect();
    const observer = new MutationObserver(collect); observer.observe(host, { childList:true, subtree:true });
    host.addEventListener('invalid',activateTarget,true); host.addEventListener('click',activateTarget);
    return () => { observer.disconnect(); host.removeEventListener('invalid',activateTarget,true); host.removeEventListener('click',activateTarget); panels.current.forEach(({node}) => node.removeAttribute('data-mobile-hidden')); };
  }, [pageKey]);
  useLayoutEffect(() => { panels.current.forEach(({node,id}) => { node.setAttribute('data-mobile-hidden',String(id !== selected)); }); },[selected,tabs]);
  return <div className="mobile-page-layout" ref={root}>{tabs.length > 1 && <nav className="mobile-section-tabs" aria-label={getLanguage() === 'ar' ? 'أقسام الصفحة' : 'Page sections'}>{tabs.map(tab => <button type="button" key={tab.id} className={tab.id === selected ? 'active' : ''} aria-pressed={tab.id === selected} onClick={() => { setSelected(tab.id); const panel = panels.current.find(value => value.id === tab.id); if(panel?.node.tagName === 'DETAILS') panel.node.open = true; }}>{tab.title}</button>)}</nav>}{children}</div>;
}
