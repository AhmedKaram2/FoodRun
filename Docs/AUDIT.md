# Food Run audit and revamp

## Scope and evidence

Applied the user-requested audit and service-revamp rules to the standalone Food Run app. Baseline gates passed before production changes: 16 shared behavioral tests and 11 iOS tests. The final suites pass 33 shared tests and 12 iOS tests. Tests use isolated preference suites or storage fakes at the repository boundary; production contains no test roster or fake service data.

## Applicable rules applied

| Rule | Implementation |
| --- | --- |
| Shared state and validation | `FoodRunController` exposes immutable state; native fields render the shared name draft, errors and eligibility. |
| Shared navigation | Typed destinations drive crew, add person, history and winner. Native back/dismiss sends one shared event. |
| Shared derived display data | Row labels, counts, winner copy, initials, wheel labels and history dates originate in Kotlin. |
| Thin platform adapters | Native code owns animation frames, platform preferences, timezone offsets and feedback. |
| Reuse and theme | Focused IosComponents package adapted with provenance; matching stateless Compose controls and centralized tokens. |
| Component correctness | Reused iOS buttons honor icons/shape/enabled state; input renders externally supplied state without a second text copy. |
| Scalable rendering | Native crew lists render lazily with stable person IDs; no per-row linear roster searches. |
| Screen structure | Both platforms separate the presentation host, content and reusable components. |
| Lifecycle and concurrency | Shared observers cancel with native owners; Android viewModelScope owns spin work; teardown cancels pending spin. Winner feedback is consumed once per result. |
| Error visibility | Shared save errors remain visible in the active form/sheet; rejected saves leave prior data intact. |
| Regression protection | Behavioral baseline plus navigation, draft, retry, cancellation, dates and legacy-upgrade coverage. |

## Rules intentionally outside this app’s scope

Food Run has no MOHRE FormDsl engine, API service, lookup fetcher, Koin graph, centralized MOHRE router, bilingual resource setup or remote loading lifecycle. Importing those frameworks would add unrelated dependencies. Native SF Symbols and drawn Android icons suit this app. Current copy is centralized English, not a claim of multilingual localization. No unrelated external source files were changed.

## Verification — 15 September 2026

| Check | Result |
| --- | --- |
| `:shared:jvmTest` | 33 passed, zero failures/errors |
| iOS XCTest, iPhone Air iOS 26.0.1 | 12 passed, zero failures |
| iOS build/launch, iPhone 16e | Passed |
| Shared Apple XCFramework | Device arm64 and simulator arm64 built successfully |
| `:androidApp:assembleRelease :androidApp:lintRelease` | Passed, zero lint errors |
| Android release installation | Passed on arm64 emulator |
| APK signature | Verified, APK signature scheme v2 |
| APK page alignment | `zipalign -c -P 16 4` passed; arm64 native library LOAD alignment 16 KB |
| Android duplicate-name form | Karim rejected, error rendered, correction accepted |
| Android add and restart | TestFriend added, included, restored after process restart |
| Android custom removal | TestFriend removed through UI; original ten preserved |
| Android spin and history | Gaber revealed; pointer, status and history matched; history restored after restart |
| iOS controlled input | Karim rejected through reused input; shared error rendered |
| iOS nested-sheet cancellation | Cancel returned to crew, Done returned to the wheel |
| Legacy preference regression | Custom name, participation, historical date and haptic setting survive shared-schema migration |

Android lint has non-blocking dependency-update, font fallback, icon and KTX suggestions. Dependencies remain pinned to the versions built and exercised here. Physical iPhone distribution requires the owner’s Apple signing team; the Android APK is ready to install directly.

Detailed findings: [component provenance](component-reuse.md), [shared architecture](SHARED_ARCHITECTURE.md), [shared coverage](SHARED_COVERAGE.md), [Android components](android-audit.md).
