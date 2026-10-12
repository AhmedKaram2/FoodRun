import React from "react";
import { StyleSheet, Text, View, type StyleProp, type ViewStyle } from "react-native";
import { colors } from "../theme";
import { textStyle } from "./Controls";
import Icon from "./Icon";
import { TabButton } from "./Motion";

export type ScreenTab = { id: string; title: string; icon: string; badge?: number; testID?: string; disabled?: boolean };
export function tabIcon(title: string): string {
  if (/history|previous|سجل|سابقة/i.test(title)) return "history";
  if (/receipt|إيصال|إيصالات/i.test(title)) return "receipt";
  if (/favourite|favorite|مفضل/i.test(title)) return "heart";
  if (/notification|unread|إشعار|مقروء/i.test(title)) return "bell";
  if (/settings|إعداد/i.test(title)) return "settings";
  if (/details|personal|profile|بيانات|شخصي/i.test(title)) return "profile";
  if (/people|member|group|أشخاص|أعضاء|مجموع/i.test(title)) return "friends";
  if (/food|menu|restaurant|طعام|قائمة|قوائم|مطعم|مطاعم/i.test(title)) return "food";
  if (/payment|wallet|balance|pay|receive|waiting|دفع|مدفوع|رصيد|استلام|تأكيد|إجراء/i.test(title)) return "wallet";
  return "room";
}

export default function ScreenTabs({ items, selected, onChange, rtl, style }: {
  items: ScreenTab[]; selected: string; onChange: (id: string) => void; rtl: boolean; style?: StyleProp<ViewStyle>;
}) {
  if (!items.length) return null;
  // Keep up to five tabs visible. Larger sets wrap instead of hiding destinations.
  const columns = items.length > 5 ? Math.ceil(items.length / Math.ceil(items.length / 4)) : items.length;
  return <View accessibilityRole="tablist" style={[styles.row, rtl && {flexDirection:"row-reverse"}, style]}>
    {items.map(item => <TabButton key={item.id} selected={item.id === selected} testID={item.testID}
      accessibilityRole="tab" accessibilityLabel={item.title} accessibilityState={{selected:item.id === selected,disabled:!!item.disabled}}
      disabled={item.disabled} onPress={()=>onChange(item.id)}
      style={[styles.tab,{flexBasis:`${100 / columns - 2}%`,flexGrow:1},item.id === selected && styles.active,item.disabled && {opacity:.45}]}>
      <Icon name={item.icon} size={16} color={item.id === selected ? colors.forest : colors.muted}/>
      <Text style={[textStyle(rtl,"semibold"),styles.label,{color:item.id === selected ? colors.forest : colors.muted}]}>{item.title}{item.badge ? ` (${item.badge})` : ""}</Text>
    </TabButton>)}
  </View>;
}
const styles=StyleSheet.create({
  row:{flexDirection:"row",flexWrap:"wrap",paddingHorizontal:18,paddingBottom:10,gap:6},
  tab:{minHeight:54,paddingHorizontal:4,paddingVertical:7,borderRadius:17,justifyContent:"center",alignItems:"center",backgroundColor:colors.white,borderWidth:1,borderColor:colors.line},
  active:{backgroundColor:colors.mint,borderColor:"#B9D7C9"},
  label:{fontSize:10,lineHeight:18,textAlign:"center"},
});
