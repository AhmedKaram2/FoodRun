# Food Run 1.5.0 — source audit and release

24 September 2026. This release uses static review, focused automated checks and compilation as requested. It does not claim a fresh authenticated, three-device meal walkthrough or iOS store distribution.

## Implemented

- Room creation keeps the wheel and adds Running names. Both animations reveal the same server-selected person. The style persists across restart and the next order; older mobile clients receive their supported wire schema.
- New room name defaults to Mohre. Delivery destination defaults to Mohre, Backside Parking, Security gate, Opposite Suni's Restaurant, with https://maps.app.goo.gl/cLba7hYb9Rtfqyjr5. Both are editable.
- The chosen person's valid saved receiving account is applied automatically. Existing active rooms without an account are backfilled; already shared accounts and completed orders remain intact.
- Restaurant-order copy and sharing have independent English/Arabic selection on web, Android and iOS. Quantities, item options and notes remain in the export.
- Authenticated notification inboxes cover order submissions, all submitted, chosen-person acceptance, placing the full order, restaurant payment, member payment declarations/confirmation/rejection, refunds, changed bills, reopening, arrival and cancellation. Action links open a current-state review; payment confirmation and sending an order require an explicit action.
- FCM delivery uses a durable outbox, retries, token rotation, per-device registration, account-scoped inboxes and membership/access checks. Reading an item suppresses its queued push. Unregistered tokens are pruned. Notification payloads exclude bank identifiers and authentication tokens.
- Shared native forms now cover payment-room creation, assigned shares, receipt photos, receipt/share editing and recording received payments. Shares must equal the receipt total. A payment action opens the payment form directly.
- Native saved-order reuse opens a review of current prices and unavailable items. Required options, sizes, notes, quantity limits and custom items are validated before adding items; it never submits automatically.
- Native administration covers users and all editable profile/payment fields, user creation, blocks/restoration/removal, restaurants, rooms, history, wallets, block requests, cleanup preview/confirmation and settings. Each request revalidates the same verified Firebase admin email used by the web. Passwords are not persisted.
- Native guided menu editing covers categories, items, availability, bilingual labels/descriptions, variants and extras with selection limits. The editor is shared by personal and admin catalogs.

## Static findings and corrections

The review corrected lowercase transfer-status handling in web notification links, stale notification actions after opening another room, notification taps received before native room memberships finish loading, draft fee isolation while editing menu children, bounded Android photo decoding, and Android's scanner dependency on an incompatible old Fragment version. Native receipt photos retain their aspect ratio.

Selection weighting is unchanged by this release: the previous person's weight is 20 and each other eligible person's weight is 80. This is not an exact 20 percent repeat probability when there are more than two eligible people.

## Validation

- Domain JVM: 35 tests, zero failures.
- Contract JVM: 6 tests, zero failures.
- Server: 143 tests, zero failures; 5 optional integration tests skipped.
- Shared native JVM: 136 tests, zero failures.
- Web: 51 tests and production build pass.
- Android signed release build and release lint pass. Lint retains nonblocking existing resource/dependency/style warnings.
- iOS arm64 simulator build passes with signing disabled. The build is not a TestFlight/App Store upload.
- Before the request to stop at static validation, one synthetic FCM message was delivered to the test Android emulator, with Copy order and Share order actions. No production order or payment was sent. Subsequent acceptance is limited to source/build/automated evidence.
- Secret scan allows only the public Firebase Android client key in its SDK configuration; no service-account key or user session is allowed.

## Firebase setup boundary

CLI account: `1ahmedkaram1@gmail.com`. Project: `devassess-c8833`.

Android and iOS apps are registered for `com.karim.foodrun`. The existing server service account has `roles/firebasecloudmessaging.admin`; FCM, registrations and installations APIs are enabled. Public SDK configuration is included in each app. Server credentials remain in the production secret configuration.

Browser background push is pending the project's Web Push public key from Firebase Console. Set `VITE_FIREBASE_VAPID_KEY` in the web build environment and rebuild to enable it. The notification inbox works while this is pending. Firebase documents key generation/import in the Console: https://firebase.google.com/docs/cloud-messaging/web/get-started.

iPhone background push is pending a paid Apple Developer team and its APNs authentication key configured in Firebase. The personal free team cannot complete that setup, and the separate employer team has not been authorized for Food Run. The native inbox remains available. Debug uses the development APNs entitlement; Release uses production.
