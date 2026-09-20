const category = (id, name, nameAr, sortOrder) => ({ id, name, nameAr, sortOrder });
const item = (categoryId, index, name, nameAr, basePriceMinor) => ({
  id: `${categoryId}-item-${index}`, categoryId, name, nameAr, description: '', descriptionAr: '',
  basePriceMinor, available: true, variants: [], optionGroupIds: [],
});
const listing = ({ id, name, nameAr, area, areaAr, cuisine, cuisineAr, phone, address, mealTypes, googleRating, googleRatingCount, source, menu }) => ({
  id, name, nameAr, branchName: '', branchNameAr: '', currency: 'AED', emirate: 'Sharjah', emirateAr: 'الشارقة', area, areaAr, cuisine, cuisineAr, mealTypes, googleRating, googleRatingCount, googleRatingVerifiedOn: '2026-09-20',
  contact: { phoneE164: phone, whatsappE164: null, address },
  pricing: { taxTreatment: 'unspecified', taxRateBasisPoints: null, defaultDeliveryFeeMinor: 0, defaultServiceFeeMinor: 0, minimumOrderMinor: 0 },
  notes: `Restaurant details are from its public website. Confirm current availability and the final bill. Source: ${source}`,
  menu: menu || { categories: [], optionGroups: [], items: [] }, openOrdering: true,
});

const teaBreakfast = category('builtin-arabian-tea-house-breakfast', 'Emirati breakfast', 'الفطور الإماراتي', 0);
const teaMains = category('builtin-arabian-tea-house-mains', 'Emirati mains', 'الأطباق الإماراتية', 1);
const teaDrinks = category('builtin-arabian-tea-house-drinks', 'Drinks', 'المشروبات', 2);
const teaItems = [
  ['Children\'s Emirati breakfast tray', 'صينية ريوق للأطفال', 5500], ['Children\'s chebab bread tray', 'صينية خبز الجباب للأطفال', 5500],
  ['Special Emirati breakfast tray', 'صينية ريوق إماراتي خاص', 8700], ['Tahta Lahm', 'تحته لحم', 7000],
].map((value, index) => item(teaBreakfast.id, index, ...value)).concat([
  ['Deyay Yumma', 'دياي يمه', 6000], ['Shrimp Jareesh', 'جريش روبيان', 5900],
  ['Fried sheri fish', 'سمك شيري مقلي', 7000], ['Fish or shrimp biryani', 'برياني سمك أو روبيان', 7000],
].map((value, index) => item(teaMains.id, index, ...value)), [
  ['Arabic coffee dallah', 'دلة قهوة عربية', 3000], ['Karak tea', 'شاي كرك', 3000],
  ['Fresh juices', 'عصائر طازجة', 3300], ['Laban ayran', 'لبن عيران', 1800],
].map((value, index) => item(teaDrinks.id, index, ...value)));

const waheedOriental = category('builtin-waheed-oriental', 'Oriental', 'مقبلات شرقية', 0);
const waheedMains = category('builtin-waheed-mains', 'Grills and mains', 'المشاوي والأطباق الرئيسية', 1);
const waheedItems = [
  ['Falafel · 10 pieces', 'فلافل · ١٠ حبات', 625], ['Hummus', 'حمص', 1250], ['Mutabbal', 'متبل', 1250],
  ['Fattoush', 'فتوش', 1250], ['Rocca salad', 'سلطة جرجير', 1500],
].map((value, index) => item(waheedOriental.id, index, ...value)).concat([
  ['Chicken kabab koobideh sandwich', 'ساندويتش كباب كوبيده دجاج', 1250], ['Shish tawook sandwich', 'ساندويتش شيش طاووق', 1250],
  ['Chicken escalope', 'اسكالوب دجاج', 1500], ['Chicken fettuccine Alfredo', 'فيتوتشيني ألفريدو بالدجاج', 2500],
  ['Shish tawook meal', 'وجبة شيش طاووق', 3125], ['Mansaf peas rice with full chicken', 'منسف أرز بالبازلاء مع دجاجة كاملة', 7500],
  ['Mixed chicken grill · 1 kg', 'مشاوي دجاج مشكلة · ١ كجم', 15000],
].map((value, index) => item(waheedMains.id, index, ...value)));

const shawermanShawarma = category('builtin-shawerman-shawarma', 'Shawarma', 'الشاورما', 0);
const shawermanItems = [
  ['Chicken shawarma small', 'ساندويش شاورما دجاج صغير', 700],
  ['Chicken shawarma large', 'ساندويش شاورما دجاج كبير', 1200],
  ['Arabic chicken shawarma', 'شاورما عربي دجاج', 1900],
  ['Arabic chicken shawarma extra', 'شاورما دجاج عربي إكسترا', 2500],
  ['Meat shawarma small', 'ساندويش شاورما لحم صغير', 1000],
  ['Meat shawarma large', 'ساندويش شاورما لحم كبير', 1700],
  ['Arabic meat shawarma', 'شاورما لحم عربي', 2700],
  ['Arabic meat shawarma extra', 'شاورما لحم عربي إكسترا', 3400],
].map((value, index) => item(shawermanShawarma.id, index, ...value));

const laffahShawarma = category('builtin-laffah-shawarma', 'Chicken shawarma', 'شاورما الدجاج', 0);
const laffahItems = [
  ['Chicken shawarma', 'شاورما دجاج', 800],
  ['Chicken shawarma · Lebanese bread', 'شاورما دجاج · خبز لبناني', 800],
  ['Double chicken shawarma', 'شاورما دجاج دبل', 1500],
  ['Chicken shawarma · samoon', 'شاورما دجاج · صمون', 1200],
  ['Arabic chicken shawarma meal', 'وجبة شاورما عربي دجاج', 2000],
  ['Double Arabic chicken shawarma meal', 'وجبة شاورما عربي دجاج دبل', 2700],
  ['Chicken shawarma plate', 'صحن شاورما دجاج', 3700],
  ['Chicken shawarma · half kilogram', 'شاورما دجاج · نصف كيلو', 5500],
  ['Arabic chicken shawarma with sliced potatoes', 'وجبة شاورما دجاج عربي مع بطاطا شرحات', 2700],
].map((value, index) => item(laffahShawarma.id, index, ...value));

export const sharjahRestaurants = [
  listing({ id: 'builtin-arabian-tea-house', name: 'Arabian Tea House', nameAr: 'أرابيان تي هاوس', area: 'Al Mareija', areaAr: 'المريجة', cuisine: 'Emirati', cuisineAr: 'إماراتي', phone: '+97165612686', address: 'Souq Al Shanasiyah, Corniche Street, Heart of Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.7, googleRatingCount: 6452, source: 'https://arabianteahouse.com/ar/الشارقة/', menu: { categories: [teaBreakfast, teaMains, teaDrinks], optionGroups: [], items: teaItems } }),
  listing({ id: 'builtin-waheed', name: 'Waheed Restaurant & Cafe', nameAr: 'مطعم وكافيه وحيد', area: 'Al Qasimia', areaAr: 'القاسمية', cuisine: 'Persian & Arabic', cuisineAr: 'فارسي وعربي', phone: '+971562266106', address: '89WW+2X9, Bu Danig, Al Qasimia, Sharjah', mealTypes: ['lunch','dinner'], googleRating: 4.7, googleRatingCount: 30, source: 'https://www.waheedrestaurant.com/', menu: { categories: [waheedOriental, waheedMains], optionGroups: [], items: waheedItems } }),
  listing({ id: 'builtin-shawerman', name: 'Shawerman Restaurant', nameAr: 'مطعم شاورمان', area: 'Al Majaz 3', areaAr: 'المجاز 3', cuisine: 'Arabic shawarma', cuisineAr: 'شاورما عربية', phone: '+97165757666', address: 'Canal Star Tower, Al Qasba, Al Majaz 3, Sharjah', mealTypes: ['lunch','dinner'], googleRating: 4.8, googleRatingCount: 34528, source: 'https://shawerman.ae/menu-item/shawarma/', menu: { categories: [shawermanShawarma], optionGroups: [], items: shawermanItems } }),
  listing({ id: 'builtin-laffah-al-qasba', name: 'Laffah Restaurant', nameAr: 'مطعم لفاح', area: 'Al Majaz 3', areaAr: 'المجاز 3', cuisine: 'Syrian shawarma & broasted', cuisineAr: 'شاورما وبروستد سوري', phone: '+97165569877', address: 'Entifadah Road, Al Qasba, Al Majaz 3, Sharjah', mealTypes: ['lunch','dinner'], googleRating: 4.2, googleRatingCount: 9865, source: 'https://protal.laffahrestaurants.com/meals', menu: { categories: [laffahShawarma], optionGroups: [], items: laffahItems } }),
  listing({ id: 'builtin-al-farooj-al-shami', name: 'Al Farooj Al Shami Restaurant', nameAr: 'مطعم الفروج الشامي', area: 'Muwaileh Commercial', areaAr: 'تجارية مويلح', cuisine: 'Syrian grills & shawarma', cuisineAr: 'مشاوي وشاورما سورية', phone: '+97165659922', address: '8F45+P5X, Muwaileh Commercial, Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.4, googleRatingCount: 2467, source: 'https://www.alfaroojalshamirestaurant.ae/branches/' }),
  listing({ id: 'builtin-aroos-damascus', name: 'Aroos Damascus', nameAr: 'عروس دمشق', area: 'Al Qasimia', areaAr: 'القاسمية', cuisine: 'Syrian', cuisineAr: 'سوري', phone: '+97165739900', address: 'King Abdul Aziz Street, Al Nad, Al Qasimia, Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.2, googleRatingCount: 8600, source: 'https://aroosdamascus.ae/locations/' }),
  listing({ id: 'builtin-mahrosah', name: 'Mahrosah Restaurant & Sweets', nameAr: 'مطعم وحلويات مهروسة', area: 'Al Khan', areaAr: 'الخان', cuisine: 'Aleppine', cuisineAr: 'حلبي', phone: '+97165560990', address: 'Al Khan Street, behind Sharjah Aquarium, Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.6, googleRatingCount: 9796, source: 'https://mahrosah.ae/' }),
  listing({ id: 'builtin-al-rabiah-al-khadra', name: 'Falafil Al Rabiah Al Khadra', nameAr: 'فلافل الرابية الخضراء', area: 'Al Soor', areaAr: 'السور', cuisine: 'Arabic street food', cuisineAr: 'مأكولات عربية شعبية', phone: '+97165774044', address: '992P+6X3, Al Soor, Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.3, googleRatingCount: 2925, source: 'https://restaurantguru.com/Falafil-Al-Rabiah-Al-Khadhra-Cafeteria-Sharjah' }),
  listing({ id: 'builtin-falafel-frayha', name: 'Falafel Frayha', nameAr: 'فلافل فريحة', area: 'Al Majaz', areaAr: 'المجاز', cuisine: 'Lebanese & Arabic', cuisineAr: 'لبناني وعربي', phone: '+97165314666', address: 'Jamal Abdul Naser Street, Al Majaz 2, Sharjah', mealTypes: ['breakfast','lunch','dinner'], googleRating: 4.4, googleRatingCount: 1495, source: 'https://falafelfrayha.com/' }),
];
