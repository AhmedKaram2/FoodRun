import React from 'react';
import { createRoot } from 'react-dom/client';
import SpendingSummary from '../src/foodrun/SpendingSummary.jsx';
import { setLanguage } from '../src/foodrun/i18n.js';
import '../src/foodrun/foodrun-base.css';
const language=new URLSearchParams(location.search).get('lang') || 'ar-SD';setLanguage(language);document.documentElement.dir=language.startsWith('ar') ? 'rtl' : 'ltr';
const summary={asOf:Date.now(),timeZone:'Asia/Dubai',periods:['week','month'].map((period,index)=>({period,startDate:index ? '2026-10-01':'2026-10-12',endDate:index ? '2026-10-31':'2026-10-18',currency:'AED',paid:index ? 33000:8500,refunds:index ? 1500:1000,total:index ? 31500:7500,meals:[{meal:'breakfast',total:index ? 6000:1500},{meal:'lunch',total:index ? 16000:4000},{meal:'dinner',total:index ? 8500:2000},{meal:'other',total:index ? 1000:0}]}))};
createRoot(document.getElementById('root')).render(<main style={{padding:12,maxWidth:1000,margin:'auto'}}><SpendingSummary summary={summary}/></main>);
