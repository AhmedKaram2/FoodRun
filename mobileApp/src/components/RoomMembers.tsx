import ScrollArea, { ScrollLoad } from "./ScrollArea";
import React, { useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, useWindowDimensions, View } from "react-native";
import type { Card } from "../types";
import { colors, tx } from "../theme";
import { CardView, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import SectionHero from "./SectionHero";
export default function RoomMembers({ cards, busy, rtl }: {cards:Card[];busy:boolean;rtl:boolean}) {
  const [selected,setSelected] = useState(""), [limit,setLimit] = useState(12), width = useWindowDimensions().width;
  const columns = width >= 600 ? 3 : 2, size = columns * 6;
  const member = cards.find(card => card.id === selected);
  return <View style={{gap:12}}><SectionHero title={tx(rtl,"Members","الأعضاء")} detail={tx(rtl,"Tap a member to see their order and status.","اضغط على العضو لعرض طلبه وحالته.")} kind="friends" rtl={rtl} badge={String(cards.length)}/><View style={[styles.grid,rtl && {flexDirection:"row-reverse"}]}>
    {cards.slice(0,limit).map(card => <Pressable key={card.id} testID={`compact:${card.id}`} accessibilityRole="button" accessibilityLabel={card.title} onPress={() => setSelected(card.id)} style={[styles.member,{width:columns === 3 ? "31%" : "48%"}]}>
      <View style={styles.avatar}><Text style={[textStyle(rtl,"bold"),{fontSize:18}]}>{Array.from(card.title)[0]}</Text></View>
      <Text numberOfLines={2} style={[textStyle(rtl,"semibold"),styles.name]}>{card.title}</Text>
      <Text numberOfLines={2} style={[textStyle(rtl),styles.detail]}>{card.badge}</Text>
      {!!card.detail && <Text numberOfLines={1} style={[textStyle(rtl),styles.detail]}>{card.detail}</Text>}
      {!!card.buttons.length && <Text style={[textStyle(rtl),styles.link]}>{tx(rtl,"Details & actions","التفاصيل والإجراءات")}</Text>}
    </Pressable>)}
  </View>{cards.length > limit && <ScrollLoad loadKey={limit} onLoad={() => setLimit(value => value+size)}><Text style={[textStyle(rtl),styles.detail]}>{tx(rtl,"Scroll for more members","مرّر لعرض أعضاء آخرين")}</Text></ScrollLoad>}
    <BottomSheet visible={!!member} title={member?.title || ""} rtl={rtl} onClose={() => setSelected("")}><ScrollArea>{member && <CardView card={member} busy={busy} rtl={rtl}/>}</ScrollArea></BottomSheet>
  </View>;
}
const styles=StyleSheet.create({
  grid:{flexDirection:"row",flexWrap:"wrap",justifyContent:"space-between",gap:10},member:{padding:12,borderRadius:18,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white,gap:6},
  avatar:{height:32,width:32,borderRadius:11,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},name:{fontSize:14,lineHeight:22},detail:{fontSize:11,lineHeight:18,color:colors.muted},link:{fontSize:11,lineHeight:18,color:colors.forest},pagination:{flexDirection:"row",alignItems:"center",justifyContent:"space-between"},
});
