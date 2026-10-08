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

The attached iPhone has not enabled notifications yet. Real iPhone delivery and notification tap verification require signing in and accepting the iPhone notification permission. No delivery to that device has been claimed from unit tests or Firebase validation alone. Gmail sender reauthorization remains separate from push delivery.

The website offers direct Android APK installation, Safari installation of the FoodRun web app, and a separate development-signed IPA download for registered testers. The native IPA requires a covered device, Developer Mode and installation through Apple Configurator or Xcode. Safari installation does not require an Apple account; it uses the website and keeps the same FoodRun identity. The archive and IPA export passed.

The latest native iPhone install was attempted after the compact carousel and sheet changes, but CoreDevice could no longer locate the attached iPhone. The earlier design build was installed successfully; the final update and real push delivery require reconnecting and unlocking the phone.

Twelve restaurant entries were saved and read back from the production shared catalog: Yasmeen Al Sham, Al Khan/Al Luluah Tower; Mama'esh Ajman City Centre, Sharjah Beach House, and nine Dubai locations. These entries include verified branch/contact references and allow custom food entry. Menu items and prices were not copied from third-party delivery menus. Mama'esh locations/menu source: https://linktr.ee/mamaesh. Yasmeen location/contact: https://www.bizmideast.com/AE/yasmeen-al-sham-supermarket-restaurant-050-788-0111 and the restaurant's social posts mirrored at FoodBevg.

Final publication identifiers will be recorded after deployment.
