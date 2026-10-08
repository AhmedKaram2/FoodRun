import tokens from "../../Branding/Intrvioo/source/brand-tokens.json";
import { Platform } from "react-native";
export const colors = {
  coral: tokens.colors.coral,
  green: tokens.colors.fresh_green,
  yellow: tokens.colors.warm_yellow,
  ink: tokens.colors.charcoal,
  cream: tokens.colors.cream,
  mint: tokens.colors.light_green,
  white: "#FFFFFF",
  muted: "#647069",
  line: "#E6E8DF",
  danger: "#B3261E",
};
export const font = (
  weight: "regular" | "medium" | "semibold" | "bold" = "regular",
  rtl = false,
) =>
  rtl
    ? Platform.OS === "ios"
      ? "NotoSansArabic"
      : "sans-serif"
    : {
        regular: "Poppins-Regular",
        medium: "Poppins-Medium",
        semibold: "Poppins-SemiBold",
        bold: "Poppins-Bold",
      }[weight];
export const tx = (rtl: boolean, en: string, ar: string) => (rtl ? ar : en);
