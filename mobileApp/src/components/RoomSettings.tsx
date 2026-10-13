import React from "react";
import { StyleSheet, Text, View } from "react-native";
import type { Action, Card, Field, Snapshot } from "../types";
import { colors, tx } from "../theme";
import { dispatch } from "../native";
import { action, Button, CardView, FieldInput, textStyle } from "./Controls";
import ScrollArea from "./ScrollArea";

const managementActions = new Set(["CANCEL", "ARCHIVE", "NEXT_ORDER"]);

export default function RoomSettings({ snapshot, cards, fields, actions, onExit }: {
  snapshot: Snapshot; cards: Card[]; fields: Field[]; actions: Action[]; onExit: () => void;
}) {
  const { rtl, busy } = snapshot.state;
  const available = [snapshot.primaryAction, ...snapshot.inlineButtons, ...snapshot.utilityButtons]
    .filter((button): button is Action => !!button && managementActions.has(button.action))
    .filter((button, index, buttons) => buttons.findIndex(other => other.action === button.action && other.value === button.value) === index);
  const labels = (button: Action) => {
    if (button.action === "CANCEL") return {
      title: tx(rtl, "Cancel the current order", "إلغاء الطلب الحالي"),
      detail: tx(rtl, "Cancel this order for everyone. The room stays available for a new order.", "يلغي الطلب الحالي لكل الأعضاء. تظل الغرفة متاحة لطلب جديد."),
    };
    if (button.action === "ARCHIVE") return {
      title: tx(rtl, "Complete and archive this order", "إنهاء الطلب وأرشفته"),
      detail: tx(rtl, "Close this order after delivery and payment settlement. Receipts stay in Past orders.", "ينهي الطلب بعد استلام الطعام وتسوية المدفوعات. تظل الإيصالات في الطلبات السابقة."),
    };
    return {
      title: tx(rtl, "Start a new order in this room", "بدء طلب جديد في الغرفة"),
      detail: tx(rtl, "Prepare a fresh order with the same members. Previous orders and receipts stay saved.", "يجهز طلباً جديداً بنفس الأعضاء. تظل الطلبات والإيصالات السابقة محفوظة."),
    };
  };
  return <ScrollArea keyboardShouldPersistTaps="handled" contentContainerStyle={{ gap: 16 }}>
    <Text style={[textStyle(rtl, "semibold"), styles.roomName]}>{snapshot.state.title}</Text>
    <View style={styles.management}>
      <Text accessibilityRole="header" style={[textStyle(rtl, "semibold"), styles.heading]}>{tx(rtl, "Close or reset the order", "إنهاء الطلب أو بدء طلب جديد")}</Text>
      {available.map(button => {
        const copy = labels(button);
        return <View key={button.action + button.value} style={{ gap: 6 }}>
          <Button action={{ ...button, title: copy.title, primary: false, destructive: button.action === "CANCEL" || button.destructive }} busy={busy} rtl={rtl}/>
          <Text style={[textStyle(rtl), styles.detail]}>{copy.detail}</Text>
        </View>;
      })}
      {!available.length && <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Order controls appear here when your role and the order stage allow them.", "تظهر إجراءات الطلب هنا حسب صلاحياتك ومرحلة الطلب.")}</Text>}
      <Button action={action(tx(rtl, "Exit room", "الخروج من الغرفة"), "OPEN_ROOMS")} busy={busy} rtl={rtl} onPress={onExit}/>
      <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Return to your rooms list. You can open this room again later.", "يرجع لقائمة الغرف. يمكنك فتح هذه الغرفة مرة أخرى لاحقاً.")}</Text>
      {snapshot.adminAvailable && <>
        <Button action={action(tx(rtl, "Delete room in administration", "حذف الغرفة من الإدارة"), "OPEN_ADMIN")} busy={busy} rtl={rtl} onPress={()=>{dispatch("ADMIN_TAB","rooms");dispatch("OPEN_ADMIN");}}/>
        <Text style={[textStyle(rtl), styles.detail]}>{tx(rtl, "Open Rooms in administration, select this room and confirm its code to delete it permanently. Active orders and unsettled payments must be resolved first.", "يفتح الغرف في الإدارة. اختر هذه الغرفة وأكد رمزها لحذفها نهائياً بعد إنهاء الطلبات النشطة وتسوية المدفوعات.")}</Text>
      </>}
    </View>
    {cards.map(card => <CardView key={card.id} card={card} busy={busy} rtl={rtl}/>)}
    {fields.map(field => <FieldInput key={field.key} field={field} busy={busy} rtl={rtl}/>)}
    {actions.filter(button => !managementActions.has(button.action)).map((button, index) => <Button key={button.action + index} action={button.action === "SHARE_ROOM" ? { ...button, title: tx(rtl, "Share room", "مشاركة الغرفة") } : button} busy={busy} rtl={rtl}/>)}
  </ScrollArea>;
}

const styles = StyleSheet.create({
  roomName: { fontSize: 14 },
  management: { gap: 12, padding: 14, borderWidth: 1, borderColor: colors.line, borderRadius: 18, backgroundColor: colors.white },
  heading: { fontSize: 15, lineHeight: 24 },
  detail: { fontSize: 12, lineHeight: 21, color: colors.muted },
});
