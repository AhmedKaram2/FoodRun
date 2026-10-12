import React, { useState } from "react";
import { Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import type { Card, Snapshot } from "../types";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { PagedCards, textStyle } from "../components/Controls";
import Illustration from "../components/Illustration";
import ScrollArea from "../components/ScrollArea";

function group(card:Card):string {
  if(card.paymentGroup)return card.paymentGroup;
  if(/payment-history/.test(card.id))return "history";
  if(/wallet-(funds|held:|holding:|balance)/.test(card.id))return "balance";
  if(/wallet-(batch:|top-up:)/.test(card.id) || card.buttons.some(button=>/^(WALLET_CONFIRM|WALLET_REJECT)$/.test(button.action)))return "waiting";
  if(/wallet-group:/.test(card.id))return "pay";
  return "pay";
}
export default function WalletScreen({snapshot}:{snapshot:Snapshot}) {
  const {rtl,busy}=snapshot.state, cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const entries=cards.filter(card=>!/(wallet-summary$|wallet-direction:|payment-history$)/.test(card.id));
  const [selected,setSelected]=useState(()=>entries.some(card=>group(card)==="pay") ? "pay" : entries.some(card=>group(card)==="waiting") ? "waiting" : "balance");
  const tabs=[{id:"balance",title:tx(rtl,"Balance","الرصيد")},{id:"pay",title:tx(rtl,"To pay","للدفع")},{id:"waiting",title:tx(rtl,"Waiting","بانتظار التأكيد")},{id:"receive",title:tx(rtl,"To receive","للاستلام")},{id:"history",title:tx(rtl,"History","السجل")}];
  const visible=entries.filter(card=>group(card)===selected).sort((a,b)=>Number(b.buttons.some(button=>button.primary)) - Number(a.buttons.some(button=>button.primary)));
  return <View style={{flex:1}}>
    <View style={{flexDirection:rtl ? "row-reverse":"row",alignItems:"center",paddingHorizontal:18,gap:8}}><Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.heading,{flex:1,paddingHorizontal:0}]}>{tx(rtl,"Wallet & payments","المحفظة والمدفوعات")}</Text><Illustration kind="wallet" size={50}/></View>
    <View style={[styles.totals,rtl && {flexDirection:"row-reverse"}]}>{[false,true].map(receive=>{const summary=cards.find(card=>card.id.endsWith(`wallet-direction:${receive}`));return summary && <Pressable key={String(receive)} accessibilityRole="button" onPress={()=>setSelected(receive ? "receive":"pay")} style={styles.total}>
      <Text style={[textStyle(rtl),styles.label]}>{tx(rtl,receive ? "To receive":"To pay",receive ? "للاستلام":"للدفع")}</Text><Text style={[textStyle(rtl,"semibold"),styles.amount]}>{summary.badge}</Text>
    </Pressable>;})}</View>
    <ScrollArea horizontal showsHorizontalScrollIndicator={false} style={{flexGrow:0}} contentContainerStyle={[styles.tabs,rtl && {flexDirection:"row-reverse"}]}>{tabs.map(tab=><Pressable key={tab.id} accessibilityRole="tab" accessibilityLabel={tab.title} accessibilityState={{selected:selected===tab.id}} onPress={()=>setSelected(tab.id)} style={[styles.tab,selected===tab.id && styles.active]}><Text style={[textStyle(rtl,"medium"),styles.tabText,selected===tab.id && {color:colors.white}]}>{tab.title}{tab.id==="waiting" && entries.filter(card=>group(card)==="waiting").length ? ` (${entries.filter(card=>group(card)==="waiting").length})` : ""}</Text></Pressable>)}</ScrollArea>
    <ScrollArea key={selected} keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={busy} onRefresh={()=>dispatch("REFRESH")} tintColor={colors.forest}/>} contentContainerStyle={styles.content}>
      {selected==="waiting" && <Text style={[textStyle(rtl),styles.label]}>{tx(rtl,"Sent payments and approvals stay here until they are resolved.","تظهر هنا الدفعات المرسلة والموافقات حتى تتم تسويتها.")}</Text>}
      <PagedCards cards={visible} busy={busy} rtl={rtl}/>
      {!visible.length && <View style={{alignItems:"center",paddingVertical:16}}><Illustration kind="wallet" size={76}/><Text style={[textStyle(rtl),styles.empty]}>{tx(rtl,"Nothing to track here right now.","لا توجد دفعات في هذا القسم حالياً.")}</Text></View>}
    </ScrollArea>
  </View>;
}
const styles=StyleSheet.create({heading:{fontSize:18,lineHeight:28,paddingHorizontal:18,paddingVertical:8},totals:{flexDirection:"row",gap:10,paddingHorizontal:18},total:{flex:1,padding:12,backgroundColor:colors.white,borderRadius:16,borderWidth:1,borderColor:colors.line},label:{fontSize:12,lineHeight:20,color:colors.muted},amount:{fontSize:16,lineHeight:26,color:colors.forest},tabs:{flexDirection:"row",gap:8,padding:18,paddingBottom:10},tab:{paddingHorizontal:12,minHeight:44,justifyContent:"center",backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,borderRadius:14},active:{backgroundColor:colors.forest,borderColor:colors.forest},tabText:{fontSize:12,lineHeight:22},content:{padding:18,paddingTop:8,gap:12,paddingBottom:28},empty:{fontSize:14,lineHeight:24,color:colors.muted,paddingVertical:20}});
