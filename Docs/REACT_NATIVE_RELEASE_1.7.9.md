# FoodRun 1.7.9 (27)

Wallet and payments now have Balance, To pay, Waiting, To receive and History views on mobile and the website. Cards identify the person, room, order, amount and current payment state. Approvals appear first on mobile. Room payment views separate actions, receipts, history and account details.

The chosen ordering person can approve a reported payment using its existing transfer record. A smaller Record money received option records money received before the sender reports payment. These use different commands and audit entries. Manual receipt is hidden while a report is pending, preventing double recording. Neither action transfers money.

Reported direct payments become confirmed after 12 hours without a recipient response. The server confirms only the reported amount, preserves partial balances and records an explicit automatic-confirmation audit and notification. The deadline survives server restarts and room archiving. Rejected reports, refunds, wallet custody/top-ups/batches and records with unknown timestamps are excluded.

Mobile food selection has compact rows, category/search controls and a persistent My order shortcut. The cart opens without scrolling through the menu. Progress uses a thin linear indicator. The compact room header moves secondary owner tools into room details, keeps Start wheel reachable across room tabs and shows the chosen person after selection. Owner actions identify the selected person they act for.

Website room navigation is below the logo/top bar, including desktop and mobile browsers. The desktop content area is wider, and member cards give names more space. English and Arabic layouts retain their direction and accessible controls.

Validation: mobile TypeScript and 58 tests; 125 web tests; shared JVM and room-server tests; browser fixtures at 320/390/1440 widths in English and Arabic for navigation, cart access, persistent selection, partial-payment waiting states and nested wallet filters. A separate browser audit verifies approval versus manual receipt commands and collector-only access. Android release assembly/lint and signed iOS archive/export are checked separately from device installation and live deployment.

Downloads: signed Android APK and a development IPA for registered devices. The IPA is not a TestFlight release. Firebase project and sender configuration checks do not establish that a real notification was received on Android or iOS; receipt requires a signed-in target device test.

Repository and migration handoff: [HYBRID_LIVE_ROOMS.md](HYBRID_LIVE_ROOMS.md) documents the command API, Firestore namespace and single-writer migration requirements. Release manifest generation must follow the final APK build so the public APK checksum matches `webApp/public/app-release.json`.
