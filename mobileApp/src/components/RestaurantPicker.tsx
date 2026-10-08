import React, { useState } from "react";
import { FlatList, Pressable, ScrollView, StyleSheet, Text, View } from "react-native";
import type { Snapshot } from "../types";
import { allActions, allCards, allFields } from "../guidedFlows";
import { dispatch, update } from "../native";
import { colors, tx } from "../theme";
import BottomSheet from "./BottomSheet";
import { Button, FieldInput, action, textStyle } from "./Controls";
import Icon from "./Icon";

export default function RestaurantPicker({snapshot}: {snapshot: Snapshot}) {
  const {rtl, busy} = snapshot.state, [filters, setFilters] = useState(false);
  const fields = allFields(snapshot), actions = allActions(snapshot);
  const poll = actions.find(a => a.action === "CONFIRM_POLL_RESTAURANTS");
  const rows = allCards(snapshot).filter(c => c.id.startsWith("restaurant:"));
  const search = fields.find(f => f.key === "RESTAURANT_SEARCH");
  const filterFields = fields.filter(f => ["RESTAURANT_EMIRATE", "RESTAURANT_AREA", "RESTAURANT_MEAL"].includes(f.key));
  const activeFilters = filterFields.filter(f => f.value).length;
  const add = actions.find(a => a.action === "NEW_RESTAURANT");
  return <BottomSheet visible title={tx(rtl, poll ? "Restaurants to vote on" : "Choose a restaurant", poll ? "مطاعم التصويت" : "اختر مطعماً")} rtl={rtl} onClose={() => { if (!busy) dispatch("BACK"); }}>
    {search && <FieldInput field={search} busy={busy} rtl={rtl}/>}
    <View style={[styles.tools, rtl && styles.reverse]}>
      <View style={{flex:1}}><Button busy={busy} rtl={rtl} action={action(tx(rtl, `Filters${activeFilters ? ` (${activeFilters})` : ""}`, `التصفية${activeFilters ? ` (${activeFilters})` : ""}`), "PICKER_FILTERS")} onPress={() => setFilters(!filters)}/></View>
      {add && <View style={{flex:1}}><Button busy={busy} rtl={rtl} action={{...add, primary:false}}/></View>}
    </View>
    {filters && <ScrollView keyboardShouldPersistTaps="handled" style={{maxHeight:220}} contentContainerStyle={styles.filters}>
      {filterFields.map(f => <FieldInput key={f.key} field={f} busy={busy} rtl={rtl}/>)}
      {!!activeFilters && <Button busy={busy} rtl={rtl} action={action(tx(rtl, "Clear filters", "مسح التصفية"), "CLEAR_RESTAURANT_FILTERS")} onPress={() => filterFields.forEach(f => update(f.key, ""))}/>}
    </ScrollView>}
    {poll && <Text style={[textStyle(rtl), styles.hint]}>{snapshot.state.subtitle}</Text>}
    <FlatList testID="restaurant-results" data={rows} keyExtractor={c => c.id} keyboardShouldPersistTaps="handled" style={styles.list} contentContainerStyle={{paddingBottom:12}}
      ListEmptyComponent={<View style={styles.empty}><Icon name="search" size={30} color={colors.forest}/><Text style={[textStyle(rtl),{textAlign:"center"}]}>{tx(rtl, "No matching restaurants. Try another search or add a restaurant.", "لا توجد مطاعم مطابقة. جرّب بحثاً آخر أو أضف مطعماً.")}</Text></View>}
      renderItem={({item}) => {
        const select = item.buttons.find(a => ["SELECT_RESTAURANT", "TOGGLE_POLL_RESTAURANT"].includes(a.action));
        const selected = !!poll && !!select?.title.startsWith("✓");
        const disabled = busy || !select?.enabled;
        return <Pressable testID={`pick:${item.id}`} accessibilityRole={poll ? "checkbox" : "button"} accessibilityLabel={`${item.title}. ${item.detail}`} accessibilityState={{disabled, ...(poll ? {checked:selected} : {})}} disabled={disabled} onPress={() => select && dispatch(select.action, select.value)} style={[styles.row, rtl && styles.reverse, selected && styles.selected, disabled && {opacity:0.5}]}>
          <View style={styles.avatar}><Icon name="food" color={colors.forest}/></View>
          <View style={{flex:1,gap:3}}><Text numberOfLines={2} style={[textStyle(rtl,"semibold"), styles.name]}>{item.title}</Text><Text numberOfLines={2} style={[textStyle(rtl),styles.detail]}>{item.detail}</Text></View>
          <Icon name={selected ? "check" : poll ? "add" : "back"} size={19} color={colors.forest}/>
        </Pressable>;
      }}/>
    {poll && <Button rtl={rtl} busy={busy} action={poll}/>}
  </BottomSheet>;
}
const styles = StyleSheet.create({
  tools:{flexDirection:"row",gap:10}, reverse:{flexDirection:"row-reverse"}, filters:{gap:8},
  list:{flexShrink:1, minHeight:90},hint:{fontSize:12,lineHeight:21,color:colors.muted},
  row:{minHeight:76,flexDirection:"row",alignItems:"center",gap:12,paddingVertical:12,paddingHorizontal:10,borderBottomWidth:1,borderColor:colors.line},
  selected:{backgroundColor:colors.mint,borderRadius:16}, avatar:{width:38,height:38,borderRadius:13,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},
  name:{fontSize:15,lineHeight:25},detail:{fontSize:12,lineHeight:21,color:colors.muted},empty:{padding:20,alignItems:"center",gap:10},
});
