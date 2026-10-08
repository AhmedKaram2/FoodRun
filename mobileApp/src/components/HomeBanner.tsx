import React, { useEffect, useMemo, useState } from "react";
import { AccessibilityInfo, Image, PanResponder, Pressable, StyleSheet, Text, View } from "react-native";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { Button, action, textStyle } from "./Controls";
import Icon from "./Icon";
import { FloatingArt, PageMotion, useMotionEnabled } from "./Motion";

export default function HomeBanner({ rtl, busy, authenticated }: { rtl: boolean; busy: boolean; authenticated: boolean }) {
  const [slide, setSlide] = useState(0), [width, setWidth] = useState(130);
  const [paused, setPaused] = useState(false), [screenReader, setScreenReader] = useState(true);
  const motionEnabled = useMotionEnabled();
  const swipe=useMemo(()=>PanResponder.create({
    onMoveShouldSetPanResponder:(_,gesture)=>Math.abs(gesture.dx)>12 && Math.abs(gesture.dx)>Math.abs(gesture.dy)*1.5,
    onPanResponderRelease:(_,gesture)=>{if(Math.abs(gesture.dx)>30)setSlide(current=>(current+((gesture.dx<0)!==rtl ? 1 : 2))%3);},
  }),[rtl]);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isScreenReaderEnabled().then(value => { if (mounted) setScreenReader(value); }).catch(() => {});
    const listener = AccessibilityInfo.addEventListener("screenReaderChanged", setScreenReader);
    return () => { mounted = false; listener.remove(); };
  }, []);
  useEffect(() => {
    if (!motionEnabled || screenReader || paused || busy) return;
    const timer = setTimeout(() => setSlide(current => (current + 1) % 3), 6000);
    return () => clearTimeout(timer);
  }, [motionEnabled, screenReader, paused, busy, slide]);
  const copy = [
    [tx(rtl, "Food is better", "الأكل أحلى"), tx(rtl, "together.", "مع بعض."), tx(rtl, "Create a room, choose your food, and share the bill with ease.", "أنشئ غرفة، واختر طعامك، وتقاسموا الحساب بسهولة.")],
    [tx(rtl, "Different tastes.", "أذواق مختلفة."), tx(rtl, "Same table.", "سفرة واحدة."), tx(rtl, "Pizza, burgers, or a salad. Everyone gets to choose.", "بيتزا، برجر، أو سلطة. كل واحد يختار ما يحب.")],
    [tx(rtl, "Split the bill.", "حساب واضح،"), tx(rtl, "Good times.", "ولَمّة أحلى."), tx(rtl, "Clear shares and a wallet that keeps everything together.", "حصص واضحة ومحفظة تجمع رصيدك ومدفوعاتك.")],
  ][slide];
  return <View style={styles.home}>
    <View testID="home-carousel" {...swipe.panHandlers} style={[styles.hero, { backgroundColor: [colors.coralWash, colors.mint, "#FFF5D9"][slide] }]}>
      <Text style={[textStyle(rtl, "semibold"), styles.eyebrow]}>{tx(rtl, "GOOD FOOD. GREAT COMPANY.", "أكل حلو. وصحبة أحلى.")}</Text>
      <PageMotion motionKey={`${slide}:${rtl}`} horizontal rtl={rtl}>
        <View style={styles.highlight}>
          <View style={[styles.highlightRow, rtl && { flexDirection: "row-reverse" }]}>
            <View style={styles.copy}>
              <Text style={[textStyle(rtl, "bold"), styles.title]}>{copy[0]}</Text>
              <Text style={[textStyle(rtl, "bold"), styles.title, { color: colors.primary }]}>{copy[1]}</Text>
            </View>
            <View style={styles.artColumn}>
              <FloatingArt>
                <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.art} onLayout={event => setWidth(event.nativeEvent.layout.width)}>
                  {slide === 0 ? <Image source={require("../assets/together-banner.png")} style={{ width: width * 1.71, height: width * 1.71 * 293 / 795, position: "absolute", right: 0, top: 0 }} resizeMode="stretch" />
                    : slide === 1 ? <View style={[styles.tasteArt, rtl && {flexDirection:"row-reverse"}]}>{["🍕","🥗","🍔"].map((food,index) => <Text key={food} style={[styles.foodArt,{transform:[{rotate:["-12deg","8deg","-6deg"][index]}]}]}>{food}</Text>)}</View>
                    : <View style={styles.splitArt}><View style={styles.splitCircle}><Icon name="split" size={32} color={colors.forest}/></View><View style={[styles.artBubble,{left:4,top:4,backgroundColor:colors.mint}]}><Icon name="friends" size={18} color={colors.forest}/></View><View style={[styles.artBubble,{right:4,bottom:4,backgroundColor:colors.coralWash}]}><Icon name="food" size={18} color={colors.primary}/></View></View>}
                </View>
              </FloatingArt>
            </View>
          </View>
          <Text style={[textStyle(rtl), styles.description]}>{copy[2]}</Text>
        </View>
      </PageMotion>
      <View style={[styles.pager, rtl && {flexDirection:"row-reverse"}]}>
        <View style={[styles.dots, rtl && {flexDirection:"row-reverse"}]}>
          {[0,1,2].map(index => <Pressable key={index} accessibilityRole="button" accessibilityLabel={tx(rtl,`Show highlight ${index+1}`,`عرض الميزة ${index+1}`)} accessibilityState={{selected:slide === index}} onPress={() => setSlide(index)} style={styles.pagerButton}><View style={[styles.dot, slide === index && styles.activeDot]}/></Pressable>)}
        </View>
        <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl, paused ? "Play highlights" : "Pause highlights", paused ? "تشغيل العرض التلقائي" : "إيقاف العرض التلقائي")} accessibilityState={{disabled: !motionEnabled || screenReader}} disabled={!motionEnabled || screenReader} onPress={() => setPaused(value => !value)} style={styles.pagerButton}><Icon name={paused ? "play" : "pause"} size={16} color={colors.forest}/></Pressable>
      </View>
    </View>
    <View style={[styles.benefits, rtl && { flexDirection: "row-reverse" }]}>
      {[["friends",tx(rtl,"Friends","الأصدقاء"),"FRIENDS_ACTION","open"],["food",tx(rtl,"Menus","القوائم"),"OPEN_LIBRARY",""],["wallet",tx(rtl,"Wallet","المحفظة"),"OPEN_WALLET",""]].map(([icon,label,kind,value]) => <Pressable key={kind} accessibilityRole="button" accessibilityLabel={label} accessibilityState={{disabled:busy || (!authenticated && kind !== "OPEN_LIBRARY")}} disabled={busy || (!authenticated && kind !== "OPEN_LIBRARY")} onPress={() => dispatch(kind,value)} style={({pressed}) => [styles.benefit, pressed && {opacity:0.65}]}><Icon name={icon} color={colors.forest}/><Text style={[textStyle(rtl,"medium"),styles.benefitLabel]}>{label}</Text></Pressable>)}
    </View>
    {authenticated && <View testID="home-room-actions" style={[styles.actions, rtl && {flexDirection:"row-reverse"}]}>
      <View style={styles.action}><Button rtl={rtl} busy={busy} action={action(tx(rtl,"Create a room","إنشاء غرفة"),"CREATE","",true)}/></View>
      <View style={styles.action}><Button rtl={rtl} busy={busy} action={action(tx(rtl,"Join a room","دخول غرفة"),"JOIN")}/></View>
    </View>}
  </View>;
}
const styles = StyleSheet.create({
  home:{gap:12},hero:{paddingHorizontal:16,paddingTop:12,paddingBottom:2,borderRadius:22,gap:8,borderWidth:1,borderColor:"#EEDFD5"},
  eyebrow:{fontSize:10,lineHeight:20,color:colors.forest},
  highlight:{gap:8},highlightRow:{flexDirection:"row",gap:10,alignItems:"center",minHeight:112},copy:{flex:1},artColumn:{width:"44%"},
  title:{fontSize:21,lineHeight:31},description:{fontSize:13,lineHeight:23,minHeight:69,color:colors.muted},
  art:{width:"100%",aspectRatio:465/330,borderRadius:18,overflow:"hidden",backgroundColor:"#FFF7ED"},
  tasteArt:{flex:1,flexDirection:"row",alignItems:"center",justifyContent:"space-evenly",backgroundColor:"#EFF7EE"},foodArt:{fontSize:30},
  splitArt:{flex:1,alignItems:"center",justifyContent:"center",backgroundColor:"#FFF7E5"},splitCircle:{width:60,height:60,borderRadius:30,backgroundColor:colors.white,alignItems:"center",justifyContent:"center"},artBubble:{position:"absolute",width:32,height:32,borderRadius:12,alignItems:"center",justifyContent:"center"},
  benefits:{flexDirection:"row",gap:8},benefit:{flex:1,alignItems:"center",gap:3,paddingVertical:8,borderRadius:14,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line},benefitLabel:{fontSize:12,lineHeight:23},
  actions:{flexDirection:"row",gap:8},action:{flex:1},
  pager:{flexDirection:"row",justifyContent:"center",alignItems:"center"},dots:{flexDirection:"row"},pagerButton:{width:44,height:44,alignItems:"center",justifyContent:"center"},
  dot:{height:6,width:6,borderRadius:8,backgroundColor:"#B4C1B9"},activeDot:{width:22,backgroundColor:colors.primary},
});
