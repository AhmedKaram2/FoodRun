import React from "react";
import { StyleSheet, Text, View } from "react-native";
import { colors } from "../theme";
import { textStyle } from "./Controls";
import Illustration, { type IllustrationKind } from "./Illustration";

export default function SectionHero({title,detail,kind,rtl,badge}:{title:string;detail?:string;kind:IllustrationKind;rtl:boolean;badge?:string}) {
 return <View style={[styles.card,rtl && {flexDirection:"row-reverse"}]}><View style={styles.art}><Illustration kind={kind} size={44}/></View><View style={{flex:1,minWidth:0}}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.title]}>{title}</Text>{!!detail && <Text style={[textStyle(rtl),styles.detail]}>{detail}</Text>}</View>{!!badge && <Text style={[textStyle(rtl,"bold"),styles.badge]}>{badge}</Text>}</View>;
}
const styles=StyleSheet.create({card:{flexDirection:"row",alignItems:"center",gap:10,padding:12,borderRadius:18,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},art:{width:48,height:48,borderRadius:14,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},title:{fontSize:14,lineHeight:23},detail:{fontSize:11,lineHeight:19,color:colors.muted},badge:{fontSize:17,lineHeight:26,color:colors.forest}});
