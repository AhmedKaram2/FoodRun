# FoodRun 1.7.5 · native navigation, languages and easier payments

The web, Android and iOS now share a full-screen room selection with a server-timed 1–2–3 countdown, animated wheel or running names, and a winner celebration. Only approved members of the spinning room are affected, including members viewing another page. Returning from the selection restores that page and any open sheet. Selection completion queues one durable result notification per member; the selected person receives Accept/Open actions and everyone else receives Open. Push preferences, removed memberships, retries and restarts remain respected.

Mobile rooms have a general status above a six-stage timeline, a joined-member count on the Members tab, horizontal tabs, compact member cards with ordering and connection status, and action sheets. Paginated lists append on scrolling; older wallet transactions and receipts load at the end of the current server page. Reduced-motion and English/Arabic layouts are supported.

The room owner and selected person can send the complete food list and mark the restaurant paid. Copying, sharing or choosing Order sent opens a confirmation sheet before locking food edits. A changed revision requires a fresh copy. Restaurant payment must match the current bill. The selected person remains the payment recipient. Owners receive a food-only restaurant summary; other members' payment receipts and receiving accounts keep their existing privacy. A negotiated capability omits this summary for older strict clients and preserves saved-command replay digests.

After the selected person declines, the owner sees Retry wheel. The declined person is excluded from subsequent selection while retaining their food and membership unless removed. The owner can remove a declined person in the lobby without a note, using the normal removal confirmation. Other removals still require reasons, and existing shared-half and pending-payment checks remain enforced.

Favourite groups use explicit list, details and editor screens. Joined-group details open in a sheet with visible Leave/Back actions, owner details have Edit/Delete, and failed operations keep their error and data visible. Account changes clear stale editor state. Web and mobile group tabs remain usable independently of the selected sheet.

Delivery is the default for new rooms. An unsaved restaurant can have an optional creator-entered menu, or members can type food and optional prices. Owners and selected people can correct prices before sending. Draft room menus stay local to that room setup rather than publishing automatically. Saved-menu price validation, currency precision and shared-half accounting remain enforced.

Transfers default to the current remaining balance. Another amount opens an amount-only form. Payment and refund notes are generated from the room, order number and amount; wallet batches fill their order details automatically. Existing partial-payment limits, pending-claim guards and recipient confirmation remain enforced.

The language selector offers English, Egyptian Arabic, Emirati Arabic and Standard Arabic. Arabic layouts and menu names stay right-to-left, and language preferences survive account refreshes. Native controls and web screens use the same dictionaries. Emirati Arabic has local wording across account, ordering, half items, wallet payments and alerts, including «شو تبا تطلب؟», «طرّشت المبلغ», «الربع» and «نص». Its ten-section help guide and FAQs have their own Emirati sentences. Native language selection uses a sheet. Push registrations retain the server's compatible Arabic language code.

Wallet opens on Required payments, with balance and payment history in separate tabs. Direct payments, top-up approvals, payments on behalf of wallet owners and grouped receipt approvals stay actionable. Sent payments remain visible while waiting for confirmation, with zero additional amount due from the sender. Room payments also show wallet obligations before and after approval. A money holder can settle their own remaining share plus other people's wallet obligations for the current room/order in one transfer. The server records all parts atomically and the recipient confirms or rejects them together. Other rooms, currencies, existing pending transfers and recipient authority remain guarded; a failed review rolls back every part. Individual approval cannot partially settle a grouped transfer. Public wallet-batch contracts remain compatible, with the own-share association stored privately on the server.

Native sign-in shows Google or email/password only. Registration asks for name, email and password; optional contact and receiving-account details remain in Profile. Registration ignores old contact/payment drafts and returns to Home. iOS has a floating navigation dock; Android has a Material-style bar. Both have animated selection, accessible tabs, shorter labels, RTL support and reduced-motion behavior, and hide the bar while entering text.

Database record-prefix reads now search the existing primary-key index instead of scanning every account record. Decrypted rooms and records are reused within one transaction and cleared after commit or rollback, with writes reflected immediately. Wallet debits are grouped once instead of repeatedly scanning every debit for each balance. Firestore remains authoritative with its existing single-writer fence. No schema or destructive data migration is needed. The fixture benchmark in `Scripts/benchmark-prefix-reads.py` compared 250 lookups over 20,020 rows: 175.53 ms before and 1.47 ms after. These are local SQL timings, not a production latency claim.

## Validation

- Domain: 43 passed; shared: 216 passed; server: 281 passed. Five Firestore emulator tests skipped because no emulator was configured. Final Wallet tests also covered member authorization, automatic notes, restart/replay and rollback.
- React Native: TypeScript and 47 interaction tests passed.
- Web: 121 tests and production build passed.
- Eight browser fixture checks passed in English/Arabic at 320, 390 and 1280 pixels: groups, restaurant drafts/prices, send confirmation, global selection, timeline/scroll/countdown, owner controls, refusal/removal and connection defaults.
- An additional 24 browser checks covered all four languages and amount-only transfers at the same widths.
- Twelve room-settlement browser checks covered combined totals, amount-free/note-free sheets and recipient-only approval across all four languages at those widths.
- Native room timeline/member previews and wheel-to-sheet restoration were inspected in an isolated iPhone simulator.
- Android release build and lint passed. iOS signed archive/export use version 1.7.5, build 23.

## Runtime boundaries

Notification recipients, outbox persistence and retries were tested with fixtures, without sending test messages to system users. Production push credentials and matching Firebase project configuration were verified; this does not prove a notification was received by a physical phone. The native-navigation build was installed and launched on the attached iPhone. The later Wallet/Emirati archive was signed and exported; the phone became unavailable during its final installation check. Its final installation outcome is reported separately.

The iOS IPA uses the existing registered-device development distribution. The public Safari installation option remains available. This release does not establish an authenticated Google sign-in result beyond the previously documented native picker checks.

## Release target

- Website: https://intrvioo.com
- Mobile release: https://github.com/AhmedKaram2/FoodRun/releases/tag/v1.7.5
- Android: `FoodRun-Android-1.7.5.apk`
- iOS: `FoodRun-iOS-RegisteredDevices-1.7.5.ipa`
- Deploy the updated API before publishing the new web and mobile clients.
