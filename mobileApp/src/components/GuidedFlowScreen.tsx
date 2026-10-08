import React, { useEffect, useRef, useState } from "react";
import { ActivityIndicator, FlatList, Keyboard, Pressable, ScrollView, StyleSheet, Text, TextInput, View } from "react-native";
import type { Action, Card, Field, Snapshot } from "../types";
import { allActions, allCards, allFields, flowSteps, stepError } from "../guidedFlows";
import type { FlowKind } from "../guidedFlows";
import { colors, tx } from "../theme";
import { dispatch } from "../native";
import { Button, CardView, FieldInput, PagedCards, action, textStyle } from "./Controls";
import { PageMotion } from "./Motion";
import BottomSheet from "./BottomSheet";
import Icon from "./Icon";
import RestaurantPicker from "./RestaurantPicker";

const fieldValue = (f: Field, rtl: boolean) => f.secret ? "" : f.key === "RECEIPT_PHOTO" ? tx(rtl,"Photo attached","الصورة مرفقة") : f.toggle ? tx(rtl, f.value === "true" ? "On" : "Off", f.value === "true" ? "مفعل" : "متوقف") : f.choices.find(c => c.value === f.value)?.label || f.value;

export default function GuidedFlowScreen({snapshot, kind, step, setStep, run, onBack, picker}: {
  snapshot: Snapshot; kind: FlowKind; step: number; setStep: (index: number) => void;
  run: (a: Action) => void; onBack: () => void; picker: Snapshot | null;
}) {
  const {rtl, busy} = snapshot.state;
  const steps = flowSteps(kind, rtl), index = Math.min(step, steps.length - 1), current = steps[index], review = index === steps.length - 1;
  const fields = allFields(snapshot), cards = allCards(snapshot), actions = allActions(snapshot);
  const scroll = useRef<ScrollView>(null);
  const submitted = useRef(false);
  const [optional, setOptional] = useState(false), [error, setError] = useState("");
  const [sheet, setSheet] = useState<"open" | "people" | "menu" | "details" | null>(null), [query, setQuery] = useState("");
  useEffect(() => { setOptional(false); setError(""); setSheet(null); scroll.current?.scrollTo({y:0,animated:false}); }, [index, kind]);
  useEffect(() => { if (!busy) submitted.current = false; }, [busy, snapshot.state.feedback?.id, snapshot.state.error]);
  const title = kind === "room" && !fields.some(f => f.key === "ROOM_NAME") ? snapshot.state.title : tx(rtl, kind === "room" ? "Create a room" : kind === "restaurant" ? "Restaurant" : kind === "payment" ? "Payment room" : "Menu entry", kind === "room" ? "إنشاء غرفة" : kind === "restaurant" ? "المطعم" : kind === "payment" ? "غرفة الدفع" : "بيانات الصنف");
  const knownFields = new Set(steps.flatMap(s => [...s.fields, ...s.optional]));
  const unknownFields = fields.filter(f => !knownFields.has(f.key) && !(kind === "room" && f.key === "RESTAURANT_NAME"));
  const primary = snapshot.primaryAction;
  const unknownActions = actions.filter(a => a !== primary && !["CREATE_ROOM", "SAVE_RESTAURANT", "SAVE_PAYMENT_ROOM", "MENU_SAVE", "RETRY", "SET_LANGUAGE", "BACK", "OPEN_LIBRARY", "OPEN_POLL_RESTAURANTS", "USE_OPEN_ORDER", "OPEN_CONNECTION_OPTIONS", ...steps.flatMap(s => s.actions)].includes(a.action));
  const contentCards = cards.filter(current.cards).filter(c => !(kind === "menu" || kind === "payment" && c.id.startsWith("share:")));
  const remainingCards = cards.filter(c => !steps.some(s => s.cards(c)) && !["restaurant", "poll-choices", "restaurant-choice-mode", "payment-account-missing"].includes(c.id) && !(kind === "payment" && c.id.startsWith("share:")));
  const stepFields = current.fields.map(key => fields.find(f => f.key === key)).filter((f): f is Field => !!f);
  const extraFields = [...current.optional.map(key => fields.find(f => f.key === key)).filter((f): f is Field => !!f), ...(index === steps.length - 2 ? unknownFields : [])];
  const poll = fields.find(f => f.key === "RESTAURANT_POLL")?.value === "true";
  const restaurant = cards.find(c => c.id === (poll ? "poll-choices" : "restaurant"));
  const choose = actions.find(a => a.action === (poll ? "OPEN_POLL_RESTAURANTS" : "OPEN_LIBRARY"));
  const open = actions.find(a => a.action === "USE_OPEN_ORDER");
  const people = cards.filter(c => c.id.startsWith("share:"));
  const assigned = people.filter(c => c.buttons.some(a => a.action === "REMOVE_PAYMENT_SHARE") || c.detail);
  const move = (next: number) => { Keyboard.dismiss(); setStep(next); };
  const advance = () => {
    const invalid = stepError(kind, index, snapshot);
    if (invalid) { setError(invalid); setOptional(true); return; }
    move(index + 1);
  };
  const finish = () => {
    if (submitted.current) return;
    if (primary?.action === "RETRY") { submitted.current = true; run(primary); return; }
    for (let i = 0; i < steps.length - 1; i++) {
      const invalid = stepError(kind, i, snapshot);
      if (invalid) { move(i); return; }
    }
    if (primary) { submitted.current = true; run(primary); }
  };
  const renderField = (f: Field) => <FieldInput key={f.key} field={f} rtl={rtl} busy={busy}/>;
  const renderButton = (a: Action) => <Button key={a.action + a.value} action={a} busy={busy} rtl={rtl} onPress={() => run(a)}/>;
  const chooseSheet = (next: typeof sheet) => { setQuery(""); setSheet(next); };
  const optionalValues = extraFields.filter(f => f.value && !["false","0","0.00"].includes(f.value)).length;

  return <View style={styles.root}>
    <View style={[styles.heading, rtl && styles.reverse]}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"Back","رجوع")} disabled={busy} onPress={onBack} style={styles.back}><View style={rtl && {transform:[{scaleX:-1}]}}><Icon name="back"/></View></Pressable>
      <Text style={[textStyle(rtl,"bold"),styles.title]}>{title}</Text>
      {busy && <ActivityIndicator color={colors.forest}/>}
    </View>
    <View style={styles.progress}>
      <View style={[styles.dots, rtl && styles.reverse]}>
        {steps.map((s, i) => <React.Fragment key={i}>
          {i > 0 && <View style={[styles.line, i <= index && {backgroundColor:colors.forest}]}/>}
          <Pressable testID={`flow-step:${i}`} accessibilityRole="button" accessibilityLabel={tx(rtl, `Step ${i + 1}: ${s.title}`, `الخطوة ${i + 1}: ${s.title}`)} accessibilityState={{selected:i === index,disabled:busy || i > index}} disabled={busy || i > index} onPress={() => move(i)} style={[styles.dot,i <= index && styles.activeDot]}>
            {i < index ? <Icon name="check" size={17} color={colors.white}/> : <Text style={[textStyle(rtl,"semibold"),{textAlign:"center",color:i === index ? colors.white : colors.muted}]}>{i + 1}</Text>}
          </Pressable>
        </React.Fragment>)}
      </View>
      <Text style={[textStyle(rtl,"medium"),styles.counter]}>{tx(rtl, `Step ${index + 1} of ${steps.length}`, `الخطوة ${index + 1} من ${steps.length}`)}</Text>
    </View>
    <ScrollView ref={scroll} testID="flow-content" keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      <PageMotion motionKey={`${kind}:${index}:${rtl}`}>
        <Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.stepTitle]}>{current.title}</Text>
        <Text style={[textStyle(rtl),styles.hint]}>{current.hint}</Text>
        {!!error && <Text accessibilityRole="alert" style={[textStyle(rtl),styles.error]}>{error}</Text>}
        {actions.filter(a => a.action === "OPEN_CONNECTION_OPTIONS").map(renderButton)}
        {!review && <>
          {stepFields.map(renderField)}
          {kind === "room" && index === 0 && <>
            {choose && <Pressable testID="choose-restaurant" accessibilityRole="button" accessibilityLabel={choose.title} disabled={busy || !choose.enabled} onPress={() => run(choose)} style={[styles.choice,rtl && styles.reverse]}>
              <View style={styles.choiceIcon}><Icon name="food" color={colors.forest} size={27}/></View>
              <View style={{flex:1,gap:3}}><Text style={[textStyle(rtl,"semibold"),styles.name]}>{restaurant?.title || choose.title}</Text><Text numberOfLines={2} style={[textStyle(rtl),styles.detail]}>{restaurant?.detail || tx(rtl,"Search saved restaurants and branches","ابحث عن المطاعم والفروع المحفوظة")}</Text><Text style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"Search or change","بحث أو تغيير")}</Text></View>
              <Icon name="search" size={20} color={colors.forest}/>
            </Pressable>}
            {open && <Button rtl={rtl} busy={busy} action={{...open,title:tx(rtl,"Use an open menu instead","استخدام قائمة مفتوحة"),primary:false}} onPress={() => chooseSheet("open")}/>}
          </>}
          {contentCards.map(c => <CardView key={c.id} card={c} rtl={rtl} busy={busy}/>)}
          {kind === "payment" && index === 1 && <>
            <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Choose people & shares","اختيار الأشخاص والحصص"),"CHOOSE_PAYMENT_PEOPLE")} onPress={() => chooseSheet("people")}/>
            {assigned.slice(0,3).map(c => <CompactPerson key={c.id} card={c} rtl={rtl} busy={busy} run={run}/>)}
            {assigned.length > 3 && <Text style={[textStyle(rtl),styles.detail]}>{tx(rtl,`${assigned.length - 3} more · open people to edit`,`${assigned.length - 3} آخرون · افتح قائمة الأشخاص للتعديل`)}</Text>}
          </>}
          {kind === "menu" && index === 1 && !!cards.length && <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Manage sizes & extras","إدارة الأحجام والإضافات"),"MENU_DETAILS")} onPress={() => chooseSheet("menu")}/>}
          {actions.filter(a => current.actions.includes(a.action)).map(renderButton)}
          {!!extraFields.length && <>
            <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"More options","خيارات إضافية")} accessibilityState={{expanded:optional}} onPress={() => setOptional(!optional)} style={[styles.expand,rtl && styles.reverse]}>
              <Icon name="settings" size={19} color={colors.forest}/><Text style={[textStyle(rtl,"semibold"),{flex:1,color:colors.forest}]}>{tx(rtl,"More options","خيارات إضافية")}{optionalValues ? ` · ${optionalValues}` : ""}</Text><View style={optional && {transform:[{rotate:"180deg"}]}}><Icon name="chevron" size={18}/></View>
            </Pressable>
            {optional && extraFields.map(renderField)}
          </>}
          {index === steps.length - 2 && <>{remainingCards.map(c => <CardView key={c.id} card={c} busy={busy} rtl={rtl}/>)}{unknownActions.map(renderButton)}</>}
        </>}
        {review && <>
          {steps.slice(0,-1).map((s,i) => {
            const values = s.fields.map(key => fields.find(f => f.key === key)).filter((f): f is Field => !!f && !!f.value && f.key !== "RECEIPT_PHOTO");
            const detail = kind === "room" && i === 0 ? restaurant?.title : kind === "payment" && i === 1 ? `${assigned.length} ${tx(rtl,"people","أشخاص")} · ${cards.find(c => c.id === "payment-shares-total")?.detail || ""}` : values.slice(0,2).map(f => `${f.label}: ${fieldValue(f,rtl)}`).join("\n");
            return <Pressable testID={`review-edit:${i}`} key={i} accessibilityRole="button" accessibilityLabel={tx(rtl,`Edit ${s.title}`,`تعديل: ${s.title}`)} disabled={busy} onPress={() => move(i)} style={[styles.summary,rtl && styles.reverse]}>
              <View style={{flex:1,gap:4}}><Text style={[textStyle(rtl,"semibold"),styles.name]}>{s.title}</Text><Text numberOfLines={3} style={[textStyle(rtl),styles.detail]}>{detail || tx(rtl,"Using your selected settings","حسب إعداداتك المختارة")}</Text></View><Text style={[textStyle(rtl,"semibold"),styles.link]}>{tx(rtl,"Edit","تعديل")}</Text>
            </Pressable>;
          })}
          <Button rtl={rtl} busy={busy} action={action(tx(rtl,"See all details","عرض جميع التفاصيل"),"REVIEW_DETAILS")} onPress={() => chooseSheet("details")}/>
          {kind === "payment" && cards.filter(c => c.id === "payment-account-missing").map(c => <CardView key={c.id} card={c} rtl={rtl} busy={busy}/>)}
        </>}
      </PageMotion>
    </ScrollView>
    <View style={[styles.footer,rtl && styles.reverse]}>
      {index > 0 && <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Back","السابق"),"FLOW_BACK")} onPress={() => move(index - 1)}/>}
      <View style={{flex:1}}>{review && primary ? <Button rtl={rtl} busy={busy} action={primary} onPress={finish}/> : <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Continue","التالي"),"FLOW_NEXT","",true)} onPress={advance}/>}</View>
    </View>
    {!!picker && <RestaurantPicker snapshot={picker}/>}
    <BottomSheet visible={!!sheet} title={tx(rtl,sheet === "open" ? "An open menu" : sheet === "people" ? "People & shares" : sheet === "menu" ? "Sizes & extras" : "Your choices",sheet === "open" ? "قائمة مفتوحة" : sheet === "people" ? "الأشخاص والحصص" : sheet === "menu" ? "الأحجام والإضافات" : "اختياراتك")} rtl={rtl} onClose={() => setSheet(null)}>
      {sheet === "open" && <>
        <Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,"Everyone can type their food items. Prices can be added in the room.","يمكن للجميع كتابة أصنافهم، وتُضاف الأسعار داخل الغرفة.")}</Text>
        {fields.filter(f => f.key === "RESTAURANT_NAME").map(renderField)}
        {open && <Button rtl={rtl} busy={busy} action={{...open,primary:true}} onPress={() => {run(open);setSheet(null);}}/>}
      </>}
      {sheet === "people" && <>
        <TextInput testID="payment-people-search" accessibilityLabel={tx(rtl,"Search people","ابحث عن شخص")} placeholder={tx(rtl,"Type a name, e.g. Ahmed","اكتب اسماً، مثل أحمد")} placeholderTextColor={colors.muted} value={query} onChangeText={setQuery} autoCorrect={false} style={[textStyle(rtl),styles.search]}/>
        <FlatList testID="payment-people-results" style={{flexShrink:1}} keyboardShouldPersistTaps="handled" data={people.filter(c => c.title.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase()))} keyExtractor={c => c.id}
          ListEmptyComponent={<Text style={[textStyle(rtl),styles.hint]}>{tx(rtl,"No matching people","لا توجد أسماء مطابقة")}</Text>}
          renderItem={({item}) => <CompactPerson card={item} rtl={rtl} busy={busy} run={run}/>}/>
      </>}
      {sheet === "menu" && <ScrollView keyboardShouldPersistTaps="handled"><PagedCards cards={cards} rtl={rtl} busy={busy}/></ScrollView>}
      {sheet === "details" && <ScrollView contentContainerStyle={{gap:12}}>
        {fields.filter(f => !f.secret && f.value).map(f => <View key={f.key} style={[styles.summary,{flexDirection:"column",alignItems:"stretch"}]}><Text style={[textStyle(rtl,"medium"),styles.detail]}>{f.label}</Text><Text style={textStyle(rtl)}>{fieldValue(f,rtl)}</Text></View>)}
        {kind === "room" && restaurant && <Text style={textStyle(rtl)}>{restaurant.title}{"\n"}{restaurant.detail}</Text>}
        {kind === "payment" && assigned.map(c => <Text key={c.id} style={textStyle(rtl)}>{c.title}{"\n"}{c.detail}</Text>)}
      </ScrollView>}
    </BottomSheet>
  </View>;
}

function CompactPerson({card,rtl,busy,run}:{card:Card;rtl:boolean;busy:boolean;run:(a:Action)=>void}) {
  const edit = card.buttons.find(a => a.action === "EDIT_PAYMENT_SHARE");
  const remove = card.buttons.find(a => a.action === "REMOVE_PAYMENT_SHARE");
  return <View style={[styles.person,rtl && styles.reverse]}>
    <Pressable style={{flex:1,gap:4}} accessibilityRole="button" accessibilityLabel={`${edit?.title}: ${card.title}`} disabled={busy || !edit?.enabled} onPress={() => edit && run(edit)}>
      <Text style={[textStyle(rtl,"semibold"),styles.name]}>{card.title}</Text><Text numberOfLines={2} style={[textStyle(rtl),styles.detail]}>{card.detail || edit?.title}</Text>
    </Pressable>
    {edit && <Pressable testID={`share-edit:${card.id}`} accessibilityRole="button" accessibilityLabel={`${edit.title}: ${card.title}`} disabled={busy || !edit.enabled} onPress={() => run(edit)} style={styles.back}><Icon name={remove ? "settings" : "add"} color={colors.forest}/></Pressable>}
    {remove && <Button rtl={rtl} busy={busy} action={remove} onPress={() => run(remove)}/>}
  </View>;
}
const styles = StyleSheet.create({
  root:{flex:1},reverse:{flexDirection:"row-reverse"},heading:{paddingHorizontal:16,paddingTop:4,flexDirection:"row",alignItems:"center",gap:8},
  back:{height:44,width:44,alignItems:"center",justifyContent:"center"},title:{flex:1,fontSize:18,lineHeight:30},
  progress:{paddingHorizontal:24,paddingVertical:10,gap:5},dots:{flexDirection:"row",alignItems:"center"},dot:{height:32,width:32,borderRadius:16,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},activeDot:{backgroundColor:colors.forest},line:{flex:1,height:2,backgroundColor:colors.line,marginHorizontal:8},counter:{fontSize:12,lineHeight:20,color:colors.muted},
  content:{paddingHorizontal:20,paddingBottom:20,gap:12},stepTitle:{fontSize:23,lineHeight:36},hint:{fontSize:14,lineHeight:23,color:colors.muted},error:{padding:12,borderRadius:12,color:colors.danger,backgroundColor:"#FFF0EB",fontSize:14,lineHeight:24},
  choice:{padding:16,borderRadius:20,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,flexDirection:"row",alignItems:"center",gap:12},choiceIcon:{height:50,width:50,borderRadius:18,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},
  name:{fontSize:15,lineHeight:25},detail:{fontSize:13,lineHeight:22,color:colors.muted},link:{fontSize:13,lineHeight:22,color:colors.forest},expand:{padding:14,borderRadius:14,backgroundColor:colors.mint,flexDirection:"row",alignItems:"center",gap:10},
  footer:{paddingHorizontal:20,paddingVertical:12,flexDirection:"row",alignItems:"center",gap:10,borderTopWidth:1,borderColor:colors.line,backgroundColor:colors.cream},summary:{padding:14,borderRadius:18,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white,flexDirection:"row",alignItems:"center",gap:12},person:{minHeight:78,paddingVertical:12,flexDirection:"row",alignItems:"center",gap:8,borderBottomWidth:1,borderColor:colors.line},search:{padding:12,minHeight:48,borderWidth:1,borderColor:colors.line,borderRadius:14,backgroundColor:colors.white},
});
