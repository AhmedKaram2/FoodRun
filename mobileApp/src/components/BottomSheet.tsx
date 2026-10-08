import React, { useEffect, useRef } from "react";
import { Animated, KeyboardAvoidingView, Modal, PanResponder, Platform, Pressable, StyleSheet, Text, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";
import { colors, font, tx } from "../theme";
import Icon from "./Icon";
import { useMotionEnabled } from "./Motion";

export default function BottomSheet({ visible, title, rtl, onClose, children }: {
  visible: boolean; title: string; rtl: boolean; onClose: () => void; children: React.ReactNode;
}) {
  const insets = useSafeAreaInsets(), motion = useMotionEnabled();
  const offset = useRef(new Animated.Value(0)).current;
  useEffect(() => { if (visible) offset.setValue(0); }, [visible, offset]);
  const handle = PanResponder.create({
    onMoveShouldSetPanResponder: (_, gesture) => gesture.dy > 6 && Math.abs(gesture.dy) > Math.abs(gesture.dx),
    onPanResponderMove: (_, gesture) => offset.setValue(Math.max(0, gesture.dy)),
    onPanResponderRelease: (_, gesture) => {
      if (gesture.dy > 70 || gesture.vy > 0.8) { onClose(); return; }
      Animated.spring(offset, { toValue: 0, useNativeDriver: true, speed: 25, bounciness: 0 }).start();
    },
    onPanResponderTerminate: () => offset.setValue(0),
  });
  return <Modal visible={visible} transparent presentationStyle="overFullScreen" animationType={motion ? "slide" : "none"} statusBarTranslucent onRequestClose={onClose}>
    <View style={styles.backdrop}>
      <Pressable testID="sheet-backdrop" accessibilityRole="button" accessibilityLabel={tx(rtl, "Dismiss sheet", "إغلاق النافذة")} onPress={onClose} style={StyleSheet.absoluteFill} />
      <KeyboardAvoidingView behavior={Platform.OS === "ios" ? "padding" : undefined} style={styles.position} pointerEvents="box-none">
        <Animated.View accessibilityViewIsModal onAccessibilityEscape={onClose} style={[styles.sheet, {paddingBottom:Math.max(insets.bottom,16), transform:[{translateY:offset}]}]}>
          <View style={styles.handleTouch} {...handle.panHandlers}><View style={styles.handle}/></View>
          <View style={[styles.heading, rtl && {flexDirection:"row-reverse"}]}>
            <Text accessibilityRole="header" style={[styles.title,{fontFamily:font("bold",rtl),writingDirection:rtl?"rtl":"ltr",textAlign:rtl?"right":"left"}]}>{title}</Text>
            <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"Close","إغلاق")} onPress={onClose} style={styles.close}><Icon name="close" size={20} color={colors.forest}/></Pressable>
          </View>
          <View style={styles.content}>{children}</View>
        </Animated.View>
      </KeyboardAvoidingView>
    </View>
  </Modal>;
}
const styles = StyleSheet.create({
  backdrop:{flex:1,backgroundColor:"#18291F66"},position:{flex:1,justifyContent:"flex-end"},
  sheet:{maxHeight:"85%",backgroundColor:colors.cream,borderTopLeftRadius:28,borderTopRightRadius:28,paddingHorizontal:20},
  handleTouch:{height:28,alignItems:"center",justifyContent:"center"},handle:{height:5,width:36,borderRadius:4,backgroundColor:"#BBC8C0"},
  heading:{flexDirection:"row",alignItems:"center",gap:12,paddingBottom:14},title:{flex:1,fontSize:20,lineHeight:32,color:colors.ink},
  close:{height:44,width:44,borderRadius:22,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},
  content:{flexShrink:1,gap:12},
});
