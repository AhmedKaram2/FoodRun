# Food Run component reuse audit

## Scope and source

The Food Run wheel, main screen, crew, add-person sheet, history and winner overlay
are audited against the applicable component-reuse and service-revamp rules. Food
Run retains its orange/cream palette, rounded typography, native icons and wheel
animation. MOHRE-specific gold branding, government form engines, network lookups,
Phosphor-only policy and generated resources are outside this app's requirements.

Requested iOS source: `/Users/karim/WORK/MohreMobile/IosComponents`, inspected at
MOHRE repository revision `3ca7f57ee058c48295565f92d4ead0d6a25d8446`.
The original source remains unchanged. A focused source snapshot makes Food Run
portable without depending on the author's absolute filesystem path.

## Findings and treatment

| Classification | Finding | Treatment |
| --- | --- | --- |
| `[EXTEND_DS]` | `FilledPrimaryButton` is the existing filled-button component, but ignores supplied shape and icons and assumes a fixed solid-color height. | Extend the snapshot with optional gradient, content padding, icon font/spacing, border, shadow and pressed appearance; preserve existing default arguments. Food Run passes its existing appearance through `PrimaryButton`. |
| `[EXTEND_DS]` | `BaseTextInputField` stores an independent `@State` copy of input, ignores the supplied font, and does not apply enabled/read-only semantics. | Render the supplied text through a binding that emits `onValueChange`. Extend appearance and focus hooks, preserving original initializer labels. Shared state remains the source of truth. |
| `[EXTEND_DS]` | `HorizontalDivider` lacks an external initializer and a precise solid rule can differ from native `Divider`. | Expose height/color as parameters and render the requested rule at its exact height. |
| `[REUSE]` | Existing ComponentsTheme separates colors, spacings, shapes and typography. | Use the focused theme source with Food Run values and native rounded fonts. |
| `[EXTRACT_REUSABLE]` | Initial avatars recur on the home, crew and history views. | Extract a Food Run avatar with size/shape variants; mirror Android's native implementation. |
| Intentional custom rendering | The segmented spinning wheel, flexible labels, pointer and confetti have no matching library component. | Keep native Canvas rendering and motion; no generic component can preserve these visuals. |
| Intentional native presentation | SwiftUI navigation and sheets handle system gestures, keyboard and accessibility. | Keep native chrome with all navigation decisions and name drafts supplied by shared state. |

## Public app component contract

| iOS | Android | Shared input/events |
| --- | --- | --- |
| `PrimaryButton` / `SecondaryButton` | `PrimaryButton` / `SecondaryButton` | title, enabled state, icon and click action |
| `FoodTextField` | `FoodTextField` | value, onValueChange, label, error, enabled, onSubmit |
| `FoodAvatar` | `FoodAvatar` | person name/color and size variant |
| `FoodDivider` | `FoodDivider` | theme color and thickness |
| `FoodCard` | `FoodCard` | corner radius, background opacity and border |

Components do not retain roster, draft text, validation or navigation state.
Keyboard focus and pressed/entrance animation are native visual state.

## Portability and adaptation policy

The package includes only the button, input, divider and supporting theme files
used by Food Run. Kingfisher, Lottie, MOHRE brand font resources, remote images,
unrelated dialogs, pager indicators and network dependencies are excluded.
Original source author headers remain in copied files. No license terms are
invented: the supplied source tree did not contain a LICENSE file.

The typography adapter accepts system/rounded font configuration instead of
registering bundled MOHRE fonts. Food Run supplies fonts explicitly, keeping its
established design. Package defaults remain available for other local callers.

## Text and localization boundary

The current release is English-only. `FoodStrings.text` reads the shared
`FoodRunText` catalog, so static wording, plural forms, validation, accessibility
and date labels have one owner across platforms. Additional languages require a
locale-aware catalog implementation; this change does not claim translated UI.

## Validation

The focused package passed standalone Swift compiler typechecking against the
iOS simulator SDK before app migration and after appearance extensions. The
coordinated iOS application build/test run and native simulator smoke tests are
recorded in the parent audit report. The app's shared behavioral tests cover name validation,
crew inclusion, persistence, spins and navigation. Visual verification checks
button height/shape, input spacing, roster avatars and winner reveal.

## Scalability follow-up

`ContentView` hosts native presentations and the winner transition, while
`FoodRunContent` renders the unchanged screen layout. `CrewSheet` renders shared
`crewItems` through `LazyVStack`; each row receives its item directly, avoiding
repeated roster scans. The home avatar preview maps only the shared three-person
preview. Save failures remain visible inside Crew/Add Person sheets, and the
root error alert is presented only from the main screen or winner overlay.
