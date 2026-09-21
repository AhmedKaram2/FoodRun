# Food Run audit — 21 September 2026

Scope: web, Android, iOS, shared domain/controller, and room server. Includes the requested mobile improvements, explicit restaurant poll selection, and Egyptian Arabic. Changes are local; this report does not establish a production deployment or a store release.

## Changes made

- Restored missing restaurant location/meal metadata when loading older saved libraries, including offline. Kept saved menus, contacts, names, and export revisions.
- Refreshed mounted web restaurant screens when the catalog changes; cancelled stale catalog requests after switching hubs. Changing Emirate clears the previous area on native.
- Added explicit administrator deletion records, persisted atomically with catalog changes. Updated clients respect these deletions instead of restoring bundled restaurants. The existing `/catalog` array remains compatible; `?includeDeleted=true` returns catalog/deletion data, with client fallback for older servers. Native home payloads carry the same deletion IDs. Older deletions made before this change cannot be reconstructed from the legacy array alone.
- Polls now use only the restaurants the organizer chooses. Web, Android, and iOS support 2–12 candidates with searchable Emirate/area/meal filters, selected counts, removal, and validation. No automatic first-12 catalog selection. The first selected candidate is the initial room choice required by the server contract. Native next-order setup clears the previous poll draft.
- Made Create Room simpler on phones: restaurant choice first on web, suggested room name, direct pickup/delivery controls, optional fees in an expandable section, larger controls, and a sticky create action. Native setup groups optional fees and guest names. The web restaurant picker supports focus containment, Escape, focus restoration, background scroll locking, and a compact layout when the keyboard reduces available height.
- Improved narrow-screen restaurant editing, top bars, sign-in placement, and action areas. Retained public download links. Inputs are at least 16px on mobile.
- Added a reviewed Egyptian Arabic override dictionary and generated native translations from the same web dictionaries: **825 shared entries**, including **432 Egyptian overrides/additions**. Localized native field labels, action labels, sections, photo controls, web conditional labels, menu editing, receipts, and common dynamic messages. Template replacement preserves names, room codes, and amounts. Restaurant names/menu content use supplied Arabic where available.
- Corrected the published menu schema to allow empty menus for open-order restaurants. Corrected outdated server documentation about administrator login.

## Verification performed

| Check | Result |
| --- | --- |
| `npm test --prefix webApp` | 24 passed |
| `npm run build --prefix webApp` | Passed; main bundle about 321 kB / 82 kB gzip |
| `node Scripts/sync-ui-translations.mjs --check` | Native/web dictionaries match; no duplicate keys or blank dictionary values |
| Gradle `check` | Passed: domain 61, contract 12, shared 158, server 71 test executions; zero failures/errors/skips in the XML reports |
| Android debug, instrumentation APK, release build | Passed |
| Android release lint | Zero errors; 17 warnings, mostly dependency-update notices and existing resource/style suggestions |
| Android emulator instrumentation | 8 passed, including encrypted storage, malformed input, lifecycle, pinned HTTPS, WebSocket reconnect, and rejection of an incorrect certificate pin |
| iOS arm64 simulator build and XCTest | 24 passed, including real Keychain storage, certificates, native HTTPS/WebSocket integration, input/storage validation, and wheel behavior |
| Real isolated HTTPS hub workflow | Two fake participants: create/join/approve, select payer, share fixture receiving account, priced menu with size/extra, submit carts, confirm totals, place, record payment, declare/confirm reimbursement, fulfill, archive. Final phase `ARCHIVED`; receipts conserved delivery 501, service 199, discount 100 minor units; total AED 57.00 |
| Browser fixture interactions | Poll opens without forcing the keyboard; Sharjah shows Kalha, Al Mahla, and Sultan; exactly those three selected IDs are submitted; removal/minimum-two rules work; direct-order toggle works; library refreshes while mounted; Laffah's 800 minor units display as 8.00 |
| Browser layout | Create, Join, Home, Profile, Library, and Room rendered without horizontal overflow at 320px, 390px, and 1440px; English and Arabic checked; 390px Arabic with 150% root font checked |
| Short browser viewport | At 390×400, poll footer ends at y=387 and results retain 127px of scrollable height |
| `git diff --check` / redacted diff secret scan | Passed |

Kotlin counts are executions across configured targets, not distinct UI scenarios. Browser screens use synthetic fixtures and capture commands locally. They do not prove authenticated Firebase behavior. Native transport tests use a dedicated local TLS hub; no production room, real restaurant order, message, or money transfer was created. The separate local Node workflow accepts only this test hub's self-signed connection; native certificate pin validation was exercised independently.

The browser harness is [webApp/test/browserAudit.jsx](../webApp/test/browserAudit.jsx). Start Vite and use a fresh browser context. In its developer console run `await (await import('/test/browserAudit.jsx')).runCreateAudit()` or `runLibraryAudit()`. `mountAudit('home' | 'profile' | 'join' | 'library' | 'room')` renders other fixture screens. This harness is not imported into the production bundle. Native tests require an isolated local hub and the optional ignored test configuration described in the existing native suites.

## What remains unverified or requires rollout

1. **Production catalog:** the live API returned four legacy restaurants, all without Emirate fields, during this audit. The app bundles 43 and restores the missing metadata. The new administrator-deletion behavior requires the updated server as well as updated clients. Production catalog migration/deployment has not been performed here.
2. **Google ratings and restaurant data:** automated checks validate stored rating ranges and catalog structure; they do not prove current Google ratings or current menu prices. Some bundled entries have no rating metadata, phone number, or priced menu. Open-order entries require prices/contact/tax confirmation before placement. No direct Google/Zomato integration or new rating verification was performed.
3. **Fresh authenticated acceptance:** Firebase registration/password reset, real account switching across web/Android/iOS, production reconnect/offline reentry, cloud restore, and a complete signed-in three-client meal were not repeated. Existing account/domain/controller/server tests cover their underlying rules, with the limits noted above.
4. **Device-only behavior:** real notification delivery, physical camera/QR permissions, photo-library permission prompts, native share sheets, screen-reader walkthroughs, and physical-device release distribution remain unverified in this run.
5. **Arabic content limits:** application field labels and main flows were checked; restaurant/user-entered content remains as supplied. Unmapped external/server errors and some secondary dynamic explanatory/export text may still appear in English. The shared lookup intentionally preserves unknown text instead of replacing it with a misleading generic message.

Implementation is verified locally within these boundaries; this is not a claim that every production or physical-device flow has been exercised.
