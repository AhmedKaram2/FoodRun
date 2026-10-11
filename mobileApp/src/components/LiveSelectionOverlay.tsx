import React, { useEffect, useRef, useState } from "react";
import { Animated, Easing, Keyboard, Modal, ScrollView, StyleSheet, Text, View } from "react-native";
import { SafeAreaView, useSafeAreaInsets } from "react-native-safe-area-context";
import type { Wheel } from "../types";
import { colors, tx } from "../theme";
import { useMotionEnabled } from "./Motion";
import { Button, action, textStyle } from "./Controls";
import LiveWheel from "./LiveWheel";

export function Celebration({ rtl }: {rtl:boolean}) {
  const motion = useMotionEnabled(), fall = useRef(new Animated.Value(0)).current, insets=useSafeAreaInsets();
  useEffect(() => {
    if(!motion) return;
    const animation = Animated.timing(fall,{toValue:1,duration:2200,easing:Easing.out(Easing.quad),useNativeDriver:true});
    animation.start(); return () => animation.stop();
  },[motion,fall]);
  return <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.confetti}>
    {motion && Array.from({length:24},(_,index) => <Animated.View key={index} style={[styles.piece,{left:`${(index*17)%100}%`,backgroundColor:[colors.coral,colors.forest,"#F7C765","#91C9FF"][index%4],
      opacity:fall.interpolate({inputRange:[0,.75,1],outputRange:[1,1,0]}),transform:[{translateY:fall.interpolate({inputRange:[0,1],outputRange:[-40-index%4*30,500]})},{rotate:fall.interpolate({inputRange:[0,1],outputRange:["0deg",`${index%2?540:-540}deg`]})}]}]} />)}
    <Text style={[textStyle(rtl,"bold"),styles.party,{top:insets.top+14}]}>🎉</Text>
  </View>;
}
export function selectionCountdown(startAt:number,time:number) { return time < startAt ? Math.min(3,Math.max(1,Math.floor((time-startAt+3000)/1000)+1)) : 0; }
function Countdown({number,rtl}:{number:number;rtl:boolean}) {
  const motion=useMotionEnabled(), pulse=useRef(new Animated.Value(0)).current;
  useEffect(() => { pulse.setValue(0); if(!motion) return; const animation=Animated.timing(pulse,{toValue:1,duration:750,easing:Easing.out(Easing.back(1.5)),useNativeDriver:true}); animation.start(); return () => animation.stop(); },[number,motion,pulse]);
  return <View style={styles.countdown} testID="selection-countdown"><Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,"Everyone’s here. Ready?","كلنا جاهزين للاختيار؟")}</Text><Animated.Text accessibilityLiveRegion="assertive" style={[textStyle(rtl,"bold"),styles.digit,motion && {opacity:pulse.interpolate({inputRange:[0,.2,1],outputRange:[0,1,1]}),transform:[{scale:pulse.interpolate({inputRange:[0,1],outputRange:[.55,1]})}]}]}>{number}</Animated.Text><Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,"The selection starts together.","هنبدأ الاختيار مع بعض.")}</Text></View>;
}
export default function LiveSelectionOverlay({ wheel, rtl, onVisibility }: {wheel?:Wheel|null;rtl:boolean;onVisibility:(visible:boolean)=>void}) {
  const [now,setNow] = useState(Date.now()), [dismissed,setDismissed] = useState(""), [presenting,setPresenting] = useState(false);
  const motion = useMotionEnabled(), end = wheel ? wheel.round.startAt + wheel.round.duration : 0;
  const ended = !!wheel && now+wheel.serverOffset >= end;
  const countdown = wheel ? selectionCountdown(wheel.round.startAt,now+wheel.serverOffset) : 0;
  const visible = !!wheel && dismissed !== wheel.round.id && now+wheel.serverOffset < end+6000;
  useEffect(() => { setNow(Date.now()); const timer=setInterval(() => setNow(Date.now()),250); return () => clearInterval(timer); },[wheel?.round.id]);
  useEffect(() => {
    setPresenting(false);
    if(visible) {
      Keyboard.dismiss();
      onVisibility(true);
      const timer = setTimeout(() => setPresenting(true),500);
      return () => clearTimeout(timer);
    }
    // UIKit must dismiss the full-screen presentation before an underlying sheet reopens.
    const timer = setTimeout(() => onVisibility(false),500);
    return () => clearTimeout(timer);
  },[visible,onVisibility]);
  useEffect(() => () => onVisibility(false),[onVisibility]);
  return <>{visible && !presenting && <View style={[StyleSheet.absoluteFill,styles.screen,{zIndex:9999}]} accessibilityViewIsModal/>}<Modal visible={visible && presenting} animationType={motion ? "fade" : "none"} presentationStyle="fullScreen" onDismiss={() => { if(!visible) onVisibility(false); }} onRequestClose={() => { if(ended && wheel) setDismissed(wheel.round.id); }}>
    <SafeAreaView style={styles.screen} edges={["top","bottom"]}>
      <ScrollView contentContainerStyle={styles.content}>
        <Text style={[textStyle(rtl,"semibold"),styles.caption]}>{wheel?.roomName || tx(rtl,"Your room","غرفتك")}</Text>
        <Text accessibilityRole="header" accessibilityLiveRegion="polite" style={[textStyle(rtl,"bold"),styles.title]}>{ended ? tx(rtl,"We have our person!","تم اختيار مسؤول الطلب!") : tx(rtl,"Who’s ordering today?","مين هيطلب لنا النهارده؟")}</Text>
        {countdown ? <Countdown number={countdown} rtl={rtl}/> : wheel && <LiveWheel key={wheel.round.id} wheel={wheel} rtl={rtl}/>}
        {ended && <><Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,"Time for a delicious team meal.","وقت الأكل الحلو مع أصحابك.")}</Text><Button rtl={rtl} action={action(tx(rtl,"Continue","متابعة"),"CONTINUE","",true)} onPress={() => { if(wheel) setDismissed(wheel.round.id); }}/></>}
      </ScrollView>
      {visible && ended && <Celebration key={wheel?.round.id} rtl={rtl}/>}
    </SafeAreaView>
  </Modal></>;
}
const styles = StyleSheet.create({
  screen:{flex:1,backgroundColor:colors.cream},content:{flexGrow:1,justifyContent:"center",padding:22,gap:14,width:"100%",maxWidth:540,alignSelf:"center"},caption:{fontSize:14,color:colors.forest,textAlign:"center"},title:{fontSize:27,lineHeight:42,textAlign:"center"},subtitle:{fontSize:15,lineHeight:26,textAlign:"center"},
  confetti:{position:"absolute",top:0,bottom:0,left:0,right:0,overflow:"hidden"},piece:{position:"absolute",top:0,width:8,height:15,borderRadius:3},party:{position:"absolute",top:14,right:22,fontSize:42},
  countdown:{minHeight:300,alignItems:"center",justifyContent:"center",gap:16},digit:{fontSize:140,lineHeight:190,color:colors.forest,textAlign:"center"},
});
