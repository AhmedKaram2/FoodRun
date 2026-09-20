import { t } from './i18n.js';
// Mirrors the server's MenuValidation rules. The server remains authoritative.
export function validateMenu(restaurant) {
  const label = value => { if (typeof value !== 'string' || !value.trim() || value.length > 160 || /[\x00-\x1f]/.test(value)) throw Error(t("Enter a valid name (up to 160 characters).")); };
  const price = value => { if (!Number.isSafeInteger(value) || value < 0 || value > 100000000) throw Error(t("Amount is outside the supported range.")); };
  const unique = values => { values.forEach(label); if (new Set(values).size !== values.length) throw Error(t("Duplicate identifiers in menu.")); };
  const bilingual = value => { label(value.name); if (value.nameAr) label(value.nameAr); };
  label(restaurant.id); bilingual(restaurant);
  if (restaurant.googleRating != null && (!Number.isFinite(restaurant.googleRating) || restaurant.googleRating < 4 || restaurant.googleRating > 5)) throw Error(t("Food Run only lists restaurants rated 4.0 or higher on Google."));
  const { categories, items, optionGroups } = restaurant.menu;
  if (items.length > 500 || categories.length > 100 || optionGroups.length > 100 || (!restaurant.openOrdering && (!items.length || !categories.length))) throw Error('Menu needs 1–500 items and 1–100 categories.');
  unique(categories.map(x => x.id)); unique(items.map(x => x.id)); unique(optionGroups.map(x => x.id));
  categories.forEach(bilingual);
  unique(optionGroups.flatMap(group => group.options.map(option => option.id)));
  optionGroups.forEach(group => {
    bilingual(group);
    if (!Number.isInteger(group.minSelections) || !Number.isInteger(group.maxSelections) || group.minSelections < 0 || group.minSelections > group.maxSelections || group.maxSelections > group.options.length || group.options.length < 1 || group.options.length > 30) throw Error(t("Check required and maximum selections for each extras group."));
    group.options.forEach(option => { bilingual(option); price(option.priceDeltaMinor); });
  });
  items.forEach(item => {
    bilingual(item); price(item.basePriceMinor);
    if ((item.description || '').length > 2000 || (item.descriptionAr || '').length > 2000) throw Error(t("Item descriptions must be under 2000 characters."));
    if (!categories.some(category => category.id === item.categoryId)) throw Error(t("Every menu item must reference an existing category."));
    if (item.variants.length > 30) throw Error(t("An item supports up to 30 sizes."));
    unique(item.variants.map(x => x.id));
    item.variants.forEach(variant => { bilingual(variant); price(variant.priceMinor); });
    unique(item.optionGroupIds);
    if (item.optionGroupIds.some(id => !optionGroups.some(group => group.id === id))) throw Error(t("Every extras group must exist in this menu."));
  });
  const pricing = restaurant.pricing;
  if (!['included', 'added', 'unspecified'].includes(pricing.taxTreatment)) throw Error(t("Choose a valid tax treatment."));
  if (pricing.taxRateBasisPoints != null && (!Number.isInteger(pricing.taxRateBasisPoints) || pricing.taxRateBasisPoints < 0 || pricing.taxRateBasisPoints > 10000) || pricing.taxTreatment === 'added' && pricing.taxRateBasisPoints == null) throw Error(t("Enter a tax rate between 0 and 100%."));
  [pricing.defaultDeliveryFeeMinor, pricing.defaultServiceFeeMinor, pricing.minimumOrderMinor].forEach(price);
  return restaurant;
}
export function removeOptionGroup(menu, groupId) {
  return { ...menu, optionGroups: menu.optionGroups.filter(group => group.id !== groupId), items: menu.items.map(item => ({ ...item, optionGroupIds: item.optionGroupIds.filter(id => id !== groupId) })) };
}
