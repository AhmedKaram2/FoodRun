import {setUiLanguage,translateUi,formatUi,languageOptions} from '../src/language';
test('language sheet has all Arabic styles and native controls use the selected style',()=>{
 expect(languageOptions.map(option=>option.value)).toEqual(['en','ar-EG','ar-AE','ar-SD','ar-001']);
 for(const [locale,word] of [['ar-EG','مبلغ تاني'],['ar-AE','مبلغ ثاني'],['ar-SD','مبلغ تاني'],['ar-001','مبلغ آخر']]){
  setUiLanguage(locale);expect(translateUi('Another amount','fallback')).toBe(word);
  expect(translateUi('Unknown','احتياطي')).toBe('احتياطي');
 }
 setUiLanguage('ar-EG');
});
test('Sudanese native Home and payment actions keep names and spacing intact',()=>{
 setUiLanguage('ar-SD');
 expect(formatUi('Hi, {name}',{name:'أمل'})).toBe('أهلاً بيك، أمل');
 expect(translateUi('Create a room','fallback')).toBe('افتح غرفة');
 expect(translateUi('What would you like?','fallback')).toBe('داير تطلب شنو؟');
 expect(translateUi('Mark paid','fallback')).toBe('حوّلت القروش');
 expect(translateUi('Confirm received','fallback')).toBe('استلمت القروش');
 setUiLanguage('en');expect(formatUi('Hi, {name}',{name:'Amal'})).toBe('Hi, Amal');
 setUiLanguage('ar-EG');
});
test('Emirati native controls use local payment and food wording',()=>{
 setUiLanguage('ar-AE');
 expect(translateUi('Half','fallback')).toBe('نص');
 expect(translateUi('Mark paid','fallback')).toBe('طرّشت المبلغ');
 expect(translateUi('Confirm received','fallback')).toBe('هيه، استلمت');
 setUiLanguage('ar-EG');
});
