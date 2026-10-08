# FoodRun 1.7.2 — mobile design and notification setup

FoodRun uses the website's home illustration, food and people icons, and the signed-in profile photo in a native mobile layout. The home highlights offer room creation, joining, restaurants and wallet shortcuts. Gentle artwork motion, page transitions, tab selection and button feedback respect Reduce Motion and stop while the app is in the background.

The bottom navigation is Home, Rooms, Wallet and More. Each room has Overview, My food, Payments and Members tabs. The separate quick picker remains available through More. Wallet top-ups and grouped payments return to the wallet when opened there. Room/Rooms and غرفة/الغرف are restored across web, native screens, invitations and server messages; food orders and receipts retain their order terminology.

The native theme uses quieter backgrounds, readable coral and green accents, clearer type spacing, and bundled Noto Sans Arabic Regular, Medium, SemiBold and Bold on both platforms. Arabic labels are shared with the website. Mobile website rooms use the same four compact tabs, including shortcuts that open the relevant payment panel.

Saving enabled notification preferences now requests device permission after the server acknowledges the setting. Disabling notifications cancels pending registration; late token or server callbacks cannot enable them again. iPhone notification taps are retained until the controller is ready during a cold launch. Wallet notifications open the wallet. Local alerts respect the profile preference.

## Validation

- Shared: 193 tests passed; backend: 271 tests passed, including the five Firestore emulator checks.
- React Native: 14 interaction tests and TypeScript checks passed.
- Website: 114 tests and production build passed. English and Arabic browser checks passed at 320, 390 and 1280 pixels, including grouped room panels and payment shortcuts.
- Android signed release and lint passed. The app was installed and launched on the Android emulator.
- iOS: 24 tests passed; two optional live-hub tests skipped. Simulator UI previews covered Arabic home and room layouts. The signed device build includes the development APNs entitlement.
- Firebase accepted a production sender validation request (HTTP 200). This validates sender configuration without delivering a message.

## Delivery boundary

Version 1.7.2 uses Android version code 20 and iOS build 20. Development installation on the attached iPhone is separate from App Store or TestFlight distribution.

The attached iPhone has not enabled notifications yet. Real iPhone delivery and notification tap verification require signing in and accepting the iPhone notification permission. No delivery to that device has been claimed from unit tests or Firebase validation alone. Gmail sender reauthorization remains separate from push delivery.

Production deployment identifiers and final iPhone installation evidence will be recorded after publication.
