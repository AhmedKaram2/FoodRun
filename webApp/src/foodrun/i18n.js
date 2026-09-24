import arabic from './ar.json' with { type: 'json' };
import egyptian from './ar-EG.json' with { type: 'json' };
let language = 'en';
export function getLanguage() { return language; }
export function setLanguage(value) { language = value === 'ar' ? 'ar' : 'en'; }
export function t(message, selectedLanguage = language) { return selectedLanguage === 'ar' ? egyptian[message] || arabic[message] || message : message; }
export function tf(message, values, selectedLanguage = language) {
  return t(message, selectedLanguage).replace(/\{(\w+)\}/g, (match, key) => Object.hasOwn(values, key) ? String(values[key]) : match);
}
