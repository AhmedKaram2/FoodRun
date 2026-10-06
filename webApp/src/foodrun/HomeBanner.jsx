import { useState } from 'react';
import { TogetherArt } from './Brand.jsx';

function BannerIcon({ kind }) {
  const paths = {
    people: <><circle cx="12" cy="7" r="3" /><path d="M6 20v-3a6 6 0 0 1 12 0v3M5 5a3 3 0 0 0 0 6M19 5a3 3 0 0 1 0 6M2 19v-2a5 5 0 0 1 3-4M22 19v-2a5 5 0 0 0-3-4" /></>,
    food: <><path d="M5 3v7M8 3v7M11 3v7M5 8a3 3 0 0 0 6 0M8 11v10M19 3c-3 3-4 7-4 10h4M19 3v18" /></>,
    split: <><path d="M12 3v18M3 12h5M16 12h5M4 7h4M16 17h4" /></>,
    arrow: <><path d="M4 12h16M14 6l6 6-6 6" /></>,
  };
  return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[kind]}</svg>;
}

export default function HomeBanner({ rtl = false, allowRoomCreation = true, onCreate, onJoin, onRestaurants }) {
  const [index, setIndex] = useState(0);
  const copy = (en, ar) => rtl ? ar : en;
  const slides = [
    { tone: 'together', eyebrow: copy('GOOD FOOD BRINGS PEOPLE CLOSER', 'الأكل الحلو يقرّبنا'),
      first: copy('Food is better', 'الأكل أحلى'), accent: copy('together.', 'مع بعض.'),
      description: copy('Create a group order, let everyone choose what they love, and split the total without the awkward math.', 'ابدأ طلب جماعي، وخلي كل واحد يختار أكله، واقسموا الحساب بسهولة.'),
      action: copy('Start a Food Run', 'ابدأ طلب جماعي'), onAction: onCreate, disabled: !allowRoomCreation },
    { tone: 'taste', eyebrow: copy('A LITTLE OF EVERYTHING. EVERYONE INCLUDED.', 'كل واحد يختار اللي يحبه'),
      first: copy('Different tastes.', 'أذواق مختلفة.'), accent: copy('Same table.', 'سفرة واحدة.'),
      description: copy('The pizza person. The burger person. The “just a salad” person. Bring your favorites to one shared order.', 'بيتزا، برجر، أو سلطة… كل واحد يختار اللي يحبه، وكل الاختيارات تتجمع في طلب واحد.'),
      action: copy('Explore restaurants', 'شوف المطاعم'), onAction: onRestaurants, disabled: false },
    { tone: 'split', eyebrow: copy('LESS HASSLE. MORE TOGETHERNESS.', 'حساب أسهل. وقت أحلى.'),
      first: copy('Split the bill.', 'اقسموا الحساب.'), accent: copy('Keep the good times.', 'واستمتعوا بالوقت.'),
      description: copy('Clear shares, one place for the order, and more time for the people around your table.', 'كل واحد يعرف حسابه، والطلب كله في مكان واحد، ووقت أكتر لصحابك.'),
      action: copy('Start a Food Run', 'ابدأ طلب جماعي'), onAction: onCreate, disabled: !allowRoomCreation },
  ];
  const slide = slides[index];
  return <section className={`home-banner home-banner-${slide.tone}`} aria-label={copy('Intrvioo highlights', 'مميزات إنترفيوو')} aria-roledescription={copy('carousel', 'عارض شرائح')}>
    <div className="home-banner-orbit" aria-hidden="true" />
    <div className="home-banner-main">
      <div className="home-banner-copy" aria-live="polite" aria-atomic="true">
        <p className="eyebrow"><span className="banner-spark" aria-hidden="true">✦</span>{slide.eyebrow}</p>
        <h2>{slide.first}<br /><span>{slide.accent}</span></h2>
        <p className="home-banner-description">{slide.description}</p>
        <div className="home-banner-actions">
          <button className="primary" disabled={slide.disabled} onClick={slide.onAction}>{slide.action}<BannerIcon kind="arrow" /></button>
          <button className="secondary" onClick={onJoin}>{copy('Join an Order', 'انضم لطلب')}</button>
        </div>
      </div>
      <div className="home-banner-visual">
        {index === 0 ? <TogetherArt /> : index === 1 ? <div className="taste-art" aria-hidden="true"><span>🍕</span><span>🥗</span><span>🍔</span><span className="taste-caption">{copy('Everyone’s invited.', 'الكل معزوم.')}</span></div> : <div className="split-art" aria-hidden="true"><span className="split-circle"><BannerIcon kind="split" /></span><span className="split-person split-person-one"><BannerIcon kind="people" /></span><span className="split-person split-person-two"><BannerIcon kind="food" /></span><span className="split-caption">{copy('Order Together. Split Smarter.', 'اطلبوا مع بعض. اقسموا الحساب بسهولة.')}</span></div>}
        <span className="banner-note">{copy('Good food. Better company.', 'أكل حلو. وصحبة أحلى.')}<span aria-hidden="true"> ♡</span></span>
      </div>
    </div>
    <div className="home-banner-bottom">
      <div className="banner-benefits">
        {[
          ['people',copy('Invite your people','اعزم أصحابك'),copy('Friends, family, your crew','صحابك، أهلك، ومجموعتك')],
          ['food',copy('Choose your food','اختار أكلك'),copy('Everyone picks their favorites','كل واحد يختار اللي يحبه')],
          ['split',copy('Split with ease','اقسموا بسهولة'),copy('Every share, clearly calculated','حساب كل واحد واضح')],
        ].map(([icon,title,detail]) => <div key={icon}><span className={`banner-benefit-icon banner-benefit-${icon}`}><BannerIcon kind={icon} /></span><span><b>{title}</b><small>{detail}</small></span></div>)}
      </div>
      <div className="banner-pager" aria-label={copy('Choose a highlight', 'اختار شريحة')}>
        {slides.map((item, i) => <button key={item.tone} aria-label={`${copy('Show slide', 'عرض الشريحة')} ${i+1}: ${item.first} ${item.accent}`} aria-pressed={index===i} onClick={()=>setIndex(i)}><span /></button>)}
      </div>
    </div>
  </section>;
}
