const allDay = ['breakfast', 'lunch', 'dinner'];
const lunchDinner = ['lunch', 'dinner'];

const listing = (id, name, nameAr, area, areaAr, cuisine, cuisineAr, phoneE164, address, mealTypes, googleRating, googleRatingCount, source) => ({
  id, name, nameAr, branchName: '', branchNameAr: '', currency: 'AED',
  emirate: 'Dubai', emirateAr: 'دبي', area, areaAr, cuisine, cuisineAr, mealTypes,
  googleRating, googleRatingCount, googleRatingVerifiedOn: '2026-09-20',
  contact: { phoneE164, whatsappE164: null, address },
  pricing: { taxTreatment: 'unspecified', taxRateBasisPoints: null, defaultDeliveryFeeMinor: 0, defaultServiceFeeMinor: 0, minimumOrderMinor: 0 },
  notes: `Google rating and branch details checked from public sources on 2026-09-20. Confirm current items and prices when ordering. Source: ${source}`,
  menu: { categories: [], optionGroups: [], items: [] }, openOrdering: true,
});

export const dubaiEgyptianRestaurants = [
  listing('builtin-dubai-hadoota-szr', 'Hadoota Masreya', 'حدوتة مصرية', 'Sheikh Zayed Road', 'شارع الشيخ زايد', 'Egyptian', 'مصري', '+97143809000', 'Matloob Building, Sheikh Zayed Road, Dubai', allDay, 4.5, 12674, 'https://restaurantguru.com/Hadoota-Masreya-Dubai'),
  listing('builtin-dubai-hadoota-ibn-battuta', 'Hadoota Masreya', 'حدوتة مصرية', 'Ibn Battuta', 'ابن بطوطة', 'Egyptian', 'مصري', '+971505750000', 'Ibn Battuta Street, Dubai', allDay, 4.5, 3610, 'https://restaurantguru.com/Hadoota-Masreya-Dubai'),
  listing('builtin-dubai-al-amoor-szr', 'Al Amoor Express', 'العمور إكسبرس', 'Trade Centre', 'المركز التجاري', 'Egyptian', 'مصري', '+971509787006', 'Aspin Commercial Tower, Sheikh Zayed Road, Dubai', allDay, 4.2, 3361, 'https://wanderlog.com/place/details/2335484/al-amoor-express-restaurant'),
  listing('builtin-dubai-koshari-abu-tarek', 'Koshari Abu Tarek', 'كشري أبو طارق', 'Al Barsha 1', 'البرشاء 1', 'Egyptian', 'مصري', '+97143541001', 'City Stay Hotel, behind Lulu, Al Barsha 1, Dubai', allDay, 4.4, 5341, 'https://restaurantguru.com/kshry-abw-tarq-alamarat-Koshari-Abu-Tarek-UAE-Dubai'),
  listing('builtin-dubai-cairo-gourmet', 'Cairo Gourmet', 'كايرو جورميه', 'Sheikh Zayed Road', 'شارع الشيخ زايد', 'Egyptian', 'مصري', '+971543265555', '260 Al Diyar Building, Sheikh Zayed Road, Dubai', allDay, 4.6, 6855, 'https://wanderlog.com/place/details/2106597/cairo-gourmet-restaurant-and-cafe'),
  listing('builtin-dubai-al-aumdah-barsha', 'Al Aumdah', 'العمدة', 'Al Barsha', 'البرشاء', 'Egyptian', 'مصري', '+971543072591', 'Etqan Building, Al Barsha, Dubai', allDay, 4.0, 2256, 'https://restaurantguru.com/Al-Aumdah-Restaurant-Al-Barsha-United-Arab-Emirates'),
  listing('builtin-dubai-al-aumdah-abu-hail', 'Al Aumdah', 'العمدة', 'Abu Hail', 'أبو هيل', 'Egyptian', 'مصري', null, 'Abu Hail, Al Mamzar, Dubai', allDay, 4.0, 2973, 'https://yalah.ae/restaurants/al-aumdah-restaurant-abu-hail-al-mamzar'),
  listing('builtin-dubai-masmat-baha-abu-hail', 'Masmat Baha', 'مسمط بحه', 'Abu Hail', 'أبو هيل', 'Egyptian', 'مصري', null, 'Al Wuheida Road, Abu Hail, Dubai', allDay, 4.3, 4388, 'https://restaurantguru.com/MasmatBaha-Dubai'),
  listing('builtin-dubai-masmat-baha-barsha', 'Masmat Baha', 'مسمط بحه', 'Al Barsha', 'البرشاء', 'Egyptian', 'مصري', '+971525544101', 'Al Zarooni Building, opposite Mall of the Emirates, Al Barsha, Dubai', allDay, 4.3, 2903, 'https://restaurantguru.com/mtam-msmt-bhh-fra-Dubai'),
  listing('builtin-dubai-tayba-gourmet', 'Tayba Gourmet', 'طيبة جورميه', 'Al Safa 1', 'الصفا 1', 'Egyptian', 'مصري', '+97143790222', 'Wasl Square, Al Hadiqah Road, Al Safa 1, Dubai', allDay, 4.8, 6517, 'https://restaurantguru.com/Tayba-Butchery-and-Grill-Dubai'),
];

export const dubaiArabicShawarmaRestaurants = [
  listing('builtin-dubai-allo-beirut-city-walk', 'Allo Beirut', 'ألو بيروت', 'City Walk', 'سيتي ووك', 'Lebanese street food & shawarma', 'مأكولات لبنانية وشاورما', null, 'Al Safa Street, Al Wasl, Dubai', allDay, 4.6, 10968, 'https://wanderlog.com/place/details/1228214/allo-beirut-city-walk'),
  listing('builtin-dubai-allo-beirut-hessa', 'Allo Beirut', 'ألو بيروت', 'Hessa Street', 'شارع حصة', 'Lebanese street food & shawarma', 'مأكولات لبنانية وشاورما', null, 'Hessa Street, Al Barsha Third, Dubai', allDay, 4.6, null, 'https://wanderlog.com/place/details/1228211/allo-beirut-hessa-street'),
  listing('builtin-dubai-operation-falafel-media-city', 'Operation Falafel', 'أوبريشن فلافل', 'Dubai Media City', 'مدينة دبي للإعلام', 'Arabic street food', 'مأكولات عربية شعبية', null, 'CNBC Building C7, Dubai Media City, Dubai', allDay, 4.6, 2145, 'https://wanderlog.com/place/details/1968127'),
  listing('builtin-dubai-operation-falafel-downtown', 'Operation Falafel', 'أوبريشن فلافل', 'Downtown Dubai', 'وسط مدينة دبي', 'Arabic street food', 'مأكولات عربية شعبية', null, 'Sheikh Mohammed bin Rashid Boulevard, Downtown Dubai', allDay, 4.6, 5025, 'https://wanderlog.com/fr/place/details/839758/operation-falafel-boulevard-downtown'),
  listing('builtin-dubai-operation-falafel-festival-city', 'Operation Falafel', 'أوبريشن فلافل', 'Dubai Festival City', 'دبي فستيفال سيتي', 'Arabic street food', 'مأكولات عربية شعبية', null, 'Rebat Street, Dubai Festival City, Dubai', allDay, 4.5, 644, 'https://wanderlog.com/place/details/8981298'),
  listing('builtin-dubai-al-mallah-dhiyafah', 'Al Mallah', 'الملاح', 'Al Satwa', 'السطوة', 'Lebanese shawarma & grills', 'شاورما ومشاوي لبنانية', '+971529987193', 'Al Dhiyafa Road, near Satwa Roundabout, Dubai', allDay, 4.0, 5089, 'https://wanderlog.com/place/details/480986/al-mallah-dhiyafah'),
  listing('builtin-dubai-shiraz-nights', 'Shiraz Nights', 'ليالي شيراز', 'Deira', 'ديرة', 'Arabic shawarma', 'شاورما عربية', null, 'Deira, Dubai', lunchDinner, 4.1, 1689, 'https://restaurantguru.com/Shiraz-Nights-Dubai'),
  listing('builtin-dubai-laffah-barsha', 'Laffah Restaurant', 'مطعم لفاح', 'Al Barsha 1', 'البرشاء 1', 'Syrian shawarma & broasted', 'شاورما وبروستد سوري', '+97142225383', 'Al Barsha 1, Dubai', lunchDinner, 4.1, 5962, 'https://wanderlog.com/place/details/865095/laffah-al-barsha-branch'),
  listing('builtin-dubai-rawabi-al-sham', 'Rawabi Al Sham', 'روابي الشام', 'Al Barsha 1', 'البرشاء 1', 'Syrian shawarma & grills', 'شاورما ومشاوي سورية', '+97143401115', 'Trio Building, Al Barsha 1, Dubai', allDay, 4.1, 2941, 'https://restaurantguru.com/Rawabi-Al-Sham-Dubai-2'),
  listing('builtin-dubai-al-beiruti-szr', 'Al Beiruti', 'البيروتي', 'Umm Al Sheif', 'أم الشيف', 'Lebanese & shawarma', 'لبناني وشاورما', '+97143200043', 'Exit 41, Sheikh Zayed Road, Umm Al Sheif, Dubai', allDay, 4.6, 10006, 'https://wanderlog.com/place/details/2466872'),
];

export const dubaiOtherRestaurants = [
  listing('builtin-dubai-ravi-satwa', 'Ravi Restaurant', 'مطعم رافي', 'Al Satwa', 'السطوة', 'Pakistani', 'باكستاني', '+97143315353', '8 9th Street, Al Satwa, Dubai', allDay, 4.0, 6942, 'https://yalah.ae/restaurants/ravi-restaurant-satwa-al-satwa'),
  listing('builtin-dubai-din-tai-fung-mall', 'Din Tai Fung', 'دين تاي فونغ', 'Dubai Mall', 'دبي مول', 'Chinese', 'صيني', '+97143200477', 'Lower Ground Floor, The Dubai Mall, Downtown Dubai', lunchDinner, 4.7, 4742, 'https://wanderlog.com/place/details/7720850/din-tai-fung-the-dubai-mall'),
  listing('builtin-dubai-bosporus-mall', 'Bosporus', 'بوسفور', 'Dubai Mall', 'دبي مول', 'Turkish', 'تركي', '+97143808090', 'Waterfront Entrance, The Dubai Mall, Downtown Dubai', allDay, 4.9, 16907, 'https://restaurantguru.com/Bosporus-Restaurant-Dubai'),
  listing('builtin-dubai-calicut-paragon', 'Calicut Paragon', 'كاليكوت باراغون', 'Al Karama', 'الكرامة', 'Indian Kerala', 'هندي كيرلا', '+97143358700', 'Mattar Al Tayer Building, 20B Street, Al Karama, Dubai', allDay, 4.4, 7071, 'https://restaurantguru.com/Calicut-Paragon-Dubai-7'),
  listing('builtin-dubai-pitfire-pizza-jlt', 'Pitfire Pizza', 'بيتفاير بيتزا', 'Jumeirah Lakes Towers', 'أبراج بحيرات جميرا', 'Italian pizza', 'بيتزا إيطالية', '+97145530465', 'Lake Terrace Tower, Cluster D, Jumeirah Lakes Towers, Dubai', lunchDinner, 4.5, 2033, 'https://restaurantguru.com/Pitfire-Pizza-Dubai-2'),
  listing('builtin-dubai-vietnamese-foodies-downtown', 'Vietnamese Foodies', 'فيتناميز فوديز', 'Downtown Dubai', 'وسط مدينة دبي', 'Vietnamese', 'فيتنامي', null, 'Tower 1, Burj Vista Residence, Downtown Dubai', lunchDinner, 4.6, 1176, 'https://restaurantguru.com/Vietnamese-Foodies-Dubai-3'),
  listing('builtin-dubai-mythos-jlt', 'Mythos Kouzina & Grill', 'ميثوس كوزينا آند جريل', 'Jumeirah Lakes Towers', 'أبراج بحيرات جميرا', 'Greek', 'يوناني', '+97143998166', 'Cluster P, Jumeirah Lakes Towers, Dubai', lunchDinner, 4.6, 2476, 'https://wanderlog.com/place/details/443462'),
  listing('builtin-dubai-reif-dar-wasl', 'REIF Japanese Kushiyaki', 'ريف جابانيز كوشياكي', 'Al Wasl', 'الوصل', 'Japanese', 'ياباني', '+97142555142', 'Dar Wasl Mall, Al Wasl Road, Dubai', lunchDinner, 4.7, 1733, 'https://wanderlog.com/place/details/444002/reif-japanese-kushiyaki-dar-wasl'),
  listing('builtin-dubai-bu-qtair', 'Bu Qtair', 'بو قطير', 'Umm Suqeim 2', 'أم سقيم 2', 'Seafood', 'مأكولات بحرية', '+971557052130', 'Fishing Harbour 2, Old 32B Street, Umm Suqeim 2, Dubai', lunchDinner, 4.2, 11384, 'https://wanderlog.com/place/details/503367'),
  listing('builtin-dubai-al-ustad', 'Al Ustad Special Kebab', 'الأستاذ للكباب الخاص', 'Bur Dubai', 'بر دبي', 'Persian kebab', 'كباب فارسي', '+97143971933', 'Al Mussallah Road, near Al Fahidi Metro Station, Bur Dubai', lunchDinner, 4.4, 13348, 'https://wanderlog.com/place/details/735526/al-ustad-special-kebab'),
];

export const dubaiRestaurants = [...dubaiEgyptianRestaurants, ...dubaiArabicShawarmaRestaurants, ...dubaiOtherRestaurants];
