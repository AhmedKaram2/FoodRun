import test from 'node:test';
import assert from 'node:assert/strict';
import {t,tf,setLanguage,getLanguage,baseLanguage,normalizeLanguage,languageOptions} from '../src/foodrun/i18n.js';
import {restaurantOrderText} from '../src/foodrun/restaurantOrderText.js';
import {localizedGuide,guideContent} from '../src/foodrun/howToUse.js';
import emirati from '../src/foodrun/ar-AE.json' with {type:'json'};
import neutral from '../src/foodrun/ar.json' with {type:'json'};
test('Emirati copy covers existing UI messages with local actions and its own complete guide',()=>{
 for(const key of Object.keys(neutral))assert.ok(emirati[key],key);
 for(const [key,value] of Object.entries(emirati))assert.deepEqual([...key.matchAll(/\{\w+\}/g)].map(match=>match[0]).sort(),[...value.matchAll(/\{\w+\}/g)].map(match=>match[0]).sort(),key);
 for(const [key,value] of [['What would you like?','شو تبا تطلب؟'],['Mark paid','طرّشت المبلغ'],['Half','نص'],['Friends','الربع'],['Confirm received','هيه، استلمت']])assert.equal(t(key,'ar-AE'),value);
 const guide=localizedGuide('ar-AE');
 assert.match(guide.sections.find(section=>section.id==='food').title,/شو تبا/);
 assert.match(guide.sections.find(section=>section.id==='collector').title,/طرّش/);
 assert.match(guide.sections.find(section=>section.id==='selection').title,/منو/);
 assert.notDeepEqual(guide.faqs,localizedGuide('ar-001').faqs);
 assert.notDeepEqual(guide.sections,localizedGuide('ar-001').sections);
});
test('four language choices preserve Egyptian preferences and use distinct Arabic wording',()=>{
  assert.deepEqual(languageOptions.map(option=>option.value),['en','ar-EG','ar-AE','ar-001']);
  assert.equal(normalizeLanguage('ar'),'ar-EG');
  for(const [locale,label] of [['ar-EG','مبلغ تاني'],['ar-AE','مبلغ ثاني'],['ar-001','مبلغ آخر']]){
    setLanguage(locale);assert.equal(getLanguage(),locale);assert.equal(t('Another amount'),label);
    assert.equal(baseLanguage(locale),'ar');assert.equal(tf('{name} was selected',{name:'أحمد'}).includes('أحمد'),true);
    assert.equal(t('A completely unknown restaurant'), 'A completely unknown restaurant');
  }
  setLanguage('en');assert.equal(t('Another amount'),'Another amount');assert.equal(baseLanguage('en'),'en');
});
test('each Arabic help page keeps matching navigation and explains amount-only transfers',()=>{
 for(const locale of ['ar-EG','ar-AE','ar-001']){
   const guide=localizedGuide(locale);
   assert.deepEqual(guide.sections.map(section=>section.id),guideContent.en.sections.map(section=>section.id));
   for(const section of guide.sections) for(const text of [section.title,section.intro,section.tip,...section.steps.flat()]) assert.match(text,/[\u0600-\u06ff]/);
 }
 assert.match(JSON.stringify(localizedGuide('ar-001').sections.find(section=>section.id==='pay')),/تضاف تفاصيل الطلب تلقائياً/);
});
test('all Arabic variants keep Arabic menu names and restaurant quantities',()=>{
 const room={destination:'دبي',deliveryMode:true,restaurant:{menu:{items:[{id:'meal',name:'Meal',nameAr:'وجبة',variants:[],optionGroups:[]}],optionGroups:[]}}};
 const receipts=[{lines:[{itemId:'meal',description:'Meal',quantity:2,optionIds:[],notes:''}]}];
 for(const locale of ['ar-EG','ar-AE','ar-001'])assert.match(restaurantOrderText(room,receipts,locale),/وجبة/);
});
