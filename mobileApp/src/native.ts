import { NativeEventEmitter, NativeModules } from "react-native";
import type { Snapshot } from "./types";
export const native = NativeModules.FoodRun as {
  getSnapshot(): Promise<string>;
  getNotificationPermission?(): Promise<string>;
  openNotificationSettings?(): Promise<void>;
  checkAppUpdate?(): Promise<string>;
  startAppUpdate?(): Promise<string>;
  getAppUpdateStatus?(): Promise<string>;
  installAppUpdate?(): Promise<string>;
  dispatch(action: string, value: string): void;
  update(key: string, value: string): void;
  tick(): void;
  quickAction(action: string, value: string): void;
  dismissFeedback(id: number): void;
  photo(key: string): Promise<string>;
  share(text: string): void;
  copy(text: string): void;
};
export const events = new NativeEventEmitter(NativeModules.FoodRun);
export const parseSnapshot = (body: string): Snapshot => JSON.parse(body);
export const dispatch = (action: string, value = "") =>
  native.dispatch(action, value);
export const update = (key: string, value: string) => native.update(key, value);
export const quickAction = (action: string, value = "") =>
  native.quickAction(action, value);
