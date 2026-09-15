# Android component and presentation audit

## Applied scope

The applicable audit and service-revamp rules are shared ownership of application
state, controlled input, native rendering, reusable controls, explicit theme
tokens, a shared copy source and small UI files. Food Run is a local, English-only
app. MOHRE's government service engine, network data sources, Koin composition,
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
| Raw copy and error messages could diverge between native screens. | `FoodRunText` is a single typed common Kotlin catalog. Android XML retains only the OS application name. Validation messages delegate to the catalog. |
| Wheel renderer derived display labels and status. | Shared state supplies wheel accessibility status, `Person.wheelLabel` and `Person.initial`; the renderer owns only geometry, truncation fit and native frames. |
| Winner copy differed across platforms. | Winner wording, sharing and action labels come from the shared catalog, matching the original iOS experience. |
| Confetti colors and sizing were defined in its canvas. | Color choices reuse the crew palette; particle dimensions/duration are named drawing tokens. The finite animation stops after five seconds. |

## Component contract

Components are in `androidApp/src/main/kotlin/com/karim/foodrun/components`, with
package `com.karim.foodrun` for simple app-local imports.

| Component | Inputs |
| --- | --- |
| `PrimaryButton` / `SecondaryButton` | `text`, nullable `icon`, `modifier`, `enabled`, `onClick` |
| `FoodTextField` | `value`, `onValueChange`, `label`, nullable `error`, `modifier`, `enabled`, `errorModifier`, `onSubmit` |
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

No generic MOHRE form, backend or asynchronous list framework was added for this
small offline feature. The shared copy catalog is the extension point for future
languages; this release does not claim Arabic or other language support.

## Validation

The source audit checks reusable component state ownership, color/size/font/copy
token usage and file sizes. New scoped UI files are all below the 250-line limit.
No rendering component owns mutable roster, input draft, validation, navigation
or winner selection. Button press state, keyboard focus, canvas paint and timed
confetti progress are native rendering concerns.

Compilation, shared behavior tests, emulator interaction checks and the installable
APK are recorded in the main project validation report after integration. This
scoped audit alone does not claim a successful platform build or device test.
