import React, { createContext, useContext, useEffect, useRef, useState } from "react";
import { AccessibilityInfo, Animated, AppState, Easing, Pressable, View } from "react-native";

const Motion = createContext(false);
export function MotionProvider({ children }: { children: React.ReactNode }) {
  const [reduced, setReduced] = useState(true);
  const [active, setActive] = useState(AppState.currentState === "active");
  useEffect(() => {
    let alive = true;
    AccessibilityInfo.isReduceMotionEnabled().then(value => { if (alive) setReduced(value); }).catch(() => {});
    const preference = AccessibilityInfo.addEventListener("reduceMotionChanged", setReduced);
    const app = AppState.addEventListener("change", value => setActive(value === "active"));
    return () => { alive = false; preference.remove(); app.remove(); };
  }, []);
  return <Motion.Provider value={!reduced && active}>{children}</Motion.Provider>;
}
export function usePressMotion() {
  const enabled = useContext(Motion), scale = useRef(new Animated.Value(1)).current;
  const run = (value: number) => {
    scale.stopAnimation();
    if (enabled) Animated.spring(scale, { toValue: value, speed: 30, bounciness: 3, useNativeDriver: true }).start();
    else scale.setValue(1);
  };
  useEffect(() => () => scale.stopAnimation(), [scale]);
  return { style: { transform: [{ scale }] }, onPressIn: () => run(0.96), onPressOut: () => run(1) };
}
export function PageMotion({ motionKey, children }: { motionKey: string; children: React.ReactNode }) {
  const enabled = useContext(Motion), progress = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    progress.stopAnimation();
    if (!enabled) { progress.setValue(1); return; }
    progress.setValue(0);
    const animation = Animated.timing(progress, { toValue: 1, duration: 230, easing: Easing.out(Easing.cubic), useNativeDriver: true });
    animation.start(); return () => animation.stop();
  }, [motionKey, enabled, progress]);
  return <Animated.View style={{ gap: 16, opacity: progress, transform: [{ translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [12, 0] }) }] }}>{children}</Animated.View>;
}
export function FloatingArt({ children }: { children: React.ReactNode }) {
  const enabled = useContext(Motion), progress = useRef(new Animated.Value(0)).current;
  useEffect(() => {
    if (!enabled) { progress.setValue(0); return; }
    const animation = Animated.loop(Animated.sequence([
      Animated.timing(progress, { toValue: 1, duration: 2300, easing: Easing.inOut(Easing.sin), useNativeDriver: true, isInteraction: false }),
      Animated.timing(progress, { toValue: 0, duration: 2300, easing: Easing.inOut(Easing.sin), useNativeDriver: true, isInteraction: false }),
    ]));
    animation.start(); return () => animation.stop();
  }, [enabled, progress]);
  return <Animated.View style={{ transform: [{ translateY: progress.interpolate({ inputRange: [0, 1], outputRange: [0, -5] }) }] }}>{children}</Animated.View>;
}
export function TabButton({ selected, children, ...props }: React.ComponentProps<typeof Pressable> & { selected: boolean; children: React.ReactNode }) {
  const enabled = useContext(Motion), scale = useRef(new Animated.Value(1)).current;
  useEffect(() => {
    if (!enabled) { scale.setValue(1); return; }
    scale.setValue(selected ? 0.9 : 1);
    const animation = Animated.spring(scale, { toValue: 1, speed: 24, bounciness: 5, useNativeDriver: true });
    animation.start(); return () => animation.stop();
  }, [selected, enabled, scale]);
  return <Pressable {...props}><Animated.View style={{ alignItems: "center", gap: 2, transform: [{ scale }] }}>{children}</Animated.View></Pressable>;
}
