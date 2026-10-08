import React, { useEffect, useState } from "react";
import { Animated, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import type { Action, Snapshot } from "../types";
import { colors, tx } from "../theme";
import { CardView, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import Icon from "./Icon";
import { usePressMotion } from "./Motion";

const paymentActions = /^(DECLARE_TRANSFER|PAY_WITH_WALLET|CONFIRM_TRANSFER|CONFIRM_REFUND|APPROVE_ADJUSTMENT|RECORD_PAYMENT|WALLET_RECORD_PAYMENT|CONFIRM_QUOTE|PAY_RESTAURANT)$/;
const needsPaymentAction=(button:Action)=>button.enabled && (paymentActions.test(button.action) || (button.action==="WALLET_FUNDS_ACTION" && /^(batch|batch-yes)\|/.test(button.value)));
function Shortcut({ icon, label, urgent, count, rtl, onPress }: {icon:string;label:string;urgent?:boolean;count?:number;rtl:boolean;onPress:()=>void}) {
  const motion = usePressMotion();
  return <Animated.View style={motion.style}><Pressable accessibilityRole="button" accessibilityLabel={label} onPress={onPress} onPressIn={motion.onPressIn} onPressOut={motion.onPressOut} style={[styles.shortcut,urgent && styles.urgent,rtl && {flexDirection:"row-reverse"}]}>
    <Icon name={icon} size={20} color={urgent ? colors.white : colors.forest}/>
    <Text style={[textStyle(rtl,"semibold"),styles.label,{color:urgent ? colors.white : colors.forest}]}>{label}</Text>
    {!!count && <View style={styles.badge}><Text style={styles.count}>{count}</Text></View>}
  </Pressable></Animated.View>;
}
export default function RoomAttention({ snapshot, selected, onOpenTab }: {snapshot:Snapshot;selected:number;onOpenTab:(tab:number)=>void}) {
  const {rtl,busy}=snapshot.state, [halvesOpen,setHalvesOpen]=useState(false);
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const halves=cards.filter(card=>card.id.startsWith("half-item:") && card.buttons.some(button=>button.action==="ACCEPT_HALF_ITEM" && button.enabled));
  const paymentCards=cards.filter(card=>card.buttons.some(needsPaymentAction));
  const globalPayment=[snapshot.primaryAction,...snapshot.inlineButtons,...snapshot.utilityButtons].some(button=>button && needsPaymentAction(button));
  const paymentCount=paymentCards.length || (globalPayment ? 1 : 0);
  const hasFood=cards.some(card=>card.id.startsWith("menu:") && card.buttons.some(button=>button.enabled));
  useEffect(()=>{if(!halves.length)setHalvesOpen(false);},[halves.length]);
  if(snapshot.state.page!=="ROOM")return null;
  return <>
    <View pointerEvents="box-none" style={[styles.dock,rtl && {alignItems:"flex-start"}]}>
      {!!halves.length && <Shortcut rtl={rtl} icon="food" urgent label={tx(rtl,"Half available","نصف متاح")} count={halves.length} onPress={()=>setHalvesOpen(true)}/>}
      {!!paymentCount && selected!==2 && <Shortcut rtl={rtl} icon="wallet" urgent label={tx(rtl,"Payment actions","دفعات تنتظرك")} count={paymentCount} onPress={()=>onOpenTab(2)}/>}
      {hasFood && selected!==1 && <Shortcut rtl={rtl} icon="food" label={tx(rtl,"Choose food","اختر طعامك")} onPress={()=>onOpenTab(1)}/>}
    </View>
    <BottomSheet visible={halvesOpen} title={tx(rtl,"Take another half","اختر النصف الآخر")} rtl={rtl} onClose={()=>setHalvesOpen(false)}>
      <Text style={textStyle(rtl)}>{tx(rtl,"Choose an available half. Your share updates for everyone when accepted.","اختر نصفاً متاحاً. تُحدَّث حصتكم لدى الجميع بعد القبول.")}</Text>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12}}>
        {halves.map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy}/>)}
      </ScrollView>
    </BottomSheet>
  </>;
}
const styles=StyleSheet.create({
  dock:{position:"absolute",left:16,right:16,bottom:12,alignItems:"flex-end",gap:8},
  shortcut:{minHeight:46,maxWidth:"100%",flexDirection:"row",alignItems:"center",gap:8,paddingHorizontal:14,paddingVertical:8,borderRadius:24,backgroundColor:colors.mint,borderWidth:1,borderColor:"#CADDD0",elevation:5,shadowColor:"#243A2D",shadowOpacity:0.14,shadowRadius:8,shadowOffset:{width:0,height:3}},
  urgent:{backgroundColor:colors.primary,borderColor:colors.primary},label:{fontSize:13,lineHeight:24},badge:{width:22,height:22,borderRadius:11,backgroundColor:colors.white,alignItems:"center",justifyContent:"center"},count:{fontSize:11,color:colors.primary,fontWeight:"700"},
});
