import React, { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import type { Snapshot } from "../types";
import { colors, tx } from "../theme";
import { Button, CardView, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import ScrollArea from "./ScrollArea";
import Icon from "./Icon";
import { isOrderCard } from "./RoomFood";

export default function RoomOrderDock({snapshot}:{snapshot:Snapshot}) {
  const [open,setOpen]=useState(false), {rtl,busy}=snapshot.state;
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const order=cards.filter(isOrderCard), lines=order.filter(card=>card.id.startsWith("cart:"));
  const submit=[snapshot.primaryAction,...snapshot.inlineButtons,...snapshot.utilityButtons].find(button=>button?.action==="SUBMIT_CART");
  const estimate=order.find(card=>card.id==="estimate");
  if(!order.length && !submit && !cards.some(card=>card.id.startsWith("menu:")))return null;
  return <>
    <Pressable testID="room-order-shortcut" accessibilityRole="button" accessibilityLabel={tx(rtl,"View my order","عرض طلبي")} accessibilityState={{expanded:open}} onPress={()=>setOpen(true)} style={[styles.dock,rtl && {flexDirection:"row-reverse"}]}>
      <View style={styles.icon}><Icon name="food" color={colors.forest}/></View>
      <View style={{flex:1}}><Text style={[textStyle(rtl,"semibold"),styles.title]}>{tx(rtl,"My order","طلبي")}{lines.length ? ` (${lines.length})` : ""}</Text>
        <Text numberOfLines={1} style={[textStyle(rtl),styles.detail]}>{estimate?.detail || tx(rtl,lines.length ? "View or edit your added food" : "Nothing added yet",lines.length ? "عرض وتعديل الطعام المضاف" : "لم تضف طعاماً بعد")}</Text>
      </View>
      <Text style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"View","عرض")}</Text>
    </Pressable>
    <BottomSheet visible={open} title={tx(rtl,"My order","طلبي")} rtl={rtl} onClose={()=>setOpen(false)}>
      <ScrollArea keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12,paddingBottom:8}}>
        {!lines.length && <Text style={textStyle(rtl)}>{tx(rtl,"No food added yet. Choose from the menu, or continue without food.","لم تضف طعاماً بعد. اختر من القائمة أو تابع بدون طعام.")}</Text>}
        {order.map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy} compactActions/>)}
      </ScrollArea>
      {!!submit && <Button action={submit} rtl={rtl} busy={busy}/>}
    </BottomSheet>
  </>;
}
const styles=StyleSheet.create({dock:{flexDirection:"row",alignItems:"center",gap:10,paddingHorizontal:18,paddingVertical:8,minHeight:62,backgroundColor:colors.white,borderTopWidth:1,borderTopColor:colors.line},icon:{height:38,width:38,borderRadius:12,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},title:{fontSize:14,lineHeight:22},detail:{fontSize:11,lineHeight:18,color:colors.muted},link:{fontSize:13,color:colors.forest}});
