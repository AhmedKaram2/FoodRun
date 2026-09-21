export function restoreRestaurantMetadata(values, bundledValues) {
  const bundledById = new Map(bundledValues.map(value => [value.id, value]));
  const metadata = (value, fallback = bundledById.get(value.id)) => fallback ? {
    ...value,
    nameAr: value.nameAr || fallback.nameAr,
    emirate: value.emirate || fallback.emirate,
    emirateAr: value.emirateAr || fallback.emirateAr,
    area: value.area || fallback.area,
    areaAr: value.areaAr || fallback.areaAr,
    cuisine: value.cuisine || fallback.cuisine,
    cuisineAr: value.cuisineAr || fallback.cuisineAr,
    mealTypes: value.mealTypes?.length ? value.mealTypes : fallback.mealTypes,
    googleRating: value.googleRating ?? fallback.googleRating,
    googleRatingCount: value.googleRatingCount ?? fallback.googleRatingCount,
    googleRatingVerifiedOn: value.googleRatingVerifiedOn || fallback.googleRatingVerifiedOn,
  } : value;
  return values.map(value => metadata(value));
}

export function mergeRestaurantCatalog({ serverValues, bundledValues, currentValues, previousManagedIds, normalize, deletedRestaurantIds = [] }) {
  const server = restoreRestaurantMetadata(serverValues, bundledValues).map(normalize);
  const serverIds = new Set(server.map(value => value.id));
  const deleted = new Set(deletedRestaurantIds);
  const managed = [...server, ...bundledValues.filter(value => !serverIds.has(value.id)).map(value => normalize(value))].filter(value => !deleted.has(value.id));
  const managedIds = [...new Set([...previousManagedIds, ...managed.map(value => value.id), ...deleted])];
  const managedIdSet = new Set(managedIds);
  const local = currentValues.filter(value => !managedIdSet.has(value.id));
  return { restaurants: [...local, ...managed], managedIds };
}
