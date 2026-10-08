# FoodRun 1.7.2 — mobile design and notification setup

FoodRun uses the website's home illustration, food and people icons, and the signed-in profile photo in a native mobile layout. The compact home carousel advances through all three highlights every six seconds, supports swiping and manual paging, and has a pause control. Room creation and joining stay visible below it, alongside friends, menus and wallet shortcuts. Automatic paging stops for screen readers, Reduce Motion, background activity and pending requests. Gentle artwork motion, page transitions, tab selection and button feedback respect Reduce Motion and stop while the app is in the background.

The bottom navigation is Home, Rooms, Wallet, Profile and More. Each room has Overview, My food, Payments and Members tabs. Floating room shortcuts open available food choices, pending payment/approval actions and untaken halves. The half shortcut opens the current offers in a sheet and disappears after live acceptance. Country codes, groups, categories and other field choices use searchable bottom sheets when the list is long. Food sizes/extras, receiving accounts, selection overrides and quick-picker people use sheets too. Returning from a selection preserves the room tab; a new spin opens Overview. The separate quick picker remains available through More. Wallet top-ups and grouped payments return to the wallet when opened there. Room/Rooms and غرفة/الغرف are restored across web, native screens, invitations and server messages; food orders and receipts retain their order terminology.

The native theme uses quieter backgrounds, readable coral and green accents, clearer type spacing, and bundled Noto Sans Arabic Regular, Medium, SemiBold and Bold on both platforms. Arabic labels are shared with the website. Mobile website rooms use the same four compact tabs, including shortcuts that open the relevant payment panel. A persistent Home/Profile bar works across website routes, including administration, offline receipts, connection screens and the guide. Navigation keeps the authenticated identity and room memberships.

Saving enabled notification preferences now requests device permission after the server acknowledges the setting. Disabling notifications cancels pending registration; late token or server callbacks cannot enable them again. iPhone notification taps are retained until the controller is ready during a cold launch. Wallet notifications open the wallet. Local alerts respect the profile preference.

## Validation

- Shared: 193 tests passed; backend: 271 tests passed, including the five Firestore emulator checks.
- React Native: 20 interaction tests and TypeScript checks passed.
- Website: 114 tests and production build passed. English and Arabic browser checks passed at 320, 390 and 1280 pixels, including grouped room panels and payment shortcuts.
- Android signed release and lint passed. The app was installed and launched on the Android emulator.
- iOS: 24 tests passed; two optional live-hub tests skipped. Simulator UI previews covered the compact Arabic home and room layouts. The signed device build includes the development APNs entitlement.
- Firebase accepted a production sender validation request (HTTP 200). This validates sender configuration without delivering a message.

## Delivery boundary

Version 1.7.2 uses Android version code 20 and iOS build 20. Development installation on the attached iPhone is separate from App Store or TestFlight distribution.

At the last device preference check, notifications were disabled on the attached iPhone. Real iPhone delivery and notification tap verification require signing in and accepting the iPhone notification permission. No delivery to that device has been claimed from unit tests or Firebase validation alone. Gmail sender reauthorization remains separate from push delivery.

The website offers direct Android APK installation, Safari installation of the FoodRun web app, and a separate development-signed IPA download for registered testers. The native IPA requires a covered device, Developer Mode and installation through Apple Configurator or Xcode. Safari installation does not require an Apple account; it uses the website and keeps the same FoodRun identity. The archive and IPA export passed.

The final archived native build was installed on the attached iPhone 15 on 2026-10-08 at 14:41 Dubai time and launched at 14:42 (process 99764). CoreDevice reported success for both operations. The connection became unavailable during the follow-up process and notification-preference checks; sustained runtime and real push delivery were not verified.

Twelve restaurant entries were saved and read back from the production shared catalog: Yasmeen Al Sham, Al Khan/Al Luluah Tower; Mama'esh Ajman City Centre, Sharjah Beach House, and nine Dubai locations. These entries include verified branch/contact references and allow custom food entry. Menu items and prices were not copied from third-party delivery menus. Mama'esh locations/menu source: https://linktr.ee/mamaesh. Yasmeen location/contact: https://www.bizmideast.com/AE/yasmeen-al-sham-supermarket-restaurant-050-788-0111 and the restaurant's social posts mirrored at FoodBevg.

## Published evidence

- Code/tag: `456edeb472bc3d10e04fb1aec8d1e6888d6b058e`, `v1.7.2`, pushed to `fork/main`.
- Website: https://intrvioo.com, Netlify deployment `6ac7707e92059b902f0c73f8`. Served entry `/assets/index-CCuJ1cSv.js` matches the local build, SHA-256 `1d5d63cc44312407d3f40eef9b85464b439d3a69404f4a2fe4ffb79f14d937cb`. Existing payment functions were preserved; their expected method checks passed.
- Backend: Render deployment `dep-db3n0260tbcc7386hh50`, live at `2026-10-08T10:32:10Z`. Health is HTTP 200 with Firestore storage. All twelve restaurant entries remain present after deployment.
- Public Android APK matches the signed local file: SHA-256 `2fb1aa36a0c5b5fd4fbaa2468b8b89e4f103307f8b4fc8f7a02f805439bd6e1d`. Signing certificate is unchanged. The final APK installed and launched on the Android emulator.
- Public development IPA matches the exported local file: SHA-256 `5c5aee70e62d1ea33ef37ae5620d49cbdfb8a2d2f22b56b903810682d9d151c1`. Version 1.7.2/build 20; attached iPhone is covered by the provisioning profile. Development APNs entitlement, archive/export and strict signing checks passed.
- Website navigation and the iPhone installation sheet passed English and Arabic checks at 320, 390 and 1280 pixels, including keyboard dismissal and focus restoration.
- Firebase accepted sender validation (HTTP 200, validation only). No registered iOS push device was present at that check. The final native build was subsequently installed and launched on the attached iPhone 15; notification delivery remains unverified.
