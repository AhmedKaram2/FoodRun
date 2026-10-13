import React, { useState } from "react";
import { StyleSheet, Text, View } from "react-native";
import type { Card } from "../types";
import { colors, tx } from "../theme";
import { CardView, PagedCards, textStyle } from "./Controls";
import ScreenTabs from "./ScreenTabs";

export default function RoomPayments({cards,busy,rtl}:{cards:Card[];busy:boolean;rtl:boolean}) {
  const [selected,setSelected]=useState("actions");
  const account=cards.filter(card=>card.id==="account");
  const summary=cards.filter(card=>/^(my-payment-status|restaurant-balance|settlement-next-step|wallet-summary)$/.test(card.id));
  const entries=cards.filter(card=>!account.includes(card) && !summary.includes(card));
  entries.sort((a,b)=>Number(b.buttons.some(button=>button.primary)) - Number(a.buttons.some(button=>button.primary)));
  const group=(card:Card)=>card.id.startsWith("receipt:") ? "receipts" : card.paymentGroup==="history" ? "history" : card.id.startsWith("transfer:") && !card.paymentGroup && !card.buttons.length ? "history" : "actions";
  if(!cards.length)return null;
  return <View style={{gap:12}}>
    {!!account.length && <View style={{gap:8}}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"Payment details","بيانات الدفع")}</Text>{account.map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy} compactActions/>)}</View>}
    {summary.map(card=><CardView key={card.id} card={card} busy={busy} rtl={rtl} compactActions/>)}
    <ScreenTabs rtl={rtl} selected={selected} onChange={setSelected} style={{paddingHorizontal:0,paddingBottom:0}} items={[{id:"actions",title:tx(rtl,"Actions","الإجراءات"),icon:"wallet"},{id:"receipts",title:tx(rtl,"Receipts","الإيصالات"),icon:"receipt"},{id:"history",title:tx(rtl,"History","السجل"),icon:"history"}]}/>
    <PagedCards key={selected} cards={entries.filter(card=>group(card)===selected)} rtl={rtl} busy={busy}/>
    {!entries.some(card=>group(card)===selected) && <Text style={[textStyle(rtl),styles.empty]}>{tx(rtl,selected==="actions" ? "No pending payment actions." : "No entries yet.",selected==="actions" ? "لا توجد إجراءات دفع معلقة." : "لا توجد معاملات بعد.")}</Text>}
  </View>;
}
const styles=StyleSheet.create({tabs:{flexDirection:"row",gap:6},tab:{flex:1,minHeight:44,paddingHorizontal:8,borderRadius:12,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,alignItems:"center",justifyContent:"center"},active:{backgroundColor:colors.forest,borderColor:colors.forest},label:{fontSize:12,lineHeight:20},details:{minHeight:44,justifyContent:"center"},link:{fontSize:13,color:colors.forest},empty:{fontSize:13,lineHeight:22,color:colors.muted}});
