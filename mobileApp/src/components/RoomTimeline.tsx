import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { colors, tx } from "../theme";
import { textStyle } from "./Controls";

const phaseSteps:Record<string,number> = {LOBBY:0,PREPARING_SPIN:1,SPINNING:1,ACCEPTING:1,COLLECTING:2,REVIEW:2,PLACED:3,FULFILLED:4,ARCHIVED:5};
export const roomTimelineStep = (phase:string) => phaseSteps[phase] ?? -1;
export default function RoomTimeline({phase,stage,rtl}:{phase:string;stage:string;rtl:boolean}) {
  const current = roomTimelineStep(phase), complete = phase === "ARCHIVED";
  const steps = [tx(rtl,"Join","انضمام"),tx(rtl,"Choose","اختيار"),tx(rtl,"Food","الطلبات"),tx(rtl,"Sent","إرسال"),tx(rtl,"Arrived","وصول"),tx(rtl,"Settle","تسوية")];
  return <View style={styles.card} testID="room-timeline">
    <View style={[styles.status,rtl && {flexDirection:"row-reverse"}]}><View style={[styles.dot,phase === "CANCELLED" && {backgroundColor:colors.danger}]}/><Text accessibilityRole="header" accessibilityLiveRegion="polite" style={[textStyle(rtl,"semibold"),styles.stage]}>{stage}</Text></View>
    <View style={[styles.steps,rtl && {flexDirection:"row-reverse"}]}>
      {steps.map((label,index) => { const done = current > index || complete, selected = current === index; return <View key={index} style={styles.step} accessibilityLabel={`${label}: ${done ? tx(rtl,"Complete","مكتمل") : selected ? tx(rtl,"Current","المرحلة الحالية") : tx(rtl,"Upcoming","قادم")}`}>
        <View style={[styles.marker,done && styles.done,selected && styles.active]}/>
      </View>;})}
    </View>
  </View>;
}
const styles=StyleSheet.create({card:{marginHorizontal:18,marginBottom:8,gap:6},status:{flexDirection:"row",alignItems:"center",gap:6},dot:{width:6,height:6,borderRadius:3,backgroundColor:colors.forest},stage:{fontSize:12,lineHeight:20},steps:{flexDirection:"row",gap:4},step:{flex:1},marker:{height:4,borderRadius:2,backgroundColor:colors.line},done:{backgroundColor:colors.green},active:{backgroundColor:colors.forest}});
