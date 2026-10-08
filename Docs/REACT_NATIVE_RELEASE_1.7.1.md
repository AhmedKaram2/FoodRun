# FoodRun 1.7.1

Android and iOS now render the same React Native native views from `mobileApp`. Kotlin Multiplatform retains account identity, order rules, billing, synchronized wheels, controllers and existing encrypted device storage. Native adapters handle Google login, photos, sharing, discovery and push. Release builds embed JavaScript and run without Metro.

## Included behavior

- FoodRun app name and Home, Orders, Wheel, More navigation. Profile, wallets, friends, restaurants, notifications and administration are available through More; sign-in stays within account actions.
- Orders, menus, prices, half-item offers, receipts, wallet approvals and transaction history use the existing shared actions. Mobile lists paginate and optional sections use tabs or native sheets.
- English Order / Orders and Arabic طلب / طلبات appear in web and native labels, invitations, help and status messages. Existing protocol fields, database tables, links and membership identifiers remain compatible.
- iOS Google login uses the native Google SDK and Firebase Auth with registered callback schemes. First-time Google login selects the Internet API before invoking account exchange; asynchronous failures become feedback rather than a controller crash.
- After a lost edit or payment acknowledgement, clients query the saved command result for the signed-in account. Confirmed work clears the stale pending request without replaying the mutation or discarding an unsubmitted edit. Unknown results retain the saved request for explicit retry.
- Expired Gmail sender authorization pauses delivery and preserves pending jobs and send allowance. Actual delivery still requires renewed authorization for foodruncollection@gmail.com.
- Native owner support uses a temporary authorized session; target credentials never replace the owner's stored account or device push registration.

- Accepted half-item offers disappear from the top; each participant keeps their half and exact split price in their own cart.
- Paid wheel exemption and half-chance purchases are removed. Historical payments remain reviewable.
- Existing restaurant menus support one-step Add & save item on web and native. Additions append atomically without replacing another user's additions, survive restart, and load into new and next orders without altering existing bills.
- Browser subscriptions recover after network suspension, foreground return and temporary server failures. Web and native reject older account snapshots so a delayed socket message cannot undo a newer wallet acknowledgement. A Firestore metadata listener fences stale read caches as well as writes during a server handover.
- Server shutdown stops and joins maintenance before closing SQLite; the local SIGTERM check stopped in 1.49 seconds with no SQLite maintenance errors.

## Local verification on 8 October 2026

| Check | Result |
| --- | --- |
| Domain / wire contract | 43 / 6 tests passed |
| Backend | 271 passed, including all 5 Firestore emulator checks |
| Shared mobile logic | 190 tests passed |
| Website | 114 tests passed; production build passed |
| React Native | 11 interaction tests and TypeScript check passed |
| iOS native | 23 passed; 2 live-hub integration tests skipped |
| Android | Signed release APK and lint passed; installed and started on emulator |
| iOS release build | ARM simulator build passed; signed development build installed and launched on the attached iPhone 15 |
| Backend isolation | installDist passed without Node or installed mobile dependencies |
| Responsive website | Arabic and English at 320, 390 and 1280 pixels; all page audits passed |

These results do not establish real-account Google completion, inbox receipt, authenticated production financial behavior or App Store/TestFlight publication. The corrected 1.7.1 build was installed and launched on the attached iPhone 15 on 8 October; its process remained running after startup. Real-account Google completion still requires confirmation on the phone. Gmail token refresh was rejected with invalid_grant; no replacement credential was saved during the timed-out authorization flow.

## Build and release

Run `npm --prefix mobileApp ci` before Android builds. For iOS run `bundle install` and `bundle exec pod install`, then use `FoodRun.xcworkspace`. iOS supports ARM devices and ARM simulators, matching the configured Kotlin targets. The bundle build phase declares its final output so incremental JavaScript changes are included in code signing. Keep the existing Android signing key, bundle identifiers and Apple signing configuration to preserve upgrades and native data.

Deploy the backend before releasing clients because saved-command recovery and native support need its new endpoints. Website deployment must preserve the existing Netlify payment functions. Android distribution uses the signed APK under v1.7.1. Reconnect and unlock the paired iPhone for development installation. Renew the Gmail sender credentials privately and update only the three Gmail environment variables before verifying pending invitations.

## Published release

Source commit: `08016abd2e7ec159e9fa7e1f0ef26ef6b6aaff38`.

- Backend: Render deployment `dep-db3l3dtg1s2s73at9ci0` became live on 8 October 2026 at 08:23:04 UTC. Health returned HTTP 200 with Firestore storage. Two authenticated production reads returned matching account, order and wallet data.
- Website: Netlify production deployment `6ac75356ebf3f619449315a7` serves the tested build on https://intrvioo.com. The entry asset checksum is `a703c8f1691efb2adac0009c8c59f704cdbf5902f58988d6f83e85970f889719`. Existing payment functions were preserved and checked.
- Android: [FoodRun 1.7.1](https://github.com/AhmedKaram2/FoodRun/releases/tag/v1.7.1) is public. Signed APK checksum: `e2f9484abb5b3840a010c7a0972c527117713e964017e258efd5282d83ae64e1`.
- iOS: development-signed FoodRun 1.7.1 installed and started on the attached iPhone 15. This is not an App Store or TestFlight release.
