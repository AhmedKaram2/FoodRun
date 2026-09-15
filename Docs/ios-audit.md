# iOS group ordering audit

Scope: native SwiftUI screens, platform adapters, IosComponents reuse, simulator signing and app metadata. Version 1.1, build 2.

## Resolved findings

- **Secure storage:** AES-GCM encrypts atomic local snapshots. The key lives in Keychain with device-only protection. Missing files and unreadable/tampered files produce different results; corruption never becomes an empty library. Names and snapshot sizes are bounded.
- **Actual app identity:** the visible pairing flow exposed Keychain error `-34018` when simulator signing was disabled. XcodeGen now declares the application identifier and private keychain access group and enables simulator ad-hoc signing. A regression exercises the real default Keychain provider through `GroupIosPlatform`, in addition to isolated cryptography tests. The visible pairing flow then saved successfully.
- **Transport:** HTTPS and WSS require the paired certificate fingerprint. Trust evaluation rejects expired and not-yet-valid certificates. Host and port checks preserve the paired endpoint; redirects are refused. Responses are bounded while receiving data.
- **Subscription lifecycle:** socket state and shared callbacks stay on the main queue. Stale socket callbacks cannot restart cancelled subscriptions. Retry work is cancelled on disposal, with bounded backoff and jitter.
- **Observer lifecycle:** the shared controller observes a weak Swift bridge. Releasing the store removes the observer and closes subscriptions.
- **Documents:** menu import reads bounded UTF-8 files off the main queue; cancellation and failures return explicit callbacks. Exports use separate temporary directories and clean them after sharing.
- **Discovery and camera:** Bonjour and camera purpose declarations are present in the built Info.plist. Discovery has bounded attempts and cleanup. Camera setup/start/stop use one serial queue; explicit cancellation returns to the pairing-link fallback.
- **UI reuse and animation:** fields reuse the adapted IosComponents input, including multiline support. Buttons and cards retain Food Run's orange/cream theme. Wheel rendering uses elapsed monotonic time, stops updating after the result, and supports reduced motion.
- **Error discoverability:** a themed error banner stays above the scrolling form, announces new messages to VoiceOver, and allows long messages to scroll within a bounded area. The screen resets scroll position on page changes and caps content width on iPad.

## Native test evidence

The last completed signed simulator suite passed **24/24 tests with zero skips** on iPhone 16e, iOS 26.0.1:

| Area | Tests |
| --- | ---: |
| Existing Quick Spin regression | 12 |
| Native storage, import, endpoint, lifecycle, clock and declarations | 9 |
| Pinned certificate identity and validity dates | 1 |
| Real local hub HTTPS/WSS and incorrect-pin rejection | 2 |

The two integration tests create a dedicated test room, join with another identity, retry the identical command, reconnect WebSocket subscriptions and reject an incorrect certificate pin. They use ignored `FoodRunTests/HubIntegrationConfig.json`; this optional file is excluded from generated project resources and shipped bundles.

The real Keychain regression was first observed failing with `status(-34018)`, then passing after the signing correction. A normal simulator app launch also completed **Use this hub → Join your people** without the previous save error.

The final signed simulator build and full test replay with the latest shared recovery/card changes succeeded: **24 passed, 0 failed, 0 skipped**. Complete CLI output is saved in `.build/ios-native-final.log`. Existing installed room data was preserved.

After that replay, the app was launched normally and its saved **Lunch Together QA** room reopened as **Order #2**, displaying **Connected · saved on this device** without another join request.

The coordinated visible Android/iOS flow also verified joining, synchronized spin and winner, payer account, food selection, AED 40 personal receipts, AED 80 restaurant payment, reimbursement confirmation, archival, server recovery and a second daily order in the same saved room. The iOS history screen displayed the first order's settled receipt and opened its native share sheet.

Camera capture, physical devices, actual banking transfers and restaurant calls are outside these simulator tests.

## Build and test

Run from the repository root, using an available simulator:

```sh
xcodegen generate
xcodebuildmcp simulator build --project-path FoodRun.xcodeproj --scheme FoodRun --simulator-name "iPhone 16e" --derived-data-path .build/ios
xcodebuildmcp simulator test --project-path FoodRun.xcodeproj --scheme FoodRun --simulator-name "iPhone 16e" --derived-data-path .build/ios
```

Keep signing enabled. `CODE_SIGNING_ALLOWED=NO` prevents the simulator app from accessing its Keychain group and is caught by the default-platform storage regression.

The signed simulator artifact is `.build/ios/Build/Products/Debug-iphonesimulator/FoodRun.app`. Build-only commands do not install or launch it.
