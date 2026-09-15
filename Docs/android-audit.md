# Android component and presentation audit

## Applied scope

The applicable audit and service-revamp rules are shared ownership of application
state, controlled input, native rendering, reusable controls, explicit theme
tokens, a shared copy source and small UI files. Food Run is a local, English-only
app with local-network group ordering. MOHRE's government service engine, network data sources, Koin composition,
moko-resource pipeline and Phosphor-only icon requirement are not needed here.

The corresponding iOS source was inspected at
`/Users/karim/WORK/MohreMobile/IosComponents`. Android uses Compose equivalents
with Food Run's existing orange/cream palette, bundled rounded font, gradient
primary button and spring press animation. It does not depend on MOHRE.

## Findings resolved

| Finding | Resolution |
| --- | --- |
| Button rendering, token definitions and vector artwork shared one file. | Separate native components from `FoodTheme` and the custom wheel's `FoodWheelTokens`. |
| Input held a second draft and validation error inside the native dialog. | `FoodTextField` accepts value, change event, error and submit event; shared state supplies the data. Only keyboard focus remains native. |
| Initial avatars were recreated across home, crew and history. | `FoodAvatar` supports circular and rounded variants, optional rim and shared `Person.initial`. |
| Grouped rows and cards duplicated backgrounds/corners. | `FoodCard` and `FoodListGroup` centralize container styling. |
| Selection checkbox decoration duplicated row semantics. | `FoodSelectionIndicator` is decorative; the containing row owns the checkbox label, checked value and event. |
| Add-person CTA duplicated button behavior. | `SecondaryButton` uses the same press, enabled and click renderer as `PrimaryButton`. |
| Raw copy and error messages could diverge between native screens. | `FoodRunText`, `GroupText`, and shared group presentation supply product copy and validation. Android XML contains the OS application name and messages for native permissions, file access and transport failures. |
| Wheel renderer derived display labels and status. | Shared state supplies wheel accessibility status, `Person.wheelLabel` and `Person.initial`; the renderer owns only geometry, truncation fit and native frames. |
| Winner copy differed across platforms. | Winner wording, sharing and action labels come from the shared catalog, matching the original iOS experience. |
| Confetti colors and sizing were defined in its canvas. | Color choices reuse the crew palette; particle dimensions/duration are named drawing tokens. The finite animation stops after five seconds. |

## Component contract

Components are in `androidApp/src/main/kotlin/com/karim/foodrun/components`, with
package `com.karim.foodrun` for simple app-local imports.

| Component | Inputs |
| --- | --- |
| `PrimaryButton` / `SecondaryButton` | `text`, nullable `icon`, `modifier`, `enabled`, `onClick` |
| `FoodTextField` | `value`, `onValueChange`, `label`, nullable `error`, `modifier`, `enabled`, `errorModifier`, `multiline`, `secret`, `keyboardOptions`, `onSubmit` |
| `FoodAvatar` | `person`, `modifier`, `size`, `circular`, `bordered` |
| `FoodCard` | `modifier`, `fill`, `bordered`, `radius`, column `content` |
| `FoodListGroup` | `modifier`, column `content` |
| `FoodSelectionIndicator` | `included`, `modifier` |
| `FoodDivider` | `modifier`, `color`, `thickness` |
| `FoodBag` / `HistoryIcon` | drawing modifier and optional FoodBag tint |

`FoodTheme` supplies Material colors and typography so native text buttons,
text fields and other standard controls use the same theme. Defaults preserve the
app's primary control appearance. Error copy uses a darker orange for legibility.

## Intentional rendering details

The wheel, pointer, food bag and history icon are custom vector artwork. Normalized
path coordinates, angular math and particle trajectories remain beside that
artwork; reusable spacing, sizing, border, font and color values use named tokens.
Platform-standard icons retain native rendering. Native sharing is invoked only
by the user's explicit share-button tap.

No generic MOHRE form or asynchronous list framework was added. The group feature
uses its own local Kotlin hub and shared contracts. The shared copy catalog is the extension point for future
languages; this release does not claim Arabic or other language support.

## Validation

The source audit checks reusable component state ownership, color/size/font/copy
token usage and file sizes. New scoped UI files are all below the 250-line limit.
No rendering component owns mutable roster, input draft, validation, navigation
or winner selection. Button press state, keyboard focus, canvas paint and timed
confetti progress are native rendering concerns.

The original quick-spin behavior remains covered by shared tests and prior native
checks. The following section records the additional group-order adapter checks.

## Group ordering audit — 15 September 2026

The native UI renders `GroupState` and dispatches typed `GroupAction` / field
updates. Business rules, room membership, quotes and selected winners remain in
shared Kotlin and the authoritative local hub.

| Finding | Resolution |
| --- | --- |
| Activity recreation discarded room drafts and active controller state. | `GroupViewModel` retains the controller and application-scoped services; Activity result launchers reattach to the current Activity. Composition removes its own observer without closing the controller. |
| Errors at the top of a long form were hidden after scrolling to Add item or Save. | A sticky themed `GroupErrorBanner` stays above the form. It announces errors to accessibility services, caps its height and allows long messages to scroll. |
| Group fields/cards duplicated native styling. | Reuse `FoodTextField`, `FoodCard` and primary/secondary buttons with the existing orange/cream tokens. |
| Group spin used wall-clock sampling and ignored reduced-motion settings. | The frame renderer uses monotonic elapsed time and the shared server offset/rotation plan. Reduced motion shows the final angle at completion without spinning. |
| Menu import used an API 33-only stream method despite supporting Android 8. | A bounded stream reader supports API 26 and rejects menus exceeding 2 MiB. Native import work runs on a file executor. |
| Storage could be overwritten incompletely or accept damaged ciphertext. | Android Keystore-backed AES-GCM authenticates snapshots; AtomicFile keeps complete writes. Tampering/truncation throws a load failure. Keys and encrypted data are excluded from backup/device transfer. |
| Every HTTP command created another unmanaged network client. | Clients are reused per paired hub and closed with their platform owner. HTTP reads are limited to 4 MiB and requests have connect/read/call timeouts. |
| Redirects could forward room credentials to another endpoint. | Both ordinary and TLS redirects are disabled. The dedicated trust manager accepts only the exact paired certificate fingerprint after checking validity. |
| WebSocket callbacks and retries could race with cancellation. | Main-thread generation checks discard stale sockets; bounded exponential retry with jitter and explicit cancellation remove pending reconnects. |
| Closing pooled TLS sockets could perform network I/O on the UI thread. | Real-hub testing exposed `NetworkOnMainThreadException`; final connection-pool teardown runs on a background cleanup executor. |
| DNS-SD discovery could leak listeners or run concurrent unsupported resolves. | Discovery uses a timeout, serial resolution and cancellation/cleanup paths; manual pairing remains available. |

Native HTTPS requests are bounded before decoding. The WebSocket text limit is
checked after OkHttp assembles a message; the trusted server also limits outbound
snapshot size. This is a local-hub design, not an untrusted public-server client.

### Emulator adapter tests

`GroupNativeTest` passed **6 of 6 tests, with no failures or skipped tests** on
`emulator-5554`, using the actual local hub at `https://10.0.2.2:8443`:

1. Encrypted data survives reopening through a new store, excludes plaintext on disk and uses a fresh nonce on each write.
2. Tampered/truncated ciphertext and path traversal storage names are rejected.
3. Stream reads accept the exact limit and reject one additional byte.
4. Activity recreation retains the same shared controller and unsaved form draft.
5. Native HTTPS creates a test room, joins it, resumes the same membership, receives a real WebSocket snapshot and stops callbacks after cancellation.
6. A mismatched certificate fingerprint rejects the real hub.

The final adapter replay completed in **1.886 seconds** after the TLS cleanup
fix, latest shared changes and sticky banner: **6 passed, 0 failed, 0 skipped**.
The signed release was then installed over the debug build without uninstalling
or clearing app data. Opening the saved `Lunch Together QA` room displayed
`Order #2 · Gathering · Together Kitchen`, code `762390`, and
`Connected · saved on this device`.

The combined Android/iOS meal, offline receipt, hub restart and next-order UI
checks are recorded in the main validation report. These results do not claim
physical-device or real-camera QR validation.

### Final Android 1.1 artifact

All four tasks passed together after the final shared changes:

```text
./gradlew :androidApp:assembleDebug :androidApp:assembleRelease :androidApp:assembleDebugAndroidTest :androidApp:lintDebug
BUILD SUCCESSFUL in 9s
```

Lint reported **0 errors and 16 warnings**. Warnings concern dependency updates,
existing launcher/font resource details and optional KTX style suggestions.

- Local artifact: `Distributions/FoodRun-1.1.apk` (generated installer excluded from Git; see [build instructions](../README.md#build-android)).
- Package: `com.karim.foodrun`
- Version name/code: `1.1` / `2`
- Size: `7,898,104` bytes
- APK SHA-256: `130437bcaf13338f3928d1490c3984c9d676132d1a599f12921c6c65c2162d08`
- `apksigner verify --verbose --print-certs`: **Verifies**, APK Signature Scheme v2 valid.
- Signing certificate SHA-256: `3b8c26c081e463f2af52d2fd725892723da2fb94f308225663414487ab70c072`

The configured local signing key is reused for development testing so the emulator
can upgrade its existing installation without deleting saved rooms or receipts.
Signing files remain outside version control. No physical phones were installed
or operated during this audit.
