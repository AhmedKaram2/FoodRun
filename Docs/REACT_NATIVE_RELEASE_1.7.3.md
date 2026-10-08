# FoodRun 1.7.3 — shorter mobile forms

New room creation and joining use the Internet API directly. Connection choices are offered after Internet connection failure. Existing saved commands keep their original hub and must be confirmed before switching. Returning from connection options keeps the draft and step. New rooms use Running names / الأسماء المتحركة by default; the shared live selection result and existing room styles are preserved. Search fields show English and Arabic guidance.

Room creation now has four steps: restaurant, people, delivery/fees and review. Continue and Back stay at the bottom. Optional settings are folded into More options, and review cards return to the corresponding step. Joining a room keeps its short form.

Restaurant selection opens a searchable bottom sheet with compact branch rows, optional emirate/area/meal filters and an Add restaurant shortcut. Poll selection keeps the shared controller's 2–12 restaurant limit and selection state. An open-menu sheet lets everyone enter custom food. Returning from restaurant creation or menu editing keeps the room draft and the current step.

Restaurant setup uses five short steps. Detailed menu entries use three steps, and payment-room creation uses four. Payment-room people and shares have a searchable sheet; individual share editing opens a sheet and returns to the same creation step. Receipt totals and assigned shares are checked before review. Only the final creation/save button submits the shared action, and repeated taps are guarded. Existing shared validation, identity, wallet and room commands remain authoritative.

The shared restaurant picker and room setup show branch names in English and Arabic so branches of the same restaurant can be distinguished.

## Validation

- TypeScript and 30 React Native interaction tests passed, covering drafts/live updates, back navigation, restaurant search/selection/addition, menu editor return, polls, payment shares, busy controls and duplicate submission taps.
- All 196 shared tests passed.
- Android signed release and lint passed. Signing certificate is unchanged. The release installed and launched on the Android emulator.
- iPhone archive, IPA export and strict signing verification passed. Version 1.7.3, build 21.
- Native simulator previews covered English and Arabic restaurant/people steps, the compact restaurant sheet and review using fixture data. Authenticated room creation was not performed by these previews.
- Website: 114 tests and production build passed. English and Arabic browser checks passed at 320, 390 and 1280 pixels for the default selection, Internet-failure fallback, search guidance, global navigation and installation links.
- The initial stepper build was installed and launched on the attached iPhone 15 on 2026-10-08. The final defaults build awaits reinstallation after the device connection became unavailable.

## Downloads

The Android APK supports direct installation. The development-signed IPA supports registered devices through Xcode or Apple Configurator with Developer Mode enabled. The website also keeps the Safari web-app installation option.

APK SHA-256: `b0c8857eb3841d2a6975339b9819d608c07310e691d7676cc74e2266577d2d92`.

IPA SHA-256: `94bfcb9a14d78199e5c0bf09b33bcd45324d661049ee1bad81ff7624411ee480`.
