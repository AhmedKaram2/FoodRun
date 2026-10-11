import React,{useState} from 'react';
import {StyleSheet,Text,View} from 'react-native';
import BottomSheet from './BottomSheet';
import {Button,action,textStyle} from './Controls';
import ScrollArea from './ScrollArea';
import {FloatingArt,PageMotion} from './Motion';
import Icon from './Icon';
import {colors,tx} from '../theme';
import steps from '../../../webApp/src/foodrun/firstUseTour.json';
export default function FirstUseTour({visible,rtl,onComplete}:{visible:boolean;rtl:boolean;onComplete:()=>void}) {
 const [index,setIndex]=useState(0),[dismissed,setDismissed]=useState(false),step=steps[index];
 const finish=()=>{setDismissed(true);onComplete();};
 return <BottomSheet visible={visible&&!dismissed} title={tx(rtl,'A quick tour','جولة سريعة')} rtl={rtl} onClose={finish}>
  <ScrollArea contentContainerStyle={styles.content}>
   <View accessibilityRole="progressbar" accessibilityLabel={tx(rtl,'Tour progress','تقدم الجولة')} accessibilityValue={{min:1,max:steps.length,now:index+1}} style={[styles.progress,rtl&&{flexDirection:'row-reverse'}]}>{steps.map((s,i)=><View key={s.id} style={[styles.segment,i<=index&&{backgroundColor:colors.forest}]}/>)}</View>
   <Text style={[textStyle(rtl),styles.caption]}>{tx(rtl,'Step {step} of {count}','الخطوة {step} من {count}').replace('{step}',String(index+1)).replace('{count}',String(steps.length))}</Text>
   <PageMotion motionKey={step.id} horizontal rtl={rtl}>
    <View style={styles.art}><FloatingArt><Icon name={step.icon} size={58} color={colors.forest}/></FloatingArt></View>
    <Text accessibilityRole="header" accessibilityLiveRegion="polite" style={[textStyle(rtl,'bold'),styles.title]}>{tx(rtl,step.title,step.title)}</Text>
    <Text style={[textStyle(rtl),styles.body]}>{tx(rtl,step.body,step.body)}</Text>
   </PageMotion>
   <View style={[styles.actions,rtl&&{flexDirection:'row-reverse'}]}>
    {index>0&&<Button rtl={rtl} action={action(tx(rtl,'Back','رجوع'),'TOUR_BACK')} onPress={()=>setIndex(index-1)}/>}
    <Button rtl={rtl} action={action(tx(rtl,index===steps.length-1?'Get started':'Next',index===steps.length-1?'ابدأ الآن':'التالي'),'TOUR_NEXT','',true)} onPress={()=>index===steps.length-1?finish():setIndex(index+1)}/>
   </View>
   <Button rtl={rtl} action={action(tx(rtl,'Skip tour','تخطي الجولة'),'TOUR_SKIP')} onPress={finish}/>
  </ScrollArea>
 </BottomSheet>;
}
const styles=StyleSheet.create({content:{gap:16,paddingBottom:8},progress:{flexDirection:'row',gap:5},segment:{height:4,flex:1,borderRadius:2,backgroundColor:colors.line},caption:{fontSize:12,color:colors.muted,textAlign:'center'},art:{height:110,alignItems:'center',justifyContent:'center',backgroundColor:colors.mint,borderRadius:24},title:{fontSize:22,lineHeight:35,textAlign:'center'},body:{fontSize:15,lineHeight:27,textAlign:'center'},actions:{flexDirection:'row',justifyContent:'center',gap:12}});
