import React,{useEffect,useRef} from 'react';
import {Animated,Easing,StyleSheet,Text,useWindowDimensions,View} from 'react-native';
import {useSafeAreaInsets} from 'react-native-safe-area-context';
import {colors,tx} from '../theme';
import {textStyle} from './Controls';
import {useMotionEnabled} from './Motion';

export const selectionColors=['#ED775C','#E8B634','#53B598','#6EAAE2','#9875CD','#EF93B7','#3C9CA8','#F2A264','#8CC96D'];
export function Celebration({rtl}:{rtl:boolean}) {
 const motion=useMotionEnabled(),fall=useRef(new Animated.Value(0)).current,burst=useRef(new Animated.Value(0)).current;
 const {width,height}=useWindowDimensions(),insets=useSafeAreaInsets();
 useEffect(()=>{
  fall.setValue(0);burst.setValue(0);
  if(!motion)return;
  const animation=Animated.parallel([
   Animated.timing(fall,{toValue:1,duration:3300,easing:Easing.linear,useNativeDriver:true,isInteraction:false}),
   Animated.timing(burst,{toValue:1,duration:1150,easing:Easing.out(Easing.cubic),useNativeDriver:true,isInteraction:false}),
  ]);
  animation.start();return()=>animation.stop();
 },[motion,fall,burst]);
 return <View pointerEvents="none" accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.confetti}>
  {motion&&Array.from({length:54},(_,index)=>{
   const delay=(index%6)*.025,travel=fall.interpolate({inputRange:[0,delay,1],outputRange:[0,0,1]});
   return <Animated.View key={index} style={[styles.piece,{left:`${index*37%100}%`,width:index%3===0?9:6,height:index%3===0?9:16,borderRadius:index%3===0?5:2,backgroundColor:selectionColors[index%selectionColors.length],opacity:travel.interpolate({inputRange:[0,.75,1],outputRange:[1,1,0]}),transform:[
    {translateY:travel.interpolate({inputRange:[0,1],outputRange:[-40-index%5*18,height+60]})},
    {translateX:travel.interpolate({inputRange:[0,.25,.6,1],outputRange:[0,index%2?30:-30,index%2?-18:18,index%2?65:-65]})},
    {rotate:travel.interpolate({inputRange:[0,1],outputRange:['0deg',`${index%2?720:-900}deg`]})},
   ]}]}/>;
  })}
  {motion&&Array.from({length:18},(_,index)=>{
   const angle=index*Math.PI/9,distance=Math.min(width*.7,270);
   return <Animated.Text key={`burst:${index}`} style={[styles.spark,{left:width/2-10,top:height*.4,color:selectionColors[index%9],opacity:burst.interpolate({inputRange:[0,.65,1],outputRange:[1,1,0]}),transform:[{translateX:burst.interpolate({inputRange:[0,1],outputRange:[0,Math.cos(angle)*distance]})},{translateY:burst.interpolate({inputRange:[0,1],outputRange:[0,Math.sin(angle)*distance]})},{scale:burst.interpolate({inputRange:[0,.25,1],outputRange:[.4,1.25,.5]})},{rotate:burst.interpolate({inputRange:[0,1],outputRange:['0deg','180deg']})}]}]}>✦</Animated.Text>;
  })}
  <Animated.Text style={[textStyle(rtl,'bold'),styles.party,{top:insets.top+14},motion&&{transform:[{scale:burst.interpolate({inputRange:[0,.3,.6,1],outputRange:[.4,1.3,.9,1]})},{rotate:burst.interpolate({inputRange:[0,.4,1],outputRange:['-25deg','15deg','0deg']})}]}]}>🎉</Animated.Text>
 </View>;
}
export function SelectionCountdown({number,rtl}:{number:number;rtl:boolean}) {
 const motion=useMotionEnabled(),pulse=useRef(new Animated.Value(1)).current;
 const tone=[colors.primary,colors.forest,colors.violet][number-1]||colors.forest;
 useEffect(()=>{
  pulse.setValue(motion?0:1);if(!motion)return;
  const animation=Animated.timing(pulse,{toValue:1,duration:880,easing:Easing.out(Easing.cubic),useNativeDriver:true});
  animation.start();return()=>animation.stop();
 },[number,motion,pulse]);
 return <View style={styles.countdown} testID="selection-countdown">
  <Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,'Everyone’s here. Ready?','كلنا جاهزين للاختيار؟')}</Text>
  <View style={styles.arena}>
   <View style={[styles.halo,{backgroundColor:[colors.coralWash,colors.mint,colors.lavender][number-1]}]}/>
   {[0,1].map(index=><Animated.View key={index} style={[styles.ring,{borderColor:tone,width:190+index*26,height:190+index*26,borderRadius:120,opacity:motion?pulse.interpolate({inputRange:[0,.5,1],outputRange:[.3,.2,0]}):.14,transform:[{scale:motion?pulse.interpolate({inputRange:[0,1],outputRange:[.65,1.2+index*.1]}):1}]}]}/>)}
   {Array.from({length:9},(_,index)=>{const angle=index*Math.PI*2/9;return <Animated.Text key={index} accessible={false} style={[styles.orbit,{color:selectionColors[index],left:110+Math.cos(angle)*100,top:110+Math.sin(angle)*100},motion&&{transform:[{scale:pulse.interpolate({inputRange:[0,.4,1],outputRange:[.3,1.3,1]})},{rotate:pulse.interpolate({inputRange:[0,1],outputRange:['-30deg','30deg']})}]}]}>✦</Animated.Text>;})}
   <Animated.Text accessibilityLiveRegion="assertive" style={[textStyle(rtl,'bold'),styles.digit,{color:tone},motion&&{opacity:pulse.interpolate({inputRange:[0,.15,1],outputRange:[0,1,1]}),transform:[{scale:pulse.interpolate({inputRange:[0,.35,.65,1],outputRange:[.35,1.18,.94,1]})},{translateY:pulse.interpolate({inputRange:[0,.35,.65,1],outputRange:[24,-8,3,0]})},{rotate:pulse.interpolate({inputRange:[0,.45,1],outputRange:['-12deg','5deg','0deg']})}]}]}>{number}</Animated.Text>
  </View>
  <Text style={[textStyle(rtl),styles.subtitle]}>{tx(rtl,'The selection starts together.','هنبدأ الاختيار مع بعض.')}</Text>
 </View>;
}
export function RunningNameDisplay({name,previous,next,index,ended,motion,rtl}:{name:string;previous:string;next:string;index:number;ended:boolean;motion:boolean;rtl:boolean}) {
 const arrival=useRef(new Animated.Value(1)).current,drift=useRef(new Animated.Value(0)).current;
 useEffect(()=>{
  arrival.setValue(motion?0:1);if(!motion)return;
  const animation=Animated.timing(arrival,{toValue:1,duration:ended?480:140,easing:Easing.out(Easing.cubic),useNativeDriver:true,isInteraction:false});
  animation.start();return()=>animation.stop();
 },[name,ended,motion,arrival]);
 useEffect(()=>{
  drift.setValue(0);if(!motion||ended)return;
  const animation=Animated.loop(Animated.sequence([
   Animated.timing(drift,{toValue:1,duration:1600,easing:Easing.inOut(Easing.sin),useNativeDriver:true,isInteraction:false}),
   Animated.timing(drift,{toValue:0,duration:1600,easing:Easing.inOut(Easing.sin),useNativeDriver:true,isInteraction:false}),
  ]));animation.start();return()=>animation.stop();
 },[motion,ended,drift]);
 return <View style={[styles.running,ended&&{backgroundColor:colors.mint,borderColor:'#A4D5BA'}]}>
  {[0,1,2].map(i=><Animated.View key={i} accessible={false} style={[styles.orb,{backgroundColor:[colors.lavender,colors.honey,colors.sky][i],left:i===1?undefined:i===0?-25:170,right:i===1?-30:undefined,top:i===0?-30:i===1?80:150},motion&&{transform:[{translateY:drift.interpolate({inputRange:[0,1],outputRange:[0,i%2?18:-18]})},{scale:drift.interpolate({inputRange:[0,1],outputRange:[1,1.15]})}]}]}/>)}
  <Animated.Text accessible={false} style={[styles.badge,{color:ended?colors.forest:selectionColors[index%9]},motion&&!ended&&{transform:[{rotate:drift.interpolate({inputRange:[0,1],outputRange:['-20deg','20deg']})}]}]}>{ended?'✓':'✦'}</Animated.Text>
  <Text accessible={false} numberOfLines={1} style={[textStyle(rtl,'medium'),styles.neighbor]}>{!ended&&motion?previous:''}</Text>
  <Animated.View style={[styles.nameCard,{borderColor:ended?'#A4D5BA':selectionColors[index%9]},motion&&{opacity:arrival.interpolate({inputRange:[0,1],outputRange:[.7,1]}),transform:[{translateY:arrival.interpolate({inputRange:[0,1],outputRange:[ended?0:13,0]})},{scale:arrival.interpolate({inputRange:[0,.6,1],outputRange:[ended?.8:.96,ended?1.06:1,1]})}]}]}>
   <Text accessibilityLiveRegion={ended?'polite':'none'} style={[textStyle(rtl,'bold'),styles.runningName]}>{name}</Text>
  </Animated.View>
  <Text accessible={false} numberOfLines={1} style={[textStyle(rtl,'medium'),styles.neighbor]}>{!ended&&motion?next:''}</Text>
 </View>;
}
const styles=StyleSheet.create({
 running:{width:'100%',minHeight:260,padding:18,gap:9,alignItems:'center',justifyContent:'center',borderRadius:26,backgroundColor:colors.coralWash,borderWidth:1,borderColor:'#F0CABB',overflow:'hidden'},orb:{position:'absolute',width:140,height:140,borderRadius:70,opacity:.75},badge:{fontSize:30,lineHeight:36},neighbor:{fontSize:18,lineHeight:27,color:colors.muted,opacity:.45,width:'100%',textAlign:'center'},nameCard:{width:'100%',minHeight:90,paddingVertical:16,paddingHorizontal:10,backgroundColor:'#FFFFFFF2',borderWidth:2,borderRadius:22,justifyContent:'center',shadowColor:colors.forest,shadowOpacity:.08,shadowRadius:12,shadowOffset:{width:0,height:5}},runningName:{fontSize:30,lineHeight:44,textAlign:'center',color:colors.ink},
 confetti:{position:'absolute',top:0,bottom:0,left:0,right:0,overflow:'hidden'},piece:{position:'absolute',top:0},spark:{position:'absolute',fontSize:22},party:{position:'absolute',right:22,fontSize:42},
 countdown:{alignItems:'center',justifyContent:'center',gap:14},subtitle:{fontSize:15,lineHeight:26,textAlign:'center'},arena:{width:240,height:240,alignItems:'center',justifyContent:'center'},halo:{position:'absolute',width:188,height:188,borderRadius:94},ring:{position:'absolute',borderWidth:3},orbit:{position:'absolute',fontSize:21,width:22,height:26,textAlign:'center'},digit:{fontSize:122,lineHeight:168,textAlign:'center'},
});
