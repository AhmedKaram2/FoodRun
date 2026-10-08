import React, { useEffect, useRef, useState } from "react";
import {
  Alert,
  Image,
  Keyboard,
  Modal,
  Pressable,
  ScrollView,
  StyleSheet,
  Switch,
  Text,
  TextInput,
  View,
} from "react-native";
import type { Action, Card, Field } from "../types";
import { colors, font, tx } from "../theme";
import { dispatch, native, update } from "../native";
import Icon from "./Icon";
export const textStyle = (
  rtl: boolean,
  weight: "regular" | "medium" | "semibold" | "bold" = "regular",
) => ({
  fontFamily: font(weight, rtl),
  textAlign: rtl ? ("right" as const) : ("left" as const),
  color: colors.ink,
});
export function Button({
  action,
  busy = false,
  rtl = false,
  onPress,
}: {
  action: Action;
  busy?: boolean;
  rtl?: boolean;
  onPress?: () => void;
}) {
  const disabled = busy || !action.enabled;
  const press = () => {
    Keyboard.dismiss();
    const run = () =>
      onPress ? onPress() : dispatch(action.action, action.value);
    if (action.destructive)
      Alert.alert(
        action.title,
        tx(rtl, "Confirm this action?", "هل تريد تأكيد هذا الإجراء؟"),
        [
          { text: tx(rtl, "Cancel", "إلغاء"), style: "cancel" },
          { text: action.title, style: "destructive", onPress: run },
        ],
      );
    else run();
  };
  return (
    <Pressable
      accessibilityRole="button"
      accessibilityState={{ disabled }}
      testID={"action:" + action.action + ":" + action.value}
      disabled={disabled}
      onPress={press}
      style={({ pressed }) => [
        styles.button,
        action.primary ? styles.primary : styles.secondary,
        action.destructive && styles.danger,
        disabled && styles.disabled,
        pressed && { opacity: 0.75 },
      ]}
    >
      <Text
        style={[
          textStyle(rtl, "semibold"),
          styles.buttonText,
          {
            color: action.primary
              ? colors.white
              : action.destructive
                ? colors.danger
                : colors.ink,
          },
        ]}
      >
        {action.title}
      </Text>
    </Pressable>
  );
}
export const action = (
  title: string,
  kind: string,
  value = "",
  primary = false,
): Action => ({
  title,
  action: kind,
  value,
  primary,
  destructive: false,
  enabled: true,
});
export function FieldInput({
  field,
  busy,
  rtl,
}: {
  field: Field;
  busy: boolean;
  rtl: boolean;
}) {
  const [select, setSelect] = useState(false),
    [secret, setSecret] = useState(""),
    [photoError, setPhotoError] = useState("");
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    if (field.secret) update(field.key, "");
    return () => {
      mounted.current = false;
    };
  }, [field.key]);
  const value = field.secret ? secret : field.value;
  const change = (next: string) => {
    if (field.secret) setSecret(next);
    update(field.key, next);
  };
  const isPhoto = ["PHOTO", "ADMIN_PHOTO", "RECEIPT_PHOTO"].includes(field.key);
  const selected = field.choices.find(
    (choice) => choice.value === value,
  )?.label;
  return (
    <View style={styles.field}>
      <Text style={[textStyle(rtl, "medium"), styles.label]}>
        {field.label}
      </Text>
      {isPhoto ? (
        <>
          {!!value && (
            <Image
              source={{ uri: value }}
              accessibilityLabel={field.label}
              style={{
                height: field.key === "RECEIPT_PHOTO" ? 220 : 100,
                width: field.key === "RECEIPT_PHOTO" ? "100%" : 100,
                borderRadius: 16,
                alignSelf: "center",
              }}
              resizeMode="contain"
            />
          )}
          <Button
            rtl={rtl}
            busy={busy}
            action={action(tx(rtl, "Choose photo", "اختر صورة"), "PHOTO")}
            onPress={async () => {
              setPhotoError("");
              try {
                const image = await native.photo(field.key);
                if (image && mounted.current) change(image);
              } catch (error) {
                if (mounted.current) setPhotoError((error as Error).message);
              }
            }}
          />
          {!!value && (
            <Button
              rtl={rtl}
              busy={busy}
              action={action(tx(rtl, "Remove photo", "إزالة الصورة"), "PHOTO")}
              onPress={() => change("")}
            />
          )}
          {!!photoError && (
            <Text style={{ color: colors.danger }}>{photoError}</Text>
          )}
        </>
      ) : field.toggle ? (
        <View style={[styles.toggle, rtl && { flexDirection: "row-reverse" }]}>
          <Text style={textStyle(rtl)}>
            {tx(
              rtl,
              value === "true" ? "Enabled" : "Disabled",
              value === "true" ? "مفعل" : "متوقف",
            )}
          </Text>
          <Switch
            disabled={busy}
            value={value === "true"}
            onValueChange={(next) => change(String(next))}
            trackColor={{ false: "#D5DBD5", true: colors.green }}
            thumbColor={colors.white}
          />
        </View>
      ) : field.choices.length ? (
        <>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={field.label}
            accessibilityState={{ disabled: busy, expanded: select }}
            disabled={busy}
            testID={field.key}
            onPress={() => setSelect(true)}
            style={styles.input}
          >
            <Text style={[textStyle(rtl), styles.inputText]}>
              {selected || tx(rtl, "Choose…", "اختر…")}
            </Text>
          </Pressable>
          <Modal
            visible={select}
            transparent
            animationType="slide"
            onRequestClose={() => setSelect(false)}
          >
            <View style={styles.backdrop}>
              <View style={styles.sheet}>
                <View style={styles.heading}>
                  <Text style={[textStyle(rtl, "bold"), styles.sheetTitle]}>
                    {field.label}
                  </Text>
                  <Pressable
                    accessibilityLabel={tx(rtl, "Close", "إغلاق")}
                    style={styles.iconButton}
                    onPress={() => setSelect(false)}
                  >
                    <Icon name="close" />
                  </Pressable>
                </View>
                <ScrollView keyboardShouldPersistTaps="handled">
                  {field.choices.map((choice) => (
                    <Pressable
                      key={choice.value}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: value === choice.value }}
                      style={styles.choice}
                      onPress={() => {
                        change(choice.value);
                        setSelect(false);
                      }}
                    >
                      <Text style={[textStyle(rtl), { flex: 1 }]}>
                        {choice.label}
                      </Text>
                      {choice.value === value && (
                        <Icon name="check" color={colors.green} />
                      )}
                    </Pressable>
                  ))}
                </ScrollView>
              </View>
            </View>
          </Modal>
        </>
      ) : (
        <TextInput
          editable={!busy}
          testID={field.key}
          accessibilityLabel={field.label}
          value={value}
          onChangeText={change}
          secureTextEntry={field.secret}
          multiline={field.multiline}
          numberOfLines={field.multiline ? 4 : 1}
          autoCapitalize={
            /EMAIL|PASSWORD|IDENTIFIER|CODE|PHONE|HUB|FINGERPRINT/.test(
              field.key,
            )
              ? "none"
              : "sentences"
          }
          autoCorrect={
            !/EMAIL|PASSWORD|IDENTIFIER|CODE|PHONE|HUB|FINGERPRINT/.test(
              field.key,
            )
          }
          keyboardType={
            /EMAIL/.test(field.key)
              ? "email-address"
              : /PHONE/.test(field.key)
                ? "phone-pad"
                : /AMOUNT|PRICE|TOTAL|FEE|QUANTITY|MINUTES|HOURS|TAX|DISCOUNT|ADJUSTMENT|RECEIVED/.test(
                      field.key,
                    )
                  ? "decimal-pad"
                  : /ROOM_CODE/.test(field.key)
                    ? "number-pad"
                    : "default"
          }
          textContentType={
            field.key === "PASSWORD"
              ? "password"
              : field.key === "EMAIL"
                ? "emailAddress"
                : undefined
          }
          style={[
            styles.input,
            styles.inputText,
            textStyle(rtl),
            field.multiline && { minHeight: 112, textAlignVertical: "top" },
          ]}
        />
      )}
    </View>
  );
}
export function CardView({
  card,
  busy,
  rtl,
}: {
  card: Card;
  busy: boolean;
  rtl: boolean;
}) {
  const contents = (
    <>
      <View
        style={[styles.cardHeading, rtl && { flexDirection: "row-reverse" }]}
      >
        <View style={styles.avatar}>
          <Text
            style={{
              fontFamily: font("bold", rtl),
              fontSize: 20,
              color: colors.ink,
            }}
          >
            {Array.from(card.title)[0]}
          </Text>
        </View>
        <Text style={[textStyle(rtl, "semibold"), styles.cardTitle]}>
          {card.title}
        </Text>
        {card.selection && (
          <Icon name="wallet" size={18} color={colors.muted} />
        )}
      </View>
      {!!card.badge && (
        <Text style={[textStyle(rtl, "medium"), styles.badge]}>
          {card.badge}
        </Text>
      )}
      {!!card.image && (
        <Image
          source={{ uri: card.image }}
          resizeMode="contain"
          style={styles.cardImage}
          accessibilityLabel={card.title}
        />
      )}
      {!!card.detail && (
        <Text selectable style={[textStyle(rtl), styles.detail]}>
          {card.detail}
        </Text>
      )}
      {card.buttons.length > 0 && (
        <View style={styles.actions}>
          {card.buttons.map((button, index) => (
            <Button
              key={button.action + button.value + index}
              action={button}
              busy={busy}
              rtl={rtl}
            />
          ))}
        </View>
      )}
    </>
  );
  return card.selection ? (
    <Pressable
      accessibilityRole="button"
      accessibilityLabel={card.title}
      disabled={busy}
      onPress={() => dispatch(card.selection!.action, card.selection!.value)}
      style={styles.card}
    >
      {contents}
    </Pressable>
  ) : (
    <View style={styles.card}>{contents}</View>
  );
}
export function PagedCards({
  cards,
  busy,
  rtl,
  pageSize = 8,
}: {
  cards: Card[];
  busy: boolean;
  rtl: boolean;
  pageSize?: number;
}) {
  const [page, setPage] = useState(0),
    pages = Math.ceil(cards.length / pageSize),
    current = Math.min(page, Math.max(0, pages - 1));
  return (
    <View style={{ gap: 12 }}>
      {cards.slice(current * pageSize, (current + 1) * pageSize).map((card) => (
        <CardView key={card.id} card={card} busy={busy} rtl={rtl} />
      ))}
      {pages > 1 && (
        <View style={styles.pagination}>
          <Button
            rtl={rtl}
            action={{
              ...action(tx(rtl, "Previous", "السابق"), "PAGE"),
              enabled: current > 0,
            }}
            onPress={() => setPage(current - 1)}
          />
          <Text style={textStyle(rtl)}>
            {current + 1} / {pages}
          </Text>
          <Button
            rtl={rtl}
            action={{
              ...action(tx(rtl, "Next", "التالي"), "PAGE"),
              enabled: current + 1 < pages,
            }}
            onPress={() => setPage(current + 1)}
          />
        </View>
      )}
    </View>
  );
}
export const styles = StyleSheet.create({
  button: {
    minHeight: 48,
    borderRadius: 15,
    paddingHorizontal: 16,
    paddingVertical: 13,
    justifyContent: "center",
    alignItems: "center",
  },
  primary: { backgroundColor: colors.ink },
  secondary: {
    backgroundColor: colors.white,
    borderColor: colors.line,
    borderWidth: 1,
  },
  danger: { borderColor: "#EAC7C0", backgroundColor: "#FFF0EB" },
  disabled: { opacity: 0.45 },
  buttonText: { fontSize: 14, textAlign: "center" },
  field: { gap: 7 },
  label: { fontSize: 14 },
  input: {
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 15,
    paddingHorizontal: 14,
    paddingVertical: 12,
    minHeight: 50,
    backgroundColor: colors.white,
  },
  inputText: { fontSize: 16 },
  toggle: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    minHeight: 48,
    paddingHorizontal: 12,
    backgroundColor: colors.white,
    borderRadius: 15,
  },
  backdrop: { flex: 1, justifyContent: "flex-end", backgroundColor: "#0007" },
  sheet: {
    maxHeight: "85%",
    backgroundColor: colors.cream,
    borderTopLeftRadius: 26,
    borderTopRightRadius: 26,
    padding: 20,
    paddingBottom: 35,
    gap: 18,
  },
  heading: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 10,
  },
  sheetTitle: { fontSize: 20, flex: 1 },
  iconButton: {
    width: 48,
    height: 48,
    justifyContent: "center",
    alignItems: "center",
  },
  choice: {
    flexDirection: "row",
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderColor: colors.line,
    gap: 12,
  },
  card: {
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    gap: 12,
  },
  cardHeading: { flexDirection: "row", alignItems: "center", gap: 10 },
  cardTitle: { fontSize: 17, flex: 1 },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 14,
    backgroundColor: colors.mint,
    alignItems: "center",
    justifyContent: "center",
  },
  badge: {
    alignSelf: "flex-start",
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 9,
    backgroundColor: colors.mint,
    fontSize: 12,
  },
  detail: { fontSize: 14, lineHeight: 24 },
  actions: { gap: 8 },
  cardImage: { width: "100%", height: 300 },
  pagination: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
});
