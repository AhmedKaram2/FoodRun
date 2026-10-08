import { useState } from 'react';
import { t } from './i18n.js';
import { dismissWalletAnnouncement, walletAnnouncementSeen } from './walletAnnouncement.js';
import './walletAnnouncement.css';

export default function WalletAnnouncement({ userId, onOpen }) {
  const [dismissedUser, setDismissedUser] = useState('');
  if (!userId || dismissedUser === userId || walletAnnouncementSeen(userId)) return null;
  const dismiss = () => { dismissWalletAnnouncement(userId); setDismissedUser(userId); };
  return <section className="card wallet-announcement" aria-labelledby="wallet-announcement-title">
    <span className="wallet-announcement-icon" aria-hidden="true">↔</span>
    <div className="stack"><p className="eyebrow">{t('NEW · YOUR WALLET')}</p>
      <h2 id="wallet-announcement-title">{t('Your wallet is here!')}</h2>
      <p>{t("Keep money with a trusted wallet holder and use your balance to pay for order orders.")}</p>
      <ol><li>{t('Choose a wallet holder and request a top-up.')}</li><li>{t('Transfer the money outside Intrvioo. Your balance updates after the holder confirms receipt.')}</li><li>{t('Choose Pay with wallet when paying your share.')}</li></ol>
      <div className="hero-actions"><button className="primary" type="button" onClick={() => { dismiss(); onOpen(); }}>{t('Open wallet')}</button><button className="secondary" type="button" onClick={dismiss}>{t('Got it')}</button></div>
    </div>
  </section>;
}
