import React from "react";
import { Image, Keyboard, Pressable, StyleSheet, Text, TextInput, View } from "react-native";
import type { Card, Field } from "../types";
import { dispatch, update } from "../native";
import { colors, tx } from "../theme";
import { CardView, FieldInput, textStyle } from "./Controls";
import ScrollArea, { ScrollLoad } from "./ScrollArea";
import Icon from "./Icon";

function MenuItem({card,busy,rtl}:{card:Card;busy:boolean;rtl:boolean}) {
  const choose=card.buttons.find(button=>button.action==="OPEN_ITEM"), add=card.buttons.find(button=>button.action==="QUICK_ADD_ITEM");
  const main=add || choose;
  return <View style={[styles.item,rtl && styles.reverse]}>
    {!!card.image && <Image source={{uri:card.image}} accessibilityLabel={card.title} style={styles.image}/>}
    <View style={styles.info}>
      <Text style={[textStyle(rtl,"semibold"),styles.name]}>{card.title}</Text>
      {!!card.detail && <Text numberOfLines={2} style={[textStyle(rtl),styles.detail]}>{card.detail}</Text>}
      {!!card.badge && <Text style={[textStyle(rtl,"medium"),styles.price]}>{card.badge}</Text>}
      {!!add && !!choose && <Pressable accessibilityRole="button" accessibilityLabel={choose.title} disabled={busy || !choose.enabled} onPress={()=>{Keyboard.dismiss();dispatch(choose.action,choose.value);}} style={styles.notes}>
        <Text style={[textStyle(rtl,"medium"),styles.link]}>{choose.title}</Text>
      </Pressable>}
    </View>
    {!!main && <Pressable testID={`action:${main.action}:${main.value}`} accessibilityRole="button" accessibilityLabel={main.title} accessibilityState={{disabled:busy || !main.enabled}} disabled={busy || !main.enabled} onPress={()=>{Keyboard.dismiss();dispatch(main.action,main.value);}} style={[styles.add,(busy || !main.enabled) && {opacity:.45}]}>
      <Icon name="add" size={18} color={colors.forest}/>
      <Text style={[textStyle(rtl,"semibold"),styles.link]}>{add ? tx(rtl,"Add","إضافة") : tx(rtl,"Choose","اختيار")}</Text>
    </Pressable>}
  </View>;
}

export default function RoomFood({cards,fields,busy,rtl}:{cards:Card[];fields:Field[];busy:boolean;rtl:boolean}) {
  const [limit,setLimit]=React.useState(12);
  const search=fields.find(field=>field.key==="MENU_SEARCH"), category=fields.find(field=>field.key==="MENU_CATEGORY");
  // Cart lines have their own fixed shortcut and sheet; filtering the menu cannot hide them.
  const visible=cards.filter(card=>!isOrderCard(card));
  return <View style={{gap:10}}>
    {!!search && <View style={[styles.search,rtl && styles.reverse]}>
      <Icon name="search" size={19} color={colors.muted}/>
      <TextInput accessibilityLabel={search.label} testID={search.key} value={search.value} editable={!busy} placeholder={tx(rtl,"Find food…","ابحث عن طعام…")} placeholderTextColor={colors.muted} onChangeText={value=>update(search.key,value)} style={[textStyle(rtl),styles.input]}/>
    </View>}
    {!!category && <ScrollArea horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={[styles.categories,rtl && styles.reverse]}>
      {category.choices.map(choice=><Pressable key={choice.value} accessibilityRole="radio" accessibilityLabel={choice.label} accessibilityState={{selected:category.value===choice.value,disabled:busy}} disabled={busy} onPress={()=>update(category.key,choice.value)} style={[styles.category,category.value===choice.value && styles.selected]}>
        <Icon name="food" size={16} color={category.value===choice.value ? colors.forest : colors.muted}/><Text style={[textStyle(rtl,"semibold"),styles.categoryText,category.value===choice.value && {color:colors.forest}]}>{choice.label}</Text>
      </Pressable>)}
    </ScrollArea>}
    {fields.filter(field=>field!==search && field!==category).map(field=><FieldInput key={field.key} field={field} busy={busy} rtl={rtl}/>)}
    {visible.slice(0,limit).map(card=>card.id.startsWith("menu:") ? <MenuItem key={card.id} card={card} busy={busy} rtl={rtl}/> : <CardView key={card.id} card={card} busy={busy} rtl={rtl}/>)}
    {visible.length>limit && <ScrollLoad loadKey={limit} onLoad={()=>setLimit(value=>value+12)}><Text style={[textStyle(rtl),styles.detail]}>{tx(rtl,"Scroll for more food","مرّر لعرض المزيد من الطعام")}</Text></ScrollLoad>}
  </View>;
}

export const isOrderCard=(card:Card)=>/^(cart:|cart-submitted$|estimate$)/.test(card.id);
const styles=StyleSheet.create({
  reverse:{flexDirection:"row-reverse"},item:{flexDirection:"row",alignItems:"center",gap:12,padding:12,borderRadius:16,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},info:{flex:1,gap:3},image:{width:56,height:56,borderRadius:12},name:{fontSize:15,lineHeight:23},detail:{fontSize:11,lineHeight:18,color:colors.muted},price:{fontSize:12,lineHeight:20,color:colors.forest},add:{minWidth:64,minHeight:56,alignItems:"center",justifyContent:"center",gap:3,padding:8,borderRadius:12,backgroundColor:colors.mint},notes:{minHeight:44,justifyContent:"center"},link:{fontSize:12,lineHeight:20,color:colors.forest},search:{flexDirection:"row",alignItems:"center",gap:8,paddingHorizontal:12,borderRadius:14,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},input:{flex:1,minHeight:46,paddingVertical:8,fontSize:14},categories:{flexDirection:"row",gap:8,paddingVertical:2},category:{minHeight:54,justifyContent:"center",alignItems:"center",gap:2,paddingHorizontal:12,borderRadius:17,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line},selected:{backgroundColor:colors.mint,borderColor:"#B9D7C9"},categoryText:{fontSize:10,lineHeight:18},
});
