import arabic from './ar.json' with { type: 'json' };
import egyptian from './ar-EG.json' with { type: 'json' };
import emirati from './ar-AE.json' with { type: 'json' };
import standard from './ar-001.json' with { type: 'json' };
import sudanese from './ar-SD.json' with { type: 'json' };
import languageOptions from './languages.json' with { type: 'json' };
export { languageOptions };
export const isArabic = value => typeof value === 'string' && value.startsWith('ar');
export const baseLanguage = value => isArabic(value) ? 'ar' : 'en';
export const normalizeLanguage = value => value === 'ar' ? 'ar-EG' : languageOptions.some(option => option.value === value) ? value : 'ar-EG';
let language = 'ar-EG';
export function getLanguage() { return language; }
export function setLanguage(value) { language = normalizeLanguage(value); }
export function t(message, selectedLanguage = language) {
  const selected = normalizeLanguage(selectedLanguage);
  if (selected === 'en') return message;
  const neutral = standard[message] || arabic[message];
  return (selected === 'ar-EG' ? egyptian[message] : selected === 'ar-AE' ? emirati[message] : selected === 'ar-SD' ? sudanese[message] : '') || neutral || message;
}
export function tf(message, values, selectedLanguage = language) {
  return t(message, selectedLanguage).replace(/\{(\w+)\}/g, (match, key) => Object.hasOwn(values, key) ? String(values[key]) : match);
}
