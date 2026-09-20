import arabic from './ar.json' with { type: 'json' };
let language = 'en';
export function setLanguage(value) { language = value === 'ar' ? 'ar' : 'en'; }
export function t(message, selectedLanguage = language) { return selectedLanguage === 'ar' ? arabic[message] || message : message; }
