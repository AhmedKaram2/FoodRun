import { useEffect, useRef } from 'react';
export default function ScrollLoad({ loadKey, enabled = true, onLoad, children }) {
  const marker = useRef(null), requested = useRef(null), latest = useRef({loadKey,enabled,onLoad});
  latest.current={loadKey,enabled,onLoad};
  useEffect(() => {
    const node=marker.current;
    if(!node) return;
    const load = () => {
      const value=latest.current;
      if(!value.enabled || requested.current === value.loadKey) return;
      requested.current=value.loadKey; value.onLoad();
    };
    if(typeof IntersectionObserver !== 'undefined') {
      const observer=new IntersectionObserver(entries => { if(entries.some(entry => entry.isIntersecting)) load(); },{rootMargin:'120px'});
      observer.observe(node); return () => observer.disconnect();
    }
    const check = () => { const rect=node.getBoundingClientRect(); if(rect.width > 0 && rect.bottom >= 0 && rect.top <= window.innerHeight+120) load(); };
    window.addEventListener('scroll',check,true); window.addEventListener('resize',check); check();
    return () => { window.removeEventListener('scroll',check,true); window.removeEventListener('resize',check); };
  },[loadKey,enabled]);
  return <div ref={marker} className="scroll-load" role="status">{children}</div>;
}
