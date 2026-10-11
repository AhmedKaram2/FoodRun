import {setUiLanguage,translateUi,languageOptions} from '../src/language';
test('language sheet has all Arabic styles and native controls use the selected style',()=>{
 expect(languageOptions.map(option=>option.value)).toEqual(['en','ar-EG','ar-AE','ar-001']);
 for(const [locale,word] of [['ar-EG','مبلغ تاني'],['ar-AE','مبلغ ثاني'],['ar-001','مبلغ آخر']]){
  setUiLanguage(locale);expect(translateUi('Another amount','fallback')).toBe(word);
  expect(translateUi('Unknown','احتياطي')).toBe('احتياطي');
 }
 setUiLanguage('ar-EG');
});
