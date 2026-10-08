import React from "react";
import { Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import type { Snapshot } from "../types";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { Button, CardView, FieldInput, textStyle } from "./Controls";
import BottomSheet from "./BottomSheet";
import Icon from "./Icon";

export default function SelectionScreen({snapshot,roomTitle}:{snapshot:Snapshot;roomTitle?:string}) {
  const {rtl,busy}=snapshot.state;
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const choices=snapshot.inlineButtons.filter(button=>button.action==="TOGGLE_OPTION");
  const actions=[...snapshot.inlineButtons,...snapshot.utilityButtons].filter(button=>button.action!=="TOGGLE_OPTION");
  return <View style={styles.context}>
    <View style={styles.contextCard}><Icon name={snapshot.state.page==="ITEM"?"food":"wallet"} size={32} color={colors.forest}/><Text style={[textStyle(rtl,"bold"),styles.roomTitle]}>{roomTitle || snapshot.state.title}</Text></View>
    <BottomSheet visible title={snapshot.state.title} rtl={rtl} onClose={()=>dispatch("BACK")}>
      {!!snapshot.state.subtitle && <Text style={[textStyle(rtl),styles.subtitle]}>{snapshot.state.subtitle}</Text>}
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
        {cards.map(card=>{
          const choice=card.buttons.find(button=>button.action==="SELECT_VARIANT"), selected=!!card.badge;
          return choice ? <Pressable key={card.id} accessibilityRole="radio" accessibilityLabel={`${card.title} · ${card.detail}`} accessibilityState={{selected,disabled:busy || !choice.enabled}} disabled={busy || !choice.enabled} onPress={()=>dispatch(choice.action,choice.value)} style={[styles.choice,selected && styles.selected,rtl && {flexDirection:"row-reverse"}]}>
            <View style={{flex:1}}><Text style={textStyle(rtl,"semibold")}>{card.title}</Text><Text style={[textStyle(rtl),styles.subtitle]}>{card.detail}</Text></View><Icon name={selected?"check":"add"} color={colors.forest}/>
          </Pressable> : <CardView key={card.id} card={card} rtl={rtl} busy={busy}/>;
        })}
        {choices.map(button=>{const checked=button.title.startsWith("✓ ");return <Pressable key={button.value} accessibilityRole="checkbox" accessibilityLabel={button.title.replace(/^✓ /,"")} accessibilityState={{checked,disabled:busy || !button.enabled}} disabled={busy || !button.enabled} onPress={()=>dispatch(button.action,button.value)} style={[styles.choice,checked && styles.selected,rtl && {flexDirection:"row-reverse"}]}><Text style={[textStyle(rtl),{flex:1}]}>{button.title.replace(/^✓ /,"")}</Text><View style={[styles.check,checked && {backgroundColor:colors.forest,borderColor:colors.forest}]}>{checked && <Icon name="check" size={16} color={colors.white}/>}</View></Pressable>;})}
        {[...snapshot.mainFields,...snapshot.extraFields].map(field=><FieldInput key={field.key} field={field} rtl={rtl} busy={busy}/>)}
        {actions.map((button,index)=><Button key={`${button.action}:${index}`} rtl={rtl} busy={busy} action={button}/>)}
      </ScrollView>
      {!!snapshot.primaryAction && <Button rtl={rtl} busy={busy} action={snapshot.primaryAction}/>}
    </BottomSheet>
  </View>;
}
const styles=StyleSheet.create({
  context:{flex:1,padding:18},contextCard:{padding:20,gap:12,borderRadius:22,backgroundColor:colors.mint},roomTitle:{fontSize:22,lineHeight:34},
  subtitle:{fontSize:13,lineHeight:23,color:colors.muted},content:{gap:12,paddingBottom:12},choice:{minHeight:54,padding:14,borderRadius:16,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white,flexDirection:"row",alignItems:"center",gap:12},selected:{backgroundColor:colors.mint,borderColor:"#ACCAB7"},check:{width:24,height:24,borderRadius:8,borderWidth:1.5,borderColor:colors.line,alignItems:"center",justifyContent:"center"},
});
