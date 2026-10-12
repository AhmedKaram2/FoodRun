import { useLayoutEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import { createPortal } from 'react-dom';
import Icon from './Icon.jsx';
import { getLanguage } from './i18n.js';
const sectionIcon = id => /favorites/.test(id) ? 'heart' : /history/.test(id) ? 'history' : /details/.test(id) ? 'profile' : /notification/.test(id) ? 'bell' : /receiving|wallet|payment|funds/.test(id) ? 'wallet' : /member|group/.test(id) ? 'friends' : /food|menu|restaurant/.test(id) ? 'food' : 'room';
export default function MobilePageLayout({ pageKey, children, alwaysTabs = false, initialSection = '' }) {
  const root = useRef(null), panels = useRef([]);
  const [tabs, setTabs] = useState([]), [selected, setSelected] = useState('');
  const [tabHost,setTabHost] = useState(null);
  useLayoutEffect(() => {
    const host = root.current;
    const collect = () => {
      setTabHost(host.querySelector('[data-page-tabs-slot]'));
      const explicit = [...host.querySelectorAll('[data-mobile-section]')];
      const candidates = explicit.length ? explicit : [...host.querySelectorAll('main .card, main .user-dashboard, main .room-main, main .room-side')];
      const values = candidates.filter(node => !candidates.some(parent => parent !== node && parent.contains(node))).map((node, index) => {
        const title = node.dataset.mobileLabel || node.querySelector('summary,h1,h2,h3,.eyebrow')?.textContent?.trim();
        return title ? { node, id: node.dataset.mobileSection || `section-${index}`, title } : null;
      }).filter(Boolean);
      const activePanels = values.length > 1 ? values : [];
      panels.current.filter(old => !activePanels.some(value => value.node === old.node)).forEach(({node}) => node.removeAttribute("data-mobile-hidden"));
      panels.current = activePanels;
      const grouped = [...new Map(panels.current.map(({id,title}) => [id,{id,title}])).values()];
      if (grouped.some(tab => tab.id === 'room-overview')) {
        const order = ['room-overview','room-food','room-payments','room-members'];
        grouped.sort((a,b) => order.indexOf(a.id) - order.indexOf(b.id));
      }
      setTabs(old => JSON.stringify(old) === JSON.stringify(grouped) ? old : grouped);
      setSelected(old => panels.current.some(value => value.id === old) ? old : grouped.find(tab=>tab.id===initialSection)?.id || grouped[0]?.id || '');
    };
    const activateTarget = event => {
      const shortcut = event.target.closest?.('[data-mobile-target]'); if(shortcut) { if(panels.current.some(panel=>panel.id===shortcut.dataset.mobileTarget))setSelected(shortcut.dataset.mobileTarget); return; }
      const target = event.type === 'invalid' ? event.target : event.target.closest?.('a[href^="#"]');
      const node = event.type === 'invalid' ? target : target && host.querySelector('#' + CSS.escape(target.getAttribute('href').slice(1)));
      const panel = panels.current.find(value => value.node.contains(node));
      if(panel) { if(event.type === 'invalid') flushSync(() => setSelected(panel.id)); else setSelected(panel.id); if(panel.node.tagName === 'DETAILS') panel.node.open = true; let parent = node?.parentElement; while(parent) { if(parent.tagName === 'DETAILS') parent.open = true; parent = parent.parentElement; } }
    };
    collect();
    const observer = new MutationObserver(collect); observer.observe(host, { childList:true, subtree:true, attributes:true, attributeFilter:["data-mobile-label","data-mobile-section"] });
    host.addEventListener('invalid',activateTarget,true); host.addEventListener('click',activateTarget);
    return () => { observer.disconnect(); host.removeEventListener('invalid',activateTarget,true); host.removeEventListener('click',activateTarget); panels.current.forEach(({node}) => node.removeAttribute('data-mobile-hidden')); };
  }, [pageKey]);
  useLayoutEffect(() => { panels.current.forEach(({node,id}) => { node.setAttribute('data-mobile-hidden',String(id !== selected)); }); },[selected,tabs]);
  const roomTabs = tabs.some(tab => tab.id === 'room-overview');
  const navigation=tabs.length>1 && <nav className={`mobile-section-tabs${roomTabs ? ' mobile-room-tabs' : ''}${alwaysTabs ? ' wallet-section-tabs' : ''}`} style={{"--screen-tab-columns":tabs.length>5 ? Math.ceil(tabs.length / Math.ceil(tabs.length/4)) : tabs.length}} role="tablist" aria-label={getLanguage().startsWith('ar') ? 'أقسام الصفحة' : 'Page sections'}>{tabs.map(tab => <button type="button" role="tab" key={tab.id} className={tab.id === selected ? 'active' : ''} aria-selected={tab.id === selected} aria-pressed={tab.id === selected} onClick={() => { setSelected(tab.id); const targets=panels.current.filter(value => value.id === tab.id); targets.forEach(({node}) => { if(node.tagName === 'DETAILS') node.open = true; }); if(!alwaysTabs && !matchMedia('(max-width:640px)').matches) targets[0]?.node.scrollIntoView({block:'start',behavior:'smooth'}); }}><Icon name={sectionIcon(tab.id)} size={16}/><span>{tab.title}</span></button>)}</nav>;
  return <div className="mobile-page-layout" ref={root}>{children}{tabHost && createPortal(navigation,tabHost)}</div>;
}
