import React from 'react';
import { createRoot } from 'react-dom/client';
import FoodRunApp from './foodrun/FoodRunApp.jsx';
import './foodrun/foodrun.css';
createRoot(document.getElementById('root')).render(<React.StrictMode><FoodRunApp /></React.StrictMode>);
if (import.meta.env.PROD && 'serviceWorker' in navigator) {
  window.addEventListener('load', () => { navigator.serviceWorker.register('/sw.js').catch(() => {}); });
}
