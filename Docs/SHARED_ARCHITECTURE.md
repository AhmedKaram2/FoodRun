# Quick Spin shared application contract

This document covers the original offline Quick Spin feature. The [full architecture guide](ARCHITECTURE.md) covers group ordering, native network adapters, secure storage and the local hub added in 1.1.

Food Run uses native SwiftUI and Jetpack Compose rendering over one Kotlin Multiplatform application contract. Its cream, orange, typography, wheel motion and platform haptics stay native.

## Ownership

```text
SwiftUI / Compose intent
    → FoodRunController / FoodRunEvent
    → FoodRunSession domain operation
    → FoodRunRepository
    → PreferencesFoodRunRepository
    → FoodRunStorage platform preference adapter

FoodRunController.state
    → immutable FoodRunState
    → native state observer
    → rendering / native presentation adapter
```

`FoodRunController` owns the roster, selected crew, add-person draft and validation, navigation, pending spin plan, winner, history, user preferences and derived presentation values. `FoodRunSession` applies domain rules and coordinates persistence. `PreferencesFoodRunRepository` owns the version 1 JSON schema and sanitizes restored data. The public `FoodRunEngine` remains a small compatibility facade for earlier clients and baseline tests.

The Quick Spin path has no MOHRE runtime, form engine or network dependency. It has its own shared navigation contract. The requested ten names are real product configuration.

## Native bridge

Construct `FoodRunController(storage, timeZone)` on the UI thread. `FoodRunTimeZone.offsetSecondsAt(epochMillis)` supplies each historical instant's UTC offset; common Kotlin performs calendar conversion and display formatting. This preserves daylight-saving history without duplicating date formatters.

Read `controller.state`, then retain `controller.observe { state -> … }`. Observation immediately emits the current state. Call the returned `FoodRunObservation.cancel()` when its owner is disposed. Actions and observer registration/cancellation are confined to the same UI thread. Reentrant events raised by observers are queued until the current state has reached every observer.

Native adapters retain only the observed shared state and ephemeral presentation mechanics: wheel rotation, frame timing, haptic ticks, keyboard focus, scroll position and modal animation machinery. Read `wheelRevision` to reset wheel rotation after a roster mutation. Read `state.spinPlan`, `isSpinning`, `canSpin`, and the returned `beginSpin(startRotation)` plan to drive native animation. Finish through `finishSpin(epochMillis)` exactly once; cancel an interrupted animation through `cancelSpin()`.

Actions may use `dispatch(FoodRunEvent)` or equivalent Objective-C-friendly methods. Both reach the same handlers:

| Intent | Method |
|---|---|
| Show crew / history / add-person | `openCrew` / `openHistory` / `openAddPerson` |
| Back, close, or cancel current presentation | `dismiss` |
| Change / submit name | `updateNameDraft` / `submitName` |
| Change participation | `togglePerson` / `includeEveryone` |
| Remove custom member | `removeAddedPerson` |
| Set haptics | `setHaptics` |
| Start / finish / cancel spin | `beginSpin` / `finishSpin` / `cancelSpin` |
| Clear visible storage error | `dismissError` |

`ADD_PERSON → dismiss → CREW → dismiss → MAIN`. Closing history or the winner returns to `MAIN`. The crew host stays presented underneath the add-person child. A native sheet binding must dispatch a single `dismiss`, and must not also dismiss locally after an action that already changed the shared destination.

## Display and resources

`FoodRunText` centralizes the app's English copy. Derived labels belong to `FoodRunState`, `CrewPersonItem`, `PickupHistoryItem`, or `Person`. Both platforms consume the same participation/accessibility labels, validation messages, status, winner text, initials, wheel labels and history dates.

The product currently has an English catalog. Adding another language should extend this catalog or introduce a resource-backed resolver at this boundary, without adding translation logic to native views.

## Persistence and errors

The JSON schema and existing preference keys remain compatible. Added people, active IDs, up to thirty pickups and the haptics preference survive restart. Pending animation, current destination and name drafts are transient. Original iOS preference migration remains in the iOS storage adapter; legacy keys are preserved.

Saves commit the new in-memory domain snapshot only after the storage adapter accepts the write. A synchronous storage error leaves the previous roster/history intact, preserves a submitted name draft, and publishes `persistenceErrorMessage`. A failed winner save cancels the pending spin and unlocks the wheel without publishing an unsaved winner. Native preference APIs may buffer disk writes; failures occurring after their API returns cannot be detected by this synchronous boundary.

## Applicable audit / service-revamp rules

| Rule | Shared implementation |
|---|---|
| Behavior gate | Original 10 tests + 6 added baseline tests passed before production extraction. |
| Structure / decomposition | Controller, contract, UI models, domain coordinator, repository, formatter and catalog are separate files; controller is below 300 lines and state has fewer than 20 stored fields. |
| KMP state / navigation | All business and presentation decisions listed above are shared. Native presentation is an adapter. |
| Android / iOS parity | One state/event API and one copy catalog are consumed by both adapters; native bridge verification is recorded by the platform work. |
| Date formatting | Common Kotlin formats display dates using per-instant native timezone offsets. |
| Sheet-owned lookups | Not applicable: there are no asynchronous lookups. Crew/history are local persisted data. |
| Unified components | Shared row and screen models expose matching events, flags and text; native component implementation is covered by the component audit. |
| Localization | Copy is centralized in the shared typed catalog; no derived native copy is needed. |
| Engine retirement | No MOHRE form-engine dependencies. `FoodRunSession` is this app's small domain coordinator; `FoodRunEngine` is a temporary compatibility facade. |
| LoadableList | No remote idle/loading/error list lifecycle exists. Native lazy lists render the local crew/history; empty history is an explicit shared state value. |
| Concurrency | No shared coroutine scopes or detached jobs. UI-thread confinement, synchronous state transitions, observer cancellation, duplicate spin guards and animation cancellation are explicit. |
| Cleanup | Serialization moved out of the application controller; duplicated validation, state and formatting are removed from native rendering as adapters migrate. |

Final shared validation: **33 JVM tests passed, with zero failures or errors**. The native bridge review identified feedback replay, lazy roster rendering and error presentation concerns; those are owned by the corresponding platform changes.
