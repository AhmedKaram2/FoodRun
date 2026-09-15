# Food Run 1.1 verification

This is the earlier release baseline. The [state consistency report](CONSISTENCY_TEST_REPORT.md) records the subsequent user-reported consent defect, related fixes, current default-on behavior and fresh verification. The older passing matrix did not establish that all app flows were complete.

Date: 15 September 2026. Scope: local Mac hub, Android emulator and iPhone 16e simulator, as requested. Test transfers, bank accounts and restaurant contacts were fictitious; no real restaurant order or bank transfer was made.

**Final result: 146 automated tests passed, zero failures, zero skips.** The cross-platform UI flow and recovery checks below also passed after the identified defects were fixed.

## Automated evidence

| Suite | Passed | Failed / skipped |
| --- | ---: | --- |
| Shared Kotlin: original Quick Spin 33 + group controller 17 | 50 | 0 / 0 |
| Menu, money, billing and room domain | 26 | 0 / 0 |
| Shared protocol | 4 | 0 / 0 |
| Server service, persistence and HTTP/WebSocket | 36 | 0 / 0 |
| Android native adapters on emulator, final build | 6 | 0 / 0 |
| iOS native adapters and original app tests, final signed build | 24 | 0 / 0 |

JVM totals were checked from Gradle XML results. Final native output is `.build/android-native-final.log` and `.build/ios-native-final.log`. The iOS result summary reports Total24, Passed24, Failed0, Skipped0 on iPhone 16e / iOS26.0.1. No live-transport tests were silently skipped in these runs.

```sh
./gradlew :order-domain:jvmTest :order-contract:jvmTest :room-server:test :shared:jvmTest
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleDebugAndroidTest :androidApp:lintRelease
xcodebuildmcp simulator test --project-path FoodRun.xcodeproj --scheme FoodRun --simulator-id 83D92D39-6999-4A81-984C-DF555E418A21 --derived-data-path .build/ios
```

Configure the native integration tests with the running hub URL/fingerprint as described in [Android audit](android-audit.md) and [iOS audit](ios-audit.md). Keep iOS simulator ad-hoc signing enabled for Keychain access.

## Actual Android ↔ iOS UI run

Room `Lunch Together QA`, code `762390`, was created in the Android UI. iOS joined as `Karam iOS`; Android approved it and the approval appeared live on iOS.

| Check | Observed result |
| --- | --- |
| Manual restaurant creation | Together Kitchen saved with Chicken Burger at AED 35 and a test phone contact |
| Shared spin | Both participants ready; Android started; iOS wheel reported the same selected participant, Karim Android |
| Duty and receiving account | Android accepted and shared a fictitious Test Bank account |
| Both carts and exact split | One AED35 burger each plus AED10 delivery → AED80 total; each receipt AED40 |
| Quote consent and placement | Both confirmed; payer recorded test restaurant acceptance |
| Incorrect restaurant payment | AED79 rejected; visible sticky validation error; AED80 accepted |
| Cross-platform reimbursement | iOS declared AED40; Android confirmed; iOS receipt became Remaining AED0 |
| Completion | Fulfilled and archived after settlement |
| Hub offline | Both clients displayed cached receipts, recipient and confirmed settlement with offline status |
| App restart offline | Android app replacement/relaunch restored its saved room and receipts without data clearing |
| Hub restart | Same persisted hub resumed; clients reconnected without joining again |
| Daily reuse | Android started order 2 with the same code762390; iOS retained approval and chose Join this order |
| Historical receipt | iOS showed order 1, settled AED40 receipt and the account used for that order |
| Native sharing | iOS Share receipt opened the system share sheet with a 397-byte text document; nothing was sent |
| Final Android release upgrade | Signed 1.1 APK installed over the test app, preserved storage and reopened order 2 connected |

The first visible review pass found duplicate roster/receipt list IDs: Android crashed and iOS left a layout gap. Namespaced IDs fixed both; the repaired review/payment/completion flow passed. Every state emitted by the full shared integration workflow now asserts unique card IDs. The iOS real storage regression also reproduced Keychain error -34018 before simulator signing/entitlements were fixed; the signed regression passes.

## Edge cases covered by tests

- Permanent rooms/memberships, skipped meals, expected absentees, late payer consent and role privacy.
- Lost acknowledgements, durable idempotency, mismatched retry payloads, app/hub restarts, reconnect during spin, clock-based animation, stale revisions and invalid transitions.
- Pending requests remain bound to the original hub; re-pairing the same certificate at a changed address updates saved sessions.
- Invalid/oversized/duplicate JSON, currency precision, menu modifiers, invalidated quotes and safe pre-placement menu amendments.
- Exact fee/discount rounding, no-food payer, partial payments, excessive claims, rejected claims, approved adjustments back to zero, refunds and mandatory settlement before next order.
- Bounded encrypted native storage, real Keychain/Keystore use, corruption/truncation/wrong-key rejection, bad TLS fingerprints, observer/lifecycle cleanup and unchanged heartbeat write throttling.
- Paginated history, offline recovery, historical recipient exports, restored fee drafts and receipt consent beside amount/recipient.

## Artifacts and practical limits

- Local artifact `Distributions/FoodRun-1.1.apk`: signed with the existing release identity; lint has 0 errors and 16 warnings.
- Local artifact `Distributions/FoodRun-Hub-1.1.zip`: Mac/Windows launchers, libraries and instructions; archive integrity and contents checked; no private keys/test data included. Requires Java 17+.
- Local artifact `Distributions/FoodRun-iOS-Simulator-1.1.zip`: signed app from a successful clean Debug build in `.build/ios-distribution`, excluding XCTest bundles and test configuration; not an iPhone-installable IPA.
- [Android room](../Preview/group/android-room.png), [iOS offline receipt](../Preview/group/ios-offline-receipt.jpg), [iOS history](../Preview/group/ios-history.jpg), [iOS native share](../Preview/group/ios-share-receipt.jpg).

These results establish the tested matrix, not every possible hardware/network condition. Physical phones, camera QR recognition, router-specific multicast/firewalls, Windows execution, real phone calls and real bank transactions were outside this emulator/simulator run. The app records manual payments; it does not move money. Live updates require the local hub/network. Rooms have no automatic expiry; retaining hub data/keys and device data is necessary to retain access.

Artifact sizes and SHA-256 checksums are recorded in [RELEASE_VERIFICATION.json](RELEASE_VERIFICATION.json).

The generated installers above are local verification artifacts excluded from Git. A fresh checkout can reproduce builds using the [root build instructions](../README.md#build-android) and [hub build instructions](../room-server/README.md#build-from-source). Screenshots and the [app/architecture video](media/food-run-demo.mp4) are tracked in the repository.
