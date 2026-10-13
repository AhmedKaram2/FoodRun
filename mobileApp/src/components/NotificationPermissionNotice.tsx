import React, {useEffect, useState} from "react";
import {AppState, Linking, Pressable, StyleSheet, Text, View} from "react-native";
import {dispatch, native} from "../native";
import {colors, tx} from "../theme";
import {textStyle} from "./Controls";
import Icon from "./Icon";

export default function NotificationPermissionNotice({rtl,accountId,busy,pushEnabled=true}:{rtl:boolean;accountId:string;busy:boolean;pushEnabled?:boolean}) {
  const [permission,setPermission]=useState(""), [error,setError]=useState("");
  useEffect(()=>{
    let active=true;
    const refresh=()=>native.getNotificationPermission?.().then(value=>{if(active)setPermission(value);}).catch(()=>{});
    refresh();
    const subscription=AppState.addEventListener("change",state=>{if(state==="active")refresh();});
    return ()=>{active=false;subscription?.remove();};
  },[accountId,busy]);
  if(pushEnabled && (!permission || permission==="enabled"))return null;
  const open=async()=>{
    setError("");
    if(!pushEnabled){dispatch("OPEN_NOTIFICATION_PREFERENCES");return;}
    if(permission==="notDetermined"){dispatch("ENABLE_ALERTS");return;}
    try {if(native.openNotificationSettings)await native.openNotificationSettings();else await Linking.openSettings();}
    catch {setError(tx(rtl,"Could not open settings. Try again.","تعذر فتح الإعدادات. حاول مجدداً."));}
  };
  return <View testID="notification-permission-notice" style={styles.notice}>
    <View style={[styles.row,rtl && {flexDirection:"row-reverse"}]}><Icon name="bell" size={18} color={colors.forest}/><View style={{flex:1}}>
      <Text style={[textStyle(rtl,"semibold"),styles.title]}>{tx(rtl,"Notifications are off","الإشعارات غير مفعلة")}</Text>
      <Text style={[textStyle(rtl),styles.detail]}>{tx(rtl,"Enable them for order, arrival and payment updates.","فعّلها لمتابعة الطلب ووصول الطعام والدفعات.")}</Text>
    </View><Pressable accessibilityRole="button" disabled={busy} accessibilityState={{disabled:busy}} onPress={open} style={styles.button}>
      <Text style={[textStyle(rtl,"semibold"),styles.label]}>{pushEnabled && permission==="notDetermined" ? tx(rtl,"Enable","تفعيل") : tx(rtl,"Open settings","فتح الإعدادات")}</Text>
    </Pressable></View>
    {!!error && <Text accessibilityRole="alert" style={[textStyle(rtl),styles.detail,{color:colors.danger}]}>{error}</Text>}
  </View>;
}
const styles=StyleSheet.create({notice:{marginHorizontal:18,marginBottom:8,padding:12,borderRadius:14,backgroundColor:colors.mint,gap:6},row:{flexDirection:"row",alignItems:"center",gap:8},title:{fontSize:12,lineHeight:20,color:colors.forest},detail:{fontSize:11,lineHeight:18,color:colors.muted},button:{minHeight:44,justifyContent:"center",paddingHorizontal:10,borderRadius:10,backgroundColor:colors.white},label:{fontSize:11,color:colors.forest}});
