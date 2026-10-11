import React from "react";
import { ScrollView, StyleSheet, Text, View } from "react-native";
import { colors, tx } from "../theme";
import { textStyle } from "./Controls";

const phaseSteps:Record<string,number> = {LOBBY:0,PREPARING_SPIN:1,SPINNING:1,ACCEPTING:1,COLLECTING:2,REVIEW:2,PLACED:3,FULFILLED:4,ARCHIVED:5};
export const roomTimelineStep = (phase:string) => phaseSteps[phase] ?? -1;
export default function RoomTimeline({phase,stage,rtl}:{phase:string;stage:string;rtl:boolean}) {
  const current = roomTimelineStep(phase), complete = phase === "ARCHIVED";
  const steps = [tx(rtl,"Join","انضمام"),tx(rtl,"Choose","اختيار"),tx(rtl,"Food","الطلبات"),tx(rtl,"Sent","إرسال"),tx(rtl,"Arrived","وصول"),tx(rtl,"Settle","تسوية")];
  return <View style={styles.card} testID="room-timeline">
    <View style={[styles.status,rtl && {flexDirection:"row-reverse"}]}><View style={[styles.dot,phase === "CANCELLED" && {backgroundColor:colors.danger}]}/><Text accessibilityRole="header" accessibilityLiveRegion="polite" style={[textStyle(rtl,"semibold"),styles.stage]}>{stage}</Text></View>
    <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.steps,rtl && {flexDirection:"row-reverse"}]}>
      {steps.map((label,index) => { const done = current > index || complete, selected = current === index; return <View key={index} style={styles.step} accessibilityLabel={`${label}: ${done ? tx(rtl,"Complete","مكتمل") : selected ? tx(rtl,"Current","المرحلة الحالية") : tx(rtl,"Upcoming","قادم")}`}>
        <View style={[styles.marker,(done || selected) && styles.active]}><Text style={[textStyle(rtl,"semibold"),styles.number,(done || selected) && {color:colors.white}]}>{done ? "✓" : index+1}</Text></View>
        <Text numberOfLines={1} style={[textStyle(rtl,selected ? "bold":"regular"),styles.label,selected && {color:colors.forest}]}>{label}</Text>
      </View>;})}
    </ScrollView>
  </View>;
}
const styles=StyleSheet.create({card:{marginHorizontal:18,marginBottom:10,padding:12,borderRadius:18,backgroundColor:colors.white,borderColor:colors.line,borderWidth:1,gap:10},status:{flexDirection:"row",alignItems:"center",gap:8},dot:{width:8,height:8,borderRadius:4,backgroundColor:colors.forest},stage:{fontSize:14,lineHeight:22},steps:{flexGrow:1,flexDirection:"row",justifyContent:"space-between",gap:6},step:{minWidth:43,alignItems:"center",gap:4},marker:{height:23,width:23,borderRadius:12,backgroundColor:colors.cream,alignItems:"center",justifyContent:"center"},active:{backgroundColor:colors.forest},number:{fontSize:11},label:{fontSize:10,color:colors.muted}});
