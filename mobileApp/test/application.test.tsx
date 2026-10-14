import React from "react";
import { fireEvent, render, waitFor, act, within } from "@testing-library/react-native";
import { Alert } from "react-native";
import App from "../App";
import {
  Button,
  FieldInput,
  PagedCards,
  action,
} from "../src/components/Controls";
import { native, events } from "../src/native";
import type { Snapshot, Field, Card } from "../src/types";
import { roomCodeFromLink } from "../src/roomLink";
import { NotificationCenter, ProfileScreen, LibraryScreen } from "../src/screens/AccountScreens";
import AuthScreen from '../src/screens/AuthScreen';
import NativeTabBar from '../src/components/NativeTabBar';
import RoomPayments from '../src/components/RoomPayments';
import RoomAttention from '../src/components/RoomAttention';
import RoomOrderDock from '../src/components/RoomOrderDock';
import RoomSettings from '../src/components/RoomSettings';
import RoomMembers from '../src/components/RoomMembers';
import RoomFood from '../src/components/RoomFood';
import WalletTopUpScreen from '../src/screens/WalletTopUpScreen';
import {setUiLanguage} from '../src/language';
import HomeScreen from '../src/screens/HomeScreen';
import AdminScreen from '../src/screens/AdminScreen';
import {Platform} from 'react-native';
jest.mock("../src/native", () => ({
  native: {
    getSnapshot: jest.fn(),
    dispatch: jest.fn(),
    update: jest.fn(),
    tick: jest.fn(),
    photo: jest.fn(),
    dismissFeedback: jest.fn(),
  },
  events: { addListener: jest.fn(() => ({ remove: jest.fn() })) },
  parseSnapshot: JSON.parse,
  dispatch: (...args: unknown[]) =>
    require("../src/native").native.dispatch(args[0], args[1] || ""),
  update: (...args: unknown[]) =>
    require("../src/native").native.update(...args),
  quickAction: jest.fn(),
}));
const card = (id: string, title = id): Card => ({
  id,
  title,
  detail: "",
  badge: "",
  buttons: [],
  image: "",
});
const field = (key: string, more: Partial<Field> = {}): Field => ({
  key,
  label: key,
  value: "",
  choices: [],
  toggle: false,
  secret: false,
  multiline: false,
  ...more,
});
const snapshot = (more: Partial<Snapshot> = {}): Snapshot => ({
  supportActive: false,
  accountId: "user",
  authenticated: true,
  state: {
    page: "HOME",
    title: "Home",
    subtitle: "",
    busy: false,
    online: true,
    status: "",
    error: "",
    roomCode: "",
    canGoBack: false,
    progressStep: -1,
    inviteLink: "",
    inviteSummary: "",
    rtl: false,
    accessBlocked: false,
    canOverrideSelection: false,
    hasJoinTimer: false,
  },
  mainFields: [],
  extraFields: [],
  topCards: [],
  sections: [],
  inlineButtons: [],
  utilityButtons: [],
  quick: {
    people: [],
    history: [],
    winner: "",
    spinning: false,
    canSpin: false,
    startRotation: 0,
    endRotation: 0,
    destination: "MAIN",
    nameDraft: "",
    error: "",
    revision: 0,
  },
  ...more,
});
function measureScroll(view:ReturnType<typeof render>,markerTop:()=>number) {
  view.UNSAFE_root.findAll(node => typeof node.instance?.getNativeScrollRef === "function").forEach(node => {
    node.instance.getNativeScrollRef.mockReturnValue({measureInWindow:(callback:Function) => callback(0,0,390,600)});
  });
  view.UNSAFE_root.findAll(node => node.props.testID === "scroll-load" && typeof node.instance?.measureInWindow === "function").forEach(node => {
    node.instance.measureInWindow.mockImplementation((callback:Function) => callback(0,markerTop(),390,20));
  });
}
beforeEach(() => {
  jest.clearAllMocks();
  setUiLanguage('ar-EG');
  (native.getSnapshot as jest.Mock).mockResolvedValue(
    JSON.stringify(snapshot()),
  );
});

test("header opens notifications with the live unread badge and More stays in the bottom tabs", async () => {
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(snapshot({notificationUnread:3})));
  const view=render(<App/>);
  fireEvent.press(await view.findByLabelText("Notifications, 3 unread"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_NOTIFICATIONS", "");
  expect(view.queryByLabelText("More options")).toBeNull();
  fireEvent.press(view.getByTestId("nav:MORE"));
  expect(view.getByText("Explore FoodRun")).toBeTruthy();
});

test("verified administrators reach the full mobile panel and each tab keeps its native action", () => {
  const value=snapshot({adminAvailable:true});value.state.page="ADMIN";
  const ids=["overview","users","rooms","history","wallets","restaurants","blocks","cleanup","settings"];
  const tabs={...card("admin-tabs","Admin panel"),buttons:ids.map(id=>({...action(id,"ADMIN_TAB",id),primary:id==="users",enabled:id!=="users"}))};
  value.topCards=[tabs,{...card("admin-user:other","Other user"),buttons:[action("Edit user details","ADMIN_USER","other"),action("Sign in as this user","ADMIN_SUPPORT_START","other")]}];
  value.mainFields=[field("ADMIN_SEARCH",{label:"Search this tab"})];
  const view=render(<AdminScreen snapshot={value}/>);
  expect(view.getByRole("tab",{name:"users"}).props.accessibilityState.selected).toBe(true);
  for(const id of ids.filter(id=>id!=="users")){
    fireEvent.press(view.getByTestId(`admin-tab:${id}`));expect(native.dispatch).toHaveBeenLastCalledWith("ADMIN_TAB",id);
  }
  fireEvent.changeText(view.getByTestId("ADMIN_SEARCH"),"room code");expect(native.update).toHaveBeenLastCalledWith("ADMIN_SEARCH","room code");
  fireEvent.press(view.getByText("Edit user details"));expect(native.dispatch).toHaveBeenLastCalledWith("ADMIN_USER","other");
  fireEvent.press(view.getByText("Sign in as this user"));expect(native.dispatch).toHaveBeenLastCalledWith("ADMIN_SUPPORT_START","other");
  value.state.busy=true;view.rerender(<AdminScreen snapshot={value}/>);
  (native.dispatch as jest.Mock).mockClear();fireEvent.press(view.getByTestId("admin-tab:rooms"));expect(native.dispatch).not.toHaveBeenCalled();
  value.adminAvailable=false;value.state.busy=false;view.rerender(<AdminScreen snapshot={value}/>);
  expect(view.queryByTestId("admin-tab:users")).toBeNull();expect(view.getByText("This account cannot access administration.")).toBeTruthy();
});

test("admin Home shortcut is hidden for other accounts and support sessions", () => {
  const value=snapshot({adminAvailable:true});
  const view=render(<HomeScreen snapshot={value}/>);
  fireEvent.press(view.getByText("Admin panel"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ADMIN","");
  value.supportActive=true;view.rerender(<HomeScreen snapshot={value}/>);expect(view.queryByText("Admin panel")).toBeNull();
  value.supportActive=false;value.adminAvailable=false;view.rerender(<HomeScreen snapshot={value}/>);expect(view.queryByText("Admin panel")).toBeNull();
});

test("room-name hold opens the existing wheel override only when authorized and idle", async () => {
  const value=snapshot();value.state={...value.state,page:"ROOM",roomCode:"123456",title:"Team lunch",canOverrideSelection:false};
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);await view.findByTestId("room-name");
  fireEvent(view.getByTestId("room-name"),"longPress");expect(native.dispatch).not.toHaveBeenCalled();
  value.state.canOverrideSelection=true;act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  fireEvent(view.getByTestId("room-name"),"longPress");expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_SELECTION_OVERRIDE","");
  value.state.busy=true;act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  (native.dispatch as jest.Mock).mockClear();fireEvent(view.getByTestId("room-name"),"longPress");expect(native.dispatch).not.toHaveBeenCalled();
});

test("notification filters follow read acknowledgements and open the original notification", () => {
  const unread={...card("notification:new","New invitation"),badge:"New",detail:"Breakfast"};
  const read=card("notification:read","Confirmed payment");
  const value=snapshot({topCards:[unread,read],inlineButtons:[action("Mark all as read","NOTIFICATIONS_READ_ALL"),action("Clear notifications","NOTIFICATIONS_CLEAR")]});
  const view=render(<NotificationCenter snapshot={value}/>);
  fireEvent.press(view.getByText("Mark all as read"));expect(native.dispatch).toHaveBeenLastCalledWith("NOTIFICATIONS_READ_ALL","");
  fireEvent.press(view.getByText("Clear notifications"));expect(native.dispatch).toHaveBeenLastCalledWith("NOTIFICATIONS_CLEAR","");
  fireEvent.press(view.getByRole("tab",{name:"Unread (1)"}));
  expect(view.queryByText("Confirmed payment")).toBeNull();
  fireEvent.press(view.getByLabelText("New invitation. Breakfast"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_NOTIFICATION","new:open");
  view.rerender(<NotificationCenter snapshot={{...value,topCards:[{...unread,badge:""},read]}}/>);
  expect(view.getByText("You're all caught up")).toBeTruthy();
});

test("profile tabs keep edits through snapshots and save to the same native profile action", () => {
  const value=snapshot({profile:{name:"Same user",photo:""},mainFields:[field("NAME",{value:"Same user"}),field("AANI",{toggle:true,value:"true"}),field("IBAN",{value:"0501234567"})],primaryAction:action("Save profile","SAVE_PROFILE","",true)});
  const view=render(<ProfileScreen snapshot={value}/>);
  fireEvent.press(view.getByRole("tab",{name:"Details"}));
  fireEvent.changeText(view.getByTestId("NAME"),"New display name");
  expect(native.update).toHaveBeenLastCalledWith("NAME","New display name");
  fireEvent.press(view.getByRole("tab",{name:"Receiving payments"}));
  view.rerender(<ProfileScreen snapshot={{...value,mainFields:[field("NAME",{value:"New display name"}),...value.mainFields.slice(1)]}}/>);
  expect(view.getByTestId("IBAN")).toBeTruthy();
  fireEvent.press(view.getByText("Save profile"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SAVE_PROFILE","");
  expect(native.dispatch).not.toHaveBeenCalledWith("REGISTER","");
});

test("account shortcuts reach receiving details and orders without losing edits", () => {
  const value=snapshot({profile:{name:"User",photo:""},mainFields:[field("NAME",{value:"User"}),field("IBAN",{value:"AE123"})],primaryAction:action("Save profile","SAVE_PROFILE","",true),topCards:[card("favorite:meal","Favourite lunch"),card("previous:old","Previous lunch") ]});
  const view=render(<ProfileScreen snapshot={value}/>);
  fireEvent.press(view.getByLabelText("Receiving payments"));
  expect(view.getByTestId("IBAN")).toBeTruthy();
  expect(view.queryByTestId("NAME")).toBeNull();
  fireEvent.press(view.getByRole("tab",{name:"Overview"}));
  fireEvent.press(view.getByLabelText("Previous orders"));
  expect(view.getByText("Previous lunch")).toBeTruthy();
  expect(view.queryByText("Favourite lunch")).toBeNull();
  fireEvent.press(view.getByRole("tab",{name:"Overview"}));
  fireEvent.press(view.getByLabelText("Edit details"));
  expect(view.getByTestId("NAME")).toBeTruthy();
  expect(view.queryByTestId("IBAN")).toBeNull();
  fireEvent.press(view.getByRole("tab",{name:"Overview"}));
  fireEvent.press(view.getByLabelText("Open wallet & payments"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET","");
});

test("profile wallet cards still open transaction history", () => {
  const wallet={...card("profile-dashboard:wallet-funds","My balance"),selection:action("Transactions","OPEN_WALLET_HISTORY","balance:original")};
  const view=render(<ProfileScreen snapshot={snapshot({profile:{name:"User",photo:""},topCards:[wallet]})}/>);
  fireEvent.press(view.getByLabelText("My balance"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET_HISTORY","balance:original");
});

test("restaurant card opens management sheet without changing rooms until explicitly selected", () => {
  const restaurant={...card("restaurant:ajman","Mama'esh · Ajman"),buttons:[action("Use for room","SELECT_RESTAURANT","ajman"),action("Edit menu","EDIT_RESTAURANT","ajman")]};
  const view=render(<LibraryScreen snapshot={snapshot({topCards:[restaurant]})}/>);
  fireEvent.press(view.getByTestId("library:restaurant:ajman"));
  expect(native.dispatch).not.toHaveBeenCalled();
  fireEvent.press(view.getByText("Edit menu"));
  expect(native.dispatch).toHaveBeenLastCalledWith("EDIT_RESTAURANT","ajman");
});
test("bottom navigation opens Home Rooms Wallet Profile and More", async () => {
  const view = render(<App />);
  await view.findByText("Ready to order?");
  fireEvent.press(view.getByLabelText("FoodRun Home"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_HOME", "");
  fireEvent.press(view.getByText("Rooms"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ROOMS", "");
  fireEvent.press(view.getByTestId("nav:OPEN_WALLET"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET", "");
  fireEvent.press(view.getByTestId("nav:OPEN_PROFILE"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_PROFILE", "");
  fireEvent.press(view.getByText("More"));
  fireEvent.press(view.getByText("My profile"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_PROFILE", "");
});
test("Arabic snapshot renders Arabic navigation and returns to English", async () => {
  const value = snapshot();
  value.state.rtl = true;
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("الرئيسية");
  expect(view.getByText("الغرف")).toBeTruthy();
  fireEvent.press(view.getByText("ع"));
  await view.findByText(/العربية المصرية/);
  expect(view.getByText("العربية الإماراتية")).toBeTruthy();
  expect(view.getByText("العربية الفصحى")).toBeTruthy();
  fireEvent.press(view.getByText("العربية الإماراتية"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SET_LANGUAGE", "ar-AE");
  fireEvent.press(view.getByText("ع"));
  fireEvent.press(view.getByText("English"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SET_LANGUAGE", "en");
});
test("compact Home keeps create join menus and profile actions reachable", async () => {
  const value = snapshot({profile:{name:"My name",photo:"https://example.test/photo.jpg"},primaryAction:action("Create a room","CREATE"),inlineButtons:[action("Join a room","JOIN"),action("Menus","OPEN_LIBRARY")]});
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("Hi, My");
  fireEvent.press(view.getByText("Create a room"));
  expect(native.dispatch).toHaveBeenLastCalledWith("CREATE", "");
  fireEvent.press(view.getByText("Join a room"));
  expect(native.dispatch).toHaveBeenLastCalledWith("JOIN", "");
  expect(view.queryByTestId("home-carousel")).toBeNull();
  fireEvent.press(view.getByLabelText("Menus"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_LIBRARY", "");
  expect(view.getByText("Create a room")).toBeTruthy();
  expect(view.getByText("Join a room")).toBeTruthy();
  fireEvent.press(view.getByLabelText("My profile"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_PROFILE", "");
});

test("Sudanese Home shows its greeting, wallet labels, shortcuts and language action", async () => {
  const value = snapshot({profile:{name:"أمل أحمد",photo:""},primaryAction:action("افتح غرفة","CREATE"),
    inlineButtons:[action("ادخل غرفة","JOIN"),action("قوائم الأكل","OPEN_LIBRARY"),action("المجموعات","FRIENDS_ACTION"),action("اختيار سريع","QUICK_SPIN")]});
  value.state.rtl=true;value.state.language="ar-SD";
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);
  await view.findByText("أهلاً بيك، أمل");
  expect(view.getByText("طلبك الجاي ساهل شديد.")).toBeTruthy();
  expect(view.getByText("المحفظة والقروش")).toBeTruthy();
  expect(view.getByText("قوائم الأكل")).toBeTruthy();
  fireEvent.press(view.getByText("افتح غرفة"));
  expect(native.dispatch).toHaveBeenLastCalledWith("CREATE","");
  fireEvent.press(view.getByText("ع"));
  expect(view.getByText(/العربية السودانية/)).toBeTruthy();
});
test("Home prioritizes the current order without duplicating it and keeps live invitations and wallet totals",()=>{
 const create={...action("Create a room","CREATE"),enabled:false},join=action("Join a room","JOIN");
 const current={...card("continue-order","Continue your order"),detail:"Lunch room",buttons:[action("Continue order","RESUME","lunch")]};
 const same={...card("session:lunch","Lunch room"),detail:"Choose food",buttons:[action("Open room","RESUME","lunch")]};
 const other={...card("session:dinner","Dinner room"),buttons:[action("Open room","RESUME","dinner")]};
 const invite={...card("invitation:invite","Join Friends"),buttons:[action("Join invitation","ACCEPT_INVITE","invite")]};
 const value=snapshot({profile:{name:"Ahmed Karam",photo:""},primaryAction:create,inlineButtons:[join,action("Menus","OPEN_LIBRARY"),action("Groups","FRIENDS_ACTION","open")],topCards:[current,same,other,invite,card("dashboard:wallet-direction:false","To pay"),{...card("dashboard:wallet-direction:true","To receive"),badge:"AED 20.00"},card("dashboard:wallet:old","Long payment details")]});
 const view=render(<HomeScreen snapshot={value}/>);
 expect(view.getAllByText("Lunch room")).toHaveLength(1);
 fireEvent.press(view.getByLabelText("Continue order · Lunch room"));expect(native.dispatch).toHaveBeenLastCalledWith("RESUME","lunch");
 native.dispatch.mockClear();fireEvent.press(view.getByText("Create a room"));expect(native.dispatch).not.toHaveBeenCalled();
 fireEvent.press(view.getByText("Join a room"));expect(native.dispatch).toHaveBeenLastCalledWith("JOIN","");
 fireEvent.press(view.getByText("Join invitation"));expect(native.dispatch).toHaveBeenLastCalledWith("ACCEPT_INVITE","invite");
 fireEvent.press(view.getByLabelText("Open room · Dinner room"));expect(native.dispatch).toHaveBeenLastCalledWith("RESUME","dinner");
 expect(view.getByText("AED 20.00")).toBeTruthy();expect(view.queryByText("Long payment details")).toBeNull();
 fireEvent.press(view.getByLabelText("Open wallet & payments"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET","");
 fireEvent.press(view.getByLabelText("View all rooms"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ROOMS","");
 fireEvent.press(view.getByLabelText("Groups"));expect(native.dispatch).toHaveBeenLastCalledWith("FRIENDS_ACTION","open");
 view.rerender(<HomeScreen snapshot={{...value,topCards:[other]}}/>);
 expect(view.queryByTestId("home-current-order")).toBeNull();expect(view.queryByText("Join Friends")).toBeNull();expect(view.queryByText("AED 20.00")).toBeNull();
 expect(view.getByText("Dinner room")).toBeTruthy();
});
test("Home keeps Arabic empty state and busy actions safe",()=>{
 const value=snapshot({inlineButtons:[action("دخول غرفة","JOIN")]});value.state={...value.state,rtl:true,online:false,busy:true};
 const view=render(<HomeScreen snapshot={value}/>);
 expect(view.getByText(/غير متصل|إنترنت/)).toBeTruthy();
 expect(view.getByText("أنشئ غرفة أو انضم لغرفة بالأعلى، وستظهر غرفك هنا.")).toBeTruthy();
 fireEvent.press(view.getByText("دخول غرفة"));fireEvent.press(view.getByLabelText("فتح المحفظة والمدفوعات"));expect(native.dispatch).not.toHaveBeenCalled();
});
test("Home shows compact horizontal recent rooms and opens every feature from its grid",()=>{
 const features=[action("Quick Choice","QUICK_SPIN"),action("Create payment room","CREATE_PAYMENT_ROOM"),action("Menus","OPEN_LIBRARY"),action("Groups","FRIENDS_ACTION","open"),action("Add restaurant","NEW_RESTAURANT")];
 const rooms=Array.from({length:8},(_,i)=>({...card(`session:${i}`,`Room ${i}`),detail:"Order #1 · Collecting orders",buttons:[action("Open room","RESUME",String(i))]}));
 const view=render(<HomeScreen snapshot={snapshot({inlineButtons:features,topCards:rooms})}/>);
 expect(view.getByTestId("home-recent-rooms").props.horizontal).toBe(true);
 expect(view.getByText("Room 5")).toBeTruthy();expect(view.queryByText("Room 6")).toBeNull();
 for(const [kind,value] of [["QUICK_SPIN",""],["CREATE_PAYMENT_ROOM",""],["OPEN_WALLET",""],["OPEN_LIBRARY",""],["FRIENDS_ACTION","open"],["NEW_RESTAURANT",""]]) {
  fireEvent.press(view.getByTestId(`home-feature:${kind}`));expect(native.dispatch).toHaveBeenLastCalledWith(kind,value);
 }
 fireEvent.press(view.getByLabelText("View all rooms"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ROOMS","");
});
test("Home spending summary opens the spending tab and does not claim zero when totals are unavailable",()=>{
 const weekly={...card("home-spending:week","This week"),badge:"AED 75.00"},monthly={...card("home-spending:month","This month"),badge:"AED 315.00"};
 const view=render(<HomeScreen snapshot={snapshot({topCards:[weekly,monthly]})}/>);
 fireEvent.press(view.getByLabelText("This week · AED 75.00"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET","spending");
 expect(view.getByText("AED 315.00")).toBeTruthy();
 view.rerender(<HomeScreen snapshot={snapshot()}/>);expect(view.getByText("Refresh to load spending statistics.")).toBeTruthy();expect(view.queryByText("AED 0.00")).toBeNull();
});
test("Wallet opens directly on spending when reached from Home statistics",async()=>{
 const value=snapshot({topCards:[{...card("profile-dashboard:wallet-spending:week:AED","This week"),badge:"AED 75.00"}]});
 value.state={...value.state,page:"WALLET",walletSection:"spending"};(native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
 const view=render(<App/>);await view.findByText("AED 75.00");expect(view.getByRole("tab",{name:"Spending"}).props.accessibilityState.selected).toBe(true);
});
test("room tabs separate food, payments and members and survive live updates", async () => {
  const value = snapshot();
  value.state.page = "ROOM";
  value.state.roomCode = "123456";
  value.topCards = [card("order-summary", "Room summary"), card("half-item:1", "Half available")];
  value.sections = [{ title: "Mixed", collapsed: false, cards: [card("menu:1", "Sandwich"), card("receipt:me", "My bill"), card("member:1", "My friend"), card("new-kind", "New update")] }];
  value.primaryAction = action("Confirm my food", "SUBMIT_CART", "", true);
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("Room summary");
  expect(view.queryByText("My friend")).toBeNull();
  fireEvent.press(view.getByTestId("room-tab:1"));
  expect(view.getByText("Sandwich")).toBeTruthy();
  expect(view.getByText("Half available")).toBeTruthy();
  fireEvent.press(view.getByTestId("room-order-shortcut"));
  fireEvent.press(view.getByText("Confirm my food"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SUBMIT_CART", "");
  fireEvent.press(view.getByLabelText("Dismiss sheet"));
  fireEvent.press(view.getByTestId("room-tab:2"));
  fireEvent.press(view.getByRole("tab",{name:"Receipts"}));
  expect(view.getByText("My bill")).toBeTruthy();
  expect(view.queryByText("Sandwich")).toBeNull();
  value.state.rtl = true;
  act(() => (events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByText("My bill")).toBeTruthy();
  expect(view.getByLabelText("المدفوعات").props.accessibilityState.selected).toBe(true);
  fireEvent.press(view.getByTestId("room-tab:3"));
  expect(view.getByText("My friend")).toBeTruthy();
  fireEvent.press(view.getByTestId("room-tab:0"));
  expect(view.getByText("New update")).toBeTruthy();
});
test("room tabs keep useful empty states and reset for another room", async () => {
  const value = snapshot();
  value.state.page = "ROOM"; value.state.roomCode = "123456";
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByLabelText("Payments");
  fireEvent.press(view.getByTestId("room-tab:2"));
  expect(view.getByText("Payments will appear here when the bill is ready.")).toBeTruthy();
  value.state.roomCode = "234567";
  act(() => (events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByLabelText("Overview").props.accessibilityState.selected).toBe(true);
});
test("chosen person stays visible while browsing food payments and members",async()=>{
  const value=snapshot();value.state={...value.state,page:"ROOM",roomCode:"123456",roomPhase:"ACCEPTING",orderingPerson:"Alice",orderingPersonPending:true};
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);await view.findByText("Chosen: Alice");
  value.state.roomPhase="COLLECTING";value.state.orderingPersonPending=false;
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  for(const tab of [0,1,2,3]) {fireEvent.press(view.getByTestId(`room-tab:${tab}`));expect(view.getByText("Ordering person: Alice")).toBeTruthy();}
  value.state.orderingPerson="Bob";
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.queryByText("Ordering person: Alice")).toBeNull();expect(view.getByText("Ordering person: Bob")).toBeTruthy();
  value.state.roomPhase="LOBBY";value.state.orderingPerson="";
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.queryByText("Ordering person: Bob")).toBeNull();
});
test("member sheet shows their food and uses the native reuse preview",()=>{
  const member={...card("member:alice","Alice"),badge:"Food submitted",detail:"Food submitted\nTheir order\n2 × Falafel · No onions",buttons:[action("Add this order to mine","REUSE_ORDER","member|alice",true)]};
  const view=render(<RoomMembers cards={[member]} busy={false} rtl={false}/>);
  fireEvent.press(view.getByTestId("compact:member:alice"));
  expect(view.getAllByText(member.detail).length).toBeGreaterThan(0);
  fireEvent.press(view.getByText("Add this order to mine"));
  expect(native.dispatch).toHaveBeenLastCalledWith("REUSE_ORDER","member|alice");
  view.rerender(<RoomMembers cards={[{...member,badge:"Order closed",buttons:[]}]} busy={false} rtl={false}/>);
  expect(view.queryByText("Add this order to mine")).toBeNull();
});
test("every menu item has a visible notes action including sized items",()=>{
  const cards=[{...card("menu:simple","Falafel"),buttons:[action("Add","QUICK_ADD_ITEM","simple"),action("Customize","OPEN_ITEM","simple")]},{...card("menu:sized","Beans"),buttons:[action("Choose size","OPEN_ITEM","sized")]}];
  const view=render(<RoomFood cards={cards} fields={[]} busy={false} rtl={false}/>);
  const notes=view.getAllByText("Add notes");expect(notes).toHaveLength(2);
  fireEvent.press(notes[1]);expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ITEM","sized");
});
test("latest order is visible in the food tab and opens a reuse preview",()=>{
  const latest={...card("reuse-latest","My latest order"),detail:"2 × Falafel",buttons:[action("Add my latest order","REUSE_ORDER","past|old-room|7",true)]};
  const value=snapshot({topCards:[latest]});value.state.page="ROOM";
  const view=render(<RoomFood cards={value.topCards} fields={[]} busy={false} rtl={false}/>);
  expect(view.getByText("My latest order")).toBeTruthy();fireEvent.press(view.getByText("Add my latest order"));
  expect(native.dispatch).toHaveBeenLastCalledWith("REUSE_ORDER","past|old-room|7");
});
test("wallet top-up steps validate choices and send only from the final review",()=>{
  const value=snapshot();value.state.page="WALLET_TOP_UP";
  value.mainFields=[field("WALLET_SEARCH"),field("WALLET_CURRENCY",{value:"AED",choices:[{value:"AED",label:"AED"}]}),field("WALLET_AMOUNT",{value:"0"}),field("WALLET_ACCOUNT",{value:"bank",choices:[{value:"bank",label:"Test Bank"}]}),field("WALLET_NOTE")];
  value.inlineButtons=[action("Search users","WALLET_FUNDS_ACTION","search"),action("I sent the top-up","WALLET_FUNDS_ACTION","top-up")];
  const view=render(<WalletTopUpScreen snapshot={value}/>);
  fireEvent.press(view.getByText("Continue"));expect(view.getByText("Choose a wallet holder before continuing.")).toBeTruthy();
  value.topCards=[card("wallet-recipient","Alice"),{...card("wallet-method:bank","Alice · Test Bank"),detail:"AE07 0331 2345 6789 0123 456",buttons:[action("Copy","WALLET_FUNDS_ACTION","copy|bank")]}];
  view.rerender(<WalletTopUpScreen snapshot={value}/>);fireEvent.press(view.getByText("Continue"));
  expect(view.getByText("Step 2 of 4")).toBeTruthy();fireEvent.press(view.getByText("Continue"));
  expect(view.getByText("Enter a positive amount with up to two decimal places.")).toBeTruthy();
  value.mainFields=value.mainFields.map(f=>f.key==="WALLET_AMOUNT" ? {...f,value:"50"} : f);
  view.rerender(<WalletTopUpScreen snapshot={value}/>);fireEvent.press(view.getByText("Continue"));
  expect(view.getByText("Step 3 of 4")).toBeTruthy();fireEvent.press(view.getByText("Copy"));
  expect(native.dispatch).toHaveBeenLastCalledWith("WALLET_FUNDS_ACTION","copy|bank");
  fireEvent.press(view.getByText("Continue"));expect(view.getByText("Step 4 of 4")).toBeTruthy();
  expect(view.getByText("AED 50")).toBeTruthy();expect(native.dispatch).not.toHaveBeenCalledWith("WALLET_FUNDS_ACTION","top-up");
  fireEvent.press(view.getByTestId("wallet-review-edit:1"));expect(view.getByTestId("WALLET_AMOUNT").props.value).toBe("50");
  fireEvent.press(view.getByText("Continue"));fireEvent.press(view.getByText("Continue"));
  fireEvent.press(view.getByText("I sent the top-up"));fireEvent.press(view.getByText("I sent the top-up"));
  expect((native.dispatch as jest.Mock).mock.calls.filter(call=>call[1]==="top-up")).toHaveLength(1);
});
test("owner can start the wheel from every room tab and sees why it is blocked",async()=>{
  const value=snapshot();value.state={...value.state,page:"ROOM",roomCode:"123456",roomPhase:"LOBBY",roomStage:"Join the room",title:"Team lunch",subtitle:"October 12 · Order 42"};
  value.primaryAction=action("Spin together","PREPARE_SPIN","",true);
  value.mainFields=[field("PAYER_MODE",{value:"wheel",choices:[{value:"wheel",label:"Use the wheel"},{value:"direct",label:"Choose person directly"}]})];
  value.sections=[{title:"All",collapsed:false,cards:[card("last-chosen","Last chosen"),card("menu:food","Sandwich")]}];
  value.inlineButtons=[action("Skip this order","PARTICIPATE","false")];
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);await view.findByText("Start choosing the ordering person");
  expect(view.queryByText(value.state.subtitle)).toBeNull();
  expect(view.queryByText("Last chosen")).toBeNull();
  expect(view.queryByTestId("PAYER_MODE")).toBeNull();
  expect(view.getByLabelText("Join: Current")).toBeTruthy();
  for(const tab of [0,1,2,3]){
    fireEvent.press(view.getByTestId(`room-tab:${tab}`));
    fireEvent.press(view.getByText("Start choosing the ordering person"));
    expect(native.dispatch).toHaveBeenLastCalledWith("PREPARE_SPIN","");
  }
  value.primaryAction.enabled=false;value.sections[0].cards.push({...card("spin-next-step","Before spinning"),detail:"Approve the waiting member first."});
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByText("Approve the waiting member first.")).toBeTruthy();
  (native.dispatch as jest.Mock).mockClear();fireEvent.press(view.getByText("Start choosing the ordering person"));expect(native.dispatch).not.toHaveBeenCalled();
  fireEvent.press(view.getByLabelText("Room settings"));
  expect(view.getByText("Last chosen")).toBeTruthy();
  expect(view.getByTestId("PAYER_MODE")).toBeTruthy();
  fireEvent.press(view.getByText("Skip this order"));expect(native.dispatch).toHaveBeenLastCalledWith("PARTICIPATE","false");
});

test("added food stays reachable before menu pagination and during search and live updates",async()=>{
  const value=snapshot();value.state={...value.state,page:"ROOM",roomCode:"123456",roomPhase:"COLLECTING",roomStage:"Choose food"};
  value.mainFields=[field("MENU_SEARCH",{label:"Find food"}),field("MENU_CATEGORY",{value:"all",choices:[{value:"all",label:"All categories"},{value:"category:sandwich",label:"Sandwiches"}]})];
  const cartLine={...card("cart:line","2 × Added sandwich"),buttons:[action("+","INCREASE_CART_QUANTITY","line"),action("Edit","EDIT_CART_ITEM","line")]};
  value.sections=[{title:"Food",collapsed:false,cards:[...Array.from({length:30},(_,i)=>({...card(`menu:${i}`,`Menu food ${i}`),buttons:[action("+ Add","QUICK_ADD_ITEM",String(i)),action("Quantity / notes","OPEN_ITEM",String(i))]})),cartLine,{...card("estimate","Your estimated total"),detail:"AED 20"}]}];
  value.primaryAction=action("Submit my food order","SUBMIT_CART","",true);
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);await view.findByTestId("room-tab:1");fireEvent.press(view.getByTestId("room-tab:1"));
  expect(view.queryByText("Menu food 29")).toBeNull();
  expect(view.getByTestId("room-order-items").props.children).toBe("2 × Added sandwich");
  fireEvent.press(view.getByText("Confirm order"));expect(native.dispatch).toHaveBeenLastCalledWith("SUBMIT_CART","");
  fireEvent.press(view.getByTestId("action:QUICK_ADD_ITEM:0"));expect(native.dispatch).toHaveBeenLastCalledWith("QUICK_ADD_ITEM","0");
  fireEvent.press(view.getByRole("radio",{name:"Sandwiches"}));expect(native.update).toHaveBeenLastCalledWith("MENU_CATEGORY","category:sandwich");
  fireEvent.changeText(view.getByLabelText("Find food"),"not in menu");expect(native.update).toHaveBeenLastCalledWith("MENU_SEARCH","not in menu");
  value.sections[0].cards=[card("menu-empty","No matching items"),cartLine];
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByText("No matching items")).toBeTruthy();
  fireEvent.press(view.getByTestId("room-order-shortcut"));expect(view.getByText("2 × Added sandwich")).toBeTruthy();
  fireEvent.press(view.getByText("+"));expect(native.dispatch).toHaveBeenLastCalledWith("INCREASE_CART_QUANTITY","line");
  cartLine.title="3 × Added sandwich";
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByText("3 × Added sandwich")).toBeTruthy();
  fireEvent.press(view.getByText("Confirm order"));expect(native.dispatch).toHaveBeenLastCalledWith("SUBMIT_CART","");
  fireEvent.press(view.getByLabelText("Dismiss sheet"));
  value.state.rtl=true;
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  fireEvent.press(view.getByLabelText("عرض طلبي"));expect(view.getByText("3 × Added sandwich")).toBeTruthy();
  value.state.roomCode="654321";value.sections=[];value.primaryAction=undefined;
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.queryByText("3 × Added sandwich")).toBeNull();
});
test("wallet opens its transaction sheet and requests older pages without mutations", async () => {
  const value = snapshot();
  value.topCards = [
    {
      ...card("wallet", "AED wallet"),
      selection: action("History", "OPEN_WALLET_HISTORY", "owner|holder|AED"),
    },
  ];
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("AED wallet");
  fireEvent.press(view.getByText("AED wallet"));
  expect(native.dispatch).toHaveBeenLastCalledWith(
    "OPEN_WALLET_HISTORY",
    "owner|holder|AED",
  );
  value.state.walletHistoryPrompt = {
    title: "Transactions",
    subtitle: "Owner → Holder",
    balance: "AED 20",
    cards: [card("paid", "Confirmed top-up")],
    loading: false,
    error: "",
    hasMore: true,
  };
  act(() => {
    (events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value));
  });
  expect(view.getByText("Confirmed top-up")).toBeTruthy();
  measureScroll(view,() => 550);
  fireEvent(view.getByTestId("scroll-load"),"layout",{nativeEvent:{layout:{x:0,y:550,width:390,height:20}}});
  await waitFor(() => expect(native.dispatch).toHaveBeenLastCalledWith("LOAD_WALLET_HISTORY", ""));
});
test("Wallet separates payments approvals balances and history without changing their actions",async()=>{
 const value=snapshot();value.state.page="WALLET";
 value.sections=[{title:"Required payments",collapsed:false,cards:[{...card("wallet-group:alice","Pay Payer for Alice"),buttons:[action("Settle Alice payment","WALLET_FUNDS_ACTION","batch|alice")]},{...card("wallet-batch:incoming","Confirm received transfer"),buttons:[action("Approve received amount","WALLET_FUNDS_ACTION","batch-yes|incoming")]}]},{title:"Wallet balance",collapsed:false,cards:[card("wallet-balance","Available AED 50")]},{title:"Payment history",collapsed:false,cards:[]}];
 (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));const view=render(<App/>);
 await view.findByText("Pay Payer for Alice");expect(view.queryByText("Available AED 50")).toBeNull();
 fireEvent.press(view.getByText("Settle Alice payment"));expect(native.dispatch).toHaveBeenLastCalledWith("WALLET_FUNDS_ACTION","batch|alice");
 fireEvent.press(view.getByRole("tab",{name:"Waiting"}));
 fireEvent.press(view.getByText("Approve received amount"));expect(native.dispatch).toHaveBeenLastCalledWith("WALLET_FUNDS_ACTION","batch-yes|incoming");
 fireEvent.press(view.getByRole("tab",{name:"Balance"}));expect(view.getByText("Available AED 50")).toBeTruthy();
 fireEvent.press(view.getByRole("tab",{name:"History"}));expect(view.queryByText("Available AED 50")).toBeNull();
});
test("Wallet spending tab shows the localized totals without mixing payment actions",async()=>{
 const value=snapshot();value.state={...value.state,page:"WALLET",language:"ar-SD",rtl:true};
 value.topCards=[{...card("profile-dashboard:wallet-spending:week:AED","الأسبوع دا"),badge:"AED 70.00",detail:"الفطور · AED 20.00\nالغداء · AED 50.00"},{...card("profile-dashboard:wallet-spending:month:AED","الشهر دا"),badge:"AED 240.00"},card("wallet-balance","رصيدك")];
 (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));const view=render(<App/>);
 await view.findByText("رصيدك");fireEvent.press(view.getByRole("tab",{name:"المصاريف"}));
 expect(view.getByText("الأسبوع دا")).toBeTruthy();expect(view.getByText("الشهر دا")).toBeTruthy();
 expect(view.getByText("AED 70.00")).toBeTruthy();expect(view.queryByText("رصيدك")).toBeNull();
});
test("room payments move a resolved transfer to history while keeping receipts and account details reachable",()=>{
 const account={...card("account","Recipient bank details"),detail:"Chosen person · Test Bank · GB82WEST12345698765432",buttons:[action("Copy IBAN","COPY_PAYMENT_DETAILS"),action("Copy all payment details","COPY_PAYMENT_DETAILS","all")]},receipt=card("receipt:me","My receipt");
 const pending={...card("transfer:sent","AED 5 sent"),paymentGroup:"waiting",buttons:[{...action("Confirm received","CONFIRM_TRANSFER","sent"),primary:true}]};
 const view=render(<RoomPayments cards={[account,receipt,...Array.from({length:12},(_,i)=>card(`wallet:${i}`,`Other payment ${i}`)),pending]} busy={false} rtl={false}/>);
 fireEvent.press(view.getByText("Confirm received"));expect(native.dispatch).toHaveBeenLastCalledWith("CONFIRM_TRANSFER","sent");
 expect(view.getByText("Recipient bank details")).toBeTruthy();
 fireEvent.press(view.getByText("Copy IBAN"));expect(native.dispatch).toHaveBeenLastCalledWith("COPY_PAYMENT_DETAILS","");
 fireEvent.press(view.getByText("Copy all payment details"));expect(native.dispatch).toHaveBeenLastCalledWith("COPY_PAYMENT_DETAILS","all");
 view.rerender(<RoomPayments cards={[account,receipt,{...pending,title:"AED 5 paid",paymentGroup:"history",buttons:[]}]} busy={false} rtl={false}/>);
 expect(view.queryByText("AED 5 paid")).toBeNull();
 fireEvent.press(view.getByRole("tab",{name:"History"}));expect(view.getByText("AED 5 paid")).toBeTruthy();expect(view.queryByText("Confirm received")).toBeNull();
 fireEvent.press(view.getByRole("tab",{name:"Receipts"}));expect(view.getByText("My receipt")).toBeTruthy();
});
test("country selector uses a native sheet and writes the selected code", () => {
  const view = render(
    <FieldInput
      busy={false}
      rtl={false}
      field={field("PROFILE_COUNTRY", {
        value: "AE",
        choices: [
          { value: "AE", label: "UAE +971" },
          { value: "EG", label: "Egypt +20" },
        ],
      })}
    />,
  );
  fireEvent.press(view.getByTestId("PROFILE_COUNTRY"));
  fireEvent.press(view.getByText("Egypt +20"));
  expect(native.update).toHaveBeenLastCalledWith("PROFILE_COUNTRY", "EG");
});
test("secret entry stays local and is never recovered from view snapshots", () => {
  const view = render(
    <FieldInput
      busy={false}
      rtl={false}
      field={field("PASSWORD", { secret: true, value: "not-for-display" })}
    />,
  );
  expect(view.getByTestId("PASSWORD").props.value).toBe("");
  fireEvent.changeText(view.getByTestId("PASSWORD"), "fixture-pass");
  expect(native.update).toHaveBeenLastCalledWith("PASSWORD", "fixture-pass");
});
test("long lists append rows when their scroll marker reaches the viewport", () => {
  jest.useFakeTimers();
  const ScrollArea=require("../src/components/ScrollArea").default;
  let markerTop=1000;
  const view=render(<ScrollArea testID="list-area"><PagedCards rtl={false} busy={false} cards={Array.from({length:18},(_,index) => card(String(index),`Person ${index}`))}/></ScrollArea>);
  measureScroll(view,() => markerTop);
  act(() => jest.advanceTimersByTime(32));
  expect(view.queryByText("Person 8")).toBeNull();
  expect(view.queryByText("Next")).toBeNull();
  markerTop=550;
  measureScroll(view,() => markerTop);
  fireEvent.scroll(view.getByTestId("list-area"),{nativeEvent:{contentOffset:{x:0,y:450},contentSize:{width:390,height:1500},layoutMeasurement:{width:390,height:600}}});
  act(() => jest.advanceTimersByTime(32));
  expect(view.getByText("Person 8")).toBeTruthy();
  expect(view.getByText("Person 0")).toBeTruthy();
  view.unmount(); jest.useRealTimers();
});

test("destructive actions require the native confirmation and busy actions cannot run", () => {
  const confirm = jest.spyOn(Alert, "alert").mockImplementation(() => {});
  const value = {
    ...action("Remove member", "REMOVE", "member"),
    destructive: true,
  };
  const view = render(<Button action={value} />);
  fireEvent.press(view.getByText("Remove member"));
  expect(native.dispatch).not.toHaveBeenCalled();
  const buttons = confirm.mock.calls[0][2]!;
  act(() => buttons[1].onPress!());
  expect(native.dispatch).toHaveBeenLastCalledWith("REMOVE", "member");
  view.rerender(<Button action={value} busy />);
  fireEvent.press(view.getByText("Remove member"));
  expect(confirm).toHaveBeenCalledTimes(1);
  confirm.mockRestore();
});
test("return from owner support remains accessible during a pending request", async () => {
  const value = snapshot({ supportActive: true });
  value.state.busy = true;
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("Return to my account");
  fireEvent.press(view.getByText("Return to my account"));
  expect(native.dispatch).toHaveBeenLastCalledWith("END_SUPPORT", "");
});
test("more menu exposes restaurants, quick wheel and notification preferences", async () => {
  const view = render(<App />);
  await view.findByText("Rooms");
  fireEvent.press(view.getByTestId("nav:MORE"));
  expect(view.getByText("Restaurants & menus")).toBeTruthy();
  expect(view.getByText("Quick pick")).toBeTruthy();
  fireEvent.press(view.getByText("Notification preferences"));
  expect(native.dispatch).toHaveBeenLastCalledWith(
    "OPEN_NOTIFICATION_PREFERENCES",
    "",
  );
});
test("native view trees contain no bare text children that crash Fabric rendering", async () => {
  const value = snapshot();
  value.topCards = [card("example", "A wallet")];
  value.state.walletHistoryPrompt = {
    title: "History",
    subtitle: "Wallet",
    balance: "AED 10",
    cards: [card("transaction", "Payment")],
    loading: false,
    error: "",
    hasMore: false,
  };
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("Rooms");
  function check(node: any, inText = false) {
    if (!node) return;
    if (Array.isArray(node)) return node.forEach((item) => check(item, inText));
    if (typeof node === "string" || typeof node === "number") {
      expect(inText).toBe(true);
      return;
    }
    const text = inText || ["Text", "RCTText", "SvgText"].includes(node.type);
    node.children?.forEach((item: any) => check(item, text));
  }
  check(view.toJSON());
});
test("order links work with Hermes and reject lookalike hosts and invalid codes", () => {
  expect(roomCodeFromLink("https://intrvioo.com/?room=123456")).toBe("123456");
  expect(roomCodeFromLink("foodrun://join?code=123456")).toBe("123456");
  expect(
    roomCodeFromLink("https://intrvioo.com.attacker.test/?room=123456"),
  ).toBeNull();
  expect(roomCodeFromLink("https://intrvioo.com/?room=%ZZ")).toBeNull();
  expect(roomCodeFromLink("foodrun://signin?code=123456")).toBeNull();
});

test("floating half offers disappear after a live acceptance and payments open their tab", async () => {
  const value=snapshot();value.state.page="ROOM";value.state.roomCode="123456";
  value.topCards=[{...card("half-item:offer","Chicken half"),buttons:[action("Take this half","ACCEPT_HALF_ITEM","offer")] }];
  value.sections=[{title:"All",collapsed:false,cards:[{...card("menu:item","Sandwich"),buttons:[action("Add food","OPEN_ITEM","item")]},{...card("transfer:pending","Pending payment"),buttons:[action("Confirm receipt","CONFIRM_TRANSFER","pending")]}]}];
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);
  await view.findByLabelText("Half available");
  fireEvent.press(view.getByLabelText("Half available"));
  expect(view.getByRole("header",{name:"Take another half"})).toBeTruthy();
  fireEvent.press(view.getAllByText("Take this half").at(-1)!);
  expect(native.dispatch).toHaveBeenLastCalledWith("ACCEPT_HALF_ITEM","offer");
  value.topCards=[];
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.queryByLabelText("Half available")).toBeNull();
  expect(view.queryByRole("header",{name:"Take another half"})).toBeNull();
  (native.dispatch as jest.Mock).mockClear();
  fireEvent.press(view.getByLabelText("Payment actions"));
  expect(view.getByLabelText("Payments").props.accessibilityState.selected).toBe(true);
  expect(view.getByText("Confirm receipt")).toBeTruthy();
  expect(native.dispatch).not.toHaveBeenCalled();
  fireEvent.press(view.getByTestId("room-tab:0"));
  value.sections[0].cards[1].buttons[0].enabled=false;
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.queryByLabelText("Payment actions")).toBeNull();
  fireEvent.press(view.getByLabelText("Choose food"));
  expect(view.getByText("Sandwich")).toBeTruthy();
});

test("confirmed orders change the floating shortcut to modify and edits restore confirmation", () => {
  const value=snapshot();value.state.page="ROOM";
  const menu={...card("menu:item","Sandwich"),buttons:[action("Add","QUICK_ADD_ITEM","item")]};
  value.topCards=[menu,card("cart:line","2 × Sandwich")];
  value.inlineButtons=[action("Submit my food order","SUBMIT_CART","revision",true)];
  const openTab=jest.fn();
  const view=render(<RoomAttention snapshot={value} selected={0} onOpenTab={openTab}/>);
  expect(view.getByLabelText("Choose food")).toBeTruthy();
  value.topCards.push(card("cart-submitted","Your food order is saved"));value.inlineButtons=[];
  view.rerender(<RoomAttention snapshot={value} selected={0} onOpenTab={openTab}/>);
  expect(view.queryByLabelText("Choose food")).toBeNull();
  fireEvent.press(view.getByLabelText("Modify order"));expect(openTab).toHaveBeenCalledWith(1);
  view.rerender(<RoomAttention snapshot={value} selected={1} onOpenTab={openTab}/>);
  expect(view.queryByLabelText("Modify order")).toBeNull();
  const dock=render(<RoomOrderDock snapshot={value}/>);
  expect(dock.queryByText("Confirm order")).toBeNull();
  fireEvent.press(dock.getByLabelText("Modify order"));
  expect(dock.getByRole("header",{name:"My order"})).toBeTruthy();
  fireEvent.press(dock.getByLabelText("Dismiss sheet"));
  value.topCards=value.topCards.filter(card=>card.id!=="cart-submitted");
  value.inlineButtons=[action("Submit my food order","SUBMIT_CART","revision",true)];
  dock.rerender(<RoomOrderDock snapshot={value}/>);
  expect(dock.queryByLabelText("Modify order")).toBeNull();
  fireEvent.press(dock.getByText("Confirm order"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SUBMIT_CART","revision");
  value.state.busy=true;dock.rerender(<RoomOrderDock snapshot={value}/>);
  (native.dispatch as jest.Mock).mockClear();fireEvent.press(dock.getByText("Confirm order"));expect(native.dispatch).not.toHaveBeenCalled();
});

test("room settings explains permitted closure actions and cancellation requires confirmation", () => {
  const value=snapshot();value.state.page="ROOM";
  const cancel=action("Cancel today's order","CANCEL");
  value.utilityButtons=[cancel];
  const exit=jest.fn(), alert=jest.spyOn(Alert,"alert").mockImplementation(()=>{});
  const view=render(<RoomSettings snapshot={value} cards={[]} fields={[]} actions={[cancel]} onExit={exit}/>);
  expect(view.queryByText("Delete room in administration")).toBeNull();
  expect(view.getByText("Cancel this order for everyone. The room stays available for a new order.")).toBeTruthy();
  expect(view.queryByText("Complete and archive this order")).toBeNull();
  expect(view.queryByText("Start a new order in this room")).toBeNull();
  fireEvent.press(view.getByText("Cancel the current order"));
  expect(native.dispatch).not.toHaveBeenCalled();
  const choices=alert.mock.calls.at(-1)![2]!;
  expect(choices[0].style).toBe("cancel");choices[1].onPress!();
  expect(native.dispatch).toHaveBeenLastCalledWith("CANCEL","");
  value.utilityButtons=[];value.primaryAction={...action("Complete this order","ARCHIVE"),enabled:false};
  view.rerender(<RoomSettings snapshot={value} cards={[]} fields={[]} actions={[]} onExit={exit}/>);
  expect(view.queryByText("Cancel the current order")).toBeNull();
  (native.dispatch as jest.Mock).mockClear();fireEvent.press(view.getByText("Complete and archive this order"));expect(native.dispatch).not.toHaveBeenCalled();
  value.primaryAction=action("Start a new order","NEXT_ORDER");
  view.rerender(<RoomSettings snapshot={value} cards={[]} fields={[]} actions={[]} onExit={exit}/>);
  fireEvent.press(view.getByText("Start a new order in this room"));expect(native.dispatch).toHaveBeenLastCalledWith("NEXT_ORDER","");
  fireEvent.press(view.getByText("Exit room"));expect(exit).toHaveBeenCalledTimes(1);
  value.adminAvailable=true;
  view.rerender(<RoomSettings snapshot={value} cards={[]} fields={[]} actions={[]} onExit={exit}/>);
  fireEvent.press(view.getByText("Delete room in administration"));
  expect(native.dispatch).toHaveBeenNthCalledWith(2,"ADMIN_TAB","rooms");
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ADMIN","");
  value.state.rtl=true;view.rerender(<RoomSettings snapshot={value} cards={[]} fields={[]} actions={[]} onExit={exit}/>);
  expect(view.getByText("بدء طلب جديد في الغرفة")).toBeTruthy();
  alert.mockRestore();
});
test("large selection sheets search choices and dismiss without changing a value",()=>{
  const view=render(<FieldInput busy={false} rtl={false} field={field("GROUP",{value:"2",choices:Array.from({length:10},(_,i)=>({value:String(i),label:`Group ${i}`}))})}/>);
  fireEvent.press(view.getByTestId("GROUP"));
  expect(view.getByRole("radio",{name:"Group 2"}).props.accessibilityState.selected).toBe(true);
  fireEvent.changeText(view.getByLabelText("Search options"),"Group 8");
  expect(view.queryByRole("radio",{name:"Group 1"})).toBeNull();
  fireEvent.press(view.getByRole("radio",{name:"Group 8"}));
  expect(native.update).toHaveBeenLastCalledWith("GROUP","8");
  (native.update as jest.Mock).mockClear();
  fireEvent.press(view.getByTestId("GROUP"));
  fireEvent.press(view.getByLabelText("Dismiss sheet"));
  expect(native.update).not.toHaveBeenCalled();
});

test("food options use a selection sheet and return to the same room tab",async()=>{
  const value=snapshot();value.state.page="ROOM";value.state.roomCode="123456";
  value.sections=[{title:"Food",collapsed:false,cards:[{...card("menu:food","Sandwich"),buttons:[action("Choose extras","OPEN_ITEM","food")]}]}];
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);await view.findByTestId("room-tab:1");fireEvent.press(view.getByTestId("room-tab:1"));
  fireEvent.press(view.getByLabelText("Choose extras"));expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ITEM","food");
  value.state.page="ITEM";value.state.title="Sandwich options";
  value.sections=[{title:"Size",collapsed:false,cards:[{...card("variant:small","Small"),badge:"Selected",buttons:[action("Choose size","SELECT_VARIANT","small")]},{...card("variant:large","Large"),buttons:[action("Choose size","SELECT_VARIANT","large")]}]}];
  value.inlineButtons=[action("Extra cheese","TOGGLE_OPTION","cheese")];value.primaryAction=action("Add to my order","ADD_CART_ITEM","",true);
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByRole("header",{name:"Sandwich options"})).toBeTruthy();
  fireEvent.press(view.getByRole("radio",{name:"Large · "}));expect(native.dispatch).toHaveBeenLastCalledWith("SELECT_VARIANT","large");
  fireEvent.press(view.getByRole("checkbox",{name:"Extra cheese"}));expect(native.dispatch).toHaveBeenLastCalledWith("TOGGLE_OPTION","cheese");
  fireEvent.press(view.getByText("Add to my order"));expect(native.dispatch).toHaveBeenLastCalledWith("ADD_CART_ITEM","");
  value.state.page="ROOM";value.state.title="Room";value.sections=[];value.inlineButtons=[];value.primaryAction=undefined;
  act(()=>(events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(value)));
  expect(view.getByTestId("room-tab:1").props.accessibilityState.selected).toBe(true);
});

test("joined group details stay visible in a sheet and leave requires confirmation", () => {
  const FriendsScreen = require("../src/screens/FriendsScreen").default;
  const group = {...card("friend-joined:alice:lunch","Lunch friends"),buttons:[action("View group","FRIENDS_ACTION","view|alice|lunch")]};
  const value = snapshot({topCards:[group]}); value.state.page="FRIENDS";
  const view=render(<FriendsScreen snapshot={value}/>);
  expect(view.getByRole("header",{name:"Groups"})).toBeTruthy();
  fireEvent.press(view.getByRole("tab",{name:"Joined groups"}));
  fireEvent.press(view.getByText("View group"));
  expect(native.dispatch).toHaveBeenLastCalledWith("FRIENDS_ACTION","view|alice|lunch");
  const leave={...action("Leave group","FRIENDS_ACTION","leave|alice|lunch"),destructive:true};
  const details={...value,topCards:[card("friend-view","Lunch friends"),{...card("friend-view-member:bob","Bob"),detail:"bob@example.test"}],inlineButtons:[leave,action("Back to groups","FRIENDS_ACTION","open")]};
  const alert=jest.spyOn(Alert,"alert").mockImplementation(()=>{});
  view.rerender(<FriendsScreen snapshot={details}/>);
  expect(view.getByText("bob@example.test")).toBeTruthy();
  fireEvent.press(view.getByText("Leave group"));
  expect(native.dispatch).not.toHaveBeenCalledWith("FRIENDS_ACTION","leave|alice|lunch");
  const choices=alert.mock.calls.at(-1)?.[2]; act(()=>choices?.[1].onPress?.());
  expect(native.dispatch).toHaveBeenLastCalledWith("FRIENDS_ACTION","leave|alice|lunch");
  view.rerender(<FriendsScreen snapshot={{...details,state:{...details.state,error:"Connection failed"}}}/>);
  expect(view.getByText("Connection failed")).toBeTruthy();
  view.rerender(<FriendsScreen snapshot={{...value,topCards:[]}}/>);
  expect(view.queryByText("bob@example.test")).toBeNull();
  expect(view.getByRole("tab",{name:"Joined groups"}).props.accessibilityState.selected).toBe(true);
  alert.mockRestore();
});

test("compact room members open actions in a sheet and preserve full names", () => {
  const RoomMembers=require("../src/components/RoomMembers").default;
  const member={...card("member:long","Mohamed Fathy"),buttons:[{...action("Remove from room","REMOVE","long"),destructive:true}]};
  const view=render(<RoomMembers cards={[member]} busy={false} rtl={false}/>);
  expect(view.queryByText("Remove from room")).toBeNull();
  fireEvent.press(view.getByTestId("compact:member:long"));
  expect(view.getByText("Remove from room")).toBeTruthy();
  view.rerender(<RoomMembers cards={[]} busy={false} rtl={false}/>);
  expect(view.queryByText("Remove from room")).toBeNull();
});

test("live selection overlays a profile screen and uses the shared winner", async () => {
  const value=snapshot(), now=Date.now(); value.state.page="PROFILE";
  value.state.wheel={roomId:"lunch",roomName:"Team lunch",names:["Alice","Bob"],winner:"Bob",style:"names",serverOffset:0,round:{id:"round-1",memberIds:["alice","bob"],winnerId:"bob",weights:[1,1],startAt:now-2000,duration:1000,turns:5,landingOffset:.5,seed:1}};
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);
  expect(await view.findByText("We have our person!")).toBeTruthy();
  expect(view.getAllByText("Bob").length).toBeGreaterThan(0);
  fireEvent.press(view.getByText("Continue"));
  expect(view.queryByText("We have our person!")).toBeNull();
  expect(native.dispatch).not.toHaveBeenCalledWith("SELECT_PAYER",expect.anything());
});

test("order-sent sheet confirms through the native action and keeps stale copies disabled", async () => {
  const value=snapshot();value.state.page="ROOM";value.state.roomCode="123456";
  value.state.restaurantSendPrompt={canConfirm:true,blocker:""};
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);
  fireEvent.press(await view.findByText("Order sent · lock food edits"));
  expect(native.dispatch).toHaveBeenLastCalledWith("CONFIRM_RESTAURANT_SEND","");
  const stale={...value,state:{...value.state,restaurantSendPrompt:{canConfirm:false,blocker:"The order changed. Copy the latest list."}}};
  act(()=> (events.addListener as jest.Mock).mock.calls[0][1](JSON.stringify(stale)));
  expect(view.getByTestId("action:CONFIRM_RESTAURANT_SEND:").props.accessibilityState.disabled).toBe(true);
  expect(view.getByText("The order changed. Copy the latest list.")).toBeTruthy();
  fireEvent.press(view.getByText("Not sent yet"));
  expect(native.dispatch).toHaveBeenLastCalledWith("DISMISS_RESTAURANT_SEND","");
});

test("room timeline, member status and joined count remain visible across tabs", async () => {
  const value=snapshot(); value.state={...value.state,page:"ROOM",roomCode:"123456",roomPhase:"COLLECTING",roomStage:"Choose food",joinedCount:2};
  value.sections=[{title:"Members",collapsed:false,cards:[{...card("member:a","Alice"),badge:"Selected to order",detail:"Organizer · Connected"},{...card("member:b","Bob"),badge:"Food submitted",detail:"Away"}]}];
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view=render(<App/>);
  expect(await view.findByText("Members (2)")).toBeTruthy();
  fireEvent.press(view.getByTestId("room-tab:3"));
  expect(view.getByText("Choose food")).toBeTruthy();
  expect(view.getByText("Food submitted")).toBeTruthy();
  expect(view.getByText("Organizer · Connected")).toBeTruthy();
});

test("shared countdown advances 1, 2, 3 and stays blocking until the result", () => {
  jest.useFakeTimers();jest.setSystemTime(10000);
  const Overlay=require("../src/components/LiveSelectionOverlay").default;
  const wheel={roomId:"lunch",roomName:"Team lunch",names:["Alice","Bob"],winner:"Bob",style:"names",serverOffset:0,round:{id:"round-count",memberIds:["alice","bob"],winnerId:"bob",weights:[1,1],startAt:13000,duration:2000,turns:5,landingOffset:.5,seed:1}};
  const visibility=jest.fn(), view=render(<Overlay wheel={wheel} rtl={false} onVisibility={visibility}/>);
  act(() => jest.advanceTimersByTime(500));
  expect(view.getByText("1")).toBeTruthy();
  expect(view.queryByText("Continue")).toBeNull();
  act(() => jest.advanceTimersByTime(500));expect(view.getByText("2")).toBeTruthy();
  act(() => jest.advanceTimersByTime(1000));expect(view.getByText("3")).toBeTruthy();
  act(() => jest.advanceTimersByTime(1000));expect(view.queryByTestId("selection-countdown")).toBeNull();
  expect(view.queryByText("Continue")).toBeNull();
  act(() => jest.advanceTimersByTime(2000));expect(view.getByText("We have our person!")).toBeTruthy();
  expect(view.getByText("Continue")).toBeTruthy();
  view.unmount();jest.useRealTimers();
});

test('sign-in shows only credentials and registration adds only a name',()=>{
 const value=snapshot({authenticated:false,mainFields:[field('EMAIL',{value:'user@example.test'}),field('PASSWORD',{value:'',secret:true}),field('NAME',{value:'User'}),field('PROFILE_PHONE'),field('AANI',{toggle:true}),field('ACCOUNT_IDENTIFIER')],primaryAction:action('Continue with Google','GOOGLE_SIGN_IN','',true),inlineButtons:[action('Sign in','SIGN_IN'),action('Create account','REGISTER'),action('Reset password','RESET_PASSWORD')]});
 const view=render(<AuthScreen snapshot={value}/>);
 expect(view.getByTestId('EMAIL')).toBeTruthy();expect(view.getByTestId('PASSWORD')).toBeTruthy();
 expect(view.queryByTestId('NAME')).toBeNull();expect(view.queryByTestId('PROFILE_PHONE')).toBeNull();expect(view.queryByTestId('ACCOUNT_IDENTIFIER')).toBeNull();
 expect(view.getByTestId('action:SIGN_IN:').props.accessibilityState.disabled).toBe(true);
 fireEvent.changeText(view.getByTestId('PASSWORD'),'fixture-password');
 fireEvent.press(view.getByText('Sign in'));expect(native.dispatch).toHaveBeenLastCalledWith('SIGN_IN','');
 fireEvent.press(view.getByText('New here? Create account'));expect(view.getByTestId('NAME')).toBeTruthy();
 expect(view.queryByTestId('PROFILE_PHONE')).toBeNull();expect(view.queryByTestId('ACCOUNT_IDENTIFIER')).toBeNull();
 fireEvent.press(view.getAllByText('Create account').at(-1)!);expect(native.dispatch).toHaveBeenLastCalledWith('REGISTER','');
 fireEvent.press(view.getByText('Already have an account? Sign in'));expect(view.queryByTestId('NAME')).toBeNull();
});
test('password sign-in remains enabled when secure snapshots hide the password',()=>{
 const value=snapshot({authenticated:false,mainFields:[field('EMAIL',{value:'user@example.test'}),field('PASSWORD',{secret:true})],inlineButtons:[action('Sign in','SIGN_IN')]});
 const view=render(<AuthScreen snapshot={value}/>);
 fireEvent.changeText(view.getByTestId('PASSWORD'),'fixture-password');
 view.rerender(<AuthScreen snapshot={{...value,mainFields:value.mainFields.map(f=>({...f,value:f.key==='PASSWORD' ? '' : f.value}))}}/>);
 expect(view.getByTestId('action:SIGN_IN:').props.accessibilityState.disabled).toBe(false);
 fireEvent.press(view.getByText('Sign in'));expect(native.dispatch).toHaveBeenLastCalledWith('SIGN_IN','');
 fireEvent.changeText(view.getByTestId('PASSWORD'),'');
 expect(view.getByTestId('action:SIGN_IN:').props.accessibilityState.disabled).toBe(true);
});
test('a profile name in the login email field gets clear guidance',()=>{
 const value=snapshot({authenticated:false,mainFields:[field('EMAIL',{value:'user-name'}),field('PASSWORD',{secret:true})],inlineButtons:[action('Sign in','SIGN_IN')]});
 const view=render(<AuthScreen snapshot={value}/>);fireEvent.changeText(view.getByTestId('PASSWORD'),'fixture-password');
 fireEvent.press(view.getByText('Sign in'));
 expect(view.getByText('Use the email you registered with. Your profile name is not your login email.')).toBeTruthy();
 expect(native.dispatch).not.toHaveBeenCalledWith('SIGN_IN','');
});

test.each(['ios','android'] as const)('native %s tab styling keeps all destinations accessible',platform=>{
 const previous=Platform.OS;Object.defineProperty(Platform,'OS',{value:platform,configurable:true});
 try {
  const select=jest.fn();const tabs=['HOME','ROOMS','WALLET','PROFILE','MORE'].map((id,index)=>({id,icon:'home',label:id,selected:index===0}));
  const view=render(<NativeTabBar tabs={tabs} rtl={false} busy={false} onSelect={select}/>);
  expect(view.getByTestId(`native-tabs:${platform}`)).toBeTruthy();expect(view.getByRole('tab',{name:'HOME'}).props.accessibilityState.selected).toBe(true);
  for(const tab of tabs){fireEvent.press(view.getByTestId(`nav:${tab.id}`));expect(select).toHaveBeenLastCalledWith(tab.id);}
  if(platform==='ios') {
   expect(within(view.getByTestId('ios-tab-dock')).queryByTestId('nav:MORE')).toBeNull();
   expect(within(view.getByTestId('ios-more-bubble')).getByTestId('nav:MORE')).toBeTruthy();
   view.rerender(<NativeTabBar tabs={tabs.map(tab=>({...tab,selected:tab.id==='MORE',label:tab.id==='MORE'?'المزيد':tab.label}))} rtl busy={false} onSelect={select}/>);
   expect(view.getByRole('tab',{name:'المزيد'}).props.accessibilityState.selected).toBe(true);
   fireEvent.press(view.getByRole('tab',{name:'المزيد'}));expect(select).toHaveBeenLastCalledWith('MORE');
  } else expect(view.queryByTestId('ios-more-bubble')).toBeNull();
 }finally{Object.defineProperty(Platform,'OS',{value:previous,configurable:true});}
});
