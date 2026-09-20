export function mergeRestaurantCatalog({ serverValues, bundledValues, currentValues, previousManagedIds, normalize }) {
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
  const server = serverValues.map(value => normalize(metadata(value)));
  const serverIds = new Set(server.map(value => value.id));
  const managed = [...server, ...bundledValues.filter(value => !serverIds.has(value.id)).map(value => normalize(value))];
  const managedIds = [...new Set([...previousManagedIds, ...managed.map(value => value.id)])];
  const managedIdSet = new Set(managedIds);
  const local = currentValues.filter(value => !managedIdSet.has(value.id));
  return { restaurants: [...local, ...managed], managedIds };
}
