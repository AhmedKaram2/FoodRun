export function menuCategories(restaurant) {
  const available = new Set(restaurant.menu.items.filter(item => item.available).map(item => item.categoryId));
  return restaurant.menu.categories.filter(category => available.has(category.id))
    .sort((a, b) => Number(isSandwichCategory(b)) - Number(isSandwichCategory(a)) || a.sortOrder - b.sortOrder);
}
export function isSandwichCategory(category) {
  return /sandwich|سند/i.test(`${category.name} ${category.nameAr || ''}`);
}
export function defaultMenuCategory(restaurant) {
  const sandwich = menuCategories(restaurant).find(isSandwichCategory);
  return sandwich ? `category:${sandwich.id}` : 'all';
}
export function browsedMenuItems(restaurant, category, search = '') {
  const query = search.trim().toLowerCase();
  return restaurant.menu.items.filter(item => item.available && (category === 'all' || category === `category:${item.categoryId}`) &&
    (!query || [item.name, item.nameAr, item.description, item.descriptionAr].some(value => value?.toLowerCase().includes(query))));
}
