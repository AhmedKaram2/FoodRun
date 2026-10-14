import Illustration from './Illustration.jsx';
export default function SectionHero({title,detail,kind='room',badge}) {
  return <header className="section-hero" data-section-hero={kind}><span className="section-hero-art"><Illustration kind={kind}/></span><div><h3>{title}</h3>{detail && <p>{detail}</p>}</div>{badge !== undefined && <strong>{badge}</strong>}</header>;
}
