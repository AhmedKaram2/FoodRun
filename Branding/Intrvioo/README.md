# Intrvioo brand integration

The supplied Downloads branding kit is preserved in `source/`, including the original board, logo variations, banners, icon references, social references, and machine-readable tokens. `order-together-reference.png` preserves the second supplied visual direction.

The product uses the kit's coral `#FF684A`, charcoal `#1F2937`, cream `#FFF7ED`, fresh green `#22C55E`, yellow `#FBBF24`, and light green `#E8F5E9`. Poppins Regular, Medium, SemiBold, and Bold are bundled locally with their Open Font License; Noto Sans Arabic remains the Arabic typeface.

The supplied logo PNGs include labels and painted backgrounds. `assets/intrvioo-symbol.svg` recreates the people/table mark as a clean vector. The web header combines that mark with the Poppins wordmark. Native headers, launcher icons, favicon, Apple touch icon, PWA icons, and social preview are rendered from the vector by `Scripts/build-brand-assets.mjs`. Existing installed PWAs also receive the new artwork at their original icon URLs.

The home screen uses a three-slide manual pager: “Food is better together,” “Different tastes. Same table,” and “Split the bill. Keep the good times.” Order/join actions are real buttons, and English/Arabic copy follows the selected language. The first slide and sign-in page display the supplied group's illustration through a CSS viewport. Rounded surfaces, soft shadows, clear shortcuts, and coral actions carry the brand through the website. The guide, Android, and iOS share the updated palette and typography. The website guide also uses the illustrated cream hero, coral role/navigation cards, refreshed screen examples in both languages, and matching image-enlargement controls.

Website primary buttons use white labels on an accessible coral shade, `#CF442B` (4.65:1 contrast), with `#B9361F` on hover (5.82:1). Secondary actions use coral text on white. Native primary buttons retain their existing charcoal labels. Small coral text uses `#B83D27` (5.30:1 on cream); large accent headings use `#DE4B32` (3.83:1 on cream). Green text uses a darker shade for readability.

To rebuild raster assets, run Vite on port 5174 and an isolated Chrome debugging session on port 9334, then run `node Scripts/build-brand-assets.mjs` from the repository root.

Validation: production web build and 86 web tests pass. Browser checks cover all three pager slides and home/sign-in screens in English and Arabic at 320, 390, 768, and 1440 pixels, with no horizontal overflow or runtime exceptions. Android debug and iOS ARM64 simulator builds pass. Browser walkthroughs use the existing sample-only preview harness; authenticated ordering is outside that visual validation.
