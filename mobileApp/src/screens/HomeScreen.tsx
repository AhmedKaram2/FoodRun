import React, { useRef } from "react";
import { Pressable, RefreshControl, ScrollView, StyleSheet, Text, View, useWindowDimensions } from "react-native";
import type { Action, Card, Snapshot } from "../types";
import { dispatch } from "../native";
import { colors, tx } from "../theme";
import { Button, PagedCards, textStyle } from "../components/Controls";
import Icon from "../components/Icon";
import Illustration, { type IllustrationKind } from "../components/Illustration";
import ScrollArea from "../components/ScrollArea";
import { formatUi } from "../language";

export default function HomeScreen({snapshot}:{snapshot:Snapshot}) {
  const {rtl,busy,online}=snapshot.state;
  const roomStrip=useRef<ScrollView>(null), {fontScale}=useWindowDimensions();
  const cards=[...snapshot.topCards,...snapshot.sections.flatMap(section=>section.cards)];
  const actions=[snapshot.primaryAction,...snapshot.inlineButtons,...snapshot.utilityButtons].filter((value):value is Action=>!!value);
  const current=cards.find(card=>card.id==="continue-order");
  const currentAction=current?.buttons.find(button=>button.action==="RESUME");
  const rooms=cards.filter(card=>card.id.startsWith("session:"));
  const currentRoom=rooms.find(card=>card.buttons.some(button=>button.action==="RESUME" && button.value===currentAction?.value));
  const recent=rooms.filter(card=>card!==currentRoom).slice(0,6);
  const invitations=cards.filter(card=>card.id.startsWith("invitation:"));
  const pay=cards.find(card=>card.id.endsWith("wallet-direction:false"));
  const receive=cards.find(card=>card.id.endsWith("wallet-direction:true"));
  const walletBalance=cards.find(card=>card.id.endsWith("wallet-funds"));
  const spending=cards.filter(card=>card.id.startsWith("home-spending:"));
  const extra=cards.filter(card=>card.id!=="continue-order" && card.id!=="about" && !/^(session:|invitation:|dashboard:|home-spending:)/.test(card.id));
  const recovery=actions.filter(button=>/^(RETRY|REFRESH|OPEN_CONNECTION_OPTIONS)$/.test(button.action));
  const shortcuts:{kind:string;art:IllustrationKind;title:string;tone:string}[]=[
    {kind:"QUICK_SPIN",art:"wheel",title:tx(rtl,"Quick Choice","اختيار سريع"),tone:colors.sky},
    {kind:"CREATE_PAYMENT_ROOM",art:"wallet",title:tx(rtl,"Payment room","غرفة دفع"),tone:colors.honey},
    {kind:"OPEN_WALLET",art:"wallet",title:tx(rtl,"Wallet","المحفظة"),tone:colors.mint},
    {kind:"OPEN_LIBRARY",art:"food",title:tx(rtl,"Menus","القوائم"),tone:colors.coralWash},
    {kind:"FRIENDS_ACTION",art:"friends",title:tx(rtl,"Groups","المجموعات"),tone:colors.sky},
    {kind:"NEW_RESTAURANT",art:"room",title:tx(rtl,"Add restaurant","إضافة مطعم"),tone:colors.honey},
  ];
  const firstName=snapshot.profile?.name.trim().split(/\s+/)[0];
  const open=(button:Action)=>{if(!busy && button.enabled)dispatch(button.action,button.value);};
  const row=(card:Card)=>{
    const button=card.buttons.find(button=>button.action==="RESUME");
    if(!button)return null;
    return <Pressable key={card.id} accessibilityRole="button" accessibilityLabel={`${button.title} · ${card.title}`} accessibilityHint={card.detail} accessibilityState={{disabled:busy || !button.enabled}} disabled={busy || !button.enabled} onPress={()=>open(button)} style={({pressed})=>[styles.room,pressed && styles.pressed]}>
      <View style={[styles.roomTop,rtl && styles.reverse]}><View style={styles.roomIcon}><Icon name="room" size={18} color={colors.forest}/></View><Text accessibilityElementsHidden style={styles.arrow}>{rtl ? "‹":"›"}</Text></View>
      <Text numberOfLines={2} style={[textStyle(rtl,"semibold"),styles.roomName]}>{card.title}</Text><Text numberOfLines={2} style={[textStyle(rtl),styles.small]}>{card.detail.split("\n").slice(0,2).join(" · ")}</Text>{!!card.badge && <Text numberOfLines={1} style={[textStyle(rtl,"semibold"),styles.notice]}>{card.badge}</Text>}
    </Pressable>;
  };
  return <ScrollArea testID="home-content" keyboardShouldPersistTaps="handled" refreshControl={<RefreshControl refreshing={busy} onRefresh={()=>dispatch("REFRESH")} tintColor={colors.forest}/>} contentContainerStyle={styles.content}>
    <View style={[styles.heading,rtl && styles.reverse]}><View style={styles.grow}><Text accessibilityRole="header" style={[textStyle(rtl,"bold"),styles.greeting]}>{firstName ? (rtl ? formatUi("Hi, {name}", {name:firstName}, "أهلاً، {name}") : `Hi, ${firstName}`) : tx(rtl,"Ready to order?","جاهز لطلبك؟")}</Text><Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"Your next meal, one tap away.","وجبتك القادمة تبدأ بضغطة.")}</Text></View><Illustration kind="food" size={58}/>{!online && <Text style={[textStyle(rtl,"medium"),styles.offline]}>{tx(rtl,"Offline","غير متصل")}</Text>}</View>
    {current && currentAction && <Pressable testID="home-current-order" accessibilityRole="button" accessibilityLabel={`${currentAction.title} · ${current.detail}`} accessibilityState={{disabled:busy || !currentAction.enabled}} disabled={busy || !currentAction.enabled} onPress={()=>open(currentAction)} style={({pressed})=>[styles.current,pressed && styles.pressed]}>
      <View style={[styles.currentHeading,rtl && styles.reverse]}><View style={styles.currentArt}><Illustration kind="food" size={42}/></View><View style={styles.grow}><Text style={[textStyle(rtl,"medium"),styles.currentLabel]}>{tx(rtl,"Your current order","طلبك الحالي")}</Text><Text numberOfLines={2} style={[textStyle(rtl,"bold"),styles.currentTitle]}>{current.detail}</Text>{!!currentRoom?.badge && <Text style={[textStyle(rtl,"semibold"),styles.currentDetail]}>{currentRoom.badge}</Text>}</View><Text accessibilityElementsHidden style={styles.currentArrow}>{rtl ? "‹":"›"}</Text></View>
      <View style={[styles.currentHeading,rtl && styles.reverse]}><Text numberOfLines={1} style={[textStyle(rtl),styles.currentDetail,styles.grow]}>{currentRoom?.detail.split("\n")[0]}</Text><Text style={[textStyle(rtl,"semibold"),styles.continue]}>{currentAction.title} {rtl ? "←":"→"}</Text></View>
    </Pressable>}
    <View testID="home-room-actions" style={[styles.actions,rtl && styles.reverse]}>{["CREATE","JOIN"].map(kind=>{const button=actions.find(button=>button.action===kind);return button && <View key={kind} style={styles.grow}><Button action={button} busy={busy} rtl={rtl} compact/></View>;})}</View>
    {recovery.map(button=><Button key={button.action} action={button} busy={busy} rtl={rtl} compact/>)}
    {!!invitations.length && <View style={styles.section}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle]}>{tx(rtl,"Invitations","الدعوات")} · {invitations.length}</Text><PagedCards cards={invitations} busy={busy} rtl={rtl} pageSize={2}/></View>}
    <View style={styles.section}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle]}>{tx(rtl,"Quick actions","إجراءات سريعة")}</Text>
      <View testID="home-feature-grid" style={[styles.shortcuts,rtl && styles.reverse]}>{shortcuts.map(shortcut=>{const button=actions.find(button=>button.action===shortcut.kind) || (shortcut.kind==="OPEN_WALLET" ? {title:shortcut.title,action:shortcut.kind,value:"",enabled:true,primary:false,destructive:false}:null);return button && <Pressable key={shortcut.kind} testID={`home-feature:${shortcut.kind}`} accessibilityRole="button" accessibilityLabel={shortcut.title} accessibilityState={{disabled:busy || !button.enabled}} disabled={busy || !button.enabled} onPress={()=>open(button)} style={({pressed})=>[styles.shortcut,{backgroundColor:shortcut.tone,flexBasis:fontScale>1.3 ? "48%":"31.5%"},pressed && styles.pressed]}><Illustration kind={shortcut.art} size={44}/><Text style={[textStyle(rtl,"semibold"),styles.shortcutText]}>{shortcut.title}</Text></Pressable>;})}</View>
    </View>
    <View style={styles.section}><View style={[styles.sectionHeading,rtl && styles.reverse]}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle,styles.grow]}>{tx(rtl,"Recent rooms","أحدث الغرف")}</Text><Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"View all rooms","عرض كل الغرف")} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_ROOMS")} style={styles.link}><Text style={[textStyle(rtl,"semibold"),styles.linkText]}>{tx(rtl,"View all","عرض الكل")}</Text></Pressable></View>
      {!!recent.length && <ScrollView ref={roomStrip} testID="home-recent-rooms" horizontal nestedScrollEnabled directionalLockEnabled showsHorizontalScrollIndicator={false} decelerationRate="fast" snapToInterval={204} style={styles.roomStrip} contentContainerStyle={[styles.roomStripContent,rtl && styles.reverse]} onContentSizeChange={()=>{if(rtl)roomStrip.current?.scrollToEnd({animated:false});}}>{recent.map(row)}</ScrollView>}
      {!rooms.length && <View style={[styles.empty,rtl && styles.reverse]}><Illustration kind="room" size={42}/><Text style={[textStyle(rtl),styles.small,styles.grow]}>{tx(rtl,"Create or join a room above. Your rooms will appear here.","أنشئ غرفة أو انضم لغرفة بالأعلى، وستظهر غرفك هنا.")}</Text></View>}
    </View>
    <View style={styles.section}><View style={[styles.sectionHeading,rtl && styles.reverse]}><Text accessibilityRole="header" style={[textStyle(rtl,"semibold"),styles.sectionTitle,styles.grow]}>{tx(rtl,"My spending","مصروفاتي")}</Text><Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"View spending","عرض المصروفات")} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_WALLET","spending")} style={styles.link}><Text style={[textStyle(rtl,"semibold"),styles.linkText]}>{tx(rtl,"Details","التفاصيل")}</Text></Pressable></View>
      <View testID="home-spending-summary" style={[styles.stats,rtl && styles.reverse]}>{spending.map((card,index)=><Pressable key={card.id} accessibilityRole="button" accessibilityLabel={`${card.title} · ${card.badge}`} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_WALLET","spending")} style={[styles.stat,{backgroundColor:index ? colors.mint:colors.honey}]}><View style={[styles.sectionHeading,rtl && styles.reverse]}><Text style={[textStyle(rtl,"medium"),styles.statTitle,styles.grow]}>{card.title}</Text><Icon name="wallet" color={colors.forest} size={17}/></View><Text style={[textStyle(rtl,"bold"),styles.statAmount]}>{card.badge}</Text><Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"Net spending","صافي المصروفات")}</Text></Pressable>)}</View>
      {!spending.length && <Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"Refresh to load spending statistics.","حدّث الصفحة لتحميل إحصاءات المصروفات.")}</Text>}
    </View>
    <Pressable testID="home-wallet-summary" accessibilityRole="button" accessibilityLabel={tx(rtl,"Open wallet & payments","فتح المحفظة والمدفوعات")} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_WALLET")} style={({pressed})=>[styles.wallet,pressed && styles.pressed]}>
      <View style={[styles.sectionHeading,rtl && styles.reverse]}><Icon name="wallet" color={colors.forest} size={19}/><Text style={[textStyle(rtl,"semibold"),styles.walletTitle]}>{tx(rtl,"Wallet & payments","المحفظة والمدفوعات")}</Text><Text accessibilityElementsHidden style={styles.arrow}>{rtl ? "‹":"›"}</Text></View>
      {pay || receive ? <View style={[styles.totals,rtl && styles.reverse]}>{[{card:pay,title:tx(rtl,"To pay","للدفع")},{card:receive,title:tx(rtl,"To receive","للاستلام")}].map(({card,title})=>card && <View key={card.id} style={styles.total}><Text style={[textStyle(rtl),styles.small]}>{title}</Text><Text numberOfLines={2} style={[textStyle(rtl,"semibold"),styles.amount]}>{card.badge}</Text></View>)}</View> : <Text style={[textStyle(rtl),styles.small]}>{tx(rtl,"View your balance and payment updates.","تابع رصيدك وتحديثات الدفع.")}</Text>}
      {!!walletBalance && <Text numberOfLines={2} style={[textStyle(rtl),styles.balance]}>{tx(rtl,"Available balance","الرصيد المتاح")} · {walletBalance.detail}</Text>}
    </Pressable>
    {snapshot.adminAvailable && !snapshot.supportActive && <Button action={{title:tx(rtl,"Admin panel","لوحة الإدارة"),action:"OPEN_ADMIN",value:"",primary:false,destructive:false,enabled:true}} rtl={rtl} busy={busy} icon="settings" caption={tx(rtl,"Users, rooms, menus, payments and app settings","المستخدمون والغرف والقوائم والمدفوعات وإعدادات التطبيق")}/>}
    <PagedCards cards={extra} busy={busy} rtl={rtl}/>
  </ScrollArea>;
}
const styles=StyleSheet.create({
 content:{padding:18,paddingTop:8,paddingBottom:28,gap:12},grow:{flex:1,minWidth:0},reverse:{flexDirection:"row-reverse"},pressed:{opacity:.72},heading:{flexDirection:"row",alignItems:"center",gap:8},greeting:{fontSize:21,lineHeight:32},small:{fontSize:12,lineHeight:20,color:colors.muted},offline:{fontSize:11,color:colors.muted,padding:8,borderRadius:10,backgroundColor:colors.white},
 currentArt:{width:48,height:48,borderRadius:15,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},current:{padding:13,borderRadius:20,backgroundColor:colors.forest,gap:6},currentHeading:{flexDirection:"row",alignItems:"center",gap:8},currentLabel:{fontSize:11,lineHeight:18,color:"#DAEDE3"},currentTitle:{fontSize:16,lineHeight:26,color:colors.white},currentDetail:{fontSize:12,lineHeight:20,color:"#DAEDE3"},continue:{fontSize:13,lineHeight:24,color:colors.white,flex:1,paddingTop:4},currentArrow:{fontSize:20,color:colors.white},actions:{flexDirection:"row",gap:10},section:{gap:8},sectionHeading:{flexDirection:"row",alignItems:"center",gap:8},sectionTitle:{fontSize:15,lineHeight:24},
 wallet:{padding:14,borderRadius:18,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white,gap:10},walletTitle:{fontSize:14,lineHeight:24,flex:1},totals:{flexDirection:"row",gap:12},total:{flex:1,minWidth:0},amount:{fontSize:16,lineHeight:26,color:colors.forest},balance:{fontSize:11,lineHeight:20,color:colors.muted,borderTopWidth:1,borderTopColor:colors.line,paddingTop:8},arrow:{fontSize:24,color:colors.muted},
 stats:{flexDirection:"row",gap:10},stat:{flex:1,minWidth:0,padding:12,borderRadius:18,gap:5},statTitle:{fontSize:11,lineHeight:20},statAmount:{fontSize:17,lineHeight:27,color:colors.forest},roomStrip:{marginHorizontal:-18,direction:"ltr"},roomStripContent:{paddingHorizontal:18,gap:10,paddingBottom:2},roomTop:{flexDirection:"row",alignItems:"center",justifyContent:"space-between"},
 room:{width:194,gap:6,padding:12,borderRadius:16,borderWidth:1,borderColor:colors.line,backgroundColor:colors.white},roomIcon:{width:34,height:34,borderRadius:12,backgroundColor:colors.mint,alignItems:"center",justifyContent:"center"},roomName:{fontSize:13,lineHeight:22},notice:{fontSize:11,lineHeight:20,color:colors.primary},link:{minHeight:44,justifyContent:"center",paddingHorizontal:6},linkText:{fontSize:12,lineHeight:20,color:colors.forest},empty:{flexDirection:"row",alignItems:"center",gap:12,padding:12,backgroundColor:colors.mint,borderRadius:14},shortcuts:{flexDirection:"row",flexWrap:"wrap",gap:8},shortcut:{flexGrow:1,minHeight:92,padding:8,alignItems:"center",justifyContent:"center",gap:6,borderRadius:16},shortcutText:{fontSize:11,lineHeight:20,textAlign:"center"},
});
