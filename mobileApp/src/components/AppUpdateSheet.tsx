import React,{useEffect,useRef,useState} from 'react';
import {AppState,Platform,StyleSheet,Text,View} from 'react-native';
import {native} from '../native';
import {colors,tx} from '../theme';
import BottomSheet from './BottomSheet';
import {Button,action,textStyle} from './Controls';
import Icon from './Icon';
type Update={available:boolean;versionName:string;state:string;progress:number;canInstall:boolean};
export default function AppUpdateSheet({rtl,paused=false}:{rtl:boolean;paused?:boolean}) {
 const [info,setInfo]=useState<Update|null>(null),[dismissed,setDismissed]=useState(false),[busy,setBusy]=useState(false),[error,setError]=useState('');
 const mounted=useRef(true),requested=useRef(false),permission=useRef(false),installing=useRef(false);
 const install=async()=>{
  if(installing.current)return;installing.current=true;setBusy(true);setError('');
  try {const result=JSON.parse(await native.installAppUpdate!());permission.current=result.state==='permission';requested.current=false;if(mounted.current)setInfo(result);}catch{if(mounted.current)setError(tx(rtl,'The update could not be installed. Try again.','تعذر تثبيت التحديث. حاول مرة أخرى.'));}finally{installing.current=false;if(mounted.current)setBusy(false);}
 };
 const receive=async(value:Update)=>{
  if(!mounted.current)return;setInfo(value);
  if(value.state==='ready'&&(requested.current||(permission.current&&value.canInstall))) {permission.current=false;await install();}
 };
 useEffect(()=>{
  mounted.current=true;if(Platform.OS!=='android'||!native.checkAppUpdate)return;
  const check=()=>native.checkAppUpdate!().then(body=>{if(mounted.current)setInfo(JSON.parse(body));}).catch(()=>{});
  check();
  const listener=AppState.addEventListener('change',state=>{if(state==='active'){if(permission.current)native.getAppUpdateStatus!().then(body=>receive(JSON.parse(body))).catch(()=>{});else check();}});
  return()=>{mounted.current=false;listener.remove();};
 },[]);
 useEffect(()=>{
  if(info?.state!=='downloading')return;
  const timer=setInterval(()=>native.getAppUpdateStatus!().then(body=>receive(JSON.parse(body))).catch(()=>{if(mounted.current){requested.current=false;setInfo(current=>current?{...current,state:'error'}:current);}}),1000);
  return()=>clearInterval(timer);
 },[info?.state]);
 const download=async()=>{
  setBusy(true);setError('');requested.current=true;
  try{await receive(JSON.parse(await native.startAppUpdate!()));}catch{requested.current=false;setError(tx(rtl,'The update could not be downloaded. Try again when connected.','تعذر تنزيل التحديث. حاول مرة أخرى عند الاتصال.'));}finally{if(mounted.current)setBusy(false);}
 };
 const close=()=>{requested.current=false;setDismissed(true);};
 if(Platform.OS!=='android'||!info?.available)return null;
 const downloading=info.state==='downloading',ready=['ready','permission','installer'].includes(info.state);
 return <BottomSheet visible={!dismissed&&!paused} title={tx(rtl,'Update FoodRun','تحديث FoodRun')} rtl={rtl} onClose={close}>
  <View style={styles.art}><Icon name="download" size={36} color={colors.forest}/></View>
  <Text style={[textStyle(rtl,'bold'),{fontSize:20}]}>{tx(rtl,'A new version is ready','نسخة جديدة متاحة')} · {info.versionName}</Text>
  <Text style={[textStyle(rtl),{lineHeight:26}]}>{tx(rtl,'Download the update here, then confirm installation with Android.','نزّل التحديث هنا، ثم أكّد التثبيت في أندرويد.')}</Text>
  {downloading&&<View accessibilityRole="progressbar" accessibilityLabel={tx(rtl,'Downloading update','جارٍ تنزيل التحديث')} accessibilityValue={{min:0,max:100,now:Math.round(info.progress*100)}}><View style={styles.track}><View style={[styles.fill,{width:`${Math.round(info.progress*100)}%`}]}/></View><Text style={[textStyle(rtl),{textAlign:'center',marginTop:8}]}>{Math.round(info.progress*100)}%</Text></View>}
  {(error||info.state==='error')&&<Text accessibilityRole="alert" style={[textStyle(rtl),{color:colors.danger}]}>{error||tx(rtl,'Download interrupted. Try again.','توقف التنزيل. حاول مرة أخرى.')}</Text>}
  {!downloading&&<Button rtl={rtl} busy={busy} action={action(tx(rtl,ready?'Install update':'Download update',ready?'تثبيت التحديث':'تنزيل التحديث'),'APP_UPDATE','',true)} onPress={ready?install:download}/>}
  <Button rtl={rtl} busy={busy} action={action(tx(rtl,'Later','لاحقاً'),'UPDATE_LATER')} onPress={close}/>
 </BottomSheet>;
}
const styles=StyleSheet.create({art:{alignItems:'center',padding:20,backgroundColor:colors.mint,borderRadius:24},track:{height:8,borderRadius:4,overflow:'hidden',backgroundColor:colors.line},fill:{height:8,backgroundColor:colors.forest,borderRadius:4}});
