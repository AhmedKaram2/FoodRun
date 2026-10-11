# FoodRun 1.7.5 · room status and shared selection

The web, Android and iOS now share a full-screen room selection with a server-timed 1–2–3 countdown, animated wheel or running names, and a winner celebration. Only approved members of the spinning room are affected, including members viewing another page. Returning from the selection restores that page and any open sheet. Selection completion queues one durable result notification per member; the selected person receives Accept/Open actions and everyone else receives Open. Push preferences, removed memberships, retries and restarts remain respected.

Mobile rooms have a general status above a six-stage timeline, a joined-member count on the Members tab, horizontal tabs, compact member cards with ordering and connection status, and action sheets. Paginated lists append on scrolling; older wallet transactions and receipts load at the end of the current server page. Reduced-motion and English/Arabic layouts are supported.

The room owner and selected person can send the complete food list and mark the restaurant paid. Copying, sharing or choosing Order sent opens a confirmation sheet before locking food edits. A changed revision requires a fresh copy. Restaurant payment must match the current bill. The selected person remains the payment recipient. Owners receive a food-only restaurant summary; other members' payment receipts and receiving accounts keep their existing privacy. A negotiated capability omits this summary for older strict clients and preserves saved-command replay digests.

After the selected person declines, the owner sees Retry wheel. The declined person is excluded from subsequent selection while retaining their food and membership unless removed. The owner can remove a declined person in the lobby without a note, using the normal removal confirmation. Other removals still require reasons, and existing shared-half and pending-payment checks remain enforced.

Favourite groups use explicit list, details and editor screens. Joined-group details open in a sheet with visible Leave/Back actions, owner details have Edit/Delete, and failed operations keep their error and data visible. Account changes clear stale editor state. Web and mobile group tabs remain usable independently of the selected sheet.

Delivery is the default for new rooms. An unsaved restaurant can have an optional creator-entered menu, or members can type food and optional prices. Owners and selected people can correct prices before sending. Draft room menus stay local to that room setup rather than publishing automatically. Saved-menu price validation, currency precision and shared-half accounting remain enforced.

## Validation

- Domain: 43 passed; shared: 206 passed; server: 273 passed. Five Firestore emulator tests skipped because no emulator was configured.
- React Native: TypeScript and 41 interaction tests passed.
- Web: 116 tests and production build passed.
- Eight browser fixture checks passed in English/Arabic at 320, 390 and 1280 pixels: groups, restaurant drafts/prices, send confirmation, global selection, timeline/scroll/countdown, owner controls, refusal/removal and connection defaults.
- Native room timeline/member previews and wheel-to-sheet restoration were inspected in an isolated iPhone simulator.
- Android release build and lint passed. iOS signed archive/export use version 1.7.5, build 23.

## Runtime boundaries

Notification recipients, outbox persistence and retries were tested with fixtures, without sending test messages to system users. Production push credentials and matching Firebase project configuration were verified; this does not prove a notification was received by a physical phone. The paired iPhone remained unavailable in Xcode, so physical installation is pending reconnection.

The iOS IPA uses the existing registered-device development distribution. The public Safari installation option remains available. This release does not establish an authenticated Google sign-in result beyond the previously documented native picker checks.

## Release target

- Website: https://intrvioo.com
- Mobile release: https://github.com/AhmedKaram2/FoodRun/releases/tag/v1.7.5
- Android: `FoodRun-Android-1.7.5.apk`
- iOS: `FoodRun-iOS-RegisteredDevices-1.7.5.ipa`
- Deploy the updated API before publishing the new web and mobile clients.
