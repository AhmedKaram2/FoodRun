# FoodRun 1.7.12 (30)

The compact account tab appearance is now shared across native room sections, wallet, payment subtabs, profile and its nested sections, notifications, restaurant library, groups, generic section navigation and administration. Each destination has an icon above its label, a rounded border and a mint selected state. Up to five tabs stay visible; larger sets wrap. Menu category filters use matching surfaces and icons while remaining horizontally scrollable when the menu has many categories.

The website uses the same compact icon-above-label style for mobile page, room, wallet, group and administration tabs. Every destination remains accessible, and existing selection state, account drafts, room-tab persistence, enabled commands and payment flows remain intact.

Validation: TypeScript; 61 native tests including room tab persistence, wallet approval groups, receipt/history selection and profile editing; 125 web tests; English/Arabic responsive checks at 320, 390 and 1440 widths; native room and wallet visual previews. Signed Android release/lint and iOS archive/export are validated independently of deployment and attached-device installation.

The release downloads include the Android APK and an iOS development IPA for registered devices. The Android manifest binds the update URL and SHA-256 to the final APK. The IPA is not a TestFlight release.
