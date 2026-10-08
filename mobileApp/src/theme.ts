import tokens from "../../Branding/Intrvioo/source/brand-tokens.json";
export const colors = {
  coral: tokens.colors.coral,
  green: tokens.colors.fresh_green,
  yellow: tokens.colors.warm_yellow,
  ink: tokens.colors.charcoal,
  cream: "#F8FAF8",
  mint: "#E7F3ED",
  primary: "#C44730",
  forest: "#245C49",
  coralWash: "#FFF0E9",
  white: "#FFFFFF",
  muted: "#5F6D66",
  line: "#DCE5DF",
  danger: "#B3261E",
};
export const font = (
  weight: "regular" | "medium" | "semibold" | "bold" = "regular",
  rtl = false,
) =>
  rtl
    ? {
        regular: "NotoSansArabic-Regular",
        medium: "NotoSansArabic-Medium",
        semibold: "NotoSansArabic-SemiBold",
        bold: "NotoSansArabic-Bold",
      }[weight]
    : {
        regular: "Poppins-Regular",
        medium: "Poppins-Medium",
        semibold: "Poppins-SemiBold",
        bold: "Poppins-Bold",
      }[weight];
export const tx = (rtl: boolean, en: string, ar: string) => (rtl ? ar : en);
