import React from "react";
import {AppState} from "react-native";
import {act, fireEvent, render, waitFor} from "@testing-library/react-native";
import NotificationPermissionNotice from "../src/components/NotificationPermissionNotice";
import {dispatch,native} from "../src/native";
jest.mock("../src/native",()=>({native:{getNotificationPermission:jest.fn(),openNotificationSettings:jest.fn()},dispatch:jest.fn()}));
beforeEach(()=>{jest.clearAllMocks();(native.openNotificationSettings as jest.Mock).mockResolvedValue(undefined);});
test("disabled device notifications offer settings and disappear after enabling",async()=>{
  let permission="disabled";let resume:(state:string)=>void=()=>{};
  (native.getNotificationPermission as jest.Mock).mockImplementation(async()=>permission);
  const listener=jest.spyOn(AppState,"addEventListener").mockImplementation((_,callback)=>{resume=callback;return {remove:jest.fn()};});
  const view=render(<NotificationPermissionNotice rtl={false} accountId="me" busy={false}/>);
  fireEvent.press(await view.findByText("Open settings"));
  expect(native.openNotificationSettings).toHaveBeenCalledTimes(1);
  permission="enabled";await act(async()=>{resume("active");});
  await waitFor(()=>expect(view.queryByTestId("notification-permission-notice")).toBeNull());
  listener.mockRestore();
});
test("first iPhone permission request uses the existing enable action",async()=>{
  (native.getNotificationPermission as jest.Mock).mockResolvedValue("notDetermined");
  const view=render(<NotificationPermissionNotice rtl={false} accountId="me" busy={false}/>);
  fireEvent.press(await view.findByText("Enable"));expect(dispatch).toHaveBeenCalledWith("ENABLE_ALERTS");
  expect(native.openNotificationSettings).not.toHaveBeenCalled();
});
test("app notification preferences have a direct Arabic settings action",async()=>{
  (native.getNotificationPermission as jest.Mock).mockResolvedValue("enabled");
  const view=render(<NotificationPermissionNotice rtl accountId="me" busy={false} pushEnabled={false}/>);
  fireEvent.press(await view.findByText("فتح الإعدادات"));expect(dispatch).toHaveBeenCalledWith("OPEN_NOTIFICATION_PREFERENCES");
});
test("failed settings navigation shows a retry message",async()=>{
  (native.getNotificationPermission as jest.Mock).mockResolvedValue("disabled");
  (native.openNotificationSettings as jest.Mock).mockRejectedValue(Error("Unavailable"));
  const view=render(<NotificationPermissionNotice rtl={false} accountId="me" busy={false}/>);
  fireEvent.press(await view.findByText("Open settings"));
  expect(await view.findByText("Could not open settings. Try again.")).toBeTruthy();
});
