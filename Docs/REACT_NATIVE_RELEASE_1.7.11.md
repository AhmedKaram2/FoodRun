# FoodRun 1.7.11 (29)

My account has a compact identity card, four visible section tabs and direct shortcuts for personal details, receiving payments, favourites and previous orders. A separate Wallet & payments shortcut opens the existing wallet flow; direction totals and the balance history remain reachable. Switching sections preserves shared profile drafts and saving uses the existing SAVE_PROFILE action.

Small vector illustrations accompany Home, account, wallet, groups, notifications, restaurants, Quick Choice and form headers. Card icons now distinguish favourites, previous orders, receipts, privacy and groups. Shared SVG illustration and icon sources keep the website, Android and iOS consistent. Decorative artwork is hidden from accessibility and adds no animation or network downloads.

The website account page shows a compact identity and direct edit shortcut. All six account sections are visible in a compact grid on phones, with icons on account, room and wallet tabs. Smaller drawings also accompany page headings and empty states.

Validation: mobile TypeScript and 61 tests; 125 web tests; English and Arabic browser checks at 320, 390 and 1440 widths for header size, overflow, language and avatar access, all wallet tabs, direct account editing, draft retention and the existing profile save command. Native account layouts were visually reviewed in English and Arabic. Signed Android release/lint and iOS archive/export passed. Publication and attached-device installation are verified independently.

Android APK and a signed iOS development IPA for registered devices are release downloads. The IPA is not a TestFlight release. The Android update manifest uses the final APK checksum.
