# Account navigation and phone entry

Home is the default authenticated screen for new Google users and existing accounts. Missing profile contact details do not redirect people into Profile or prevent joining or creating a food room. Previously opened rooms remain in Home's room list. Explicit room invitation links open their join screen and retain the room code through sign-in.

The logo and footer Home button return to Home. Saving Profile returns to Home, or to the join screen when completing details from an invitation. Changing signed-in accounts clears the previous screen selection.

Profile contact phones are optional. A supplied phone must still normalize to a valid international format; names and receiving payment accounts retain their validation. A display name can be entered directly on room creation/join if the account has no saved name.

Web, Android and iOS expose a country selector for profile mobile numbers and an explicit UAE (+971) selector for Aani. National prefixes are removed for countries that use them, while significant zeros, such as Italian landlines, remain. Existing international numbers select their country. Country calling codes and national prefixes were extracted from [Google's libphonenumber metadata](https://github.com/google/libphonenumber/blob/master/resources/PhoneNumberMetadata.xml) on 2026-10-07; names use Unicode CLDR. This is an entry helper; server validation remains authoritative.

Aani mobile aliases accept local, international, Arabic-digit and Persian-digit formats. They normalize to one +971 alias, including inputs with a national zero after the country code. Foreign numbers and landlines remain invalid Aani aliases. IBAN and Aani drafts remain separate.

Server tests cover Google identity exchange, room joining with no contact phone and saving an Aani account. Native tests cover country choices, normalization and returning Home. Browser fixtures exercise the actual authenticated navigation with captured commands: Home default, leaving Profile with the logo, account changes, invitation codes, joining without a phone, country selection and Arabic Aani entry in English/Arabic at 320px, 390px and desktop width.

Browser fixtures and fake identity-provider tests do not exercise a live Google popup or transfer money. Published asset/health verification is recorded separately from authenticated production behavior.
