import React, { useEffect, useRef, useState } from "react";
import {
  Alert,
  Animated,
  Image,
  Keyboard,
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
import { usePressMotion } from "./Motion";
import BottomSheet from "./BottomSheet";
export const textStyle = (
  rtl: boolean,
  weight: "regular" | "medium" | "semibold" | "bold" = "regular",
) => ({
  fontFamily: font(weight, rtl),
  writingDirection: rtl ? ("rtl" as const) : ("ltr" as const),
  includeFontPadding: false,
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
  const motion = usePressMotion();
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
      onPressIn={motion.onPressIn}
      onPressOut={motion.onPressOut}
      style={({ pressed }) => [
        styles.button,
        action.primary ? styles.primary : styles.secondary,
        action.destructive && styles.danger,
        disabled && styles.disabled,
        pressed && { opacity: 0.75 },
      ]}
    >
      <Animated.Text
        style={[
          textStyle(rtl, "semibold"),
          styles.buttonText,
          motion.style,
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
      </Animated.Text>
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
    [query, setQuery] = useState(""),
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
            onPress={() => { setQuery(""); setSelect(true); }}
            style={[styles.input, styles.selector, rtl && {flexDirection:"row-reverse"}]}
          >
            <Text style={[textStyle(rtl), styles.inputText, {flex:1}]}>
              {selected || tx(rtl, "Choose…", "اختر…")}
            </Text>
            <Icon name="chevron" size={18} color={colors.muted}/>
          </Pressable>
          <BottomSheet
            visible={select}
            title={field.label}
            rtl={rtl}
            onClose={() => setSelect(false)}
          >
                {field.choices.length > 8 && <TextInput accessibilityLabel={tx(rtl,"Search options","البحث في الخيارات")} placeholder={tx(rtl,"Search options","البحث في الخيارات")} placeholderTextColor={colors.muted} value={query} onChangeText={setQuery} style={[styles.input,textStyle(rtl)]}/>}
                <ScrollView keyboardShouldPersistTaps="handled">
                  {field.choices.filter(choice => choice.label.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())).map((choice) => (
                    <Pressable
                      key={choice.value}
                      accessibilityRole="radio"
                      accessibilityState={{ selected: value === choice.value, disabled:busy }}
                      disabled={busy}
                      style={[styles.choice, value === choice.value && styles.selectedChoice, rtl && {flexDirection:"row-reverse"}]}
                      onPress={() => {
                        change(choice.value);
                        setSelect(false);
                      }}
                    >
                      <Text style={[textStyle(rtl), { flex: 1 }]}>
                        {choice.label}
                      </Text>
                      <View style={[styles.radio, value === choice.value && styles.selectedRadio]}>{choice.value === value && <Icon name="check" size={14} color={colors.white}/>}</View>
                    </Pressable>
                  ))}
                  {!field.choices.some(choice => choice.label.toLocaleLowerCase().includes(query.trim().toLocaleLowerCase())) && <Text style={[textStyle(rtl),{paddingVertical:24}]}>{tx(rtl,"No matching options","لا توجد خيارات مطابقة")}</Text>}
                </ScrollView>
          </BottomSheet>
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
  const person = /^(member:|person:|friend-result:)/.test(card.id);
  const icon = /(wallet|receipt|transfer|payment|account)/.test(card.id) ? "wallet"
    : /^(menu:|cart:|half-item:|estimate)/.test(card.id) ? "food" : "room";
  const contents = (
    <>
      <View
        style={[styles.cardHeading, rtl && { flexDirection: "row-reverse" }]}
      >
        <View style={styles.avatar}>
          {person ?
          <Text
            style={{
              fontFamily: font("bold", rtl),
              fontSize: 20,
              color: colors.ink,
            }}
          >
            {Array.from(card.title)[0]}
          </Text>
          : <Icon name={icon} color={colors.forest} />}
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
  primary: { backgroundColor: colors.primary },
  secondary: {
    backgroundColor: colors.white,
    borderColor: colors.line,
    borderWidth: 1,
  },
  danger: { borderColor: "#EAC7C0", backgroundColor: "#FFF0EB" },
  disabled: { opacity: 0.45 },
  buttonText: { fontSize: 15, lineHeight: 24, textAlign: "center" },
  field: { gap: 7 },
  label: { fontSize: 14, lineHeight: 24, color: colors.muted },
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
  selector:{flexDirection:"row",alignItems:"center",gap:10},
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
    alignItems:"center",
    minHeight:56,
    paddingHorizontal:12,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderColor: colors.line,
    gap: 12,
  },
  selectedChoice:{backgroundColor:colors.mint,borderRadius:14},
  radio:{height:22,width:22,borderRadius:11,borderWidth:1.5,borderColor:colors.line,alignItems:"center",justifyContent:"center"},
  selectedRadio:{backgroundColor:colors.forest,borderColor:colors.forest},
  card: {
    padding: 16,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    gap: 12,
  },
  cardHeading: { flexDirection: "row", alignItems: "center", gap: 10 },
  cardTitle: { fontSize: 17, lineHeight: 28, flex: 1 },
  avatar: {
    width: 40,
    height: 40,
    borderRadius: 14,
    backgroundColor: colors.coralWash,
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
  detail: { fontSize: 15, lineHeight: 26 },
  actions: { gap: 8 },
  cardImage: { width: "100%", height: 300 },
  pagination: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    gap: 8,
  },
});
