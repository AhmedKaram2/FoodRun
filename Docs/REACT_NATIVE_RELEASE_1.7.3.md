# FoodRun 1.7.3 — shorter mobile forms

Room creation now has four steps: restaurant, people, delivery/fees and review. Continue and Back stay at the bottom. Optional settings are folded into More options, and review cards return to the corresponding step. Joining a room keeps its short form.

Restaurant selection opens a searchable bottom sheet with compact branch rows, optional emirate/area/meal filters and an Add restaurant shortcut. Poll selection keeps the shared controller's 2–12 restaurant limit and selection state. An open-menu sheet lets everyone enter custom food. Returning from restaurant creation or menu editing keeps the room draft and the current step.

Restaurant setup uses five short steps. Detailed menu entries use three steps, and payment-room creation uses four. Payment-room people and shares have a searchable sheet; individual share editing opens a sheet and returns to the same creation step. Receipt totals and assigned shares are checked before review. Only the final creation/save button submits the shared action, and repeated taps are guarded. Existing shared validation, identity, wallet and room commands remain authoritative.

The shared restaurant picker and room setup show branch names in English and Arabic so branches of the same restaurant can be distinguished.

## Validation

- TypeScript and 27 React Native interaction tests passed, covering drafts/live updates, back navigation, restaurant search/selection/addition, menu editor return, polls, payment shares, busy controls and duplicate submission taps.
- All 193 shared tests passed.
- Android signed release and lint passed. Signing certificate is unchanged. The release installed and launched on the Android emulator.
- iPhone archive, IPA export and strict signing verification passed. Version 1.7.3, build 21.
- Native simulator previews covered English and Arabic restaurant/people steps, the compact restaurant sheet and review using fixture data. Authenticated room creation was not performed by these previews.
- Website: 114 tests and production build passed. Website changes only update the release download links and version label.
- FoodRun 1.7.3 was installed and launched successfully on the attached iPhone 15 on 2026-10-08. CoreDevice reported success for both operations.

## Downloads

The Android APK supports direct installation. The development-signed IPA supports registered devices through Xcode or Apple Configurator with Developer Mode enabled. The website also keeps the Safari web-app installation option.

APK SHA-256: `746ea2ab99fc4d0c7188db5900419e332fbd5879db3c6e24bf150b01ecf9c6b9`.

IPA SHA-256: `c98b772bc89acfedaf7c1f6b712d3785fb4fe564ac06b33d577a98cfdfe3ca9a`.
