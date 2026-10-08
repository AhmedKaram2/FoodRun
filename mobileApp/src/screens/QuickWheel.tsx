import React, { useEffect, useRef, useState } from "react";
import {
  Animated,
  Easing,
  ScrollView,
  Text,
  TextInput,
  View,
} from "react-native";
import type { QuickWheel as State } from "../types";
import { quickAction } from "../native";
import { colors, tx } from "../theme";
import { Button, action, textStyle, styles } from "../components/Controls";
import { WheelDrawing } from "../components/LiveWheel";
import BottomSheet from "../components/BottomSheet";
export default function QuickWheel({
  state,
  rtl,
}: {
  state: State;
  rtl: boolean;
}) {
  const rotation = useRef(new Animated.Value(0)).current,
    [angle, setAngle] = useState(0),
    latest = useRef(state);
  latest.current = state;
  useEffect(() => {
    const event = rotation.addListener((value) => setAngle(value.value));
    return () => rotation.removeListener(event);
  }, [rotation]);
  useEffect(() => {
    if (!state.spinning) return;
    rotation.setValue(state.startRotation);
    const anim = Animated.timing(rotation, {
      toValue: state.endRotation,
      duration: 6500,
      easing: Easing.out(Easing.poly(4)),
      useNativeDriver: false,
    });
    anim.start(({ finished }) => {
      if (finished) quickAction("finish");
    });
    return () => anim.stop();
  }, [state.spinning, state.endRotation, rotation]);
  useEffect(
    () => () => {
      if (latest.current.spinning) quickAction("cancel");
    },
    [],
  );
  const names = state.people
    .filter((value) => value.active)
    .map((value) => value.name);
  return (
    <View style={{flex:1}}>
    <ScrollView
      keyboardShouldPersistTaps="handled"
      contentContainerStyle={{ padding: 16, gap: 14 }}
    >
        <>
          <Text style={[textStyle(rtl, "bold"), { fontSize: 26, lineHeight: 40 }]}>
            {tx(rtl, "Who’s getting the food?", "من سيتولى إحضار الطعام؟")}
          </Text>
          <WheelDrawing names={names} rotation={angle} />
          {!!state.winner && (
            <Text
              style={[
                textStyle(rtl, "bold"),
                { fontSize: 27, textAlign: "center" },
              ]}
            >
              {state.winner}
            </Text>
          )}
          <Button
            rtl={rtl}
            busy={state.spinning}
            action={{
              ...action(
                tx(rtl, "Pick someone", "اختيار شخص"),
                "QUICK",
                String(angle),
                true,
              ),
              enabled: state.canSpin && names.length > 0,
            }}
            onPress={() => quickAction("spin", String(angle))}
          />
          <View style={{ flexDirection: rtl ? "row-reverse" : "row", gap: 8 }}>
            <View style={{ flex: 1 }}>
              <Button
                rtl={rtl}
                busy={state.spinning}
                action={action(tx(rtl, "People", "الأشخاص"), "QUICK")}
                onPress={() => quickAction("crew")}
              />
            </View>
            <View style={{ flex: 1 }}>
              <Button
                rtl={rtl}
                busy={state.spinning}
                action={action(tx(rtl, "History", "السجل"), "QUICK")}
                onPress={() => quickAction("history")}
              />
            </View>
          </View>
        </>
    </ScrollView>
    <BottomSheet visible={["CREW","ADD_PERSON","HISTORY"].includes(state.destination)} rtl={rtl} title={state.destination==="HISTORY" ? tx(rtl,"Previous picks","الاختيارات السابقة") : tx(rtl,"Your people","أصدقاؤك")} onClose={()=>quickAction("dismiss")}>
      <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={{gap:12}}>
      {state.destination === "HISTORY" ? (
        <>
          {!state.history.length && <Text style={textStyle(rtl)}>{tx(rtl,"Your picks will appear here.","ستظهر اختياراتك هنا.")}</Text>}
          {state.history.map((item) => (
            <View style={styles.card} key={item.id}>
              <Text style={textStyle(rtl, "semibold")}>{item.name}</Text>
              <Text style={textStyle(rtl)}>{item.date}</Text>
            </View>
          ))}
          <Button
            rtl={rtl}
            action={action(tx(rtl, "Back", "رجوع"), "QUICK")}
            onPress={() => quickAction("dismiss")}
          />
        </>
      ) : (
        <>

          <Button
            rtl={rtl}
            action={action(
              tx(rtl, "Include everyone", "مشاركة الجميع"),
              "QUICK",
            )}
            onPress={() => quickAction("everyone")}
          />
          {state.people.map((person) => (
            <View
              key={person.id}
              style={[
                styles.card,
                { flexDirection: "row", alignItems: "center" },
              ]}
            >
              <View style={{ flex: 1 }}>
                <Text style={textStyle(rtl, "semibold")}>{person.name}</Text>
              </View>
              <Button
                rtl={rtl}
                action={action(
                  tx(
                    rtl,
                    person.active ? "Included" : "Include",
                    person.active ? "مشارك" : "مشاركة",
                  ),
                  "QUICK",
                )}
                onPress={() => quickAction("toggle", String(person.id))}
              />
              {person.removable && (
                <Button
                  rtl={rtl}
                  action={action(tx(rtl, "Remove", "إزالة"), "QUICK")}
                  onPress={() => quickAction("remove", String(person.id))}
                />
              )}
            </View>
          ))}
          {state.destination === "ADD_PERSON" ? (
            <>
              <TextInput
                style={[styles.input, textStyle(rtl)]}
                accessibilityLabel={tx(rtl, "Name", "الاسم")}
                value={state.nameDraft}
                onChangeText={(value) => quickAction("name", value)}
                maxLength={32}
              />
              {!!state.error && (
                <Text style={{ color: colors.danger }}>{state.error}</Text>
              )}
              <Button
                rtl={rtl}
                action={action(
                  tx(rtl, "Save person", "حفظ الشخص"),
                  "QUICK",
                  "",
                  true,
                )}
                onPress={() => quickAction("save")}
              />
            </>
          ) : (
            <Button
              rtl={rtl}
              action={action(tx(rtl, "Add person", "إضافة شخص"), "QUICK")}
              onPress={() => quickAction("add")}
            />
          )}
          <Button
            rtl={rtl}
            action={action(tx(rtl, "Back", "رجوع"), "QUICK")}
            onPress={() => quickAction("dismiss")}
          />
        </>
      )}
      </ScrollView>
    </BottomSheet>
    </View>
  );
}
