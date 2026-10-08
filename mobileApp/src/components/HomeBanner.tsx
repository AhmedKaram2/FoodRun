import React, { useState } from "react";
import { Image, Pressable, StyleSheet, Text, View } from "react-native";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { Button, action, textStyle } from "./Controls";
import Icon from "./Icon";
import { FloatingArt, PageMotion } from "./Motion";

export default function HomeBanner({ rtl, busy, authenticated }: { rtl: boolean; busy: boolean; authenticated: boolean }) {
  const [slide, setSlide] = useState(0), [width, setWidth] = useState(300);
  const copy = [
    [tx(rtl, "Food is better", "الأكل أحلى"), tx(rtl, "together.", "مع بعض."), tx(rtl, "Create a room, choose your food, and share the bill with ease.", "أنشئ غرفة، واختر طعامك، وتقاسموا الحساب بسهولة.")],
    [tx(rtl, "Different tastes.", "أذواق مختلفة."), tx(rtl, "Same table.", "سفرة واحدة."), tx(rtl, "Pizza, burgers, or a salad. Everyone gets to choose.", "بيتزا، برجر، أو سلطة. كل واحد يختار ما يحب.")],
    [tx(rtl, "Split the bill.", "حساب واضح،"), tx(rtl, "Keep the good times.", "ولَمّة أحلى."), tx(rtl, "Clear shares and a wallet that keeps everything together.", "حصص واضحة ومحفظة تجمع رصيدك ومدفوعاتك.")],
  ][slide];
  return <View style={[styles.hero, { backgroundColor: [colors.coralWash, colors.mint, "#FFF5D9"][slide] }]}>
    <Text style={[textStyle(rtl, "semibold"), styles.eyebrow]}>{tx(rtl, "GOOD FOOD. GREAT COMPANY.", "أكل حلو. وصحبة أحلى.")}</Text>
    <PageMotion motionKey={`${slide}:${rtl}`}>
      <View>
        <Text style={[textStyle(rtl, "bold"), styles.title]}>{copy[0]}</Text>
        <Text style={[textStyle(rtl, "bold"), styles.title, { color: colors.primary }]}>{copy[1]}</Text>
      </View>
      <Text style={[textStyle(rtl), styles.description]}>{copy[2]}</Text>
      <FloatingArt>
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.art} onLayout={event => setWidth(event.nativeEvent.layout.width)}>
          {slide === 0 ? <Image source={require("../assets/together-banner.png")} style={{ width: width * 1.71, height: width * 1.71 * 293 / 795, position: "absolute", right: 0, top: -Math.max(0, width * 1.71 * 293 / 795 - width * 270 / 465) * 0.2 }} resizeMode="stretch" />
            : slide === 1 ? <View style={[styles.tasteArt, rtl && {flexDirection:"row-reverse"}]}>{["🍕","🥗","🍔"].map((food,index) => <Text key={food} style={[styles.foodArt,{transform:[{rotate:["-12deg","8deg","-6deg"][index]}]}]}>{food}</Text>)}</View>
            : <View style={styles.splitArt}><View style={styles.splitCircle}><Icon name="split" size={50} color={colors.forest}/></View><View style={[styles.artBubble,{left:18,top:24,backgroundColor:colors.mint}]}><Icon name="friends" size={28} color={colors.forest}/></View><View style={[styles.artBubble,{right:18,bottom:24,backgroundColor:colors.coralWash}]}><Icon name="food" size={28} color={colors.primary}/></View></View>}
        </View>
      </FloatingArt>
    </PageMotion>
    <View style={[styles.benefits, rtl && { flexDirection: "row-reverse" }]}>
      {[["friends",tx(rtl,"Friends","الأصدقاء"),"FRIENDS_ACTION","open"],["food",tx(rtl,"Menus","القوائم"),"OPEN_LIBRARY",""],["wallet",tx(rtl,"Wallet","المحفظة"),"OPEN_WALLET",""]].map(([icon,label,kind,value]) => <Pressable key={kind} accessibilityRole="button" accessibilityLabel={label} disabled={busy || (!authenticated && kind !== "OPEN_LIBRARY")} onPress={() => dispatch(kind,value)} style={({pressed}) => [styles.benefit, pressed && {opacity:0.65}]}><Icon name={icon} color={colors.forest}/><Text style={[textStyle(rtl,"medium"),styles.benefitLabel]}>{label}</Text></Pressable>)}
    </View>
    {(authenticated || slide === 1) && <Button rtl={rtl} busy={busy} action={action(slide === 1 ? tx(rtl,"Explore restaurants","استكشف المطاعم") : tx(rtl,"Create a room","إنشاء غرفة"),slide === 1 ? "OPEN_LIBRARY" : "CREATE","",true)}/>}
    {authenticated && <Button rtl={rtl} busy={busy} action={action(tx(rtl,"Join a room","الانضمام إلى غرفة"),"JOIN")}/>}
    <View style={[styles.pager, rtl && {flexDirection:"row-reverse"}]}>
      {[0,1,2].map(index => <Pressable key={index} accessibilityRole="button" accessibilityLabel={tx(rtl,`Show highlight ${index+1}`,`عرض الميزة ${index+1}`)} accessibilityState={{selected:slide === index}} onPress={() => setSlide(index)} style={styles.pagerButton}><View style={[styles.dot, slide === index && styles.activeDot]}/></Pressable>)}
    </View>
  </View>;
}
const styles = StyleSheet.create({
  hero:{padding:20,borderRadius:26,gap:16,borderWidth:1,borderColor:"#EEDFD5"},
  eyebrow:{fontSize:11,lineHeight:22,color:colors.forest},
  title:{fontSize:29,lineHeight:44},description:{fontSize:15,lineHeight:26,color:colors.muted},
  art:{width:"100%",aspectRatio:465/270,borderRadius:24,overflow:"hidden",backgroundColor:"#FFF7ED"},
  tasteArt:{flex:1,flexDirection:"row",alignItems:"center",justifyContent:"space-evenly",backgroundColor:"#EFF7EE"},foodArt:{fontSize:58},
  splitArt:{flex:1,alignItems:"center",justifyContent:"center",backgroundColor:"#FFF7E5"},splitCircle:{width:98,height:98,borderRadius:49,backgroundColor:colors.white,alignItems:"center",justifyContent:"center"},artBubble:{position:"absolute",width:60,height:60,borderRadius:24,alignItems:"center",justifyContent:"center"},
  benefits:{flexDirection:"row",gap:6},benefit:{flex:1,alignItems:"center",gap:8,paddingVertical:10,borderRadius:14,backgroundColor:"#FFFFFFA6"},benefitLabel:{fontSize:12,lineHeight:23},
  pager:{flexDirection:"row",justifyContent:"center",marginTop:-8,marginBottom:-8},pagerButton:{width:44,height:32,alignItems:"center",justifyContent:"center"},
  dot:{height:6,width:6,borderRadius:8,backgroundColor:"#B4C1B9"},activeDot:{width:22,backgroundColor:colors.primary},
});
