import React,{useEffect,useState} from 'react';
import {Keyboard,Platform,StyleSheet,Text,View} from 'react-native';
import {colors} from '../theme';
import {textStyle} from './Controls';
import Icon from './Icon';
import {TabButton} from './Motion';

export type NativeTab={id:string;icon:string;label:string;selected:boolean};
const tones=[colors.mint,colors.honey,colors.sky,colors.coralWash,colors.lavender];

export default function NativeTabBar({tabs,rtl,busy,onSelect}:{tabs:NativeTab[];rtl:boolean;busy:boolean;onSelect:(id:string)=>void}) {
 const ios=Platform.OS==='ios';
 const [keyboard,setKeyboard]=useState(false);
 useEffect(()=>{
  const shown=Keyboard.addListener('keyboardDidShow',()=>setKeyboard(true));
  const hidden=Keyboard.addListener('keyboardDidHide',()=>setKeyboard(false));
  return()=>{shown.remove();hidden.remove();};
 },[]);
 if(keyboard)return null;
 return <View testID={`native-tabs:${ios?'ios':'android'}`} style={[styles.container,ios?styles.ios:styles.android,rtl&&styles.reverse]}>
  {tabs.map((tab,index)=><TabButton key={tab.id} selected={tab.selected} testID={`nav:${tab.id}`} accessibilityRole="tab" accessibilityLabel={tab.label} accessibilityState={{selected:tab.selected,disabled:busy}} disabled={busy} onPress={()=>onSelect(tab.id)} android_ripple={{color:tones[index%tones.length],borderless:false}} style={styles.item}>
   <View style={[styles.icon,ios?styles.iosIcon:styles.androidIcon,tab.selected&&{backgroundColor:tones[index%tones.length]},tab.selected&&ios&&styles.iosSelected]}>
    <Icon name={tab.icon} size={ios?23:24} color={tab.selected?colors.forest:colors.muted}/>
   </View>
   <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.78} style={[textStyle(rtl,tab.selected?'bold':'medium'),styles.label,{color:tab.selected?colors.forest:colors.muted,fontSize:rtl?11:12}]}>{tab.label}</Text>
   {ios&&<View style={[styles.dot,{backgroundColor:tab.selected?colors.coral:'transparent'}]}/>}
  </TabButton>)}
 </View>;
}
const styles=StyleSheet.create({
 container:{flexDirection:'row',alignItems:'center',paddingHorizontal:6,paddingVertical:8,gap:2,flexShrink:0},reverse:{flexDirection:'row-reverse'},
 ios:{marginHorizontal:12,marginTop:6,marginBottom:6,borderRadius:32,backgroundColor:'#FFFFFFF5',borderWidth:1,borderColor:'#FFFFFF',shadowColor:'#264B3A',shadowOffset:{width:0,height:5},shadowOpacity:0.13,shadowRadius:16},
 android:{backgroundColor:colors.white,borderTopLeftRadius:26,borderTopRightRadius:26,borderTopWidth:1,borderColor:'#DCE7DF',elevation:10},
 item:{flex:1,minHeight:58,justifyContent:'center',alignItems:'center',borderRadius:22},
 icon:{alignItems:'center',justifyContent:'center'},iosIcon:{width:43,height:35,borderRadius:17},androidIcon:{width:55,height:33,borderRadius:18},
 iosSelected:{borderWidth:1,borderColor:'#FFFFFF'},label:{textAlign:'center',maxWidth:64,lineHeight:18,paddingHorizontal:2},dot:{width:4,height:4,borderRadius:2,marginTop:1},
});
