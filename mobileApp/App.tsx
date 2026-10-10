import React, { useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Image,
  AppState,
  BackHandler,
  KeyboardAvoidingView,
  Linking,
  Platform,
  Pressable,
  RefreshControl,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaProvider, SafeAreaView } from "react-native-safe-area-context";
import { colors, font, tx } from "./src/theme";
import { dispatch, events, native, parseSnapshot, update } from "./src/native";
import type { Snapshot, Section, Field } from "./src/types";
import Brand from "./src/components/Brand";
import Icon from "./src/components/Icon";
import {
  Button,
  CardView,
  FieldInput,
  PagedCards,
  action,
  textStyle,
  styles as controls,
} from "./src/components/Controls";
import LiveWheel from "./src/components/LiveWheel";
import HomeBanner from "./src/components/HomeBanner";
import BottomSheet from "./src/components/BottomSheet";
import RoomAttention from "./src/components/RoomAttention";
import SelectionScreen from "./src/components/SelectionScreen";
import GuidedFlowScreen from "./src/components/GuidedFlowScreen";
import { useGuidedFlow } from "./src/guidedFlows";
import { MotionProvider, PageMotion, TabButton } from "./src/components/Motion";
import QuickWheel from "./src/screens/QuickWheel";
import { MoreSheet, NotificationCenter, ProfileScreen, LibraryScreen } from "./src/screens/AccountScreens";
import { roomCodeFromLink } from "./src/roomLink";
import { isRoom, roomSections, roomActionTab, roomFieldTab } from "./src/roomTabs";

function sectionsFor(snapshot: Snapshot): Section[] {
  const { state } = snapshot;
  if (isRoom(state.page)) return roomSections(snapshot);
  if (state.page === "FRIENDS") {
    const cards = snapshot.sections.flatMap((section) => section.cards);
    return [
      {
        title: tx(state.rtl, "Edit group", "تعديل المجموعة"),
        cards: cards.filter((card) =>
          /^friend-(email|result|searching|view)/.test(card.id),
        ),
        collapsed: false,
      },
      {
        title: tx(state.rtl, "Your groups", "مجموعاتك"),
        cards: cards.filter((card) => card.id.startsWith("friend-group:")),
        collapsed: false,
      },
      {
        title: tx(state.rtl, "Groups I joined", "المجموعات التي انضممت إليها"),
        cards: cards.filter((card) => card.id.startsWith("friend-joined:")),
        collapsed: false,
      },
    ];
  }
  return snapshot.sections;
}
function Application() {
  const content = useRef<ScrollView>(null);
  const roomContext = useRef<Snapshot | null>(null);
  const savedRoomTab = useRef<{key:string;tab:number} | null>(null);
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null),
    [failure, setFailure] = useState(""),
    [menu, setMenu] = useState(false),
    [selected, setSelected] = useState(0),
    [options, setOptions] = useState(false);
  const guided = useGuidedFlow(snapshot);
  useEffect(() => {
    let alive = true;
    const accept = (body: string) => {
      if (alive) {
        const incoming = parseSnapshot(body);
        if(incoming.state.page === "ROOM") roomContext.current = incoming;
        setSnapshot(incoming);
        setFailure("");
      }
    };
    const subscription = events.addListener("FoodRunState", accept);
    native
      .getSnapshot()
      .then(accept)
      .catch(() => setFailure("Could not open the app. Please restart."));
    const timer = setInterval(() => {
      if (AppState.currentState === "active") native.tick();
    }, 1000);
    return () => {
      alive = false;
      clearInterval(timer);
      subscription.remove();
    };
  }, []);
  useEffect(() => {
    const key = `${snapshot?.accountId}:${snapshot?.state.roomCode}`;
    setSelected(snapshot?.state.page === "ROOM" && savedRoomTab.current?.key === key ? savedRoomTab.current.tab : 0);
    setOptions(false);
  }, [snapshot?.state.page, snapshot?.state.roomCode, snapshot?.accountId]);
  useEffect(() => {
    if (snapshot?.state.wheel?.round.id) {
      setSelected(0);
      savedRoomTab.current = {key:`${snapshot.accountId}:${snapshot.state.roomCode}`,tab:0};
    }
  }, [snapshot?.state.wheel?.round.id]);
  useEffect(() => {
    const event = BackHandler.addEventListener("hardwareBackPress", () => {
      if (menu) {
        setMenu(false);
        return true;
      }
      if (snapshot?.state.walletHistoryPrompt) {
        dispatch("DISMISS_WALLET_HISTORY");
        return true;
      }
      if (guided.back()) return true;
      if (snapshot?.state.canGoBack) {
        dispatch("BACK");
        return true;
      }
      return false;
    });
    return () => event.remove();
  }, [snapshot?.state.canGoBack, snapshot?.state.walletHistoryPrompt, menu, guided.kind, guided.step, guided.picker, snapshot?.state.busy]);
  useEffect(() => {
    const open = (value: string) => {
      const room = roomCodeFromLink(value);
      if (room) {
        update("ROOM_CODE", room);
        dispatch("JOIN");
      }
    };
    Linking.getInitialURL().then((value) => {
      if (value) open(value);
    });
    const event = Linking.addEventListener("url", (event) => open(event.url));
    return () => event.remove();
  }, []);
  const sections = useMemo(
    () => (snapshot ? sectionsFor(snapshot) : []),
    [snapshot],
  );
  if (!snapshot)
    return (
      <SafeAreaView style={ui.loading}>
        <Brand />
        <ActivityIndicator color={colors.coral} />
        {!!failure && <Text style={{ color: colors.danger }}>{failure}</Text>}
      </SafeAreaView>
    );
  const { state } = snapshot,
    { rtl, busy } = state,
    section = sections[Math.min(selected, Math.max(0, sections.length - 1))],
    profileDetails = state.page === "PROFILE",
    room = isRoom(state.page),
    wallet = state.page === "WALLET",
    roomRoute = ["ROOMS", "ROOM", "ITEM", "CUSTOM_ITEM", "PRICE_ITEM", "PRICES", "PAYMENT", "REORDER", "RECEIPTS", "HISTORY", "ACCOUNT", "BLOCK_REQUEST", "WHEEL_PROTECTION", "SELECTION_OVERRIDE", "SETUP"].includes(state.page),
    walletRoute = ["WALLET", "WALLET_TOP_UP", "WALLET_BATCH"].includes(state.page);
  const adminTabs = snapshot.sections
    .flatMap((value) => value.cards)
    .find((card) => card.id === "admin-tabs");
  const fields = snapshot.mainFields.filter(field => !room || roomFieldTab(field) === selected);
  const changeTab = (index: number) => {
    if(room) savedRoomTab.current={key:`${snapshot.accountId}:${state.roomCode}`,tab:index};
    setSelected(index);
    setOptions(false);
    content.current?.scrollTo({ y: 0, animated: false });
  };
  const menuActions = [
    action(
      tx(
        rtl,
        snapshot.authenticated ? "My profile" : "Sign in / register",
        snapshot.authenticated
          ? "ملفي الشخصي"
          : "تسجيل الدخول أو إنشاء حساب",
      ),
      "OPEN_PROFILE",
    ),
    action(
      tx(rtl, "Friend groups", "مجموعات الأصدقاء"),
      "FRIENDS_ACTION",
      "open",
    ),
    action(tx(rtl, "Notifications", "الإشعارات"), "OPEN_NOTIFICATIONS"),
    action(tx(rtl, "Restaurants & menus", "المطاعم والقوائم"), "OPEN_LIBRARY"),
    action(tx(rtl, "Quick pick", "اختيار سريع"), "QUICK_SPIN"),
    action(
      tx(rtl, "Create payment room", "إنشاء غرفة دفع"),
      "CREATE_PAYMENT_ROOM",
    ),
    action(
      tx(rtl, "Notification preferences", "تفضيلات الإشعارات"),
      "OPEN_NOTIFICATION_PREFERENCES",
    ),
    ...(snapshot.adminAvailable
      ? [action(tx(rtl, "Administration", "الإدارة"), "OPEN_ADMIN")]
      : []),
    ...(snapshot.authenticated && !snapshot.supportActive
      ? [
          {
            ...action(tx(rtl, "Sign out", "تسجيل الخروج"), "SIGN_OUT"),
            destructive: true,
          },
        ]
      : []),
  ];
  const inline = snapshot.inlineButtons.filter(
    (button) =>
      (!room || roomActionTab(button) === selected) &&
      button.action !== "SET_LANGUAGE" &&
      (state.page !== "HOME" ||
        ["CREATE_PAYMENT_ROOM", "OPEN_CONNECTION_OPTIONS"].includes(
          button.action,
        )),
  );
  return (
    <SafeAreaView edges={["top", "bottom"]} style={ui.root}>
      <StatusBar barStyle="dark-content" backgroundColor={colors.cream} />
      <View style={[ui.header, rtl && ui.reverse]}>
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx(rtl, "FoodRun Home", "FoodRun الرئيسية")}
          onPress={() => dispatch("OPEN_HOME")}
        >
          <Brand small />
        </Pressable>
        <View style={ui.headerTools}>
          {snapshot.authenticated && snapshot.profile && <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"My profile","ملفي الشخصي")} onPress={() => dispatch("OPEN_PROFILE")} style={ui.profilePhoto}>
            {snapshot.profile.photo ? <Image source={{uri:snapshot.profile.photo}} style={ui.profilePhoto}/> : <Text style={[textStyle(rtl,"semibold"), {textAlign:"center",fontSize:16}]}>{Array.from(snapshot.profile.name)[0]}</Text>}
          </Pressable>}
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx(rtl, "Switch to Arabic", "التبديل إلى الإنجليزية")}
            style={ui.language}
            onPress={() => dispatch("SET_LANGUAGE", rtl ? "en" : "ar")}
          >
            <Text
              style={{ fontFamily: font("semibold", rtl), color: colors.ink }}
            >
              {rtl ? "EN" : "ع"}
            </Text>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={tx(rtl, `Notifications${snapshot.notificationUnread ? `, ${snapshot.notificationUnread} unread` : ""}`, `الإشعارات${snapshot.notificationUnread ? `، ${snapshot.notificationUnread} غير مقروءة` : ""}`)}
            style={[ui.icon, {backgroundColor:colors.mint,borderRadius:16}]}
            onPress={() => dispatch(snapshot.authenticated ? "OPEN_NOTIFICATIONS" : "OPEN_PROFILE")}
          >
            <Icon name="bell" color={colors.forest}/>
            {!!snapshot.notificationUnread && <View pointerEvents="none" style={{position:"absolute",top:2,right:0,minWidth:17,height:17,paddingHorizontal:3,borderRadius:9,backgroundColor:colors.primary,alignItems:"center",justifyContent:"center"}}><Text style={{fontFamily:font("semibold",false),fontSize:9,color:colors.white}}>{snapshot.notificationUnread > 99 ? "99+" : snapshot.notificationUnread}</Text></View>}
          </Pressable>
        </View>
      </View>
      {snapshot.supportActive && (
        <View style={ui.feedback}>
          <Text style={[textStyle(rtl), { flex: 1 }]}>
            {tx(
              rtl,
              "Owner support session · expires after 30 minutes",
              "جلسة دعم المالك · تنتهي بعد 30 دقيقة",
            )}
          </Text>
          <Button
            rtl={rtl}
            action={action(
              tx(rtl, "Return to my account", "العودة إلى حسابي"),
              "END_SUPPORT",
            )}
          />
        </View>
      )}
      {!!state.feedback && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx(rtl, "Dismiss message", "إغلاق الرسالة")}
          onPress={() => native.dismissFeedback(state.feedback!.id)}
          style={[ui.feedback, state.feedback.isError && ui.error]}
        >
          <Text
            accessibilityLiveRegion="polite"
            style={[
              textStyle(rtl),
              {
                flex: 1,
                color: state.feedback.isError ? colors.danger : colors.ink,
              },
            ]}
          >
            {state.feedback.message}
          </Text>
          <Icon name="close" size={18} />
        </Pressable>
      )}
      {!!state.error && !state.feedback && (
        <View style={ui.error}>
          <Text
            accessibilityRole="alert"
            style={[textStyle(rtl), { color: colors.danger }]}
          >
            {state.error}
          </Text>
        </View>
      )}
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === "ios" ? "padding" : undefined}
      >
        {state.page === "QUICK_SPIN" ? (
          <QuickWheel state={snapshot.quick} rtl={rtl} />
        ) : guided.kind && guided.screen ? (
          <GuidedFlowScreen key={`${snapshot.accountId}:${guided.kind}`} snapshot={guided.screen} kind={guided.kind} step={guided.step} setStep={guided.setStep} run={guided.run} onBack={guided.back} picker={guided.picker}/>
        ) : state.page === "NOTIFICATIONS" ? (
          <NotificationCenter key={snapshot.accountId} snapshot={snapshot}/>
        ) : state.page === "PROFILE" && snapshot.authenticated && snapshot.profile ? (
          <ProfileScreen key={snapshot.accountId} snapshot={snapshot}/>
        ) : state.page === "LIBRARY" ? (
          <LibraryScreen key={snapshot.accountId} snapshot={snapshot}/>
        ) : (["ITEM", "ACCOUNT", "SELECTION_OVERRIDE", "PAYMENT_SHARE"].includes(state.page) || state.page === "MENU_ENTITY") ? (
          <SelectionScreen snapshot={snapshot} roomTitle={roomContext.current?.accountId === snapshot.accountId ? roomContext.current.state.title : undefined}/>
        ) : (
          <>
            {(state.page !== "HOME" || snapshot.authenticated) && <View style={[ui.heading, rtl && ui.reverse]}>
              {state.canGoBack && (
                <Pressable
                  accessibilityRole="button"
                  accessibilityLabel={tx(rtl, "Back", "رجوع")}
                  style={ui.icon}
                  onPress={() => dispatch("BACK")}
                >
                  <View style={rtl && { transform: [{ scaleX: -1 }] }}>
                    <Icon name="back" />
                  </View>
                </Pressable>
              )}
              <View style={{ flex: 1 }}>
                <Text style={[textStyle(rtl, "bold"), ui.title]}>
                  {wallet ? tx(rtl, "Wallet", "المحفظة") : state.page === "HOME" && snapshot.profile ? tx(rtl, `Good food, ${snapshot.profile.name.split(" ")[0]}.`, `أهلاً، ${snapshot.profile.name.split(" ")[0]}.`) : state.title}
                </Text>
                {!!state.subtitle && (
                  <Text style={[textStyle(rtl), ui.subtitle]}>
                    {state.subtitle}
                  </Text>
                )}
              </View>
              {busy && <ActivityIndicator color={colors.coral} />}
            </View>}
            {(sections.length > 1 ||
              (profileDetails && snapshot.extraFields.length > 0)) && (
              <View style={room && ui.roomTabs}>
              {room ? <View style={[ui.roomTabRow, rtl && ui.reverse]}>
                {sections.map((item, index) => (
                  <TabButton selected={selected === index} key={index} testID={`room-tab:${index}`}
                    accessibilityRole="tab" accessibilityLabel={item.title}
                    accessibilityState={{ selected: selected === index }}
                    onPress={() => changeTab(index)}
                    style={[ui.roomTab, selected === index && ui.selectedRoomTab]}>
                    <Icon name={["room", "food", "wallet", "friends"][index]}
                      size={20} color={selected === index ? colors.forest : colors.muted} />
                    <Text numberOfLines={1} adjustsFontSizeToFit minimumFontScale={0.8}
                      style={[textStyle(rtl, selected === index ? "semibold" : "medium"), ui.roomTabLabel,
                        { color: selected === index ? colors.forest : colors.muted }]}>{item.title}</Text>
                  </TabButton>
                ))}
              </View> : <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                style={ui.tabs}
                contentContainerStyle={[ui.tabRow, rtl && ui.reverse]}
              >
                {sections.map((item, index) => (
                  <Pressable
                    key={item.title + index}
                    accessibilityRole="tab"
                    accessibilityState={{ selected: selected === index }}
                    onPress={() => changeTab(index)}
                    style={[ui.tab, selected === index && ui.activeTab]}
                  >
                    <Text
                      style={[
                        textStyle(rtl, "medium"),
                        {
                          fontSize: 13,
                          color: selected === index ? colors.white : colors.ink,
                        },
                      ]}
                    >
                      {item.title || tx(rtl, "Details", "التفاصيل")}
                    </Text>
                  </Pressable>
                ))}
                {profileDetails && snapshot.extraFields.length > 0 && (
                  <Pressable
                    accessibilityRole="tab"
                    accessibilityState={{ selected: options }}
                    onPress={() => setOptions(!options)}
                    style={[ui.tab, options && ui.activeTab]}
                  >
                    <Text
                      style={[
                        textStyle(rtl),
                        { color: options ? colors.white : colors.ink },
                      ]}
                    >
                      {tx(rtl, "Your details", "بياناتك")}
                    </Text>
                  </Pressable>
                )}
              </ScrollView>}
              </View>
            )}
            {adminTabs && (
              <ScrollView
                horizontal
                style={ui.tabs}
                contentContainerStyle={ui.tabRow}
                showsHorizontalScrollIndicator={false}
              >
                {adminTabs.buttons.map((button) => (
                  <Button
                    key={button.value}
                    action={button}
                    busy={busy}
                    rtl={rtl}
                  />
                ))}
              </ScrollView>
            )}
            <View style={{flex:1}}>
            <ScrollView
              ref={content}
              key={`${state.page}:${state.roomCode}:${snapshot.accountId}`}
              keyboardShouldPersistTaps="handled"
              contentContainerStyle={[ui.content, room && {paddingBottom:180}]}
              refreshControl={
                <RefreshControl
                  refreshing={busy}
                  onRefresh={() => dispatch("REFRESH")}
                  tintColor={colors.coral}
                />
              }
            >
              <PageMotion motionKey={`${state.page}:${state.roomCode}:${selected}:${rtl}`}>
              {state.page === "HOME" && <HomeBanner rtl={rtl} busy={busy} authenticated={snapshot.authenticated} />}
              {!!state.roomCode && (!room || selected === 0) && (
                <View style={[ui.roomCode, rtl && ui.reverse]}>
                  <View>
                    <Text style={[textStyle(rtl, "medium"), { fontSize: 12 }]}>
                      {tx(rtl, "ROOM CODE", "رمز الغرفة")}
                    </Text>
                    <Text selectable style={ui.code}>
                      {state.roomCode}
                    </Text>
                  </View>
                  <Button
                    rtl={rtl}
                    busy={busy}
                    action={action(
                      tx(rtl, "Invite", "دعوة"),
                      "SHARE_ROOM",
                    )}
                  />
                </View>
              )}
              {!!state.wheel && (!room || selected === 0) && <LiveWheel wheel={state.wheel} rtl={rtl} />}
              {state.progressStep >= 0 && (!room || selected === 0) && (
                <ScrollView
                  horizontal
                  showsHorizontalScrollIndicator={false}
                  contentContainerStyle={{ gap: 8 }}
                >
                  {(rtl
                    ? [
                        "انضمام",
                        "المطعم",
                        "الطعام",
                        "مسؤول الطلب",
                        "إرسال الطلب",
                        "الدفع",
                      ]
                    : [
                        "Join",
                        "Restaurant",
                        "Food",
                        "Payer",
                        "Send order",
                        "Pay",
                      ]
                  ).map((label, index) => (
                    <Text
                      key={label}
                      style={[
                        ui.progress,
                        textStyle(rtl, "medium"),
                        index === state.progressStep && {
                          backgroundColor: colors.mint,
                        },
                      ]}
                    >
                      {index < state.progressStep ? "✓ " : ""}
                      {label}
                    </Text>
                  ))}
                </ScrollView>
              )}
              {(!room ? snapshot.topCards : selected === 0 ? snapshot.topCards.filter(card => card.id.startsWith("half-item:")) : []).map((card) => (
                <CardView key={card.id} card={card} busy={busy} rtl={rtl} />
              ))}
              {fields.map((field) => (
                <FieldInput
                  key={`${snapshot.accountId}:${state.page}:${field.key}`}
                  field={field}
                  busy={busy}
                  rtl={rtl}
                />
              ))}
              {inline.map((button, index) => (
                <Button
                  key={button.action + button.value + index}
                  action={button}
                  busy={busy}
                  rtl={rtl}
                />
              ))}
              {section && (
                <PagedCards
                  key={state.page + section.title}
                  cards={section.cards.filter(
                    (card) => card.id !== "admin-tabs",
                  )}
                  busy={busy}
                  rtl={rtl}
                />
              )}
              {room && selected !== 0 && section?.cards.length === 0 && fields.length === 0 && inline.length === 0 && (
                <View style={ui.empty}>
                  <Icon name={["room", "food", "wallet", "friends"][selected]} size={32} color={colors.forest} />
                  <Text style={[textStyle(rtl), ui.emptyText]}>{[
                    "",
                    tx(rtl, "Your menu and food choices will appear here when ordering opens.", "ستظهر القائمة واختياراتك هنا عند فتح الطلبات."),
                    tx(rtl, "Payments will appear here when the bill is ready.", "ستظهر المدفوعات هنا عندما تكون الفاتورة جاهزة."),
                    tx(rtl, "Room members will appear here.", "سيظهر أعضاء الغرفة هنا."),
                  ][selected]}</Text>
                </View>
              )}
              {(snapshot.extraFields.length > 0 ||
                snapshot.utilityButtons.length > 0) && (!room || selected === 0) && (
                <>
                  {!profileDetails && (
                    <Button
                      action={action(
                        tx(rtl, "More options", "خيارات إضافية"),
                        "OPTIONS",
                      )}
                      rtl={rtl}
                      onPress={() => setOptions(!options)}
                    />
                  )}
                  {options && (
                    <View style={{ gap: 12 }}>
                      {snapshot.extraFields.map((field) => (
                        <FieldInput
                          key={field.key}
                          field={field}
                          busy={busy}
                          rtl={rtl}
                        />
                      ))}
                      {snapshot.utilityButtons.map((button, index) => (
                        <Button
                          key={button.action + index}
                          action={button}
                          busy={busy}
                          rtl={rtl}
                        />
                      ))}
                    </View>
                  )}
                </>
              )}
              </PageMotion>
            </ScrollView>
            {room && <RoomAttention snapshot={snapshot} selected={selected} onOpenTab={changeTab}/>}
            </View>
            {!!snapshot.primaryAction && !(state.page === "HOME" && snapshot.authenticated) && (!room || roomActionTab(snapshot.primaryAction) === selected) && (
              <View style={ui.primary}>
                <Button action={snapshot.primaryAction} busy={busy} rtl={rtl} />
              </View>
            )}
          </>
        )}
      </KeyboardAvoidingView>
      <View style={[ui.bottom, rtl && ui.reverse]}>
        {[
          [
            "home",
            tx(rtl, "Home", "الرئيسية"),
            "OPEN_HOME",
            "",
            state.page === "HOME",
          ],
          [
            "room",
            tx(rtl, "Rooms", "الغرف"),
            "OPEN_ROOMS",
            "",
            roomRoute,
          ],
          [
            "wallet",
            tx(rtl, "Wallet", "المحفظة"),
            "OPEN_WALLET",
            "",
            walletRoute,
          ],
          ["profile", tx(rtl, "Profile", "حسابي"), "OPEN_PROFILE", "", profileDetails],
          ["menu", tx(rtl, "More", "المزيد"), "MORE", "", menu || (state.page !== "HOME" && !roomRoute && !walletRoute && !profileDetails)],
        ].map(([icon, label, kind, value, active]) => (
          <TabButton selected={!!active}
            key={String(kind)}
            testID={`nav:${kind}`}
            accessibilityRole="tab"
            accessibilityState={{ selected: !!active, disabled: busy }}
            disabled={busy}
            onPress={() =>
              kind === "MORE"
                ? setMenu(true)
                : dispatch(String(kind), String(value))
            }
            style={ui.navItem}
          >
            <View style={[ui.navIcon, !!active && ui.activeNavIcon]}>
            <Icon
              name={String(icon)}
              color={active ? colors.forest : colors.muted}
            />
            </View>
            <Text
              numberOfLines={1}
              adjustsFontSizeToFit
              minimumFontScale={0.8}
              style={[
                textStyle(rtl, "medium"),
                ui.navLabel,
                { color: active ? colors.ink : colors.muted },
              ]}
            >
              {String(label)}
            </Text>
          </TabButton>
        ))}
      </View>
      <MoreSheet snapshot={snapshot} visible={menu} actions={menuActions} onClose={() => setMenu(false)}/>
      <BottomSheet visible={!!state.walletHistoryPrompt} title={state.walletHistoryPrompt?.title || tx(rtl,"Transactions","المعاملات")} rtl={rtl} onClose={() => dispatch("DISMISS_WALLET_HISTORY")}>
        {state.walletHistoryPrompt && <>
                <Text style={textStyle(rtl)}>
                  {state.walletHistoryPrompt.subtitle}
                </Text>
                <Text style={[textStyle(rtl, "bold"), { fontSize: 20 }]}>
                  {state.walletHistoryPrompt.balance}
                </Text>
                <ScrollView contentContainerStyle={{ gap: 12 }}>
                  <PagedCards
                    cards={state.walletHistoryPrompt.cards}
                    busy={false}
                    rtl={rtl}
                  />
                  {state.walletHistoryPrompt.loading && (
                    <ActivityIndicator color={colors.coral} />
                  )}
                  {!!state.walletHistoryPrompt.error && (
                    <Text style={{ color: colors.danger }}>
                      {state.walletHistoryPrompt.error}
                    </Text>
                  )}
                  <Button
                    rtl={rtl}
                    busy={state.walletHistoryPrompt.loading}
                    action={action(
                      tx(rtl, "Refresh", "تحديث"),
                      "REFRESH_WALLET_HISTORY",
                    )}
                  />
                  {state.walletHistoryPrompt.hasMore && (
                    <Button
                      rtl={rtl}
                      busy={state.walletHistoryPrompt.loading}
                      action={action(
                        tx(
                          rtl,
                          "Load older transactions",
                          "تحميل معاملات أقدم",
                        ),
                        "LOAD_WALLET_HISTORY",
                      )}
                    />
                  )}
                </ScrollView>

        </>}
      </BottomSheet>
      <BottomSheet visible={!!state.reminderEmailPrompt} title={`${tx(rtl,"Recipient email","بريد المستلم")} · ${state.reminderEmailPrompt?.name || ""}`} rtl={rtl} onClose={() => dispatch("DISMISS_REMINDER_EMAIL")}>
        {state.reminderEmailPrompt && <>
                <FieldInput
                  busy={busy}
                  rtl={rtl}
                  field={{
                    key: "REMINDER_EMAIL",
                    label: tx(rtl, "Email", "البريد الإلكتروني"),
                    value: state.reminderEmailPrompt.address,
                    choices: [],
                    toggle: false,
                    secret: false,
                    multiline: false,
                  }}
                />
                {!!state.reminderEmailPrompt.error && (
                  <Text style={{ color: colors.danger }}>
                    {state.reminderEmailPrompt.error}
                  </Text>
                )}
                <Button
                  busy={busy}
                  rtl={rtl}
                  action={action(
                    tx(rtl, "Send reminder", "إرسال التذكير"),
                    "SAVE_REMINDER_EMAIL",
                    "",
                    true,
                  )}
                />
                <Button
                  busy={busy}
                  rtl={rtl}
                  action={action(
                    tx(rtl, "Cancel", "إلغاء"),
                    "DISMISS_REMINDER_EMAIL",
                  )}
                />

        </>}
      </BottomSheet>
    </SafeAreaView>
  );
}
export default function App() {
  return (
    <SafeAreaProvider>
      <MotionProvider><Application /></MotionProvider>
    </SafeAreaProvider>
  );
}
const ui = StyleSheet.create({
  root: { flex: 1, backgroundColor: colors.cream },
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    gap: 24,
    backgroundColor: colors.cream,
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
  },
  reverse: { flexDirection: "row-reverse" },
  headerTools: { flexDirection: "row", alignItems: "center", gap: 8 },
  profilePhoto: { width: 36, height: 36, borderRadius: 18, backgroundColor: colors.mint, alignItems: "center", justifyContent: "center" },
  language: {
    height: 44,
    minWidth: 44,
    alignItems: "center",
    justifyContent: "center",
    borderRadius: 14,
    borderWidth: 1,
    borderColor: colors.line,
  },
  icon: {
    height: 44,
    width: 44,
    justifyContent: "center",
    alignItems: "center",
  },
  heading: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: "row",
    alignItems: "center",
    gap: 8,
  },
  title: { fontSize: 24, lineHeight: 36 },
  subtitle: { fontSize: 14, lineHeight: 24, color: colors.muted },
  content: { padding: 18, gap: 16, paddingBottom: 28 },
  tabs: { maxHeight: 58, flexGrow: 0 },
  tabRow: {
    paddingHorizontal: 16,
    paddingBottom: 10,
    gap: 8,
    alignItems: "center",
  },
  tab: {
    paddingVertical: 12,
    paddingHorizontal: 14,
    minHeight: 44,
    borderRadius: 999,
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
  },
  activeTab: { backgroundColor: colors.forest, borderColor: colors.forest },
  roomTabs: { marginHorizontal: 16, marginBottom: 4, backgroundColor: colors.white, borderRadius: 20, borderWidth: 1, borderColor: colors.line },
  roomTabRow: { flexDirection: "row", padding: 5 },
  roomTab: { flex: 1, minHeight: 62, borderRadius: 15, alignItems: "center", justifyContent: "center", gap: 3, paddingHorizontal: 2 },
  selectedRoomTab: { backgroundColor: colors.mint },
  roomTabLabel: { fontSize: 12, lineHeight: 22 },
  empty: { padding: 28, alignItems: "center", gap: 14, backgroundColor: colors.white, borderRadius: 20, borderWidth: 1, borderColor: colors.line },
  emptyText: { fontSize: 15, lineHeight: 26, textAlign: "center" },
  bottom: {
    flexDirection: "row",
    borderTopWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    paddingTop: 6,
    paddingBottom: 4,
  },
  navItem: {
    flex: 1,
    minHeight: 64,
    justifyContent: "center",
    alignItems: "center",
    gap: 1,
  },
  navIcon: { minWidth: 48, height: 32, alignItems: "center", justifyContent: "center", borderRadius: 16 },
  activeNavIcon: { backgroundColor: colors.mint },
  navLabel: { fontSize: 12, lineHeight: 22, textAlign: "center" },
  primary: {
    paddingHorizontal: 16,
    paddingVertical: 10,
    borderTopWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.cream,
  },
  feedback: {
    padding: 12,
    backgroundColor: colors.mint,
    flexDirection: "row",
    gap: 10,
    alignItems: "center",
  },
  error: { padding: 14, backgroundColor: "#FFF0EB" },
  hero: { padding: 22, borderRadius: 26, backgroundColor: colors.forest, gap: 12 },
  heroTitle: { fontSize: 30, lineHeight: 45, color: colors.white },
  heroText: { fontSize: 15, lineHeight: 26, color: "#E3F1E9" },
  roomCode: {
    flexDirection: "row",
    alignItems: "center",
    justifyContent: "space-between",
    padding: 14,
    backgroundColor: colors.coralWash,
    borderRadius: 18,
  },
  code: {
    fontFamily: font("bold"),
    fontSize: 29,
    color: colors.ink,
    letterSpacing: 3,
  },
  progress: {
    borderWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.white,
    paddingHorizontal: 12,
    paddingVertical: 10,
    borderRadius: 12,
    fontSize: 12,
  },
});
