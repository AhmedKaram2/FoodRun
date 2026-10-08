import React from "react";
import { fireEvent, render, waitFor, act } from "@testing-library/react-native";
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
beforeEach(() => {
  jest.clearAllMocks();
  (native.getSnapshot as jest.Mock).mockResolvedValue(
    JSON.stringify(snapshot()),
  );
});
test("bottom navigation shows Home Rooms Wallet More with sign-in inside the menu", async () => {
  const view = render(<App />);
  await view.findByText("Food is better");
  fireEvent.press(view.getByLabelText("FoodRun Home"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_HOME", "");
  fireEvent.press(view.getByText("Rooms"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_ROOMS", "");
  fireEvent.press(view.getByTestId("nav:OPEN_WALLET"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_WALLET", "");
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
  fireEvent.press(view.getByText("EN"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SET_LANGUAGE", "en");
});
test("home highlights keep working create, join, menus and profile actions", async () => {
  const value = snapshot({profile:{name:"My name",photo:"https://example.test/photo.jpg"}});
  (native.getSnapshot as jest.Mock).mockResolvedValue(JSON.stringify(value));
  const view = render(<App />);
  await view.findByText("Food is better");
  fireEvent.press(view.getByText("Create a room"));
  expect(native.dispatch).toHaveBeenLastCalledWith("CREATE", "");
  fireEvent.press(view.getByText("Join a room"));
  expect(native.dispatch).toHaveBeenLastCalledWith("JOIN", "");
  fireEvent.press(view.getByLabelText("Show highlight 2"));
  expect(view.getByText("Different tastes.")).toBeTruthy();
  fireEvent.press(view.getByText("Explore restaurants"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_LIBRARY", "");
  fireEvent.press(view.getByLabelText("My profile"));
  expect(native.dispatch).toHaveBeenLastCalledWith("OPEN_PROFILE", "");
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
  fireEvent.press(view.getByText("Confirm my food"));
  expect(native.dispatch).toHaveBeenLastCalledWith("SUBMIT_CART", "");
  fireEvent.press(view.getByTestId("room-tab:2"));
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
  fireEvent.press(view.getByText("Load older transactions"));
  expect(native.dispatch).toHaveBeenLastCalledWith("LOAD_WALLET_HISTORY", "");
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
test("long lists page on mobile with eight records per page", () => {
  const view = render(
    <PagedCards
      rtl={false}
      busy={false}
      cards={Array.from({ length: 18 }, (_, index) =>
        card(String(index), `Person ${index}`),
      )}
    />,
  );
  expect(view.queryByText("Person 8")).toBeNull();
  fireEvent.press(view.getByText("Next"));
  expect(view.getByText("Person 8")).toBeTruthy();
  expect(view.queryByText("Person 0")).toBeNull();
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
  fireEvent.press(view.getByLabelText("More options"));
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
