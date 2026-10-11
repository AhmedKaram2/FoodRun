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
 const renderTab=(tab:NativeTab,index:number,separate=false)=><TabButton key={tab.id} selected={tab.selected} testID={`nav:${tab.id}`} accessibilityRole="tab" accessibilityLabel={tab.label} accessibilityState={{selected:tab.selected,disabled:busy}} disabled={busy} onPress={()=>onSelect(tab.id)} android_ripple={{color:tones[index%tones.length],borderless:false}} style={[styles.item,separate&&styles.moreItem]}>
   <View style={[styles.icon,ios?styles.iosIcon:styles.androidIcon,tab.selected&&!separate&&{backgroundColor:tones[index%tones.length]},tab.selected&&ios&&!separate&&styles.iosSelected]}>
    <Icon name={tab.icon} size={ios?23:24} color={tab.selected?colors.forest:colors.muted}/>
   </View>
   <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.78} style={[textStyle(rtl,tab.selected?'bold':'medium'),styles.label,{color:tab.selected?colors.forest:colors.muted,fontSize:rtl?11:12}]}>{tab.label}</Text>
 </TabButton>;
 const more=tabs.find(tab=>tab.id==='MORE');
 return <View testID={`native-tabs:${ios?'ios':'android'}`} style={[ios?styles.iosRow:styles.android,rtl&&styles.reverse]}>
  {ios ? <>
   <View testID="ios-tab-dock" style={[styles.glass,styles.dock,rtl&&styles.reverse]}>{tabs.filter(tab=>tab.id!=='MORE').map((tab,index)=>renderTab(tab,index))}</View>
   {more&&<View testID="ios-more-bubble" style={[styles.glass,styles.moreBubble,more.selected&&styles.moreSelected]}>{renderTab(more,tabs.indexOf(more),true)}</View>}
  </> : tabs.map((tab,index)=>renderTab(tab,index))}
 </View>;
}
const styles=StyleSheet.create({
 reverse:{flexDirection:'row-reverse'},
 iosRow:{flexDirection:'row',alignItems:'center',gap:10,marginHorizontal:12,marginVertical:8,flexShrink:0},
 glass:{backgroundColor:'#FFFFFFF2',borderWidth:1,borderColor:'#FFFFFF',shadowColor:'#264B3A',shadowOffset:{width:0,height:5},shadowOpacity:0.13,shadowRadius:16},
 dock:{flex:1,minWidth:0,flexDirection:'row',alignItems:'center',paddingHorizontal:4,paddingVertical:5,borderRadius:36},
 moreBubble:{width:68,height:68,borderRadius:34,alignItems:'center',justifyContent:'center'},moreSelected:{backgroundColor:colors.lavender},moreItem:{flex:0,width:66,minHeight:66,borderRadius:33},
 android:{flexDirection:'row',alignItems:'center',paddingHorizontal:6,paddingVertical:8,gap:2,flexShrink:0,backgroundColor:colors.white,borderTopLeftRadius:26,borderTopRightRadius:26,borderTopWidth:1,borderColor:'#DCE7DF',elevation:10},
 item:{flex:1,minWidth:0,minHeight:58,justifyContent:'center',alignItems:'center',borderRadius:28},
 icon:{alignItems:'center',justifyContent:'center'},iosIcon:{width:43,height:33,borderRadius:17},androidIcon:{width:55,height:33,borderRadius:18},
 iosSelected:{borderWidth:1,borderColor:'#FFFFFF'},label:{textAlign:'center',maxWidth:64,lineHeight:18,paddingHorizontal:2},
});
