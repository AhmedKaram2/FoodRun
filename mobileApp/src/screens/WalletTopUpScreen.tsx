import React, {useEffect, useRef, useState} from "react";
import {ActivityIndicator, Keyboard, Pressable, StyleSheet, Text, View} from "react-native";
import type {Snapshot} from "../types";
import {allActions, allCards, allFields} from "../guidedFlows";
import {dispatch, update} from "../native";
import {colors, tx} from "../theme";
import {action, Button, CardView, FieldInput, PagedCards, textStyle} from "../components/Controls";
import ScrollArea from "../components/ScrollArea";
import Icon from "../components/Icon";

export default function WalletTopUpScreen({snapshot,onBackReady}:{snapshot:Snapshot;onBackReady?:(handler:(()=>boolean)|null)=>void}) {
  const {rtl,busy}=snapshot.state, [step,setStep]=useState(0), [error,setError]=useState("");
  const sent=useRef(false), content=useRef<any>(null);
  const fields=allFields(snapshot), cards=allCards(snapshot), actions=allActions(snapshot);
  const field=(key:string)=>fields.find(value=>value.key===key);
  const holder=cards.find(card=>card.id==="wallet-recipient");
  const method=cards.find(card=>card.id===`wallet-method:${field("WALLET_ACCOUNT")?.value}`);
  const search=actions.find(button=>button.action==="WALLET_FUNDS_ACTION" && button.value==="search");
  const refresh=actions.find(button=>button.action==="WALLET_FUNDS_ACTION" && button.value==="methods");
  const submit=actions.find(button=>button.action==="WALLET_FUNDS_ACTION" && button.value==="top-up");
  const retry=actions.find(button=>button.action==="RETRY");
  const titles=[tx(rtl,"Choose wallet holder","اختر حامل أموال المحفظة"),tx(rtl,"Top-up amount","مبلغ الشحن"),tx(rtl,"Payment details","بيانات الدفع"),tx(rtl,"Review & confirm","المراجعة والتأكيد")];
  const move=(next:number)=>{Keyboard.dismiss();setError("");setStep(next);content.current?.scrollTo({y:0,animated:false});};
  const back=()=>{if(step>0){move(step-1);return true;}return false;};
  useEffect(()=>{onBackReady?.(back);return()=>onBackReady?.(null);},[step,onBackReady]);
  useEffect(()=>{if(!busy)sent.current=false;},[busy,snapshot.state.error,snapshot.state.feedback?.id]);
  const validation=(index:number)=>{
    if(index===0 && !holder)return tx(rtl,"Choose a wallet holder before continuing.","اختر حامل أموال المحفظة قبل المتابعة.");
    const amount=(field("WALLET_AMOUNT")?.value || "").trim().replace(/[٠-٩]/g,char=>String("٠١٢٣٤٥٦٧٨٩".indexOf(char))).replace("٫",".");
    if(index===1 && (!/^\d+(\.\d{1,2})?$/.test(amount) || Number(amount)<=0))return tx(rtl,"Enter a positive amount with up to two decimal places.","أدخل مبلغاً أكبر من صفر وبحد أقصى منزلتين عشريتين.");
    if(index===2 && !method)return tx(rtl,"Choose an available receiving method. Refresh if none is shown.","اختر وسيلة استلام متاحة. حدّث البيانات إذا لم تظهر وسيلة.");
    return "";
  };
  const next=()=>{const invalid=validation(step);if(invalid){setError(invalid);return;}move(step+1);};
  const finish=()=>{
    if(busy || sent.current)return;
    if(retry){sent.current=true;dispatch(retry.action,retry.value);return;}
    for(let i=0;i<3;i++){const invalid=validation(i);if(invalid){move(i);setError(invalid);return;}}
    if(submit?.enabled){sent.current=true;dispatch(submit.action,submit.value);}
  };
  const input=(key:string)=>{const value=field(key);return value && <FieldInput key={key} field={value} rtl={rtl} busy={busy}/>;};
  return <View style={{flex:1}}>
    <View style={[styles.heading,rtl && styles.reverse]}><Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"Back","رجوع")} onPress={()=>{if(!busy && !back())dispatch("BACK");}} style={styles.back}><View style={rtl && {transform:[{scaleX:-1}]}}><Icon name="back"/></View></Pressable><Text style={[textStyle(rtl,"bold"),styles.title]}>{tx(rtl,"Charge wallet","شحن المحفظة")}</Text>{busy && <ActivityIndicator color={colors.forest}/>}</View>
    <View style={styles.progress}><View style={[styles.steps,rtl && styles.reverse]}>{titles.map((title,index)=><Pressable key={index} testID={`wallet-step:${index}`} accessibilityRole="button" accessibilityLabel={title} accessibilityState={{selected:index===step,disabled:busy || index>step}} disabled={busy || index>step} onPress={()=>move(index)} style={[styles.marker,index<=step && styles.active]}><Text style={[textStyle(rtl,"semibold"),{color:index<=step ? colors.white : colors.muted}]}>{index<step ? "✓" : index+1}</Text></Pressable>)}</View><Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,`Step ${step+1} of 4`,`الخطوة ${step+1} من ٤`)}</Text></View>
    <ScrollArea ref={content} testID="wallet-top-up-content" keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      <Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.stepTitle]}>{titles[step]}</Text>
      {!!error && <Text accessibilityRole="alert" style={[textStyle(rtl),styles.hint,{color:colors.danger}]}>{error}</Text>}
      {step===0 && <>
        <Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,"Choose the person who will receive and hold your wallet money.","اختر الشخص الذي سيستلم أموال محفظتك ويحتفظ بها.")}</Text>
        {input("WALLET_CURRENCY")}{input("WALLET_SEARCH")}
        {!!search && <Button rtl={rtl} busy={busy} action={search}/>}
        {holder && <CardView card={{...holder,detail:tx(rtl,"Selected wallet holder","حامل أموال المحفظة المختار")}} rtl={rtl} busy={busy}/>}
        <PagedCards cards={cards.filter(card=>/^wallet-(person:|search-)/.test(card.id))} rtl={rtl} busy={busy} pageSize={8}/>
      </>}
      {step===1 && <>
        <Text style={[textStyle(rtl),styles.hint]}>{holder?.title} · {field("WALLET_CURRENCY")?.value}</Text>
        {input("WALLET_AMOUNT")}
        <View style={[styles.steps,rtl && styles.reverse]}>{[50,100,200].map(amount=><Pressable key={amount} accessibilityRole="button" disabled={busy} onPress={()=>update("WALLET_AMOUNT",String(amount))} style={styles.preset}><Text style={[textStyle(rtl,"semibold"),styles.hint]}>{amount}</Text></Pressable>)}</View>
      </>}
      {step===2 && <>
        <Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,"Copy these details and send the amount from your bank app or in cash, then continue.","انسخ البيانات وأرسل المبلغ عبر تطبيق البنك أو نقداً، ثم تابع.")}</Text>
        {input("WALLET_ACCOUNT")}{method && <CardView card={method} rtl={rtl} busy={busy}/>}
        {cards.filter(card=>card.id==="wallet-method-empty").map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy}/>)}
        {!!refresh && <Button action={refresh} rtl={rtl} busy={busy}/>}{input("WALLET_NOTE")}
      </>}
      {step===3 && <>
        {[{label:titles[0],value:holder?.title,index:0},{label:titles[1],value:`${field("WALLET_CURRENCY")?.value} ${field("WALLET_AMOUNT")?.value}`,index:1},{label:titles[2],value:`${method?.title || ""}\n${method?.detail || ""}${field("WALLET_NOTE")?.value ? "\n"+field("WALLET_NOTE")?.value : ""}`,index:2}].map(row=><Pressable key={row.index} testID={`wallet-review-edit:${row.index}`} accessibilityRole="button" disabled={busy} onPress={()=>move(row.index)} style={styles.summary}><Text style={[textStyle(rtl,"semibold"),styles.hint]}>{row.label}</Text><Text style={[textStyle(rtl),styles.hint]}>{row.value}</Text><Text style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"Edit","تعديل")}</Text></Pressable>)}
        <Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,"Confirm only after sending the money. FoodRun records the payment; your wallet is credited when the holder confirms receipt.","أكد بعد إرسال المال فقط. FoodRun يسجل الدفع، ويُضاف الرصيد إلى محفظتك عندما يؤكد حامل الأموال الاستلام.")}</Text>
      </>}
    </ScrollArea>
    <View style={[styles.footer,rtl && styles.reverse]}>{step>0 && <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Back","السابق"),"WALLET_STEP_BACK")} onPress={()=>move(step-1)}/>}<View style={{flex:1}}>{step<3 ? <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Continue","التالي"),"WALLET_STEP_NEXT","",true)} onPress={next}/> : (retry || submit) && <Button rtl={rtl} busy={busy} action={retry || {...submit!,primary:true}} onPress={finish}/>}</View></View>
  </View>;
}
const styles=StyleSheet.create({heading:{flexDirection:"row",alignItems:"center",gap:10,paddingHorizontal:18,paddingVertical:12},reverse:{flexDirection:"row-reverse"},back:{minHeight:44,minWidth:44,justifyContent:"center",alignItems:"center"},title:{flex:1,fontSize:22},progress:{paddingHorizontal:24,gap:8,alignItems:"center"},steps:{flexDirection:"row",gap:12},marker:{width:34,height:34,borderRadius:17,alignItems:"center",justifyContent:"center",backgroundColor:colors.line},active:{backgroundColor:colors.forest},content:{padding:18,gap:14,paddingBottom:30},stepTitle:{fontSize:22,lineHeight:34},hint:{fontSize:13,lineHeight:23},preset:{paddingHorizontal:20,minHeight:44,justifyContent:"center",borderRadius:12,backgroundColor:colors.mint},summary:{padding:14,gap:5,borderRadius:14,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},link:{fontSize:12,color:colors.forest},footer:{flexDirection:"row",padding:14,gap:10,borderTopWidth:1,borderColor:colors.line,backgroundColor:colors.cream}});
