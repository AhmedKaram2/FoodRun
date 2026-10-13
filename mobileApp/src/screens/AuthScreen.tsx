import Illustration from "../components/Illustration";
import React,{useState} from 'react';
import {KeyboardAvoidingView,Platform,Pressable,StyleSheet,Text,View} from 'react-native';
import ScrollArea from '../components/ScrollArea';
import {Button,FieldInput,textStyle} from '../components/Controls';
import {allActions,allFields} from '../guidedFlows';
import {colors,tx} from '../theme';
import type {Snapshot} from '../types';
import {dispatch} from '../native';

/** Returning users see only sign-in. Registration never asks for receiving accounts. */
export default function AuthScreen({snapshot}:{snapshot:Snapshot}) {
 const {rtl,busy}=snapshot.state;
 const [register,setRegister]=useState(false);
 const [passwordLength,setPasswordLength]=useState(0),[error,setError]=useState('');
 const fields=allFields(snapshot),actions=allActions(snapshot);
 const keys=register?['NAME','EMAIL','PASSWORD']:['EMAIL','PASSWORD'];
 const submit=actions.find(value=>value.action===(register?'REGISTER':'SIGN_IN'));
 const google=actions.find(value=>value.action==='GOOGLE_SIGN_IN');
 const reset=actions.find(value=>value.action==='RESET_PASSWORD');
 const complete=passwordLength>=6 && keys.filter(key=>key!=='PASSWORD').every(key=>fields.find(field=>field.key===key)?.value.trim());
 const send=()=>{
  if(!fields.find(field=>field.key==='EMAIL')?.value.trim().includes('@')){setError(tx(rtl,'Use the email you registered with. Your profile name is not your login email.','استخدم البريد الإلكتروني الذي سجلت به. اسم ملفك ليس بريد تسجيل الدخول.'));return;}
  setError('');if(submit && complete && !busy)dispatch(submit.action,submit.value);
 };
 return <KeyboardAvoidingView style={{flex:1}} behavior={Platform.OS==='ios'?'padding':undefined}>
  <ScrollArea keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
   <View style={styles.card}>
    <View style={{flexDirection:rtl ? "row-reverse":"row",alignItems:"center",gap:8}}><Text accessibilityRole="header" style={[textStyle(rtl,'bold'),styles.title,{flex:1}]}>{tx(rtl,register?'Create account':'Welcome back',register?'إنشاء حساب':'مرحباً بعودتك')}</Text><Illustration kind="profile" size={58}/></View>
    <Text style={textStyle(rtl)}>{tx(rtl,register?'Just your name, email and password.':'Sign in to your FoodRun account.',register?'اسمك وبريدك الإلكتروني وكلمة المرور فقط.':'سجّل الدخول إلى حساب FoodRun.')}</Text>
    {google&&<Button action={{...google,primary:true}} rtl={rtl} busy={busy}/>}
    <Text style={[textStyle(rtl),styles.or]}>{tx(rtl,'or use email','أو استخدم البريد الإلكتروني')}</Text>
    <Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,'Use the email from registration. Your profile name is shown to other members.','استخدم بريد التسجيل. يظهر اسم ملفك للأعضاء الآخرين.')}</Text>
    {keys.map(key=>{const field=fields.find(value=>value.key===key);return field?<FieldInput key={key} field={key==='NAME'?{...field,label:tx(rtl,'Your name','اسمك')}:field} rtl={rtl} busy={busy} onEdited={key==='PASSWORD'?value=>setPasswordLength(value.length):undefined}/>:null;})}
    {!!error&&<Text accessibilityRole="alert" style={[textStyle(rtl),{color:colors.danger}]}>{error}</Text>}
    {submit&&<Button action={{...submit,primary:true,enabled:submit.enabled&&complete}} rtl={rtl} busy={busy} onPress={send}/>}
    {!register&&reset&&<Button action={{...reset,primary:false}} rtl={rtl} busy={busy}/>}
    <Pressable accessibilityRole="button" disabled={busy} onPress={()=>{setRegister(value=>!value);setError('');}} style={styles.switch}>
     <Text style={[textStyle(rtl,'semibold'),{color:colors.forest}]}>{tx(rtl,register?'Already have an account? Sign in':'New here? Create account',register?'لديك حساب؟ سجّل الدخول':'مستخدم جديد؟ أنشئ حساباً')}</Text>
    </Pressable>
   </View>
  </ScrollArea>
 </KeyboardAvoidingView>;
}
const styles=StyleSheet.create({content:{padding:18,flexGrow:1,justifyContent:'center'},card:{backgroundColor:colors.white,borderRadius:26,padding:20,gap:14},title:{fontSize:21,lineHeight:32},or:{textAlign:'center',color:colors.muted},hint:{fontSize:12,lineHeight:20,color:colors.muted},switch:{minHeight:48,justifyContent:'center',alignItems:'center'}});
