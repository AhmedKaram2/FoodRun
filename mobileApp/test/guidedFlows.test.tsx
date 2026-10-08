import React from "react";
import { act, fireEvent, render, within } from "@testing-library/react-native";
import App from "../App";
import { native, events } from "../src/native";
import { action } from "../src/components/Controls";
import type { Card, Field, Snapshot } from "../src/types";

jest.mock("../src/native", () => ({
  native: {getSnapshot:jest.fn(),dispatch:jest.fn(),update:jest.fn(),tick:jest.fn(),photo:jest.fn(),dismissFeedback:jest.fn()},
  events: {addListener:jest.fn(() => ({remove:jest.fn()}))}, parseSnapshot:JSON.parse,
  dispatch:(a:string,v="") => require("../src/native").native.dispatch(a,v),
  update:(...args:unknown[]) => require("../src/native").native.update(...args), quickAction:jest.fn(),
}));
const field = (key:string,value="",more:Partial<Field>={}):Field => ({key,label:key,value,choices:[],toggle:false,multiline:false,secret:false,...more});
const card = (id:string,title=id,more:Partial<Card>={}):Card => ({id,title,detail:"",badge:"",image:"",buttons:[],...more});
const base = ():Snapshot => ({
  supportActive:false,accountId:"me",authenticated:true,
  state:{page:"SETUP",title:"Create your room",subtitle:"",busy:false,online:true,status:"",error:"",roomCode:"",canGoBack:true,progressStep:-1,inviteLink:"",inviteSummary:"",rtl:false,accessBlocked:false,canOverrideSelection:false,hasJoinTimer:false},
  mainFields:[field("NAME","My name"),field("ROOM_NAME","Lunch"),field("FRIEND_GROUP_CHOICE","",{choices:[{value:"",label:"No group"},{value:"friends",label:"My friends"}]}),field("JOIN_TIMER","false",{toggle:true}),field("SELECTION_STYLE","wheel",{choices:[{value:"wheel",label:"Wheel"}]}),field("RESTAURANT_POLL","false",{toggle:true}),field("DELIVERY","false",{toggle:true})],
  extraFields:[field("SERVICE_FEE","0.00"),field("DISCOUNT","0.00"),field("EXPECTED_NAMES")],
  topCards:[card("restaurant","Mama'esh · Ajman",{detail:"Open menu"})],sections:[],
  primaryAction:action("Create room","CREATE_ROOM","",true),inlineButtons:[action("Choose restaurant","OPEN_LIBRARY")],utilityButtons:[],
  quick:{people:[],history:[],winner:"",spinning:false,canSpin:false,startRotation:0,endRotation:0,destination:"MAIN",nameDraft:"",error:"",revision:0},
});
let current:Snapshot;
const emit = (s:Snapshot) => {current=s;act(() => (events.addListener as jest.Mock).mock.calls.find(([name]) => name === "FoodRunState")[1](JSON.stringify(s)));};
const next = (view:ReturnType<typeof render>) => fireEvent.press(view.getByTestId("action:FLOW_NEXT:"));
beforeEach(() => {
  jest.clearAllMocks();current=base();
  (native.getSnapshot as jest.Mock).mockImplementation(async() => JSON.stringify(current));
  (native.update as jest.Mock).mockImplementation((key,value) => {
    const s={...current,mainFields:current.mainFields.map(f=>f.key===key?{...f,value}:f),extraFields:current.extraFields.map(f=>f.key===key?{...f,value}:f)};
    emit(s);
  });
});

test("room creation uses short steps, keeps drafts during live updates and creates only after review",async() => {
  const view=render(<App/>);await view.findByText("Choose the food");
  expect(view.queryByTestId("ROOM_NAME")).toBeNull();
  expect(view.queryByTestId("action:CREATE_ROOM:")).toBeNull();
  expect(view.getByTestId("flow-step:3").props.accessibilityState.disabled).toBe(true);
  next(view);
  fireEvent.changeText(view.getByTestId("ROOM_NAME"),"Friday breakfast");
  emit({...current,state:{...current.state,rtl:true}});
  expect(view.getByText("الخطوة 2 من 4")).toBeTruthy();
  expect(view.getByTestId("ROOM_NAME").props.value).toBe("Friday breakfast");
  next(view);expect(view.queryByTestId("ROOM_NAME")).toBeNull();
  next(view);expect(native.dispatch).not.toHaveBeenCalledWith("CREATE_ROOM","");
  fireEvent.press(view.getByTestId("review-edit:1"));
  expect(view.getByTestId("ROOM_NAME").props.value).toBe("Friday breakfast");
  next(view);next(view);
  fireEvent.press(view.getByTestId("action:CREATE_ROOM:"));
  fireEvent.press(view.getByTestId("action:CREATE_ROOM:"));
  expect((native.dispatch as jest.Mock).mock.calls.filter(([a])=>a==="CREATE_ROOM")).toHaveLength(1);
});

test("missing restaurant and invalid timer stay on their step; busy blocks navigation",async() => {
  current.mainFields.push(field("RESTAURANT_NAME"));
  const view=render(<App/>);await view.findByText("Choose the food");next(view);
  expect(view.getByText("Choose a restaurant or use an open menu first.")).toBeTruthy();
  emit({...current,mainFields:current.mainFields.filter(f=>f.key!=="RESTAURANT_NAME")});next(view);
  emit({...current,mainFields:[...current.mainFields.map(f=>f.key==="JOIN_TIMER"?{...f,value:"true"}:f),field("JOIN_TIMER_MINUTES","0")]});next(view);
  expect(view.getByText("Choose a join time from 1 to 1440 minutes.")).toBeTruthy();
  emit({...current,state:{...current.state,busy:true}});
  expect(view.getByTestId("action:FLOW_NEXT:").props.accessibilityState.disabled).toBe(true);
  expect(native.dispatch).not.toHaveBeenCalledWith("CREATE_ROOM","");
});

test("restaurant sheet searches compact rows and preserves the room step after selection or dismissal",async() => {
  const draft=base();
  (native.dispatch as jest.Mock).mockImplementation((a,value) => {
    if(a==="OPEN_LIBRARY") emit({...draft,state:{...draft.state,page:"LIBRARY"},mainFields:[field("RESTAURANT_SEARCH")],extraFields:[],primaryAction:null,inlineButtons:[action("Add restaurant","NEW_RESTAURANT")],topCards:[card("restaurant:ajman","Mama'esh · Ajman",{buttons:[action("Use","SELECT_RESTAURANT","ajman"),action("Delete","DELETE_RESTAURANT","ajman")]}),card("restaurant:sharjah","Mama'esh · Sharjah",{buttons:[action("Use","SELECT_RESTAURANT","sharjah") ]})]});
    if(a==="SELECT_RESTAURANT") emit({...draft,topCards:[card("restaurant",`Chosen ${value}`)]});
    if(a==="BACK") emit(draft);
  });
  const view=render(<App/>);await view.findByText("Choose the food");
  fireEvent.press(view.getByTestId("choose-restaurant"));
  expect(view.getByTestId("restaurant-results")).toBeTruthy();
  expect(view.queryByText("Delete")).toBeNull();
  fireEvent.changeText(view.getByTestId("RESTAURANT_SEARCH"),"Sharjah");
  expect(native.update).toHaveBeenCalledWith("RESTAURANT_SEARCH","Sharjah");
  fireEvent.press(view.getByTestId("pick:restaurant:sharjah"));
  expect(view.getByText("Chosen sharjah")).toBeTruthy();
  next(view);next(view);next(view);fireEvent.press(view.getByTestId("review-edit:0"));
  fireEvent.press(view.getByTestId("choose-restaurant"));fireEvent.press(view.getByLabelText("Close"));
  expect(view.getByText("Step 1 of 4")).toBeTruthy();
  expect(view.queryByTestId("restaurant-results")).toBeNull();
});

test("adding a restaurant and visiting its menu keeps the room draft and restaurant progress",async() => {
  const draft=base();draft.mainFields.find(f=>f.key==="ROOM_NAME")!.value="My breakfast";
  let library:Snapshot,restaurant:Snapshot;
  (native.dispatch as jest.Mock).mockImplementation(a => {
    if(a==="OPEN_LIBRARY") {library={...draft,state:{...draft.state,page:"LIBRARY"},mainFields:[field("RESTAURANT_SEARCH")],primaryAction:null,inlineButtons:[action("Add restaurant","NEW_RESTAURANT")],topCards:[]};emit(library);}
    if(a==="NEW_RESTAURANT") {restaurant={...draft,state:{...draft.state,page:"RESTAURANT"},mainFields:[field("RESTAURANT_NAME","New cafe"),field("BRANCH"),field("CURRENCY","AED")],extraFields:[],topCards:[],primaryAction:action("Save restaurant","SAVE_RESTAURANT","",true),inlineButtons:[action("Edit menu","MENU_OPEN")]};emit(restaurant);}
    if(a==="MENU_OPEN") emit({...restaurant,state:{...restaurant.state,page:"MENU_EDITOR"},mainFields:[field("MENU_ITEM_NAME"),field("MENU_ITEM_PRICE")],primaryAction:action("Add item","ADD_MENU_ITEM","",true),inlineButtons:[]});
    if(a==="BACK") emit(restaurant);
    if(a==="SAVE_RESTAURANT") emit({...library,topCards:[card("restaurant:new","New cafe",{buttons:[action("Use","SELECT_RESTAURANT","new")]})]});
    if(a==="SELECT_RESTAURANT") emit({...draft,topCards:[card("restaurant","New cafe")]});
  });
  const view=render(<App/>);await view.findByText("Choose the food");fireEvent.press(view.getByTestId("choose-restaurant"));fireEvent.press(view.getByText("Add restaurant"));
  await view.findByText("The essentials");next(view);next(view);next(view);
  fireEvent.press(view.getByText("Edit menu"));expect(view.getByTestId("MENU_ITEM_NAME")).toBeTruthy();
  fireEvent.press(view.getByLabelText("Back"));expect(view.getByText("Step 4 of 5")).toBeTruthy();
  next(view);fireEvent.press(view.getByTestId("action:SAVE_RESTAURANT:"));
  fireEvent.press(view.getByTestId("pick:restaurant:new"));expect(view.getByText("Step 1 of 4")).toBeTruthy();
  next(view);expect(view.getByTestId("ROOM_NAME").props.value).toBe("My breakfast");
});

test("poll sheet keeps multi-selection and uses the controller's disabled confirmation",async() => {
  const draft=base();draft.mainFields=draft.mainFields.map(f=>f.key==="RESTAURANT_POLL"?{...f,value:"true"}:f);draft.inlineButtons=[action("Choose poll restaurants","OPEN_POLL_RESTAURANTS")];draft.topCards=[card("poll-choices","1 restaurants in the poll")];current=draft;
  (native.dispatch as jest.Mock).mockImplementation(a => {
    if(a==="OPEN_POLL_RESTAURANTS") emit({...draft,state:{...draft.state,page:"LIBRARY"},mainFields:[field("RESTAURANT_SEARCH")],primaryAction:{...action("Use selected restaurants","CONFIRM_POLL_RESTAURANTS","",true),enabled:false},inlineButtons:[],topCards:[card("restaurant:a","Cafe A",{buttons:[action("✓ Selected · remove","TOGGLE_POLL_RESTAURANT","a")]}),card("restaurant:b","Cafe B",{buttons:[action("Add to poll","TOGGLE_POLL_RESTAURANT","b")]})]});
  });
  const view=render(<App/>);await view.findByText("Choose the food");next(view);expect(view.getByText("Choose at least two restaurants for the poll.")).toBeTruthy();
  fireEvent.press(view.getByTestId("choose-restaurant"));
  expect(view.getByTestId("pick:restaurant:a").props.accessibilityState.checked).toBe(true);
  expect(view.getByTestId("action:CONFIRM_POLL_RESTAURANTS:").props.accessibilityState.disabled).toBe(true);
  fireEvent.press(view.getByTestId("pick:restaurant:b"));expect(native.dispatch).toHaveBeenLastCalledWith("TOGGLE_POLL_RESTAURANT","b");
});

test("payment room shows searchable people in a sheet, checks totals and returns from share editing",async() => {
  current={...base(),state:{...base().state,page:"PAYMENT_ROOM"},mainFields:[field("PAYMENT_ROOM_NAME","Lunch"),field("PAYMENT_CURRENCY","AED"),field("PAYMENT_TOTAL","30"),field("PAYMENT_DETAILS","Lunch receipt")],extraFields:[],topCards:[card("payment-shares-total","Assigned shares",{detail:"AED 20.00"}),card("share:me","Me",{detail:"My share\nAED 10.00",buttons:[action("Edit share","EDIT_PAYMENT_SHARE","me")]}),card("share:friend","My friend",{detail:"Food\nAED 10.00",buttons:[action("Edit share","EDIT_PAYMENT_SHARE","friend"),action("Remove","REMOVE_PAYMENT_SHARE","friend")]}),card("share:other","Someone else",{buttons:[action("Add person","EDIT_PAYMENT_SHARE","other")]})],primaryAction:action("Create payment room","SAVE_PAYMENT_ROOM","",true),inlineButtons:[]};
  const draft=current;
  (native.dispatch as jest.Mock).mockImplementation(a => {if(a==="EDIT_PAYMENT_SHARE")emit({...draft,state:{...draft.state,page:"PAYMENT_SHARE"},mainFields:[field("PAYMENT_DESCRIPTION","Lunch"),field("PAYMENT_SHARE","20")],topCards:[],primaryAction:action("Save share","SAVE_PAYMENT_SHARE","",true)});if(a==="SAVE_PAYMENT_SHARE")emit({...draft,topCards:draft.topCards.map(c=>c.id==="payment-shares-total"?{...c,detail:"AED 30.00"}:c)});});
  const view=render(<App/>);await view.findByText("The receipt");next(view);
  expect(view.queryByText("Someone else")).toBeNull();next(view);expect(view.getByText("Adjust the shares to match the receipt total.")).toBeTruthy();
  fireEvent.press(view.getByText("Choose people & shares"));fireEvent.changeText(view.getByTestId("payment-people-search"),"My friend");
  expect(view.queryByText("Someone else")).toBeNull();fireEvent.press(within(view.getByTestId("payment-people-results")).getByTestId("share-edit:share:friend"));
  expect(view.getByTestId("PAYMENT_SHARE")).toBeTruthy();fireEvent.press(view.getByText("Save share"));
  expect(view.getByText("Step 2 of 4")).toBeTruthy();next(view);next(view);
  expect(native.dispatch).not.toHaveBeenCalledWith("SAVE_PAYMENT_ROOM","");fireEvent.press(view.getByText("Create payment room"));expect(native.dispatch).toHaveBeenLastCalledWith("SAVE_PAYMENT_ROOM","");
});

test("short joining stays simple and a fresh room starts on the first step",async() => {
  const view=render(<App/>);await view.findByText("Choose the food");next(view);
  emit({...base(),state:{...base().state,page:"HOME"},primaryAction:action("Create","CREATE","",true)});
  emit(base());expect(view.getByText("Step 1 of 4")).toBeTruthy();
  emit({...base(),mainFields:[field("NAME","Me"),field("ROOM_CODE","123456")],primaryAction:action("Join","JOIN_ROOM","",true)});
  expect(view.getByTestId("ROOM_CODE")).toBeTruthy();expect(view.queryByTestId("flow-step:0")).toBeNull();
});

test("toggling the restaurant poll opens the sheet even when native navigation comes from a field update",async() => {
  const draft=base();
  (native.update as jest.Mock).mockImplementation((key,value) => {
    if(key==="RESTAURANT_POLL") emit({...draft,state:{...draft.state,page:"LIBRARY"},mainFields:[field("RESTAURANT_SEARCH")],primaryAction:{...action("Use selected restaurants","CONFIRM_POLL_RESTAURANTS","",true),enabled:false},inlineButtons:[],topCards:[card("restaurant:a","Cafe A",{buttons:[action("Add to poll","TOGGLE_POLL_RESTAURANT","a")]})]});
  });
  const view=render(<App/>);await view.findByText("Choose the food");
  fireEvent(view.getAllByRole("switch")[0],"valueChange",true);
  expect(view.getByTestId("restaurant-results")).toBeTruthy();
  expect(view.getByTestId("RESTAURANT_SEARCH").props.placeholder).toContain("Name, branch");
});

test("a saved creation failure retains review and retries the original request without a second create",async() => {
  const view=render(<App/>);await view.findByText("Choose the food");next(view);next(view);next(view);
  fireEvent.press(view.getByTestId("action:CREATE_ROOM:"));
  emit({...current,state:{...current.state,error:"Network unavailable. Retry your saved request."},primaryAction:action("Retry saved request","RETRY","",true),inlineButtons:[...current.inlineButtons,action("Create room","CREATE_ROOM","",true)]});
  expect(view.getByText("Step 4 of 4")).toBeTruthy();
  expect(view.queryByTestId("action:CREATE_ROOM:")).toBeNull();
  fireEvent.press(view.getByTestId("action:RETRY:"));
  expect(native.dispatch).toHaveBeenLastCalledWith("RETRY","");
  expect((native.dispatch as jest.Mock).mock.calls.filter(([a])=>a==="CREATE_ROOM")).toHaveLength(1);
});

test("connection fallback and cancel preserve the room step and draft",async() => {
  const view=render(<App/>);await view.findByText("Choose the food");next(view);
  fireEvent.changeText(view.getByTestId("ROOM_NAME"),"My breakfast");next(view);
  const draft={...current,state:{...current.state,error:"Network unavailable"},inlineButtons:[...current.inlineButtons,action("Connection options","OPEN_CONNECTION_OPTIONS")]};
  (native.dispatch as jest.Mock).mockImplementation(a=>{if(a==="OPEN_CONNECTION_OPTIONS")emit({...draft,state:{...draft.state,page:"CONNECT"},mainFields:[field("HUB_URL","https://example.test")],extraFields:[],topCards:[],primaryAction:action("Use internet room","USE_INTERNET","",true),inlineButtons:[]});if(a==="BACK")emit(draft);});
  emit(draft);fireEvent.press(view.getByText("Connection options"));
  fireEvent.press(view.getByLabelText("Back"));expect(view.getByText("Step 3 of 4")).toBeTruthy();
  fireEvent.press(view.getByTestId("action:FLOW_BACK:"));expect(view.getByTestId("ROOM_NAME").props.value).toBe("My breakfast");
  expect(native.dispatch).not.toHaveBeenCalledWith("CREATE_ROOM","");
});
