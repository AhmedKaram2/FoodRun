import ScrollArea, { ScrollLoad } from "../components/ScrollArea";
import React, { useEffect, useRef, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import type { Action, Snapshot } from "../types";
import { colors, tx } from "../theme";
import { dispatch, update } from "../native";
import { allActions, allCards, allFields } from "../guidedFlows";
import {
  Button,
  CardView,
  FieldInput,
  PagedCards,
  action,
  textStyle,
} from "../components/Controls";
import BottomSheet from "../components/BottomSheet";
import Icon from "../components/Icon";
import Illustration, { screenIllustration } from "../components/Illustration";
import { PageMotion, TabButton } from "../components/Motion";

function Intro({
  snapshot,
  icon,
  title,
  subtitle,
}: {
  snapshot: Snapshot;
  icon: string;
  title: string;
  subtitle: string;
}) {
  const { rtl, busy, canGoBack } = snapshot.state;
  return (
    <View style={[styles.intro, rtl && styles.reverse]}>
      {canGoBack && (
        <Pressable
          accessibilityRole="button"
          accessibilityLabel={tx(rtl, "Back", "رجوع")}
          onPress={() => dispatch("BACK")}
          style={styles.back}
        >
          <View style={rtl && { transform: [{ scaleX: -1 }] }}>
            <Icon name="back" size={20} />
          </View>
        </Pressable>
      )}
      <View style={{ flex: 1, gap: 3 }}>
        <Text
          accessibilityRole="header"
          style={[textStyle(rtl, "bold"), styles.title]}
        >
          {title}
        </Text>
        <Text style={[textStyle(rtl), styles.detail]}>{subtitle}</Text>
      </View>
      {busy ? (
        <ActivityIndicator color={colors.forest} />
      ) : (
        <Illustration kind={screenIllustration(icon.toUpperCase())} size={54}/>
      )}
    </View>
  );
}
function Tabs({
  rtl,
  items,
  selected,
  onChange,
  icons,
}: {
  rtl: boolean;
  items: string[];
  selected: number;
  onChange: (value: number) => void;
  icons?: string[];
}) {
  return (
    <ScrollArea
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.tabScroll}
      contentContainerStyle={[styles.tabs, icons && {width:"100%",gap:6}, rtl && styles.reverse]}
    >
      {items.map((title, i) => (
        <TabButton
          key={title}
          accessibilityRole="tab"
          accessibilityLabel={title}
          accessibilityState={{ selected: i === selected }}
          selected={i === selected}
          onPress={() => onChange(i)}
          style={[styles.tab, icons && {flex:1,paddingHorizontal:4,alignItems:"center",gap:2}, i === selected && styles.activeTab]}
        >
          {icons && <Icon name={icons[i]} size={16} color={i === selected ? colors.forest : colors.muted}/>}
          <Text
            style={[
              textStyle(rtl, "semibold"),
              styles.tabLabel,
              icons && {fontSize:10,lineHeight:18},
              { color: i === selected ? colors.forest : colors.muted },
            ]}
          >
            {title}
          </Text>
        </TabButton>
      ))}
    </ScrollArea>
  );
}
const refresh = (snapshot: Snapshot, kind = "REFRESH") => (
  <RefreshControl
    refreshing={snapshot.state.busy}
    onRefresh={() => dispatch(kind)}
    tintColor={colors.forest}
  />
);

export function MoreSheet({
  snapshot,
  visible,
  actions,
  onClose,
}: {
  snapshot: Snapshot;
  visible: boolean;
  actions: Action[];
  onClose: () => void;
}) {
  const { rtl, busy } = snapshot.state;
  const descriptions: Record<string, [string, string, string, string]> = {
    OPEN_PROFILE: [
      "profile",
      "Your details & favourites",
      "بياناتك وطلباتك المفضلة",
      colors.mint,
    ],
    FRIENDS_ACTION: [
      "friends",
      "Bring your people together",
      "اجمع أصحابك في مكان واحد",
      colors.lavender,
    ],
    OPEN_NOTIFICATIONS: [
      "bell",
      "Invitations & payment updates",
      "الدعوات وتحديثات المدفوعات",
      colors.sky,
    ],
    OPEN_LIBRARY: [
      "food",
      "Find your next favourite meal",
      "اكتشف وجبتك المفضلة",
      colors.coralWash,
    ],
    QUICK_SPIN: [
      "wheel",
      "Let luck choose today",
      "خلّي الحظ يختار اليوم",
      colors.honey,
    ],
    CREATE_PAYMENT_ROOM: [
      "split",
      "Share a payment easily",
      "قسّم الدفعة بسهولة",
      colors.mint,
    ],
  };
  const available = actions.filter(
    (a) =>
      snapshot.authenticated ||
      ["OPEN_PROFILE", "OPEN_LIBRARY"].includes(a.action),
  );
  const tiles = available.filter((a) => descriptions[a.action]);
  const settings = available.filter((a) => !descriptions[a.action]);
  return (
    <BottomSheet
      visible={visible}
      rtl={rtl}
      title={tx(rtl, "Explore FoodRun", "اكتشف FoodRun")}
      onClose={onClose}
    >
      <ScrollArea contentContainerStyle={styles.content}>
        <Text style={[textStyle(rtl), styles.detail]}>
          {tx(
            rtl,
            "Good food. Good company. Everything in one place.",
            "أكل حلو وصحبة أحلى. كل ما تحتاجه هنا.",
          )}
        </Text>
        <View style={[styles.grid, rtl && styles.reverse]}>
          {tiles.map((a) => {
            const [icon, en, ar, surface] = descriptions[a.action];
            return (
              <View key={a.action} style={styles.tile}>
                <Button
                  action={a}
                  busy={busy}
                  rtl={rtl}
                  tile
                  icon={icon}
                  caption={tx(rtl, en, ar)}
                  surface={surface}
                  onPress={() => {
                    onClose();
                    dispatch(a.action, a.value);
                  }}
                />
              </View>
            );
          })}
        </View>
        {!!settings.length && (
          <View style={styles.panel}>
            <Text style={[textStyle(rtl, "semibold"), styles.sectionTitle]}>
              {tx(rtl, "Account & settings", "الحساب والإعدادات")}
            </Text>
            {settings.map((a) => (
              <Button
                key={a.action}
                action={a}
                rtl={rtl}
                busy={busy}
                onPress={() => {
                  onClose();
                  dispatch(a.action, a.value);
                }}
              />
            ))}
          </View>
        )}
      </ScrollArea>
    </BottomSheet>
  );
}

export function NotificationCenter({ snapshot }: { snapshot: Snapshot }) {
  const { rtl, busy } = snapshot.state,
    [tab, setTab] = useState(0);
  const cards = allCards(snapshot),
    items = cards.filter((c) => c.id.startsWith("notification:"));
  const unread = items.filter((c) => !!c.badge),
    shown = tab === 1 ? unread : items;
  return (
    <View style={styles.screen}>
      <Intro
        snapshot={snapshot}
        icon="bell"
        title={tx(rtl, "Notification center", "مركز الإشعارات")}
        subtitle={tx(
          rtl,
          "Your invitations and payment updates",
          "دعواتك وتحديثات المدفوعات",
        )}
      />
      <Tabs
        rtl={rtl}
        items={[
          tx(rtl, "All", "الكل"),
          tx(
            rtl,
            `Unread (${unread.length})`,
            `غير المقروءة (${unread.length})`,
          ),
        ]}
        selected={tab}
        onChange={setTab}
      />
      <View style={[styles.noticeActions,{paddingHorizontal:16,paddingVertical:8}]}>
        {allActions(snapshot).filter(a=>['NOTIFICATIONS_READ_ALL','NOTIFICATIONS_CLEAR'].includes(a.action)).map(a=><Button key={a.action} action={a} busy={busy} rtl={rtl}/>)}
      </View>
      <ScrollArea
        refreshControl={refresh(snapshot, "OPEN_NOTIFICATIONS")}
        contentContainerStyle={styles.content}
      >
        <PageMotion motionKey={`notifications:${tab}`} rtl={rtl}>
          {shown.map((c) => (
            <View
              key={c.id}
              style={[styles.notice, !!c.badge && styles.newNotice]}
            >
              <Pressable
                accessibilityRole="button"
                accessibilityLabel={`${c.title}. ${c.detail}`}
                disabled={busy}
                onPress={() =>
                  dispatch(
                    "OPEN_NOTIFICATION",
                    `${c.id.slice("notification:".length)}:open`,
                  )
                }
              >
                <View style={[styles.noticeHeading, rtl && styles.reverse]}>
                  <View style={styles.noticeIcon}>
                    <Icon name="bell" size={19} color={colors.forest} />
                  </View>
                  <Text
                    style={[
                      textStyle(rtl, "semibold"),
                      { flex: 1, fontSize: 15, lineHeight: 25 },
                    ]}
                  >
                    {c.title}
                  </Text>
                  {!!c.badge && (
                    <View accessibilityLabel={c.badge} style={styles.dot} />
                  )}
                </View>
                <Text style={[textStyle(rtl), styles.noticeBody]}>
                  {c.detail}
                </Text>
              </Pressable>
              {!!c.buttons.length && (
                <View style={styles.noticeActions}>
                  {c.buttons.map((a, i) => (
                    <Button
                      key={a.value + i}
                      action={a}
                      busy={busy}
                      rtl={rtl}
                    />
                  ))}
                </View>
              )}
            </View>
          ))}
          {!shown.length && (
            <View style={styles.empty}>
              <View style={styles.emptyIcon}>
                <Icon name="check" size={32} color={colors.forest} />
              </View>
              <Text style={[textStyle(rtl, "semibold"), styles.sectionTitle]}>
                {tx(
                  rtl,
                  tab === 1 ? "You're all caught up" : "No notifications yet",
                  tab === 1 ? "كل الإشعارات مقروءة" : "لا توجد إشعارات بعد",
                )}
              </Text>
              <Text
                style={[textStyle(rtl), styles.detail, { textAlign: "center" }]}
              >
                {tx(
                  rtl,
                  "Invitations and payment updates will appear here.",
                  "ستظهر الدعوات وتحديثات المدفوعات هنا.",
                )}
              </Text>
            </View>
          )}
          {cards
            .filter(
              (c) =>
                !c.id.startsWith("notification:") &&
                c.id !== "notifications-empty",
            )
            .map((c) => (
              <CardView key={c.id} card={c} busy={busy} rtl={rtl} />
            ))}
          <View style={styles.panel}>
            <Text style={[textStyle(rtl, "semibold"), styles.sectionTitle]}>
              {tx(rtl, "Stay in the loop", "خليك على اطلاع")}
            </Text>
            {allActions(snapshot).filter(a=>!['NOTIFICATIONS_READ_ALL','NOTIFICATIONS_CLEAR'].includes(a.action)).map((a) => (
              <Button
                key={a.action + a.value}
                action={a}
                busy={busy}
                rtl={rtl}
              />
            ))}
            <Button
              action={action(
                tx(rtl, "Notification preferences", "تفضيلات الإشعارات"),
                "OPEN_NOTIFICATION_PREFERENCES",
              )}
              busy={busy}
              rtl={rtl}
            />
          </View>
        </PageMotion>
      </ScrollArea>
    </View>
  );
}

export function ProfileScreen({ snapshot }: { snapshot: Snapshot }) {
  const { rtl, busy } = snapshot.state,
    [tab, setTab] = useState(0),
    [details, setDetails] = useState(0),
    [orders, setOrders] = useState(0);
  const scroll = useRef<ScrollView>(null),
    cards = allCards(snapshot),
    fields = allFields(snapshot),
    actions = allActions(snapshot);
  const identity = new Set([
    "NAME",
    "PHOTO",
    "PROFILE_COUNTRY",
    "PROFILE_PHONE",
    "DISCOVERABLE",
  ]);
  const summary = cards.filter((c) => c.id.startsWith("profile-dashboard:"));
  const orderCards = cards.filter((c) =>
    orders === 0 ? c.id.startsWith("favorite:") : c.id.startsWith("previous:"),
  );
  const save = actions.find((a) => a.action === "SAVE_PROFILE");
  const switchTab = (value: number) => {
    setTab(value);
    scroll.current?.scrollTo({ y: 0, animated: false });
  };
  return (
    <View style={styles.screen}>
      <Intro
        snapshot={snapshot}
        icon="profile"
        title={tx(rtl, "My account", "حسابي")}
        subtitle={tx(
          rtl,
          "Details, meals and payments",
          "بياناتك ووجباتك ومدفوعاتك",
        )}
      />
      <Tabs
        rtl={rtl}
        items={[
          tx(rtl, "Overview", "نظرة عامة"),
          tx(rtl, "Details", "البيانات"),
          tx(rtl, "Orders", "الطلبات"),
          tx(rtl, "Settings", "الإعدادات"),
        ]}
        icons={["profile","edit","heart","settings"]}
        selected={tab}
        onChange={switchTab}
      />
      <ScrollArea
        ref={scroll}
        keyboardShouldPersistTaps="handled"
        refreshControl={refresh(snapshot)}
        contentContainerStyle={styles.content}
      >
        <PageMotion motionKey={`profile:${tab}`} rtl={rtl}>
          {tab === 0 && (
            <>
              <View style={[styles.profileHero, rtl && styles.reverse]}>
                <View style={styles.avatar}>
                  {snapshot.profile?.photo ? (
                    <Image
                      source={{ uri: snapshot.profile.photo }}
                      style={styles.avatar}
                    />
                  ) : (
                    <Text
                      style={[
                        textStyle(rtl, "bold"),
                        { fontSize: 26, color: colors.forest },
                      ]}
                    >
                      {Array.from(snapshot.profile?.name || "F")[0]}
                    </Text>
                  )}
                </View>
                <View style={{ flex: 1, gap: 5 }}>
                  <Text
                    style={[
                      textStyle(rtl, "bold"),
                      { fontSize: 20, lineHeight: 32 },
                    ]}
                  >
                    {snapshot.profile?.name}
                  </Text>
                  <Text style={[textStyle(rtl), styles.detail]}>
                    {tx(
                      rtl,
                      "Your meals and payments, together.",
                      "وجباتك ومدفوعاتك في مكان واحد.",
                    )}
                  </Text>
                </View>
              </View>
              <View style={[styles.grid, rtl && styles.reverse]}>
                {[
                  {title:tx(rtl,"Edit details","تعديل البيانات"),icon:"edit",tone:colors.coralWash,run:()=>{setDetails(0);switchTab(1);}},
                  {title:tx(rtl,"Receiving payments","استلام الأموال"),icon:"wallet",tone:colors.mint,run:()=>{setDetails(1);switchTab(1);}},
                  {title:tx(rtl,"Favourites","المفضلة"),icon:"heart",tone:colors.coralWash,run:()=>{setOrders(0);switchTab(2);}},
                  {title:tx(rtl,"Previous orders","الطلبات السابقة"),icon:"history",tone:colors.honey,run:()=>{setOrders(1);switchTab(2);}},
                ].map(item=><Pressable key={item.icon} accessibilityRole="button" accessibilityLabel={item.title} onPress={item.run} style={[styles.accountShortcut,rtl && styles.reverse]}>
                  <View style={[styles.shortcutIcon,{backgroundColor:item.tone}]}><Icon name={item.icon} size={19} color={colors.forest}/></View>
                  <Text style={[textStyle(rtl,"medium"),styles.shortcutLabel]}>{item.title}</Text>
                </Pressable>)}
              </View>
              <Pressable accessibilityRole="button" accessibilityLabel={tx(rtl,"Open wallet & payments","فتح المحفظة والمدفوعات")} disabled={busy} accessibilityState={{disabled:busy}} onPress={()=>dispatch("OPEN_WALLET")} style={[styles.walletShortcut,rtl && styles.reverse]}>
                <Icon name="wallet" size={21} color={colors.forest}/><Text style={[textStyle(rtl,"semibold"),{flex:1,fontSize:14}]}>{tx(rtl,"Wallet & payments","المحفظة والمدفوعات")}</Text><Text accessibilityElementsHidden style={{fontSize:23,color:colors.forest}}>{rtl ? "‹":"›"}</Text>
              </Pressable>
              <View style={[styles.profileTotals,rtl && styles.reverse]}>{[false,true].map(receive=>{const total=summary.find(card=>card.id.endsWith(`wallet-direction:${receive}`));return total && <Pressable key={String(receive)} accessibilityRole="button" accessibilityLabel={`${total.title} · ${total.badge}`} disabled={busy} onPress={()=>dispatch("OPEN_WALLET")} style={styles.profileTotal}><Text style={[textStyle(rtl),styles.detail]}>{tx(rtl,receive ? "To receive":"To pay",receive ? "للاستلام":"للدفع")}</Text><Text style={[textStyle(rtl,"semibold"),{fontSize:16,color:colors.forest}]}>{total.badge}</Text></Pressable>;})}</View>
              <PagedCards cards={summary.filter(card=>card.id.endsWith("wallet-funds"))} rtl={rtl} busy={busy} />

            </>
          )}
          {tab === 1 && (
            <>
              <Tabs
                rtl={rtl}
                items={[
                  tx(rtl, "Personal", "شخصية"),
                  tx(rtl, "Receiving payments", "استلام الأموال"),
                ]}
                selected={details}
                onChange={setDetails}
              />
              <View style={styles.panel}>
                <View style={[styles.noticeHeading,rtl && styles.reverse]}><Icon name={details ? "wallet":"profile"} color={colors.forest}/><Text style={[textStyle(rtl,"semibold"),styles.sectionTitle]}>{details ? tx(rtl,"How people pay you","كيف تستلم الأموال") : tx(rtl,"About you","بياناتك")}</Text></View>
                {fields
                  .filter((f) =>
                    details === 0 ? identity.has(f.key) : !identity.has(f.key),
                  )
                  .map((f) => (
                    <FieldInput key={f.key} field={f} rtl={rtl} busy={busy} />
                  ))}
              </View>
              {cards
                .filter((c) => details === 1 && c.id === "profile-privacy")
                .map((c) => (
                  <CardView key={c.id} card={c} rtl={rtl} busy={busy} />
                ))}
            </>
          )}
          {tab === 2 && (
            <>
              <Tabs
                rtl={rtl}
                items={[
                  tx(rtl, "Favourites", "المفضلة"),
                  tx(rtl, "Previous orders", "الطلبات السابقة"),
                ]}
                selected={orders}
                onChange={setOrders}
              />
              <PagedCards key={orders} cards={orderCards} busy={busy} rtl={rtl} pageSize={orders === 1 ? 100000 : 8} />
              {!orderCards.length && (
                <View style={styles.empty}>
                  <Illustration kind="food" size={76}/>
                  <Text style={[textStyle(rtl), styles.detail]}>
                    {tx(
                      rtl,
                      "Your meals will appear here.",
                      "ستظهر وجباتك هنا.",
                    )}
                  </Text>
                </View>
              )}
              {orders === 1 &&
                actions
                  .filter((a) => a.action === "MORE_PREVIOUS_ORDERS")
                  .map((a) => (
                    <ScrollLoad key={a.action} loadKey={orderCards.map(card => card.id).join(":")} enabled={!busy && !snapshot.state.error} onLoad={() => dispatch(a.action,a.value)}><Text style={textStyle(rtl)}>{tx(rtl,"Scroll for older orders","مرّر لعرض طلبات أقدم")}</Text></ScrollLoad>
                  ))}
            </>
          )}
          {tab === 3 && (
            <>
              <View style={styles.panel}>
                {actions
                  .filter(
                    (a) =>
                      ![
                        "SAVE_PROFILE",
                        "MORE_PREVIOUS_ORDERS",
                        "SET_LANGUAGE",
                      ].includes(a.action),
                  )
                  .map((a) => (
                    <Button
                      key={a.action + a.value}
                      icon={a.action === "OPEN_NOTIFICATION_PREFERENCES" ? "bell" : a.action === "SIGN_OUT" ? "logout" : "settings"}
                      action={
                        a.action === "SIGN_OUT"
                          ? { ...a, destructive: true }
                          : a
                      }
                      busy={busy}
                      rtl={rtl}
                    />
                  ))}
              </View>
              <PagedCards
                cards={cards.filter(
                  (c) =>
                    !c.id.startsWith("profile-dashboard:") &&
                    !c.id.startsWith("favorite") &&
                    !c.id.startsWith("previous:") &&
                    c.id !== "profile-privacy",
                )}
                rtl={rtl}
                busy={busy}
              />
            </>
          )}
        </PageMotion>
      </ScrollArea>
      {tab === 1 && save && (
        <View style={styles.footer}>
          <Button action={save} rtl={rtl} busy={busy} />
        </View>
      )}
    </View>
  );
}

export function LibraryScreen({ snapshot }: { snapshot: Snapshot }) {
  const { rtl, busy } = snapshot.state,
    [tab, setTab] = useState(0),
    [filters, setFilters] = useState(false),
    [restaurantId, setRestaurantId] = useState(""),
    [limit, setLimit] = useState(12);
  const fields = allFields(snapshot),
    actions = allActions(snapshot),
    cards = allCards(snapshot),
    restaurants = cards.filter((c) => c.id.startsWith("restaurant:"));
  const current = restaurants.find((c) => c.id === restaurantId),
    search = fields.find((f) => f.key === "RESTAURANT_SEARCH");
  const filterFields = fields.filter((f) =>
    ["RESTAURANT_EMIRATE", "RESTAURANT_AREA", "RESTAURANT_MEAL"].includes(
      f.key,
    ),
  );
  const active = filterFields.filter((f) => !!f.value).length,
    add = actions.find((a) => a.action === "NEW_RESTAURANT");
  const queryKey = [search?.value, ...filterFields.map((field) => field.value)].join("\n");
  useEffect(() => setLimit(12), [queryKey]);
  return (
    <View style={styles.screen}>
      <Intro
        snapshot={snapshot}
        icon="food"
        title={tx(rtl, "Restaurants & menus", "المطاعم والقوائم")}
        subtitle={tx(
          rtl,
          "Find something delicious for your next room",
          "اختار أكلة حلوة لغرفتك القادمة",
        )}
      />
      <Tabs
        rtl={rtl}
        items={[
          tx(rtl, "Restaurants", "المطاعم"),
          tx(rtl, "Import menu", "استيراد قائمة"),
        ]}
        selected={tab}
        onChange={setTab}
      />
      {tab === 0 && (
        <View style={styles.search}>
          {search && <FieldInput field={search} busy={busy} rtl={rtl} />}
          <View style={[styles.tools, rtl && styles.reverse]}>
            <View style={{ flex: 1 }}>
              <Button
                action={action(
                  tx(
                    rtl,
                    `Filters${active ? ` (${active})` : ""}`,
                    `التصفية${active ? ` (${active})` : ""}`,
                  ),
                  "LIBRARY_FILTERS",
                )}
                rtl={rtl}
                busy={busy}
                onPress={() => setFilters(true)}
              />
            </View>
            {add && (
              <View style={{ flex: 1 }}>
                <Button action={add} busy={busy} rtl={rtl} />
              </View>
            )}
          </View>
        </View>
      )}
      <ScrollArea
        keyboardShouldPersistTaps="handled"
        refreshControl={refresh(snapshot)}
        contentContainerStyle={styles.content}
      >
        <PageMotion motionKey={`library:${tab}`} rtl={rtl}>
          {tab === 0 ? (
            <>
              {restaurants.slice(0, limit).map((c, i) => (
                <Pressable
                  key={c.id}
                  testID={`library:${c.id}`}
                  accessibilityRole="button"
                  accessibilityLabel={`${c.title}. ${c.detail}`}
                  disabled={busy}
                  onPress={() => setRestaurantId(c.id)}
                  style={[styles.restaurant, rtl && styles.reverse]}
                >
                  <View
                    style={[
                      styles.restaurantIcon,
                      {
                        backgroundColor: [
                          colors.mint,
                          colors.coralWash,
                          colors.honey,
                          colors.lavender,
                        ][i % 4],
                      },
                    ]}
                  >
                    {c.image ? (
                      <Image
                        source={{ uri: c.image }}
                        style={styles.restaurantIcon}
                      />
                    ) : (
                      <Icon name="food" size={26} color={colors.forest} />
                    )}
                  </View>
                  <View style={{ flex: 1, gap: 4 }}>
                    <Text
                      style={[textStyle(rtl, "semibold"), styles.sectionTitle]}
                    >
                      {c.title}
                    </Text>
                    <Text
                      numberOfLines={2}
                      style={[textStyle(rtl), styles.detail]}
                    >
                      {c.detail}
                    </Text>
                    {!!c.badge && (
                      <Text style={[textStyle(rtl, "medium"), styles.badge]}>
                        {c.badge}
                      </Text>
                    )}
                  </View>
                  <View style={!rtl && { transform: [{ scaleX: -1 }] }}>
                    <Icon name="back" size={18} color={colors.muted} />
                  </View>
                </Pressable>
              ))}
              {restaurants.length > limit && <ScrollLoad loadKey={`${queryKey}:${limit}`} onLoad={() => setLimit(value => value+12)}><Text style={textStyle(rtl)}>{tx(rtl,"Scroll for more restaurants","مرّر لعرض مطاعم أخرى")}</Text></ScrollLoad>}
              {!restaurants.length && (
                <View style={styles.empty}>
                  <Icon name="search" size={32} color={colors.forest} />
                  <Text style={[textStyle(rtl), styles.detail]}>
                    {tx(
                      rtl,
                      "Try another name or area, or add a restaurant.",
                      "جرّب اسماً أو منطقة أخرى، أو أضف مطعماً.",
                    )}
                  </Text>
                </View>
              )}
              {actions
                .filter(
                  (a) =>
                    ![
                      "NEW_RESTAURANT",
                      "IMPORT_MENU",
                      "PREVIEW_MENU",
                      "CONFIRM_MENU_IMPORT",
                    ].includes(a.action),
                )
                .map((a) => (
                  <Button
                    key={a.action + a.value}
                    action={a}
                    rtl={rtl}
                    busy={busy}
                  />
                ))}
            </>
          ) : (
            <View style={styles.panel}>
              <View style={styles.emptyIcon}>
                <Icon name="food" size={30} color={colors.forest} />
              </View>
              <Text style={[textStyle(rtl, "semibold"), styles.sectionTitle]}>
                {tx(rtl, "Bring your menu", "أضف قائمتك")}
              </Text>
              {fields
                .filter(
                  (f) =>
                    f.key !== "RESTAURANT_SEARCH" && !filterFields.includes(f),
                )
                .map((f) => (
                  <FieldInput key={f.key} field={f} rtl={rtl} busy={busy} />
                ))}
              {actions
                .filter((a) =>
                  [
                    "IMPORT_MENU",
                    "PREVIEW_MENU",
                    "CONFIRM_MENU_IMPORT",
                  ].includes(a.action),
                )
                .map((a) => (
                  <Button
                    key={a.action + a.value}
                    action={a}
                    rtl={rtl}
                    busy={busy}
                  />
                ))}
            </View>
          )}
          <PagedCards
            cards={cards.filter((c) => !c.id.startsWith("restaurant:"))}
            rtl={rtl}
            busy={busy}
          />
        </PageMotion>
      </ScrollArea>
      <BottomSheet
        visible={filters}
        title={tx(rtl, "Find your food", "اعثر على أكلتك")}
        rtl={rtl}
        onClose={() => setFilters(false)}
      >
        <ScrollArea contentContainerStyle={styles.content}>
          {filterFields.map((f) => (
            <FieldInput key={f.key} field={f} busy={busy} rtl={rtl} />
          ))}
          {!!active && (
            <Button
              action={action(
                tx(rtl, "Clear filters", "مسح التصفية"),
                "CLEAR_FILTERS",
              )}
              rtl={rtl}
              busy={busy}
              onPress={() => filterFields.forEach((f) => update(f.key, ""))}
            />
          )}
          <Button
            action={action(
              tx(rtl, "Show restaurants", "عرض المطاعم"),
              "CLOSE_FILTERS",
              "",
              true,
            )}
            rtl={rtl}
            onPress={() => setFilters(false)}
          />
        </ScrollArea>
      </BottomSheet>
      <BottomSheet
        visible={!!current}
        title={current?.title || ""}
        rtl={rtl}
        onClose={() => setRestaurantId("")}
      >
        {current && (
          <ScrollArea contentContainerStyle={styles.content}>
            <Text style={[textStyle(rtl), styles.detail]}>
              {current.detail}
            </Text>
            {current.buttons.map((a) => (
              <Button
                key={a.action + a.value}
                action={a}
                busy={busy}
                rtl={rtl}
                onPress={() => {
                  setRestaurantId("");
                  dispatch(a.action, a.value);
                }}
              />
            ))}
          </ScrollArea>
        )}
      </BottomSheet>
    </View>
  );
}

const styles = StyleSheet.create({
  screen: { flex: 1 },
  reverse: { flexDirection: "row-reverse" },
  intro: {
    padding: 18,
    paddingBottom: 12,
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
  },
  title: { fontSize: 19, lineHeight: 30 },
  detail: { fontSize: 12, lineHeight: 22, color: colors.muted },
  back: {
    width: 36,
    minHeight: 44,
    justifyContent: "center",
    alignItems: "center",
  },
  heroIcon: {
    width: 46,
    height: 46,
    borderRadius: 17,
    backgroundColor: colors.mint,
    alignItems: "center",
    justifyContent: "center",
  },
  tabScroll: { flexGrow: 0, flexShrink: 0, height: 58 },
  tabs: {
    paddingHorizontal: 18,
    paddingBottom: 10,
    gap: 8,
    flexDirection: "row",
  },
  tab: {
    paddingHorizontal: 15,
    paddingVertical: 9,
    minHeight: 44,
    borderRadius: 17,
    justifyContent: "center",
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
  },
  activeTab: { backgroundColor: colors.mint, borderColor: "#B9D7C9" },
  tabLabel: { fontSize: 12, lineHeight: 23 },
  content: { padding: 18, gap: 14, paddingBottom: 24 },
  panel: {
    backgroundColor: colors.white,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 24,
    padding: 16,
    gap: 12,
  },
  accountShortcut:{width:"48%",flexGrow:1,flexDirection:"row",alignItems:"center",gap:8,minHeight:60,padding:10,borderRadius:16,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line},
  shortcutIcon:{width:32,height:32,borderRadius:11,alignItems:"center",justifyContent:"center"},
  shortcutLabel:{flex:1,fontSize:11,lineHeight:20},
  walletShortcut:{flexDirection:"row",alignItems:"center",gap:10,minHeight:48,paddingHorizontal:12,borderRadius:14,backgroundColor:colors.mint},
  profileTotals:{flexDirection:"row",gap:10},profileTotal:{flex:1,minHeight:64,padding:10,borderRadius:14,backgroundColor:colors.white,borderWidth:1,borderColor:colors.line,gap:4},
  grid: { flexDirection: "row", flexWrap: "wrap", gap: 10 },
  tile: { width: "47.8%", flexGrow: 1 },
  sectionTitle: { fontSize: 15, lineHeight: 26 },
  profileHero: {
    backgroundColor: colors.mint,
    padding: 14,
    borderRadius: 20,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  avatar: {
    height: 64,
    width: 64,
    borderRadius: 24,
    backgroundColor: colors.white,
    alignItems: "center",
    justifyContent: "center",
  },
  footer: {
    padding: 16,
    borderTopWidth: 1,
    borderColor: colors.line,
    backgroundColor: colors.cream,
  },
  notice: {
    backgroundColor: colors.white,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: colors.line,
    padding: 16,
    gap: 12,
  },
  newNotice: { borderColor: "#B9D7C9", backgroundColor: "#F1F8F4" },
  noticeHeading: { flexDirection: "row", alignItems: "center", gap: 10 },
  noticeIcon: {
    width: 35,
    height: 35,
    borderRadius: 13,
    backgroundColor: colors.mint,
    alignItems: "center",
    justifyContent: "center",
  },
  noticeBody: {
    fontSize: 13,
    lineHeight: 23,
    marginTop: 10,
    color: colors.muted,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
    backgroundColor: colors.primary,
  },
  noticeActions: { gap: 8 },
  empty: { padding: 30, gap: 12, alignItems: "center" },
  emptyIcon: {
    height: 66,
    width: 66,
    borderRadius: 25,
    backgroundColor: colors.mint,
    alignItems: "center",
    justifyContent: "center",
  },
  search: { paddingHorizontal: 18, gap: 10, paddingBottom: 4 },
  tools: { flexDirection: "row", gap: 10 },
  restaurant: {
    flexDirection: "row",
    alignItems: "center",
    gap: 14,
    padding: 15,
    borderWidth: 1,
    borderColor: colors.line,
    borderRadius: 24,
    backgroundColor: colors.white,
  },
  restaurantIcon: {
    height: 56,
    width: 56,
    borderRadius: 19,
    alignItems: "center",
    justifyContent: "center",
  },
  badge: { color: colors.forest, fontSize: 11, lineHeight: 19 },
});
