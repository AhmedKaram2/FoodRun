import React, { useEffect, useState } from "react";
import { Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import type { QuickWheel as State } from "../types";
import { quickAction } from "../native";
import { colors, tx } from "../theme";
import { Button, action, textStyle, styles } from "../components/Controls";
import { WheelDrawing } from "../components/LiveWheel";
import { PageMotion, useMotionEnabled } from "../components/Motion";
import BottomSheet from "../components/BottomSheet";
import Icon from "../components/Icon";

export default function QuickWheel({ state, rtl }: { state: State; rtl: boolean }) {
  const [search, setSearch] = useState("");
  const motion = useMotionEnabled(), angle = 0;
  useEffect(() => setSearch(""), [state.destination]);
  const names = state.people.filter(value => value.active).map(value => value.name);
  const style = state.style || "names", purpose = state.purpose || tx(rtl,"Food pickup","إحضار الطعام");
  const selectedGroup = state.groups?.find(group => group.id === state.groupId);
  const index = names.length ? Math.floor((((-angle + 180 / names.length) % 360) + 360) % 360 / (360 / names.length)) % names.length : 0;
  const visibleNames = state.people.filter(person => person.name.toLocaleLowerCase().includes(search.trim().toLocaleLowerCase()));
  const open = (destination:string) => quickAction(destination);
  const button = (title:string, command:string, value="", primary=false) => <Button rtl={rtl} busy={state.spinning} action={action(title,"QUICK",value,primary)} onPress={() => quickAction(command,value)}/>;
  return <View style={{flex:1}}>
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={local.content}>
      <PageMotion motionKey="quick-choice" rtl={rtl}>
        <Text accessibilityRole="header" style={[textStyle(rtl,"bold"),local.heading]}>{tx(rtl,"Quick Choice","الاختيار السريع")}</Text>
        <Text style={[textStyle(rtl),local.detail]}>{tx(rtl,"Choose someone for anything. Everyone included has an equal chance.","اختَر شخصاً لأي سبب. لكل المشاركين فرصة متساوية.")}</Text>
        <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"Edit purpose and animation","تعديل السبب وطريقة العرض")} disabled={state.spinning} onPress={() => open("settings")} style={[local.purpose,rtl && local.reverse]}>
          <Icon name="wheel" color={colors.forest}/><View style={{flex:1}}><Text style={[textStyle(rtl),local.detail]}>{tx(rtl,"Choosing for","سبب الاختيار")}</Text><Text style={textStyle(rtl,"bold")}>{purpose}</Text></View><Icon name="edit" size={18}/>
        </Pressable>
        {style === "wheel" && names.length ? <WheelDrawing names={names} rotation={angle}/> : <View style={local.runner}>
          <Text style={local.star}>{state.winner ? "🎉" : "✦"}</Text>
          {motion && state.spinning && names.length > 1 && <Text style={[textStyle(rtl),local.neighbor]}>{names[(index+names.length-1)%names.length]}</Text>}
          <Text accessibilityLiveRegion={state.spinning ? "none" : "polite"} style={[textStyle(rtl,"bold"),local.name]}>{state.winner || (state.spinning ? (motion ? names[index] : tx(rtl,"Choosing…","جارٍ الاختيار…")) : tx(rtl,"Who will it be?","مَن سيكون الاختيار؟"))}</Text>
          {motion && state.spinning && names.length > 1 && <Text style={[textStyle(rtl),local.neighbor]}>{names[(index+1)%names.length]}</Text>}
          <Text style={[textStyle(rtl),local.detail]}>{purpose}</Text>
        </View>}
        {style === "wheel" && !!state.winner && <Text accessibilityLiveRegion="polite" style={[textStyle(rtl,"bold"),local.name]}>{state.winner}</Text>}
        <Text style={[textStyle(rtl),local.count]}>{names.length} / {state.people.length} · {tx(rtl,"Included","المشاركون")}{selectedGroup ? ` · ${selectedGroup.name}` : ""}</Text>
        {!names.length && <Text style={[textStyle(rtl),local.empty]}>{tx(rtl,"Add names or choose a group to get started.","أضف أسماء أو اختَر مجموعة للبدء.")}</Text>}
        {!!state.error && <Text accessibilityRole="alert" style={[textStyle(rtl),{color:colors.danger}]}>{state.error}</Text>}
        <Button rtl={rtl} busy={state.spinning} action={{...action(tx(rtl,state.spinning ? "Choosing…" : "Pick someone",state.spinning ? "جارٍ الاختيار…" : "اختيار شخص"),"QUICK","",true),enabled:state.canSpin && !!names.length}} onPress={() => quickAction("spin",String(angle))}/>
        <View style={[local.actions,rtl && local.reverse]}><View style={{flex:1}}>{button(tx(rtl,"People","الأشخاص"),"crew")}</View><View style={{flex:1}}>{button(tx(rtl,"Choose group","اختيار مجموعة"),"groups")}</View></View>

      </PageMotion>
    </ScrollView>
    <BottomSheet visible={state.destination !== "MAIN"} rtl={rtl} title={state.destination === "HISTORY" ? tx(rtl,"Previous picks","الاختيارات السابقة") : state.destination === "GROUPS" ? tx(rtl,"Choose group","اختيار مجموعة") : state.destination === "SETTINGS" ? tx(rtl,"Purpose & animation","السبب وطريقة العرض") : tx(rtl,"Participants","المشاركون")} onClose={() => quickAction("dismiss")}>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12}}>
        {state.destination === "SETTINGS" ? <>
          <Text style={textStyle(rtl,"semibold")}>{tx(rtl,"Choosing for","سبب الاختيار")}</Text>
          <TextInput style={[styles.input,textStyle(rtl)]} accessibilityLabel={tx(rtl,"Choosing for","سبب الاختيار")} placeholder={tx(rtl,"e.g. food, a game, a task or a prize","مثلاً: الطعام، لعبة، مهمة أو جائزة")} value={state.purpose || ""} maxLength={160} onChangeText={value => quickAction("purpose",value)}/>
          {button(tx(rtl,"Use food default","استخدام الطعام كاختيار افتراضي"),"purpose")}
          <Text style={textStyle(rtl,"semibold")}>{tx(rtl,"Selection animation","طريقة عرض الاختيار")}</Text>
          {[["names",tx(rtl,"Running names","الأسماء المتحركة")],["wheel",tx(rtl,"Wheel","العجلة")]].map(([value,label]) => <Pressable key={value} accessibilityRole="radio" accessibilityState={{selected:style === value}} onPress={() => quickAction("style",value)} style={[styles.card,local.actions,rtl && local.reverse]}><Text style={[textStyle(rtl,"semibold"),{flex:1}]}>{label}</Text><Text>{style === value ? "✓" : "○"}</Text></Pressable>)}
          <Text style={textStyle(rtl,"semibold")}>{tx(rtl,"Who sees this choice?","مَن يشاهد الاختيار؟")}</Text>
          {[["participants",tx(rtl,"Included group members","المشاركون من المجموعة")],["all",tx(rtl,"Everyone online","جميع المتصلين الآن")]].map(([value,label]) => <Pressable key={value} accessibilityRole="radio" accessibilityState={{selected:(state.audience || "participants")===value}} onPress={()=>quickAction("audience",value)} style={[styles.card,local.actions,rtl && local.reverse]}><Text style={[textStyle(rtl),{flex:1}]}>{label}</Text><Text>{(state.audience || "participants")===value ? "✓" : "○"}</Text></Pressable>)}
          <Text style={textStyle(rtl)}>{tx(rtl,"Group members see it automatically. Choose everyone online to share custom names with others.","يرى أعضاء المجموعة الاختيار تلقائياً. اختَر جميع المتصلين لمشاركة الأسماء اليدوية مع الآخرين.")}</Text>
        </> : state.destination === "GROUPS" ? <>
          <Text style={textStyle(rtl)}>{tx(rtl,"All group members will be included. You can exclude anyone afterward.","سيشارك جميع أعضاء المجموعة. يمكنك استبعاد أي شخص بعد ذلك.")}</Text>
          <TextInput style={[styles.input,textStyle(rtl)]} accessibilityLabel={tx(rtl,"Search groups","البحث في المجموعات")} placeholder={tx(rtl,"Search by group name…","ابحث باسم المجموعة…")} value={search} onChangeText={setSearch}/>
          {!(state.groups || []).length && <Text style={textStyle(rtl)}>{tx(rtl,"Create or join a group first, or add names yourself.","أنشئ مجموعة أو انضم إلى مجموعة، أو أضف الأسماء بنفسك.")}</Text>}
          {(state.groups || []).filter(group => group.name.toLocaleLowerCase().includes(search.toLocaleLowerCase())).map(group => <Pressable key={group.id} accessibilityRole="button" onPress={() => quickAction("group",group.id)} style={[styles.card,local.actions,rtl && local.reverse]}><View style={{flex:1}}><Text style={textStyle(rtl,"semibold")}>{group.name}</Text><Text style={textStyle(rtl)}>{group.count} · {tx(rtl,"People","الأشخاص")}</Text></View><Text>{group.id === state.groupId ? "✓" : "+"}</Text></Pressable>)}
        </> : state.destination === "HISTORY" ? <>
          {!state.history.length && <Text style={textStyle(rtl)}>{tx(rtl,"Your picks will appear here.","ستظهر اختياراتك هنا.")}</Text>}
          {state.history.map(item => <View style={styles.card} key={item.id}><Text style={textStyle(rtl,"bold")}>{item.name}</Text><Text style={textStyle(rtl)}>{item.purpose || tx(rtl,"Food pickup","إحضار الطعام")} · {item.date}</Text></View>)}
        </> : state.destination === "ADD_PERSON" ? <>
          <TextInput style={[styles.input,textStyle(rtl)]} accessibilityLabel={tx(rtl,"Name","الاسم")} placeholder={tx(rtl,"Enter a participant’s name","أدخل اسم المشارك")} value={state.nameDraft} onChangeText={value => quickAction("name",value)} maxLength={80}/>
          {button(tx(rtl,"Add person","إضافة شخص"),"save","",true)}
        </> : <>
          <View style={[local.actions,rtl && local.reverse]}><View style={{flex:1}}>{button(tx(rtl,"Add person","إضافة شخص"),"add","",true)}</View><View style={{flex:1}}>{button(tx(rtl,"Choose group","اختيار مجموعة"),"groups")}</View></View>
          <Text style={textStyle(rtl)}>{tx(rtl,"Changes here only affect this picker. Your group stays the same.","التغييرات هنا تخص هذا الاختيار فقط. تبقى مجموعتك كما هي.")}</Text>
          <TextInput style={[styles.input,textStyle(rtl)]} accessibilityLabel={tx(rtl,"Search names","البحث في الأسماء")} placeholder={tx(rtl,"Search by name…","ابحث بالاسم…")} value={search} onChangeText={setSearch}/>
          {button(tx(rtl,"Include everyone","مشاركة الجميع"),"everyone")}
          {visibleNames.map(person => <View key={person.id} style={[local.person,rtl && local.reverse]}><Pressable accessibilityRole="checkbox" accessibilityLabel={person.name} accessibilityState={{checked:person.active}} onPress={() => quickAction("toggle",person.id)} style={[local.personToggle,rtl && local.reverse]}><Text style={[local.check,person.active && local.checked]}>{person.active ? "✓" : "○"}</Text><View style={{flex:1}}><Text style={textStyle(rtl,"semibold")}>{person.name}</Text><Text style={[textStyle(rtl),local.detail]}>{tx(rtl,person.active ? "Included" : "Excluded",person.active ? "مشارك" : "مستبعد")}</Text></View></Pressable><Pressable accessibilityRole="button" accessibilityLabel={`${tx(rtl,"Remove","إزالة")} ${person.name}`} onPress={() => quickAction("remove",person.id)} style={local.remove}><Icon name="close" size={18} color={colors.danger}/></Pressable></View>)}
          {!state.people.length && <Text style={textStyle(rtl)}>{tx(rtl,"No participants yet","لا يوجد مشاركون بعد")}</Text>}
        </>}
        {!!state.error && state.destination !== "MAIN" && <Text accessibilityRole="alert" style={[textStyle(rtl),{color:colors.danger}]}>{state.error}</Text>}
        {button(tx(rtl,"Done","تم"),"dismiss")}
      </ScrollView>
    </BottomSheet>
  </View>;
}
const local = StyleSheet.create({content:{padding:16,gap:14},heading:{fontSize:27,lineHeight:42},detail:{fontSize:12,lineHeight:22,color:colors.muted},purpose:{padding:14,marginVertical:12,borderRadius:20,backgroundColor:colors.mint,flexDirection:"row",alignItems:"center",gap:12},reverse:{flexDirection:"row-reverse"},actions:{flexDirection:"row",alignItems:"center",gap:8,marginVertical:5},runner:{minHeight:225,justifyContent:"center",alignItems:"center",gap:8,backgroundColor:colors.honey,borderRadius:28,padding:16,marginBottom:12},star:{fontSize:34,color:colors.forest},name:{fontSize:29,lineHeight:48,textAlign:"center"},neighbor:{fontSize:16,color:colors.muted,opacity:.5},count:{fontSize:13,textAlign:"center",marginBottom:12},empty:{textAlign:"center",color:colors.muted,marginBottom:14},person:{flexDirection:"row",alignItems:"center",borderBottomWidth:1,borderColor:colors.line},personToggle:{flex:1,flexDirection:"row",alignItems:"center",gap:12,paddingVertical:10},check:{width:30,height:30,textAlign:"center",lineHeight:30,borderRadius:10,backgroundColor:colors.line,color:colors.ink},checked:{backgroundColor:colors.forest,color:colors.white},remove:{padding:12}});
