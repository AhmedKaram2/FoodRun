// Keep the name left-to-right in Arabic too; application content follows its own language.
export function BrandMark() {
  return <img className="brand-mark" src="/branding/intrvioo-symbol.svg" alt="" width="50" height="58" />;
}

export function BrandLogo({ className = '' }) {
  return <span className={`brand-logo ${className}`} dir="ltr" aria-label="Intrvioo">
    <BrandMark /><span>Intrvi<span className="brand-logo-accent">oo</span></span>
  </span>;
}

export function TogetherArt() {
  // The supplied banner contains English text and a painted CTA. Display only the
  // illustration; translated copy and actionable buttons are rendered by the screen.
  return <div className="together-art" aria-hidden="true" />;
}
