import ScrollArea, { ScrollLoad } from "../components/ScrollArea";
import React, { useRef, useState } from "react";
import { ActivityIndicator, RefreshControl, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import type { Card, Snapshot } from "../types";
import { colors, tx } from "../theme";
import { dispatch } from "../native";
import { allActions, allCards, allFields } from "../guidedFlows";
import { Button, FieldInput, PagedCards, action, textStyle } from "../components/Controls";
import BottomSheet from "../components/BottomSheet";
import { TabButton } from "../components/Motion";

export default function FriendsScreen({ snapshot }: { snapshot: Snapshot }) {
  const { rtl, busy, feedback, error } = snapshot.state;
  const [tab, setTab] = useState(0), [query, setQuery] = useState("");
  const cards = allCards(snapshot), buttons = allActions(snapshot), fields = allFields(snapshot);
  const detail = cards.find(card => card.id === "friend-view" || card.id === "friend-editor");
  const list = useRef<Card[]>([]);
  if (!detail) list.current = cards;
  const visible = list.current.filter(card => card.id.startsWith(tab ? "friend-joined:" : "friend-group:") &&
    `${card.title} ${card.detail}`.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()));
  const close = () => { if (!busy) dispatch("FRIENDS_ACTION", "open"); };
  return <View style={{flex:1}}>
    <View style={styles.heading}>
      <Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.title]}>{tx(rtl,"Groups","المجموعات")}</Text>
      <Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,"Invite your favourite people to your next room.","ادعُ أصحابك إلى غرفتك القادمة.")}</Text>
      <Button action={action(tx(rtl,"New group","مجموعة جديدة"),"FRIENDS_ACTION","new",true)} rtl={rtl} busy={busy}/>
      <View style={[styles.tabs,rtl && {flexDirection:"row-reverse"}]}>
        {[tx(rtl,"Your groups","مجموعاتك"),tx(rtl,"Joined groups","المجموعات المنضم إليها")].map((title,index) =>
          <TabButton key={title} selected={index === tab} accessibilityRole="tab" accessibilityLabel={title} accessibilityState={{selected:index === tab}} onPress={() => setTab(index)} style={[styles.tab,index === tab && styles.active]}>
            <Text style={textStyle(rtl,"semibold")}>{title}</Text>
          </TabButton>)}
      </View>
      <TextInput accessibilityLabel={tx(rtl,"Search groups","البحث في المجموعات")} placeholder={tx(rtl,"Search by group name…","ابحث باسم المجموعة…")} value={query} onChangeText={setQuery} style={[styles.search,textStyle(rtl)]}/>
    </View>
    <ScrollArea contentContainerStyle={styles.content} refreshControl={<RefreshControl refreshing={busy} onRefresh={() => dispatch("REFRESH")} tintColor={colors.forest}/> }>
      <PagedCards key={`${tab}:${query}`} cards={visible} busy={busy} rtl={rtl}/>
      {!visible.length && <Text style={textStyle(rtl)}>{tx(rtl,"No groups to show.","لا توجد مجموعات لعرضها.")}</Text>}
    </ScrollArea>
    <BottomSheet visible={!!detail} title={detail?.title || ""} rtl={rtl} onClose={close}>
      <ScrollArea keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:14,paddingBottom:8}}>
        {!!detail?.detail && <Text style={textStyle(rtl)}>{detail.detail}</Text>}
        {fields.map(field => <FieldInput key={field.key} field={field} busy={busy} rtl={rtl}/>)}
        {cards.filter(card => card.id.startsWith("friend-view-member:")).map(card => <View key={card.id} style={[styles.person,rtl && {flexDirection:"row-reverse"}]}>
          <View style={styles.avatar}><Text style={textStyle(rtl,"bold")}>{Array.from(card.title)[0]}</Text></View><View style={{flex:1,gap:3}}><Text style={textStyle(rtl,"semibold")}>{card.title}</Text><Text style={[textStyle(rtl),{fontSize:12,color:colors.muted}]}>{card.detail}</Text></View>
        </View>)}
        <PagedCards cards={cards.filter(card => card.id !== detail?.id && !card.id.startsWith("friend-view-member:"))} busy={busy} rtl={rtl}/>
        {busy && <ActivityIndicator color={colors.forest}/>}
        {!!(feedback?.message || error) && <Text accessibilityRole={feedback?.isError || error ? "alert" : "text"} style={[textStyle(rtl),{color:feedback?.isError || error ? colors.danger : colors.forest}]}>{feedback?.message || error}</Text>}
      </ScrollArea>
      <View style={{gap:8}}>{buttons.map(button => <Button key={button.action+button.value} action={button} busy={busy} rtl={rtl}/>)}</View>
    </BottomSheet>
  </View>;
}
const styles = StyleSheet.create({
  person:{flexDirection:"row",alignItems:"center",gap:10,padding:12,borderRadius:16,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line},avatar:{height:32,width:32,borderRadius:11,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},
  heading:{paddingHorizontal:20,paddingTop:12,gap:12},title:{fontSize:26,lineHeight:40},subtitle:{color:colors.muted,fontSize:14,lineHeight:24},
  tabs:{flexDirection:"row",gap:8},tab:{flex:1,padding:12,borderRadius:16,alignItems:"center",justifyContent:"center"},active:{backgroundColor:colors.mint},
  search:{backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,borderRadius:16,padding:14,minHeight:48},content:{padding:20,gap:12,paddingBottom:32},
});
