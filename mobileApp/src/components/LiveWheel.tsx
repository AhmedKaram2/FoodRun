import React, { useEffect, useState } from "react";
import {
  AccessibilityInfo,
  AppState,
  StyleSheet,
  Text,
  View,
} from "react-native";
import Svg, { Circle, G, Path, Text as SvgText } from "react-native-svg";
import type { Wheel } from "../types";
import { colors, font, tx } from "../theme";
// The website and mobile apps use identical weighted geometry and server timing.
const geometry = require("../../../webApp/src/foodrun/wheel.js") as {
  WHEEL_PALETTE: string[];
  wheelSlice: (
    index: number,
    count: number,
    weights: number[],
  ) => { center: number; sweep: number };
  wheelSlicePath: (
    index: number,
    count: number,
    radius: number,
    center: number,
    weights: number[],
  ) => string;
  polarPoint: (angle: number, radius: number, center: number) => number[];
  spinRotation: (spin: unknown, now: number) => number;
  wheelLabel: (name: string) => string;
  runningNameIndex: (spin: unknown, now: number, count: number) => number;
};
export function WheelDrawing({
  names,
  weights = [],
  rotation = 0,
}: {
  names: string[];
  weights?: number[];
  rotation?: number;
}) {
  return (
    <View
      style={styles.drawing}
      accessible
      accessibilityLabel={names.join(", ")}
    >
      <Svg viewBox="0 0 400 420" width="100%" height="100%">
        <Circle cx={200} cy={200} r={188} fill={colors.white} />
        <G transform={`rotate(${rotation} 200 200)`}>
          {names.length === 1 ? (
            <Circle
              cx={200}
              cy={200}
              r={172}
              fill={geometry.WHEEL_PALETTE[0]}
            />
          ) : (
            names.map((name, index) => {
              const slice = geometry.wheelSlice(index, names.length, weights),
                point = geometry.polarPoint(-90 + slice.center, 116, 200);
              return (
                <G key={index}>
                  <Path
                    d={geometry.wheelSlicePath(
                      index,
                      names.length,
                      172,
                      200,
                      weights,
                    )}
                    fill={
                      geometry.WHEEL_PALETTE[
                        index % geometry.WHEEL_PALETTE.length
                      ]
                    }
                    stroke="#FFF"
                    strokeWidth={2}
                  />
                  <SvgText
                    x={point[0]}
                    y={point[1]}
                    fontSize={Math.max(8, Math.min(14, 90 / names.length + 7))}
                    fontWeight="600"
                    fill={colors.ink}
                    textAnchor="middle"
                    transform={`rotate(${slice.center} ${point[0]} ${point[1]})`}
                  >
                    {geometry.wheelLabel(name)}
                  </SvgText>
                </G>
              );
            })
          )}
        </G>
        <Circle cx={200} cy={200} r={35} fill={colors.ink} />
        <SvgText
          x={200}
          y={206}
          textAnchor="middle"
          fill="#FFF"
          fontSize={13}
          fontWeight="700"
        >
          FOODRUN
        </SvgText>
        <Path d="M185 6H215L200 33Z" fill={colors.coral} />
      </Svg>
    </View>
  );
}
export default function LiveWheel({
  wheel,
  rtl,
}: {
  wheel: Wheel;
  rtl: boolean;
}) {
  const [now, setNow] = useState(Date.now()),
    [reduced, setReduced] = useState(true),
    [active, setActive] = useState(AppState.currentState === "active");
  const spin = wheel.round,
    time = now + wheel.serverOffset,
    ended = time >= spin.startAt + spin.duration;
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled().then(value => { if(mounted) setReduced(value); }).catch(()=>{});
    const event = AccessibilityInfo.addEventListener(
      "reduceMotionChanged",
      setReduced,
    );
    const app=AppState.addEventListener("change",state=>{setActive(state==="active");if(state==="active")setNow(Date.now());});
    return () => { mounted=false; event.remove(); app.remove(); };
  }, []);
  useEffect(()=>setNow(Date.now()),[wheel.round.id,wheel.serverOffset]);
  useEffect(() => {
    if(!active || ended)return;
    const timer = setInterval(() => setNow(Date.now()), reduced ? 500 : 32);
    return () => clearInterval(timer);
  }, [wheel.round.id, reduced, active, ended]);
  const rotation = geometry.spinRotation(
    spin,
    reduced ? (ended ? spin.startAt + spin.duration : spin.startAt) : time,
  );
  const index = Math.min(
    wheel.names.length - 1,
    Math.max(0, geometry.runningNameIndex(spin, time, wheel.names.length)),
  );
  return (
    <View style={styles.card}>
      <Text style={[styles.caption,{fontFamily:font("semibold",rtl),letterSpacing:rtl?0:1.5}]}>
        {tx(rtl, "LIVE SELECTION", "الاختيار المباشر")}
      </Text>
      {wheel.style === "names" ? (
        <View style={styles.running}>
          <Text style={[styles.name, { fontFamily: font("bold", rtl) }]}>
            {ended ? wheel.winner : wheel.names[index]}
          </Text>
        </View>
      ) : (
        <WheelDrawing
          names={wheel.names}
          weights={spin.weights}
          rotation={rotation}
        />
      )}
      {ended && (
        <Text
          accessibilityLiveRegion="polite"
          style={[styles.winner, { fontFamily: font("bold", rtl) }]}
        >
          {wheel.winner}
        </Text>
      )}
      <Text style={[styles.help, { fontFamily: font("regular", rtl) }]}>
        {tx(
          rtl,
          "One live selection. The same person for everyone.",
          "اختيار مباشر واحد، والنتيجة نفسها لدى الجميع.",
        )}
      </Text>
    </View>
  );
}
const styles = StyleSheet.create({
  drawing: {
    aspectRatio: 400 / 420,
    width: "100%",
    maxWidth: 380,
    alignSelf: "center",
  },
  card: {
    padding: 16,
    borderRadius: 24,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
    alignItems: "center",
    gap: 8,
  },
  caption: {
    fontFamily: font("semibold"),
    color: colors.muted,
    fontSize: 12,
    letterSpacing: 1.5,
  },
  winner: { fontSize: 27, color: colors.ink, textAlign: "center" },
  help: {
    fontSize: 13,
    lineHeight: 20,
    color: colors.muted,
    textAlign: "center",
  },
  running: {
    height: 200,
    justifyContent: "center",
    width: "100%",
    backgroundColor: colors.mint,
    borderRadius: 20,
  },
  name: { fontSize: 30, textAlign: "center", color: colors.ink },
});
