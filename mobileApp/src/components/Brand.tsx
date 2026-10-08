import React from "react";
import { View, Text, StyleSheet } from "react-native";
import { SvgXml } from "react-native-svg";
import { brandSymbol } from "../assets/brandSymbol";
import { colors, font } from "../theme";
export default function Brand({ small = false }: { small?: boolean }) {
  return (
    <View style={styles.row}>
      <SvgXml xml={brandSymbol} width={small ? 30 : 44} height={small ? 30 : 44} />
      <Text style={[styles.word, small && {fontSize:20}]}>
        FoodRun<Text style={{ color: colors.coral }}>●</Text>
      </Text>
    </View>
  );
}
const styles = StyleSheet.create({
  row: { flexDirection: "row", alignItems: "center", gap: 9 },
  word: { color: colors.ink, fontFamily: font("bold"), fontSize: 24 },
});
