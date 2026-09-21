import React, { useEffect, useState } from 'react';
import { getRedirectResult, signInWithPopup, signInWithRedirect } from 'firebase/auth';
import { auth, googleProvider } from '../firebase.js';
import { t } from './i18n.js';

export default function NativeGoogleSignIn() {
  const challenge = new URLSearchParams(location.search).get('nativeSignIn') || '';
  const [busy, setBusy] = useState(false), [error, setError] = useState(''), [callback, setCallback] = useState('');
  const complete = async user => {
    setBusy(true);
    try {
      const response = await fetch('https://foodrun-api-q6b9.onrender.com/auth/native/complete', {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, cache: 'no-store', referrerPolicy: 'no-referrer',
        body: JSON.stringify({ challenge, firebaseToken: await user.getIdToken() }),
      });
      const result = await response.json();
      if (!response.ok || !/^[A-Za-z0-9_-]{43}$/.test(result.code || '')) throw Error(result.error || t('Sign-in could not be completed. Start again in the app.'));
      const link = `foodrun://signin?code=${encodeURIComponent(result.code)}&state=${challenge}`;
      setCallback(link); location.href = link;
    } catch (failure) { setError(failure.message); } finally { setBusy(false); }
  };
  useEffect(() => { getRedirectResult(auth).then(result => { if (result?.user) complete(result.user); }).catch(failure => setError(failure.message)); }, []);
  const signIn = async () => {
    setError(''); setBusy(true);
    try { const result = await signInWithPopup(auth, googleProvider); await complete(result.user); }
    catch (failure) {
      if (failure.code === 'auth/popup-blocked') {
        try { await signInWithRedirect(auth, googleProvider); } catch (redirectError) { setError(redirectError.message); }
        return;
      }
      setError(failure.message);
    } finally { setBusy(false); }
  };
  return <main className="auth-shell"><section className="card stack"><p className="eyebrow">Food Run</p><h1>{t('Continue to the mobile app')}</h1><p>{t('Use the same Google account as the website. New accounts are created automatically.')}</p>{!/^[a-f0-9]{64}$/.test(challenge) ? <p role="alert">{t('Start Google sign-in from the Food Run app.')}</p> : callback ? <a className="primary action-link" href={callback}>{t('Return to Food Run')}</a> : <button className="primary" disabled={busy} onClick={signIn}>{busy ? t('Signing in…') : t('Continue with Google')}</button>}{error && <p role="alert" className="form-message">{error}</p>}</section></main>;
}
