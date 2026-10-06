import { useEffect, useRef, useState } from 'react';
import { Page } from './FoodRunApp.jsx';
import { guideContent } from './howToUse.js';
import { TogetherArt } from './Brand.jsx';
import './guide.css';
import './guide-theme.css';

export default function HowToUse({ language, onBack }) {
  const content = guideContent[language] || guideContent.en;
  const [screen, setScreen] = useState(null);
  const dialog = useRef(null);
  useEffect(() => { if (screen) dialog.current?.showModal(); else dialog.current?.close(); }, [screen]);
  useEffect(() => {
    const previous = document.title;
    document.title = `${language === 'ar' ? 'إزاي تستخدم إنترفيو' : 'How to use Intrvioo'} | Intrvioo`;
    return () => { document.title = previous; };
  }, [language]);
  useEffect(() => { setScreen(null); }, [language]);
  const navigateSection = (event, id) => {
    if (event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return;
    event.preventDefault();
    const url = new URL(window.location.href); url.hash = id;
    window.history.replaceState(window.history.state, '', url);
    document.getElementById(id)?.scrollIntoView({ block: 'start' });
  };
  const imagePath = section => `/guide/${language === 'ar' ? 'ar' : 'en'}/${section.screen}.webp`;
  return <Page title={language === 'ar' ? 'إزاي تستخدم إنترفيو' : 'How to use Intrvioo'} onBack={onBack}>
    <div className="guide-page" dir={language === 'ar' ? 'rtl' : 'ltr'}>
      <section className="guide-hero">
        <div className="guide-hero-main">
          <div className="guide-hero-copy">
            <p className="eyebrow">{content.eyebrow}</p><h2>{content.title}</h2><p>{content.intro}</p>
            <a className="primary guide-start" href="#guide-account" onClick={event => navigateSection(event, 'guide-account')}>{language === 'ar' ? 'ابدأ خطوة بخطوة' : 'Show me how'}<span aria-hidden="true">↗</span></a>
          </div>
          <TogetherArt />
        </div>
        <div className="guide-path" aria-hidden="true">{(language === 'ar' ? ['ادخل','اختاروا','اطلب','سوّي الحساب'] : ['Join','Select','Order','Settle']).map((label, i) => <span key={label}><b>{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(i + 1)}</b>{label}</span>)}</div>
      </section>
      <section className="guide-roles" aria-labelledby="guide-roles-title"><h2 id="guide-roles-title">{content.quickTitle}</h2><p>{content.quickDescription}</p>
        <div className="guide-role-grid">{content.roles.map((role, i) => <a href={`#guide-${role.target}`} onClick={event => navigateSection(event, `guide-${role.target}`)} key={role.target}><span className="guide-role-icon" aria-hidden="true">{['＋','⌂','✓'][i]}</span><strong>{role.title}</strong><p>{role.detail}</p><span className="guide-role-arrow" aria-hidden="true">↗</span></a>)}</div>
      </section>
      <div className="guide-layout">
        <nav className="guide-toc" aria-label={content.contents}><p className="eyebrow">{content.contents}</p>{content.sections.map(section => <a key={section.id} href={`#guide-${section.id}`} onClick={event => navigateSection(event, `guide-${section.id}`)}>{section.title}</a>)}<a href="#guide-faq" onClick={event => navigateSection(event, 'guide-faq')}>{content.faqTitle}</a></nav>
        <div className="guide-sections">{content.sections.map(section => <section className="card guide-section" id={`guide-${section.id}`} key={section.id} aria-labelledby={`guide-title-${section.id}`}>
          <header><span className="guide-section-badge" aria-hidden="true">{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en', { minimumIntegerDigits: 2 }).format(content.sections.indexOf(section) + 1)}</span><h2 id={`guide-title-${section.id}`}>{section.title}</h2><p>{section.intro}</p></header>
          {section.screen && <figure className="guide-screen"><button type="button" onClick={() => setScreen(section)} aria-label={`${content.zoom}: ${section.title}`}>
            <img src={imagePath(section)} alt={section.alt} loading="lazy" decoding="async" width="1100" height="780" />
            <span className="guide-zoom" aria-hidden="true">⌕ {content.zoom}</span>
          </button><figcaption>{content.example}</figcaption></figure>}
          <h3>{content.stepsTitle}</h3><ol className="guide-steps">{section.steps.map(([action, detail], i) => <li key={action}><span className="guide-step-number" aria-hidden="true">{new Intl.NumberFormat(language === 'ar' ? 'ar-EG' : 'en').format(i + 1)}</span><div><strong>{action}</strong><p>{detail}</p></div></li>)}</ol>
          <aside className="guide-tip"><strong>{content.tip}</strong><p>{section.tip}</p></aside>
        </section>)}
          <section className="card guide-checklist"><h2>{content.checklistTitle}</h2><ul>{content.checklist.map(item => <li key={item}><span aria-hidden="true">✓</span>{item}</li>)}</ul><button type="button" className="primary" onClick={onBack}>{content.back}</button></section>
          <section className="guide-faq" id="guide-faq"><h2>{content.faqTitle}</h2>{content.faqs.map(([question, answer]) => <details className="card" key={question}><summary>{question}</summary><p>{answer}</p></details>)}</section>
        </div>
      </div>
      <dialog ref={dialog} className="guide-lightbox" aria-label={content.zoom} onCancel={() => setScreen(null)} onClose={() => setScreen(null)}>
        <header><strong>{screen?.title}</strong><button type="button" className="secondary" onClick={() => setScreen(null)}>{content.close} ×</button></header>
        {screen && <img src={imagePath(screen)} alt={screen.alt} />}
      </dialog>
    </div>
  </Page>;
}
