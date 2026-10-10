# FoodRun 1.7.4 · native navigation and Google sign-in

Android and iOS use the same refreshed native screens. The header opens the notification center with the unread count from the shared inbox. More remains in the bottom tabs and opens a sheet of coloured cards. Notification All/Unread tabs retain the original invitation, wallet and payment actions; opening a notification acknowledges it through the existing API.

Profile uses Overview, Details, Orders and Settings tabs. Personal and receiving-payment fields are separate within Details, with a visible Save action. Edits still update the current account; wallet cards retain transaction history. Restaurants use compact branch cards, searchable filters in a sheet, paged results, a separate import tab and a sheet for restaurant actions. English and Arabic retain the bundled Poppins and Noto Sans Arabic fonts and reduced-motion support.

Android's Continue with Google button now invokes Credential Manager's explicit Google account picker and Firebase Auth. Firebase's ID token goes to the existing shared FIREBASE_SIGN_IN action, preserving the web/iOS UID. The browser broker, manual return link and activity foreground cancellation timer have been removed from Android. Cancellation, activity destruction and late callbacks are handled without changing rooms or account identity. The backend broker remains available for older clients.

## Validation

- React Native typecheck and 35 interaction tests passed.
- Shared JVM tests: 197 passed, including live unread/read acknowledgement and existing Google token exchange.
- Website: 114 tests and production build passed. English/Arabic browser defaults, navigation and app-install checks passed at 320, 390 and 1280 pixels.
- Signed Android release build, lint and existing signing-certificate verification passed. Emulator install/launch passed. Google's native account picker appeared; Back returned to FoodRun with cancellation feedback.
- English/Arabic profile, notification center, More and restaurant previews were inspected in an isolated iPhone simulator using fixture data. Restaurant tab clipping was corrected.
- iOS archive/export and signature validation passed for the existing registered-device distribution. Version 1.7.4, build 22.

## Runtime limits

Selecting the emulator's existing Google account prompted Google's Verify it's you screen. No password, account recovery or verification bypass was attempted. A completed authenticated Google-to-FoodRun session is not established by the picker/cancellation test.

The local Android Firebase file has a web OAuth client but no Android OAuth entry. Firebase Management access returned HTTP 403 for the available CLI, Google Cloud and service accounts; the remote signing registration could not be verified or updated. The project owner should confirm package com.karim.foodrun and release SHA-1 27:87:46:C2:1F:4D:3B:A9:85:20:29:49:89:3C:67:58:75:9B:3F:DF in the Firebase Android app, then confirm native sign-in with a current Google device session. This build uses the generated web client ID rather than an Android client ID.

The paired iPhone 15 remained unavailable. Installation/launch of the final 1.7.4 archive on that physical phone is pending reconnection; simulator previews and the signed IPA are separate evidence. This IPA is for devices covered by the current development profile; Safari installation remains the public iOS option without an Apple account.

## Publication verified on 10 October 2026

- Application commit and public v1.7.4 tag: `732a2ebf735062ffa52261c5b03dccc349dd0802`. The authorized fork was aligned with the local branch.
- GitHub release: https://github.com/AhmedKaram2/FoodRun/releases/tag/v1.7.4, published at 12:45 UTC. Anonymous APK and IPA downloads matched the signed local artifact hashes.
- Website production deploy: `6aca33e67693205c5ea440af`. The exact served JavaScript bundle at https://intrvioo.com matched the local production build; existing payment function OPTIONS checks passed.
- Render deployment: `dep-db5364942hec73fi7rr0`, live at 12:49 UTC on the application commit. API health passed with durable Firestore storage.
- APK SHA-256: `f5b66b944342ba4a4e53bcce8285b03e4ad7d11ba8f74c2374f9988be70de51e`.
- IPA SHA-256: `26f0a34e14425a1c8b1f4e5a85d4a46e1a2f50a29ede3f764ecc160444633f28`. The actual paired iPhone 15 identity is covered by the exported development profile; the device connection remained unavailable.
