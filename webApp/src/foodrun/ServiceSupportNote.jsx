import { t } from './i18n.js';
import './walletAnnouncement.css';

export const serviceSupportMessage = 'Payments for paid features and subscriptions help renew our servers and improve the website service.';
export default function ServiceSupportNote() {
  return <p className="service-support-note">{t(serviceSupportMessage)}</p>;
}
