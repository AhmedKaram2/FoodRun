import paths from '../../../Branding/Intrvioo/source/icons.json';

export default function Icon({ name, size = 20 }) {
  return <svg className="ui-icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
    {name !== 'more' && <path d={paths[name] || paths.room}/>}
    {name === 'more' && [5,12,19].map(x=><circle key={x} cx={x} cy="12" r="1.7" fill="currentColor" stroke="none"/>)}
    {name === 'friends' && <circle cx="12" cy="7" r="3"/>}
    {name === 'search' && <circle cx="10" cy="10" r="7"/>}
    {name === 'wheel' && <circle cx="12" cy="12" r="10"/>}
    {name === 'wallet' && <circle cx="17" cy="13.5" r=".6" fill="currentColor"/>}
  </svg>;
}
