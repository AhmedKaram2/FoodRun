import React from 'react';
import {createRoot} from 'react-dom/client';
import QuickChoice from '../src/foodrun/QuickChoice.jsx';
import QuickChoiceOverlay from '../src/foodrun/QuickChoiceOverlay.jsx';
import {NotificationCenter} from '../src/foodrun/NotificationCenter.jsx';
import {LiveSelectionWheel} from '../src/foodrun/FoodRunApp.jsx';
import {setLanguage,t} from '../src/foodrun/i18n.js';
import '../src/foodrun/foodrun.css';
const pause=()=>new Promise(resolve=>setTimeout(resolve,180));
const assert=(condition,message)=>{if(!condition)throw Error(message);};
const input=(node,value)=>{Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set.call(node,value);node.dispatchEvent(new Event('input',{bubbles:true}));};
export async function runQuickChoiceAudit(language='en',style='names') {
  setLanguage(language);document.documentElement.lang=language;document.documentElement.dir=language.startsWith('ar') ? 'rtl' : 'ltr';
  const host=document.createElement('div');document.body.replaceChildren(host);const root=createRoot(host),commands=[];
  const home={profile:{userId:'owner',name:'Owner'},friendGroups:[{id:'g',name:'Team',members:[{userId:'a',name:'Alice'},{userId:'b',name:'Bob'}]}],joinedFriendGroups:[]};
  const click=label=>{const button=[...document.querySelectorAll('button')].find(node=>node.textContent.trim()===t(label));assert(button,`Missing ${label}`);button.click();};
  root.render(<QuickChoice home={home} onBack={()=>{}} startQuickChoice={async value=>commands.push(value)}/>);await pause();
  assert(host.querySelector('.primary.wide').disabled,'Empty picker can start');
  click('Choose group');await pause();document.querySelector('.quick-group').click();await pause();
  const checks=document.querySelectorAll('.quick-person input');assert(checks.length===3 && [...checks].every(node=>node.checked),'Group did not include everyone');
  checks[2].click();await pause();assert(!document.querySelectorAll('.quick-person input')[2].checked,'Exclude failed');
  click('Done');await pause();document.querySelector('.quick-purpose').click();await pause();
  input(document.querySelector('input[maxlength="160"]'),'Prize draw');await pause();
  if(style==='wheel'){click('Wheel');await pause();}
  click('Done');await pause();click('Pick someone');await pause();
  assert(commands.length===1 && commands[0].people.length===2 && commands[0].purpose==='Prize draw' && commands[0].style===style,'Custom selection payload wrong');
  assert(!commands[0].people.some(person=>person.id==='user:b'),'Excluded user was submitted');
  const originalNow=Date.now,base=originalNow();let offset=0;Date.now=()=>base+offset;
  const choice={id:'round',ownerId:'owner',ownerName:'Owner',purpose:'Prize draw',style,people:commands[0].people,spin:{id:'round',memberIds:commands[0].people.map(person=>person.id),winnerId:'user:a',startAt:base+3000,duration:5400,turns:7},expiresAt:base+60000,serverOffset:0};
  const data={quickSelection:choice,home,startQuickChoice:async value=>commands.push(value)};
  try {
    root.render(<><main><h1>Another page</h1></main><QuickChoiceOverlay data={data} renderSelection={props=><LiveSelectionWheel {...props}/>}/></>);await pause();
    assert(document.querySelector('.selection-screen') && document.querySelector('.selection-countdown strong')?.textContent==='1','Global countdown missing');
    assert(document.querySelector('.selection-screen .icon-button').disabled,'Countdown can be closed');
    offset=1100;await pause();assert(document.querySelector('.selection-countdown strong')?.textContent==='2','Countdown 2 missing');
    offset=2100;await pause();assert(document.querySelector('.selection-countdown strong')?.textContent==='3','Countdown 3 missing');
    offset=3200;await pause();assert(document.querySelector(style==='names' ? '.running-names' : '.live-wheel'),'Animation missing');
    offset=8700;await pause();assert(document.querySelector('.selection-winner')?.textContent.includes('Alice') && document.querySelectorAll('.selection-confetti span').length===24,'Shared result/celebration missing');
    click('Try again');await pause();assert(commands.at(-1).action==='retry','Retry missing');
    assert(document.documentElement.scrollWidth<=innerWidth+1,'Horizontal overflow');
    const imageChecks={language,style,width:innerWidth,countdown:true,celebration:true,excluded:true};
    click('Close');await pause();assert(commands.at(-1).action==='close','Close missing');
    return imageChecks;
  } finally {Date.now=originalNow;root.unmount();}
}
export async function runNotificationActionsAudit(language='en') {
  setLanguage(language);const host=document.createElement('div');document.body.replaceChildren(host);const root=createRoot(host),commands=[];
  const data={home:{profile:{userId:'owner'},wallet:{topUps:[{id:'top',holderId:'owner',customerId:'a',status:'PENDING',amount:1000,currency:'AED'}],payments:[],batches:[]}},user:{uid:'owner'},busy:false,send:async(kind,fields)=>{commands.push({kind,fields});return {ok:true};}};
  const notifications={configured:false,items:[{id:'n',kind:'wallet_top_up',transferId:'top',title:'Wallet top-up',body:'Alice sent AED 10',actions:[],createdAt:Date.now()}],read:()=>{}};
  root.render(<NotificationCenter data={data} notifications={notifications} onBack={()=>{}} onOpen={()=>{}}/>);await pause();
  const button=[...host.querySelectorAll('button')].find(node=>node.textContent===t('Confirm received'));assert(button,'Inbox approval missing');button.click();await pause();
  assert(commands[0]?.kind==='WALLET_REVIEW_TOP_UP' && commands[0].fields.transferId==='top' && commands[0].fields.flag===true,'Inbox sent wrong action');
  assert(document.documentElement.scrollWidth<=innerWidth+1,'Inbox overflow');root.unmount();return {language,width:innerWidth,directApproval:true};
}
