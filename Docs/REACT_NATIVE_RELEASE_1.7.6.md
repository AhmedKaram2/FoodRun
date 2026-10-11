# FoodRun 1.7.6 · shared Quick Choice and notification actions

Quick Choice works for any purpose; food remains the default. Add or remove names, select an owned or joined friend group, and exclude individual participants without editing the group. Moving names remain the default animation; the wheel is also available.

The creator selects the audience: included group members by default, or everyone online. Their existing live account subscriptions automatically show a full-screen 1–2–3 countdown, the same selection, and a celebration over the current page. The creator can retry or close the selection; viewers can close their result. No joining or room setup is required.

Quick Choice configuration and rounds are temporary. The server holds rounds only in memory, expires them automatically, and does not create a room, database record, payment or durable notification for them. Restarting the service clears all rounds. Signed-in users need an active connection to see the shared animation.

The notification center exposes current wallet top-up approvals, wallet-payment approvals, grouped receipt confirmations and room payment actions. Room invitations retain their Join action. Actions use current payment state and the existing server authorization checks. Legacy wallet notifications receive their related payment identifiers when projected into the inbox.

Validation:

- Shared JVM: 216 passed.
- Server: 286 passed; five Firestore emulator checks skipped.
- React Native: TypeScript passed; 51 tests passed.
- Website: production build passed; 124 tests passed.
- Browser fixtures: 36 checks passed across English and three Arabic variants at 320, 390 and 1280 pixels, with both selection styles and direct inbox approval.
- Android: release build and lint passed.
- iOS: signed archive completed for version 1.7.6, build 24. Physical installation is verified separately.

Browser fixtures and server tests verify the flows without sending real financial transactions or broadcasting test selections to production users. Physical push receipt and authenticated multi-device selection are not claimed by these checks.

Release: https://github.com/AhmedKaram2/FoodRun/releases/tag/v1.7.6

- Android: `FoodRun-Android-1.7.6.apk`
- iOS registered devices: `FoodRun-iOS-RegisteredDevices-1.7.6.ipa`
- Website: https://intrvioo.com
