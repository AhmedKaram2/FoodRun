import React from "react";
import { View } from "react-native";
import { SvgXml } from "react-native-svg";
import drawings from "../../../Branding/Intrvioo/source/illustrations.json";

export type IllustrationKind = keyof typeof drawings;
export function screenIllustration(page: string): IllustrationKind {
  if (/PAYMENT|WALLET|ACCOUNT|RECEIPT/.test(page)) return "wallet";
  if (/PROFILE|AUTH/.test(page)) return "profile";
  if (/FRIEND|PEOPLE|GROUP/.test(page)) return "friends";
  if (/NOTIFICATION|BELL/.test(page)) return "bell";
  if (/WHEEL|SPIN|SELECTION/.test(page)) return "wheel";
  if (/HOME|ITEM|MENU|LIBRARY|RESTAURANT|FOOD/.test(page)) return "food";
  return "room";
}

export default function Illustration({ kind, size = 58 }: { kind: IllustrationKind; size?: number }) {
  return <View accessible={false} accessibilityElementsHidden importantForAccessibility="no-hide-descendants" pointerEvents="none">
    <SvgXml xml={drawings[kind]} width={size} height={size * 5 / 6}/>
  </View>;
}
