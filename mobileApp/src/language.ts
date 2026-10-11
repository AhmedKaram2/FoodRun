import arabic from '../../webApp/src/foodrun/ar.json';
import egyptian from '../../webApp/src/foodrun/ar-EG.json';
import emirati from '../../webApp/src/foodrun/ar-AE.json';
import standard from '../../webApp/src/foodrun/ar-001.json';
import options from '../../webApp/src/foodrun/languages.json';

export const languageOptions = options;
let language = 'ar-EG';
export function setUiLanguage(value: string) { language = value === 'ar' ? 'ar-EG' : value; }
export function translateUi(en: string, fallback: string) {
  const dictionary: Record<string,string> = language === 'ar-AE' ? emirati : language === 'ar-001' ? standard : egyptian;
  return dictionary[en] || (standard as Record<string,string>)[en] || (arabic as Record<string,string>)[en] || fallback;
}
