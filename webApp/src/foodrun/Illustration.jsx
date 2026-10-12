import drawings from '../../../Branding/Intrvioo/source/illustrations.json';

export default function Illustration({ kind = 'room', className = '' }) {
  return <img className={`screen-illustration ${className}`} src={`data:image/svg+xml,${encodeURIComponent(drawings[kind] || drawings.room)}`} width="64" height="54" alt="" aria-hidden="true" />;
}
