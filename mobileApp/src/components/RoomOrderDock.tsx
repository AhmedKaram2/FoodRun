import React, { useState } from "react";
import { Pressable, StyleSheet, Text, View } from "react-native";
import type { Snapshot } from "../types";
import { colors, tx } from "../theme";
import { Button, CardView, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import ScrollArea from "./ScrollArea";
import { isOrderCard } from "./RoomFood";

export default function RoomOrderDock({snapshot}:{snapshot:Snapshot}) {
  const [open,setOpen]=useState(false), {rtl,busy}=snapshot.state;
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const order=cards.filter(isOrderCard), lines=order.filter(card=>card.id.startsWith("cart:"));
  const submit=[snapshot.primaryAction,...snapshot.inlineButtons,...snapshot.utilityButtons].find(button=>button?.action==="SUBMIT_CART");
  const confirm=submit && lines.length ? {...submit,title:tx(rtl,"Confirm order","تأكيد الطلب"),primary:true} : submit;
  const itemSummary=lines.map(card=>card.title).join(" · ");
  const estimate=order.find(card=>card.id==="estimate");
  const confirmed=order.some(card=>card.id==="cart-submitted");
  const viewLabel=confirmed ? tx(rtl,"Modify order","تعديل الطلب") : tx(rtl,"View order details","عرض تفاصيل الطلب");
  if(!order.length && !submit && !cards.some(card=>card.id.startsWith("menu:")))return null;
  return <>
    <View accessibilityElementsHidden={open} importantForAccessibility={open ? "no-hide-descendants" : "auto"} style={[styles.dock,rtl && {flexDirection:"row-reverse"}]}>
      <Pressable testID="room-order-shortcut" accessibilityRole="button" accessibilityLabel={tx(rtl,"View my order","عرض طلبي")} accessibilityHint={itemSummary || undefined} accessibilityState={{expanded:open}} onPress={()=>setOpen(true)} style={styles.summary}>
        <Text style={[textStyle(rtl,"semibold"),styles.title]}>{tx(rtl,"My order","طلبي")}{lines.length ? ` (${lines.length})` : ""}</Text>
        {!!itemSummary && <Text testID="room-order-items" numberOfLines={2} style={[textStyle(rtl),styles.items]}>{itemSummary}</Text>}
        <Text numberOfLines={1} style={[textStyle(rtl),styles.detail]}>{estimate?.detail || tx(rtl,lines.length ? "View or edit your added food" : "Nothing added yet",lines.length ? "عرض وتعديل الطعام المضاف" : "لم تضف طعاماً بعد")}</Text>
      </Pressable>
      <View style={[styles.actions,rtl && {flexDirection:"row-reverse"}]}>
        <Pressable accessibilityRole="button" accessibilityLabel={viewLabel} onPress={()=>setOpen(true)} style={styles.view}>
          <Text style={[textStyle(rtl,"semibold"),styles.link]}>{confirmed ? tx(rtl,"Modify order","تعديل الطلب") : tx(rtl,"View","عرض")}</Text>
        </Pressable>
        {!!confirm && <Button action={confirm} rtl={rtl} busy={busy} compact/>}
      </View>
    </View>
    <BottomSheet visible={open} title={tx(rtl,"My order","طلبي")} rtl={rtl} onClose={()=>setOpen(false)}>
      <ScrollArea keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12,paddingBottom:8}}>
        {!lines.length && <Text style={textStyle(rtl)}>{tx(rtl,"No food added yet. Choose from the menu, or continue without food.","لم تضف طعاماً بعد. اختر من القائمة أو تابع بدون طعام.")}</Text>}
        {order.map(card=><CardView key={card.id} card={card} rtl={rtl} busy={busy} compactActions/>)}
      </ScrollArea>
      {!!confirm && <Button action={confirm} rtl={rtl} busy={busy}/>}
    </BottomSheet>
  </>;
}
const styles=StyleSheet.create({dock:{flexDirection:"row",flexWrap:"wrap",alignItems:"center",gap:8,paddingHorizontal:16,paddingVertical:8,minHeight:62,backgroundColor:colors.white,borderTopWidth:1,borderTopColor:colors.line},summary:{flex:1,minWidth:120,minHeight:44,justifyContent:"center"},actions:{flexDirection:"row",alignItems:"center",gap:6,flexShrink:1},view:{minHeight:44,minWidth:44,justifyContent:"center",alignItems:"center"},title:{fontSize:14,lineHeight:22},items:{fontSize:12,lineHeight:20},detail:{fontSize:11,lineHeight:18,color:colors.muted},link:{fontSize:13,color:colors.forest}});
