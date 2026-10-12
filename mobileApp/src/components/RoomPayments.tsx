import React, { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import type { Card } from "../types";
import { colors, tx } from "../theme";
import { CardView, PagedCards, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import ScrollArea from "./ScrollArea";

export default function RoomPayments({cards,busy,rtl}:{cards:Card[];busy:boolean;rtl:boolean}) {
  const [selected,setSelected]=useState("actions"), [details,setDetails]=useState(false);
  const account=cards.filter(card=>card.id==="account");
  const summary=cards.filter(card=>/^(my-payment-status|restaurant-balance|settlement-next-step|wallet-summary)$/.test(card.id));
  const entries=cards.filter(card=>!account.includes(card) && !summary.includes(card));
  entries.sort((a,b)=>Number(b.buttons.some(button=>button.primary)) - Number(a.buttons.some(button=>button.primary)));
  const group=(card:Card)=>card.id.startsWith("receipt:") ? "receipts" : card.paymentGroup==="history" ? "history" : card.id.startsWith("transfer:") && !card.paymentGroup && !card.buttons.length ? "history" : "actions";
  if(!cards.length)return null;
  return <View style={{gap:12}}>
    {summary.map(card=><CardView key={card.id} card={card} busy={busy} rtl={rtl} compactActions/>)}
    <View style={[styles.tabs,rtl && {flexDirection:"row-reverse"}]}>{[{id:"actions",title:tx(rtl,"Actions","الإجراءات")},{id:"receipts",title:tx(rtl,"Receipts","الإيصالات")},{id:"history",title:tx(rtl,"History","السجل")}].map(tab=><Pressable key={tab.id} accessibilityRole="tab" accessibilityLabel={tab.title} accessibilityState={{selected:selected===tab.id}} onPress={()=>setSelected(tab.id)} style={[styles.tab,selected===tab.id && styles.active]}><Text style={[textStyle(rtl,"medium"),styles.label,selected===tab.id && {color:colors.white}]}>{tab.title}</Text></Pressable>)}</View>
    {!!account.length && <Pressable accessibilityRole="button" onPress={()=>setDetails(true)} style={styles.details}><Text style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"Payment details","بيانات الدفع")}</Text></Pressable>}
    <PagedCards key={selected} cards={entries.filter(card=>group(card)===selected)} rtl={rtl} busy={busy}/>
    {!entries.some(card=>group(card)===selected) && <Text style={[textStyle(rtl),styles.empty]}>{tx(rtl,selected==="actions" ? "No pending payment actions." : "No entries yet.",selected==="actions" ? "لا توجد إجراءات دفع معلقة." : "لا توجد معاملات بعد.")}</Text>}
    {details && <BottomSheet visible title={tx(rtl,"Payment details","بيانات الدفع")} rtl={rtl} onClose={()=>setDetails(false)}><ScrollArea contentContainerStyle={{gap:12}}>{account.map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy}/>)}</ScrollArea></BottomSheet>}
  </View>;
}
const styles=StyleSheet.create({tabs:{flexDirection:"row",gap:6},tab:{flex:1,minHeight:44,paddingHorizontal:8,borderRadius:12,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,alignItems:"center",justifyContent:"center"},active:{backgroundColor:colors.forest,borderColor:colors.forest},label:{fontSize:12,lineHeight:20},details:{minHeight:44,justifyContent:"center"},link:{fontSize:13,color:colors.forest},empty:{fontSize:13,lineHeight:22,color:colors.muted}});
