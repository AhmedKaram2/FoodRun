# Simulator/emulator UI verification

These are stateful Android Maestro checkpoints used alongside an iOS simulator on the same real Food Run hub. They are not independent tests and must not be run as an unordered directory. Native adapter suites and the complete three-client controller workflow run separately.

Start the hub using [its guide](../../room-server/README.md). Install the current debug or signed release APK. Start Android with `adb shell am start -W -n com.karim.foodrun/.MainActivity` before running a checkpoint. This test environment's Maestro `launchApp` driver was unreliable; direct activity launch works. Do not clear app data between recovery checkpoints.

Example pairing command:

```sh
maestro --device emulator-5554 test -e HUB_PAIRING='foodrun://pair?host=10.0.2.2&port=8443&fingerprint=YOUR_HUB_SHA256' Tests/UI/android-pair.yaml
```

Use `127.0.0.1` for the iOS simulator and `10.0.2.2` for Android's host-machine alias. Physical phones use the computer's LAN address. The fingerprint comes from the running hub.

## Paired sequence

1. Android home → `android-pair`, `android-create-details`, `android-add-restaurant`, `android-create-room`.
2. Read the new room code. On iOS, pair, enter `Karam iOS`, and request to join that code.
3. Android → `android-ready`, `android-approve-ios`. On iOS opt into duty and mark ready.
4. Android → `android-spin`. Verify both screens show the same spin/result.
5. If Android is selected, use `android-accept-account`; otherwise perform acceptance/account entry on iOS. All bank/contact values here are fictitious test fixtures.
6. Choose one Chicken Burger on each device and submit both carts. `android-order-food` handles Android and opens review.
7. iOS confirms its displayed total and recipient. Android → `android-confirm-place`, `android-pay-restaurant`. The payment checkpoint rejects79, shows the error, then accepts80 for two35 meals plus10 delivery.
8. iOS declares40 reimbursement. Android → `android-settle`, then `android-open-receipts`. Verify both receipts are settled.
9. Stop the hub. Verify cached receipts and offline status on both clients; restart the apps without clearing data. Use `android-resume` from home.
10. Restart the same hub/data directory. On Android's receipt screen run `android-next-order` with `-e ROOM_CODE=the-existing-code`. iOS resumes the same membership, taps **Join this order**, and opens **Past orders**.

`android-connect`, `android-retry`, `android-fix-menu` and `android-resume` are optional checkpoints for specific recovery states, not extra mandatory sequence steps. Scroll direction assumes the preceding checkpoint's position. Dynamic winner, room code and IDs must come from the actual screen. If selecting a field near the screen edge, center it and verify the value before submission.

iOS was driven with `xcodebuildmcp ui-automation snapshot-ui`, `tap`, `type-text` and `swipe`, using the same field/action accessibility identifiers. Native share sheets were inspected without sending anything. See [the measured report](../../Docs/GROUP_ORDER_TEST_REPORT.md).
