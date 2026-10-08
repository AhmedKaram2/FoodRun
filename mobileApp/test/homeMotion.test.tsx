import React from "react";
import { AccessibilityInfo, Animated, AppState } from "react-native";
import { act, fireEvent, render } from "@testing-library/react-native";
import HomeBanner from "../src/components/HomeBanner";
import LiveWheel from "../src/components/LiveWheel";
import { MotionProvider } from "../src/components/Motion";
import type { Wheel } from "../src/types";
jest.mock("../src/native",()=>({dispatch:jest.fn()}));
let appListeners: Array<(state:string)=>void>;
let accessibilityListeners: Record<string,Array<(value:boolean)=>void>>;
const originalAppState = Object.getOwnPropertyDescriptor(AppState,"currentState")!;
beforeEach(()=>{
  jest.useFakeTimers();jest.setSystemTime(1000000);
  appListeners=[];accessibilityListeners={};
  Object.defineProperty(AppState,"currentState",{value:"active",writable:true,configurable:true});
  jest.spyOn(AppState,"addEventListener").mockImplementation((_event,listener)=>{
    appListeners.push(listener as any);return {remove:jest.fn()};
  });
  jest.spyOn(AccessibilityInfo,"isReduceMotionEnabled").mockResolvedValue(false);
  jest.spyOn(AccessibilityInfo,"isScreenReaderEnabled").mockResolvedValue(false);
  jest.spyOn(AccessibilityInfo,"addEventListener").mockImplementation((event,listener)=>{
    (accessibilityListeners[event] ||= []).push(listener as any);return {remove:jest.fn()};
  });
  jest.spyOn(Animated,"loop").mockReturnValue({start:jest.fn(),stop:jest.fn(),reset:jest.fn()} as any);
});
afterEach(()=>{Object.defineProperty(AppState,"currentState",originalAppState);jest.restoreAllMocks();jest.useRealTimers();});
const advance=(ms:number)=>act(()=>jest.advanceTimersByTime(ms));
test("home automatically shows all highlights while keeping room actions visible and can pause",async()=>{
  const view=render(<MotionProvider><HomeBanner rtl={false} busy={false} authenticated/></MotionProvider>);
  await act(async()=>{});
  advance(6000);expect(view.getByText("Different tastes.")).toBeTruthy();
  expect(view.getByText("Create a room")).toBeTruthy();expect(view.getByText("Join a room")).toBeTruthy();
  advance(6000);expect(view.getByText("Split the bill.")).toBeTruthy();
  advance(6000);expect(view.getByText("Food is better")).toBeTruthy();
  fireEvent.press(view.getByLabelText("Pause highlights"));advance(12000);
  expect(view.getByText("Food is better")).toBeTruthy();
  fireEvent.press(view.getByLabelText("Play highlights"));advance(6000);
  expect(view.getByText("Different tastes.")).toBeTruthy();view.unmount();
});
test("home autoplay stops in the background and respects Reduce Motion and screen readers",async()=>{
  const view=render(<MotionProvider><HomeBanner rtl={true} busy={false} authenticated/></MotionProvider>);
  await act(async()=>{});
  act(()=>appListeners.forEach(listener=>listener("background")));advance(12000);
  expect(view.getByText("الأكل أحلى")).toBeTruthy();
  act(()=>appListeners.forEach(listener=>listener("active")));advance(6000);
  expect(view.getByText("أذواق مختلفة.")).toBeTruthy();
  act(()=>accessibilityListeners.reduceMotionChanged.forEach(listener=>listener(true)));advance(12000);
  expect(view.getByText("أذواق مختلفة.")).toBeTruthy();
  act(()=>accessibilityListeners.reduceMotionChanged.forEach(listener=>listener(false)));
  act(()=>accessibilityListeners.screenReaderChanged.forEach(listener=>listener(true)));advance(12000);
  expect(view.getByText("أذواق مختلفة.")).toBeTruthy();
  fireEvent.press(view.getByLabelText("عرض الميزة 3"));expect(view.getByText("حساب واضح،")).toBeTruthy();view.unmount();
});
test("the live wheel catches up using server time after foregrounding and keeps the server winner",async()=>{
  const wheel:Wheel={names:["Ana","Bob"],winner:"Bob",style:"wheel",serverOffset:2000,round:{id:"round1",memberIds:["a","b"],winnerId:"b",startAt:1001500,duration:6500,weights:[1,2],turns:7,landingOffset:0,seed:0}};
  const view=render(<LiveWheel wheel={wheel} rtl={false}/>);await act(async()=>{});
  const angle=()=>view.UNSAFE_getAllByType("G" as any).find(node=>node.props.transform)?.props.transform;
  const before=angle();
  act(()=>appListeners.forEach(listener=>listener("background")));advance(9000);
  expect(angle()).toBe(before);
  act(()=>appListeners.forEach(listener=>listener("active")));
  expect(angle()).not.toBe(before);expect(view.getByText("Bob")).toBeTruthy();
  view.rerender(<LiveWheel wheel={{...wheel,style:"names"}} rtl={true}/>);
  expect(view.getAllByText("Bob")).toHaveLength(2);
  expect(view.getByText("اختيار مباشر واحد، والنتيجة نفسها لدى الجميع.")).toBeTruthy();view.unmount();
});
