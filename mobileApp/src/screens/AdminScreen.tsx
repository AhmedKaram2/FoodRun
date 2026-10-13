import React, { useEffect, useState } from "react";
import { ActivityIndicator, Pressable, RefreshControl, StyleSheet, Text, TextInput, View } from "react-native";
import type { Snapshot } from "../types";
import { colors, tx } from "../theme";
import { dispatch, update } from "../native";
import { allActions, allCards, allFields } from "../guidedFlows";
import { action, Button, CardView, FieldInput, textStyle } from "../components/Controls";
import ScreenTabs from "../components/ScreenTabs";
import ScrollArea, { ScrollLoad } from "../components/ScrollArea";
import Icon from "../components/Icon";

const icons: Record<string, string> = { overview: "room", users: "profile", rooms: "room", history: "history", wallets: "wallet", restaurants: "food", blocks: "shield", cleanup: "shield", settings: "settings" };

export default function AdminScreen({ snapshot }: { snapshot: Snapshot }) {
  const { rtl, busy, page } = snapshot.state;
  const cards = allCards(snapshot), fields = allFields(snapshot), actions = allActions(snapshot);
  const tabs = cards.find(card => card.id === "admin-tabs");
  const selected = tabs?.buttons.find(button => button.primary || !button.enabled)?.value || "overview";
  const search = fields.find(field => field.key === "ADMIN_SEARCH");
  const [limit, setLimit] = useState(8);
  useEffect(() => setLimit(8), [selected, search?.value, page]);
  const metrics = cards.filter(card => card.id.startsWith("admin-metric:"));
  const entries = cards.filter(card => card !== tabs && !metrics.includes(card));
  const title = page === "ADMIN_USER" ? tx(rtl, fields.some(field => field.key === "ADMIN_EMAIL") ? "Add user" : "Edit user details", fields.some(field => field.key === "ADMIN_EMAIL") ? "إضافة مستخدم" : "تعديل بيانات المستخدم")
    : page === "ADMIN_CONFIRM" ? tx(rtl, "Review admin action", "مراجعة إجراء الإدارة") : tx(rtl, "Admin panel", "لوحة الإدارة");
  const paymentMethod = fields.find(field => field.key === "ADMIN_METHOD")?.value;
  const formFields = fields.filter(field => field !== search &&
    !(paymentMethod === "none" && ["ADMIN_HOLDER", "ADMIN_BANK", "ADMIN_IDENTIFIER"].includes(field.key)) &&
    !(paymentMethod === "aani" && field.key === "ADMIN_BANK"));
  const otherActions = actions.filter(button => button !== snapshot.primaryAction && button.action !== "OPEN_ADMIN");
  const photo = fields.find(field => field.key === "ADMIN_PHOTO");

  if (!snapshot.authenticated || !snapshot.adminAvailable || snapshot.supportActive) return <View style={styles.content}>
    <Text style={textStyle(rtl)}>{tx(rtl, "This account cannot access administration.", "هذا الحساب لا يستطيع فتح لوحة الإدارة.")}</Text>
    <Button action={action(tx(rtl, "Home", "الرئيسية"), "OPEN_HOME")} rtl={rtl}/>
  </View>;

  return <View style={{ flex: 1 }}>
    <View style={[styles.heading, rtl && styles.reverse]}>
      <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl, "Back", "رجوع")} onPress={() => dispatch("BACK")} style={styles.icon}>
        <View style={rtl && { transform: [{ scaleX: -1 }] }}><Icon name="back" size={20}/></View>
      </Pressable>
      <View style={{ flex: 1, minWidth: 0 }}>
        <Text accessibilityRole="header" style={[textStyle(rtl, "bold"), styles.title]}>{title}</Text>
        {page === "ADMIN" && <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Manage FoodRun from your phone.", "تحكم في FoodRun من هاتفك.")}</Text>}
      </View>
      {busy ? <ActivityIndicator color={colors.forest}/> : page === "ADMIN" && <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl, "Refresh", "تحديث")} onPress={() => dispatch("OPEN_ADMIN")} style={styles.icon}><Icon name="refresh" size={20} color={colors.forest}/></Pressable>}
    </View>
    {page === "ADMIN" && tabs && <ScreenTabs rtl={rtl} selected={selected} items={tabs.buttons.map(button => ({ id: button.value, title: button.title, icon: icons[button.value] || "settings", disabled: busy, testID: `admin-tab:${button.value}` }))} onChange={id => {
      const button = tabs.buttons.find(button => button.value === id);
      if (button?.enabled && !busy) dispatch(button.action, button.value);
    }}/>}
    <ScrollArea testID="admin-content" keyboardShouldPersistTaps="handled" refreshControl={page === "ADMIN" ? <RefreshControl refreshing={busy} onRefresh={() => dispatch("OPEN_ADMIN")} tintColor={colors.forest}/> : undefined} contentContainerStyle={styles.content}>
      {!!search && <FieldInput field={search} rtl={rtl} busy={busy}/>}
      {!!metrics.length && <View style={[styles.metrics, rtl && styles.reverse]}>{metrics.map(card => <View key={card.id} style={styles.metric}>
        <Text style={[textStyle(rtl), styles.detail]}>{card.title}</Text>
        <Text style={[textStyle(rtl, "bold"), styles.metricValue]}>{card.detail}</Text>
      </View>)}</View>}
      {formFields.map(field => <View key={field.key} style={{ gap: 8 }}>
        <FieldInput field={field} rtl={rtl} busy={busy}/>
        {field === photo && <>
          <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Photo URL (optional)", "رابط الصورة (اختياري)")}</Text>
          <TextInput accessibilityLabel={tx(rtl, "Photo URL (optional)", "رابط الصورة (اختياري)")} value={field.value.startsWith("data:") ? "" : field.value} placeholder="https://…" autoCapitalize="none" autoCorrect={false} editable={!busy} onChangeText={value=>update("ADMIN_PHOTO",value)} style={[textStyle(rtl), styles.url]}/>
        </>}
      </View>)}
      {entries.slice(0, limit).map(card => <CardView key={card.id} card={card} rtl={rtl} busy={busy} compactActions/>)}
      {entries.length > limit && <ScrollLoad loadKey={`${selected}:${search?.value}:${limit}`} onLoad={() => setLimit(value => value + 8)}><Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Scroll for more", "مرّر لعرض المزيد")}</Text></ScrollLoad>}
      {otherActions.map((button, index) => <Button key={button.action + button.value + index} action={button} rtl={rtl} busy={busy}/>)}
      {!busy && page === "ADMIN" && tabs && !entries.length && !metrics.length && !formFields.length && <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "No records in this section.", "لا توجد بيانات في هذا القسم.")}</Text>}
      {!tabs && page === "ADMIN" && <Button action={action(tx(rtl, "Refresh", "تحديث"), "OPEN_ADMIN")} rtl={rtl} busy={busy}/>}
    </ScrollArea>
    {!!snapshot.primaryAction && <View style={styles.footer}><Button action={snapshot.primaryAction} busy={busy} rtl={rtl}/></View>}
  </View>;
}

const styles = StyleSheet.create({
  heading: { flexDirection: "row", alignItems: "center", gap: 8, paddingHorizontal: 16, paddingVertical: 10 },
  reverse: { flexDirection: "row-reverse" }, icon: { minHeight: 44, minWidth: 44, alignItems: "center", justifyContent: "center" },
  title: { fontSize: 20, lineHeight: 30 }, detail: { fontSize: 12, lineHeight: 21, color: colors.muted },
  content: { padding: 18, paddingTop: 8, paddingBottom: 28, gap: 14 },
  metrics: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  metric: { flexBasis: "46%", flexGrow: 1, padding: 14, borderRadius: 16, backgroundColor: colors.mint, gap: 6 },
  metricValue: { fontSize: 19, lineHeight: 30, color: colors.forest },
  url: { minHeight: 50, borderWidth: 1, borderColor: colors.line, borderRadius: 15, padding: 12, backgroundColor: colors.white },
  footer: { paddingHorizontal: 18, paddingVertical: 10, borderTopWidth: 1, borderTopColor: colors.line, backgroundColor: colors.cream },
});
