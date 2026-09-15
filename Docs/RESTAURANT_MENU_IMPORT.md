# Turn a restaurant menu into Food Run JSON

Give an AI agent a menu PDF, photos, text, or an accessible menu page plus the files below. The agent produces `restaurant-menu.json`, which you can preview and save in Food Run's restaurant library.

## Files to give the agent

| File | Purpose |
| --- | --- |
| [AI conversion prompt](AI_MENU_PROMPT.md) | Copy-paste instructions for extracting the full menu and resolving missing information. |
| [JSON Schema](restaurant-menu.schema.json) · [raw download](https://raw.githubusercontent.com/KarimMetawea/FoodRun/main/Docs/restaurant-menu.schema.json) | Machine-readable structure, allowed properties, types and limits. |
| [Complete example](restaurant-menu.example.json) · [raw download](https://raw.githubusercontent.com/KarimMetawea/FoodRun/main/Docs/restaurant-menu.example.json) | A fictional restaurant with categories, size variants, extras and a simple item. |

The **schema describes the format**. The **example and generated menu contain restaurant data**. Import the generated menu into Food Run; importing the schema document itself will fail.

## From menu to saved restaurant

1. Attach all menu pages and the schema/example files to your AI agent. If the agent cannot open GitHub links, download and attach the actual files.
2. Paste the [conversion prompt](AI_MENU_PROMPT.md). Supply the restaurant name, branch and currency if the menu does not state them. For an update, attach the existing Food Run export too.
3. Answer questions about unclear prices or choices. Check the output against the source: item count, sizes, currency, prices, extras, tax and delivery charges.
4. Save the result as a UTF-8 file named `restaurant-menu.json`.
5. In Food Run, open **Restaurant library → Import menu JSON**, choose the file, review the preview, then tap **Confirm import**. Alternatively, open **Paste menu JSON**, paste the content, and tap **Preview JSON** followed by **Confirm import**.
6. Select the saved restaurant when organizing an order. Confirm that order's fees before continuing.

An import with the same `restaurant.id` replaces that saved restaurant on this device. It does not merge individual items or update existing room menus. Preserve the ID for menu revisions; use a different ID for a different restaurant or branch. `revision` is positive metadata, not a safeguard against importing an older file—review the file before confirming.

## Example source menu

The [example JSON](restaurant-menu.example.json) represents this fictional menu:

> **Example Kitchen — Example branch**
>
> Currency: AED. Listed prices include tax. Delivery: AED 10.00. Service fee: AED 0.00. Minimum order: AED 0.00.
>
> **Mains:** Burger — Regular AED 35.00; Large AED 42.00.
>
> Optional burger extras: Extra cheese +AED 3.00, Extra sauce +AED 1.00. Choose zero, one or both; each extra once.
>
> **Drinks:** Water — AED 3.00.
>
> Both items available. Phone, WhatsApp and address not supplied.

In that file, a large burger with cheese costs `4200 + 300 = 4500` minor units, or **AED 45.00**, before order-level fees. The variant replaces the base price. Water has no variants or extras.

## Field reference

| Object | Fields and meaning |
| --- | --- |
| Root | `schema: "foodrun.restaurant"`, `schemaVersion: 1`, `exportId`, positive `revision`, `restaurant`. Generate a new export identity for a new restaurant; preserve it for revisions. |
| `restaurant` | Stable `id`, `name`, `branchName`, `currency`, `contact`, `pricing`, `notes`, `menu`. |
| `contact` | `phoneE164`, `whatsappE164`, `address`. Use `null` when unknown. A WhatsApp link is not a phone number. |
| `pricing` | `taxTreatment`, `taxRateBasisPoints`, `defaultDeliveryFeeMinor`, `defaultServiceFeeMinor`, `minimumOrderMinor`. Fees/minimums are fixed money amounts. |
| `menu.categories[]` | Unique `id`, `name`, integer `sortOrder`. Use one “Menu” category if the source has no headings. |
| `menu.items[]` | Unique `id`, existing `categoryId`, `name`, `description`, `basePriceMinor`, `available`, `variants`, `optionGroupIds`. |
| `variants[]` | `id`, `name`, `priceMinor` as the full replacement price. IDs must be unique within that item. A variant selection is required when variants exist. |
| `menu.optionGroups[]` | Unique `id`, `name`, `minSelections`, `maxSelections`, `options`. Link a group to each applicable item using `optionGroupIds`. |
| `options[]` | `id`, `name`, `priceDeltaMinor` as the additional charge. IDs must be unique across all groups; zero is a free choice. |

The schema lists which fields the importer can default. The AI prompt asks for the complete form shown in the example so currency, tax and optional collections are explicit. Unknown properties are rejected everywhere. Do not add `$schema`, image URLs, source metadata or payment details to the generated file.

### Prices and currencies

Write money as nonnegative JSON integers in **minor units**, never formatted prices or decimal numbers.

| Supported currencies | Multiplier | Example |
| --- | --- | --- |
| AED, USD, EUR, GBP, SAR, EGP | ×100 | AED 35.50 → `3550` |
| KWD, BHD, OMR | ×1000 | KWD 1.250 → `1250` |
| JPY | ×1 | JPY 500 → `500` |

Every money field must be between `0` and `100000000`, inclusive. Do not round excess decimal places or substitute zero for an unreadable price. Ask the menu owner to resolve those values.

### Tax and missing information

- **`included`:** the menu explicitly includes tax in listed prices. Food Run does not add it again.
- **`added`:** the menu explicitly adds tax; `taxRateBasisPoints` is required. A 5% rate is `500`. Keep listed prices before tax. Food Run applies added tax to food after discounts plus delivery/service fees; confirm this matches the restaurant's rule.
- **`unspecified`:** the source does not state tax treatment. Food Run adds no separate tax in this mode; confirm the tax rule before relying on the total.

Use `null` for an unknown tax rate or contact field, and empty text for an absent description/branch. The fee/minimum fields cannot be null: use `0` when unprovided and record that uncertainty in `restaurant.notes`; confirm the actual amounts for the order. Missing restaurant names, currencies, item prices and unclear option rules need clarification before final JSON generation.

Percentage service fees, negative option discounts, mixed item tax rates, and extras whose prices depend on the selected size have no direct representation in this format. Have the owner confirm a supported representation; do not silently invent one.

### Choice rules

`0 <= minSelections <= maxSelections <= options.length` must hold for every group. For a required “Choose one sauce” group, use `minSelections: 1` and `maxSelections: 1`. For the example's two independent optional extras, use `0` and `2`. Every option can be selected at most once per item. An option group only affects the items that reference its ID.

## Validation and limits

The schema uses [JSON Schema Draft 2020-12](https://json-schema.org/draft/2020-12/json-schema-validation). A compatible validator checks structure, allowed fields and numeric/string/collection limits. It does not fill in the documented defaults.

Food Run's [actual importer](../order-domain/src/commonMain/kotlin/com/karim/foodrun/orders/Menu.kt) and [JSON input checks](../order-domain/src/commonMain/kotlin/com/karim/foodrun/orders/JsonInputValidation.kt) are the final authority. In addition to schema validation, check:

- **References and identity:** each item category and linked option group exists; IDs are unique in the scopes described above; an item cannot repeat a group ID.
- **Relative limits:** option selection counts obey the group rule above.
- **File encoding:** UTF-8 JSON, at most 2,097,152 bytes, nesting depth at most 24, no duplicate object keys. Write integer fields as digits without decimal points or exponent notation.
- **Text:** names/IDs are nonblank, contain no characters below U+0020, and are at most 160 UTF-16 code units. Branch name ≤160, item description ≤2000, restaurant notes ≤4000, address ≤1000. Standard JSON Schema counts Unicode code points, so emoji near a limit need the app check too.
- **Collections:** 1–100 categories, 1–500 items, at most 100 option groups, 1–30 options per group, at most 30 variants per item.

The repository regression test imports the published example using the production parser and checks its documented burger price:

```sh
./gradlew :order-domain:jvmTest
```

For a generated menu, use the app's **Preview JSON** or file import preview before confirming. If it fails, give the agent the exact error, the schema and the generated file to correct. A successful preview establishes format compatibility; comparing with the original menu establishes that the extracted content is correct.
