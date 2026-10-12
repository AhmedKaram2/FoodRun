import React from "react";
import { Pressable, RefreshControl, StyleSheet, Text, View } from "react-native";
import type { Action, Card, Snapshot } from "../types";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { Button, PagedCards, textStyle } from "../components/Controls";
import Icon from "../components/Icon";
import ScrollArea from "../components/ScrollArea";

export default function HomeScreen({snapshot}:{snapshot:Snapshot}) {
  const {rtl,busy,online}=snapshot.state;
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const actions=[snapshot.primaryAction,...snapshot.inlineButtons,...snapshot.utilityButtons].filter((value):value is Action=>!!value);
  const current=cards.find(card=>card.id==="continue-order");
  const currentAction=current?.buttons.find(button=>button.action==="RESUME");
  const rooms=cards.filter(card=>card.id.startsWith("session:"));
  const currentRoom=rooms.find(card=>card.buttons.some(button=>button.action==="RESUME" && button.value===currentAction?.value));
  const recent=rooms.filter(card=>card!==currentRoom).slice(0,3);
  const invitations=cards.filter(card=>card.id.startsWith("invitation:"));
  const pay=cards.find(card=>card.id.endsWith("wallet-direction:false"));
  const receive=cards.find(card=>card.id.endsWith("wallet-direction:true"));
  const walletBalance=cards.find(card=>card.id.endsWith("wallet-funds"));
  const extra=cards.filter(card=>card.id!=="continue-order" && card.id!=="about" && !/^(session:|invitation:|dashboard:)/.test(card.id));
  const recovery=actions.filter(button=>/^(RETRY|REFRESH|OPEN_CONNECTION_OPTIONS)$/.test(button.action));
  const shortcuts=[{kind:"OPEN_LIBRARY",icon:"food",title:tx(rtl,"Menus","القوائم"),tone:colors.honey},{kind:"FRIENDS_ACTION",icon:"friends",title:tx(rtl,"Groups","المجموعات"),tone:colors.mint},{kind:"QUICK_SPIN",icon:"wheel",title:tx(rtl,"Quick Choice","الاختيار السريع"),tone:colors.sky}];
  const firstName=snapshot.profile?.name.trim().split(/\s+/)[0];
  const open=(button:Action)=>{if(!busy && button.enabled)dispatch(button.action,button.value);};
  const row=(card:Card)=>{
    const button=card.buttons.find(button=>button.action==="RESUME");
    if(!button)return null;
    return <Pressable key={card.id} accessibilityRole="button" accessibilityLabel={`${button.title} · ${card.title}`} accessibilityState={{disabled:busy || !button.enabled}} disabled={busy || !button.enabled} onPress={()=>open(button)} style={({pressed})=>[styles.room,rtl && styles.reverse,pressed && styles.pressed]}>
      <View style={styles.roomIcon}><Icon name="room" size={19} color={colors.forest}/></View>
      <View style={styles.grow}><Text numberOfLines={1} style={[textStyle(rtl,"semibold"),styles.roomName]}>{card.title}</Text><Text numberOfLines={2} style={[textStyle(rtl),styles.small]}>{card.detail}</Text>{!!card.badge && <Text style={[textStyle(rtl,"semibold"),styles.notice]}>{card.badge}</Text>}</View>
      <Text accessibilityElementsHidden style={styles.arrow}>{rtl ? "‹":"›"}</Text>
    </Pressable>;
  };
  return <ScrollArea testID="home-content" keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={busy} onRefresh={()=>dispatch("REFRESH")} tintColor={colors.forest}/>} contentContainerStyle={styles.content}>
    <View style={[styles.heading,rtl && styles.reverse]}><View style={styles.grow}><Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.greeting]}>{firstName ? tx(rtl,`Hi, ${firstName}`,`أهلاً، ${firstName}`) : tx(rtl,"Ready to order?","جاهز لطلبك؟")}</Text><Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"Your next meal, one tap away.","وجبتك القادمة تبدأ بضغطة.")}</Text></View>{!online && <Text style={[textStyle(rtl,"medium"),styles.offline]}>{tx(rtl,"Offline","غير متصل")}</Text>}</View>
    {current && currentAction && <Pressable testID="home-current-order" accessibilityRole="button" accessibilityLabel={`${currentAction.title} · ${current.detail}`} accessibilityState={{disabled:busy || !currentAction.enabled}} disabled={busy || !currentAction.enabled} onPress={()=>open(currentAction)} style={({pressed})=>[styles.current,pressed && styles.pressed]}>
      <View style={[styles.currentHeading,rtl && styles.reverse]}><Icon name="food" color={colors.white} size={18}/><Text style={[textStyle(rtl,"medium"),styles.currentLabel]}>{tx(rtl,"Your current order","طلبك الحالي")}</Text></View>
      <Text numberOfLines={2} style={[textStyle(rtl,"bold"),styles.currentTitle]}>{current.detail}</Text>
      {!!currentRoom?.detail && <Text numberOfLines={2} style={[textStyle(rtl),styles.currentDetail]}>{currentRoom.detail}</Text>}
      {!!currentRoom?.badge && <Text style={[textStyle(rtl,"semibold"),styles.currentDetail]}>{currentRoom.badge}</Text>}
      <View style={[styles.currentHeading,rtl && styles.reverse]}><Text style={[textStyle(rtl,"semibold"),styles.continue]}>{currentAction.title}</Text><Text accessibilityElementsHidden style={styles.currentArrow}>{rtl ? "←":"→"}</Text></View>
    </Pressable>}
    <View testID="home-room-actions" style={[styles.actions,rtl && styles.reverse]}>{["CREATE","JOIN"].map(kind=>{const button=actions.find(button=>button.action===kind);return button && <View key={kind} style={styles.grow}><Button action={button} busy={busy} rtl={rtl} compact/></View>;})}</View>
    {recovery.map(button=><Button key={button.action} action={button} busy={busy} rtl={rtl} compact/>)}
    {!!invitations.length && <View style={styles.section}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle]}>{tx(rtl,"Invitations","الدعوات")} · {invitations.length}</Text><PagedCards cards={invitations} busy={busy} rtl={rtl} pageSize={2}/></View>}
    <Pressable testID="home-wallet-summary" accessibilityRole="button" accessibilityLabel={tx(rtl,"Open wallet & payments","فتح المحفظة والمدفوعات")} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_WALLET")} style={({pressed})=>[styles.wallet,pressed && styles.pressed]}>
      <View style={[styles.sectionHeading,rtl && styles.reverse]}><Icon name="wallet" color={colors.forest} size={19}/><Text style={[textStyle(rtl,"semibold"),styles.walletTitle]}>{tx(rtl,"Wallet & payments","المحفظة والمدفوعات")}</Text><Text accessibilityElementsHidden style={styles.arrow}>{rtl ? "‹":"›"}</Text></View>
      {pay || receive ? <View style={[styles.totals,rtl && styles.reverse]}>{[{card:pay,title:tx(rtl,"To pay","للدفع")},{card:receive,title:tx(rtl,"To receive","للاستلام")}].map(({card,title})=>card && <View key={card.id} style={styles.total}><Text style={[textStyle(rtl),styles.small]}>{title}</Text><Text numberOfLines={2} style={[textStyle(rtl,"semibold"),styles.amount]}>{card.badge}</Text></View>)}</View> : <Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"View your balance and payment updates.","تابع رصيدك وتحديثات الدفع.")}</Text>}
      {!!walletBalance && <Text numberOfLines={2} style={[textStyle(rtl),styles.balance]}>{tx(rtl,"Available balance","الرصيد المتاح")} · {walletBalance.detail}</Text>}
    </Pressable>
    <View style={styles.section}><View style={[styles.sectionHeading,rtl && styles.reverse]}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle,styles.grow]}>{tx(rtl,"Your rooms","غرفك")}</Text><Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"View all rooms","عرض كل الغرف")} disabled={busy} onPress={()=>dispatch("OPEN_ROOMS")} style={styles.link}><Text style={[textStyle(rtl,"semibold"),styles.linkText]}>{tx(rtl,"View all","عرض الكل")}</Text></Pressable></View>{recent.map(row)}{!rooms.length && <View style={styles.empty}><Icon name="room" color={colors.forest}/><Text style={[textStyle(rtl),styles.small,styles.grow]}>{tx(rtl,"Create or join a room above. Your rooms will appear here.","أنشئ غرفة أو انضم لغرفة بالأعلى، وستظهر غرفك هنا.")}</Text></View>}</View>
    <View style={[styles.shortcuts,rtl && styles.reverse]}>{shortcuts.map(shortcut=>{const button=actions.find(button=>button.action===shortcut.kind);return button && <Pressable key={shortcut.kind} accessibilityRole="button" accessibilityLabel={shortcut.title} accessibilityState={{disabled:busy || !button.enabled}} disabled={busy || !button.enabled} onPress={()=>open(button)} style={({pressed})=>[styles.shortcut,{backgroundColor:shortcut.tone},pressed && styles.pressed]}><Icon name={shortcut.icon} color={colors.forest}/><Text style={[textStyle(rtl,"medium"),styles.shortcutText]}>{shortcut.title}</Text></Pressable>;})}</View>
    <PagedCards cards={extra} busy={busy} rtl={rtl}/>
  </ScrollArea>;
}
const styles=StyleSheet.create({
 content:{padding:18,paddingTop:8,paddingBottom:28,gap:16},grow:{flex:1,minWidth:0},reverse:{flexDirection:"row-reverse"},pressed:{opacity:.72},heading:{flexDirection:"row",alignItems:"center",gap:8},greeting:{fontSize:21,lineHeight:32},small:{fontSize:12,lineHeight:20,color:colors.muted},offline:{fontSize:11,color:colors.muted,padding:8,borderRadius:10,backgroundColor:colors.white},
 current:{padding:16,borderRadius:20,backgroundColor:colors.forest,gap:6},currentHeading:{flexDirection:"row",alignItems:"center",gap:8},currentLabel:{fontSize:11,lineHeight:18,color:"#DAEDE3"},currentTitle:{fontSize:17,lineHeight:26,color:colors.white},currentDetail:{fontSize:12,lineHeight:20,color:"#DAEDE3"},continue:{fontSize:13,lineHeight:24,color:colors.white,flex:1,paddingTop:4},currentArrow:{fontSize:20,color:colors.white},actions:{flexDirection:"row",gap:10},section:{gap:8},sectionHeading:{flexDirection:"row",alignItems:"center",gap:8},sectionTitle:{fontSize:15,lineHeight:24},
 wallet:{padding:14,borderRadius:18,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white,gap:10},walletTitle:{fontSize:14,lineHeight:24,flex:1},totals:{flexDirection:"row",gap:12},total:{flex:1,minWidth:0},amount:{fontSize:16,lineHeight:26,color:colors.forest},balance:{fontSize:11,lineHeight:20,color:colors.muted,borderTopWidth:1,borderTopColor:colors.line,paddingTop:8},arrow:{fontSize:24,color:colors.muted},
 room:{flexDirection:"row",alignItems:"center",gap:10,padding:12,borderRadius:16,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},roomIcon:{width:34,height:34,borderRadius:12,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},roomName:{fontSize:13,lineHeight:22},notice:{fontSize:11,lineHeight:20,color:colors.primary},link:{minHeight:44,justifyContent:"center",paddingHorizontal:6},linkText:{fontSize:12,lineHeight:20,color:colors.forest},empty:{flexDirection:"row",alignItems:"center",gap:12,padding:12,backgroundColor:colors.mint,borderRadius:14},shortcuts:{flexDirection:"row",gap:8},shortcut:{flex:1,minHeight:76,padding:8,alignItems:"center",justifyContent:"center",gap:6,borderRadius:16},shortcutText:{fontSize:11,lineHeight:20,textAlign:"center"},
});
