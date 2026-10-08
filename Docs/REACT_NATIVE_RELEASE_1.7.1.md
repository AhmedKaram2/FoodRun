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

## Local verification on 8 October 2026

| Check | Result |
| --- | --- |
| Domain / wire contract | 43 / 6 tests passed |
| Backend | 262 passed; 5 Firestore emulator checks skipped |
| Shared mobile logic | 188 tests passed |
| Website | 108 tests passed; production build passed |
| React Native | 11 interaction tests and TypeScript check passed |
| iOS native | 23 passed; 2 live-hub integration tests skipped |
| Android | Signed release APK and lint passed; installed and started on emulator |
| iOS device build | Signed development Release build prepared |
| Backend isolation | installDist passed without Node or installed mobile dependencies |
| Responsive website | Arabic and English at 320, 390 and 1280 pixels; all page audits passed |

These results do not establish real-account Google completion, inbox receipt, authenticated production financial behavior or App Store/TestFlight publication. The original 1.7.0 build was installed on the user's iPhone; the corrected 1.7.1 installation requires the device to reconnect. Gmail token refresh was rejected with invalid_grant; no replacement credential was saved during the timed-out authorization flow.

## Build and release

Run `npm --prefix mobileApp ci` before Android builds. For iOS run `bundle install` and `bundle exec pod install`, then use `FoodRun.xcworkspace`. Keep the existing Android signing key, bundle identifiers and Apple signing configuration to preserve upgrades and native data.

Deploy the backend before releasing clients because saved-command recovery and native support need its new endpoints. Website deployment must preserve the existing Netlify payment functions. Android distribution uses the signed APK under v1.7.1. Reconnect and unlock the paired iPhone for development installation. Renew the Gmail sender credentials privately and update only the three Gmail environment variables before verifying pending invitations.
