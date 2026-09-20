package com.karim.foodrun.orders

/** Dubai branch listings whose Google rating was checked as 4.0 or higher on 2026-09-20. */
internal object DubaiRestaurantCatalog {
    private val allDay = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER)
    private val lunchDinner = listOf(MealType.LUNCH, MealType.DINNER)

    private fun openOrder(
        id: String, name: String, nameAr: String, area: String, areaAr: String,
        cuisine: String, cuisineAr: String, phone: String?, address: String,
        meals: List<MealType>, rating: Double, ratingCount: Int?, source: String,
    ) = RestaurantExport(exportId = id, restaurant = Restaurant(
        id = id, name = name, nameAr = nameAr, currency = "AED",
        contact = RestaurantContact(phoneE164 = phone, address = address),
        pricing = RestaurantPricing(TaxTreatment.UNSPECIFIED),
        notes = "Google rating and branch details checked from public sources on 2026-09-20. Confirm current items and prices when ordering. Source: $source",
        openOrdering = true,
        emirate = "Dubai", emirateAr = "دبي", area = area, areaAr = areaAr,
        cuisine = cuisine, cuisineAr = cuisineAr, mealTypes = meals,
        googleRating = rating, googleRatingCount = ratingCount, googleRatingVerifiedOn = "2026-09-20",
    ))

    val egyptian = listOf(
        openOrder("builtin-dubai-hadoota-szr", "Hadoota Masreya", "حدوتة مصرية", "Sheikh Zayed Road", "شارع الشيخ زايد", "Egyptian", "مصري", "+97143809000", "Matloob Building, Sheikh Zayed Road, Dubai", allDay, 4.5, 12_674, "https://restaurantguru.com/Hadoota-Masreya-Dubai"),
        openOrder("builtin-dubai-hadoota-ibn-battuta", "Hadoota Masreya", "حدوتة مصرية", "Ibn Battuta", "ابن بطوطة", "Egyptian", "مصري", "+971505750000", "Ibn Battuta Street, Dubai", allDay, 4.5, 3_610, "https://restaurantguru.com/Hadoota-Masreya-Dubai"),
        openOrder("builtin-dubai-al-amoor-szr", "Al Amoor Express", "العمور إكسبرس", "Trade Centre", "المركز التجاري", "Egyptian", "مصري", "+971509787006", "Aspin Commercial Tower, Sheikh Zayed Road, Dubai", allDay, 4.2, 3_361, "https://wanderlog.com/place/details/2335484/al-amoor-express-restaurant"),
        openOrder("builtin-dubai-koshari-abu-tarek", "Koshari Abu Tarek", "كشري أبو طارق", "Al Barsha 1", "البرشاء 1", "Egyptian", "مصري", "+97143541001", "City Stay Hotel, behind Lulu, Al Barsha 1, Dubai", allDay, 4.4, 5_341, "https://restaurantguru.com/kshry-abw-tarq-alamarat-Koshari-Abu-Tarek-UAE-Dubai"),
        openOrder("builtin-dubai-cairo-gourmet", "Cairo Gourmet", "كايرو جورميه", "Sheikh Zayed Road", "شارع الشيخ زايد", "Egyptian", "مصري", "+971543265555", "260 Al Diyar Building, Sheikh Zayed Road, Dubai", allDay, 4.6, 6_855, "https://wanderlog.com/place/details/2106597/cairo-gourmet-restaurant-and-cafe"),
        openOrder("builtin-dubai-al-aumdah-barsha", "Al Aumdah", "العمدة", "Al Barsha", "البرشاء", "Egyptian", "مصري", "+971543072591", "Etqan Building, Al Barsha, Dubai", allDay, 4.0, 2_256, "https://restaurantguru.com/Al-Aumdah-Restaurant-Al-Barsha-United-Arab-Emirates"),
        openOrder("builtin-dubai-al-aumdah-abu-hail", "Al Aumdah", "العمدة", "Abu Hail", "أبو هيل", "Egyptian", "مصري", null, "Abu Hail, Al Mamzar, Dubai", allDay, 4.0, 2_973, "https://yalah.ae/restaurants/al-aumdah-restaurant-abu-hail-al-mamzar"),
        openOrder("builtin-dubai-masmat-baha-abu-hail", "Masmat Baha", "مسمط بحه", "Abu Hail", "أبو هيل", "Egyptian", "مصري", null, "Al Wuheida Road, Abu Hail, Dubai", allDay, 4.3, 4_388, "https://restaurantguru.com/MasmatBaha-Dubai"),
        openOrder("builtin-dubai-masmat-baha-barsha", "Masmat Baha", "مسمط بحه", "Al Barsha", "البرشاء", "Egyptian", "مصري", "+971525544101", "Al Zarooni Building, opposite Mall of the Emirates, Al Barsha, Dubai", allDay, 4.3, 2_903, "https://restaurantguru.com/mtam-msmt-bhh-fra-Dubai"),
        openOrder("builtin-dubai-tayba-gourmet", "Tayba Gourmet", "طيبة جورميه", "Al Safa 1", "الصفا 1", "Egyptian", "مصري", "+97143790222", "Wasl Square, Al Hadiqah Road, Al Safa 1, Dubai", allDay, 4.8, 6_517, "https://restaurantguru.com/Tayba-Butchery-and-Grill-Dubai"),
    )

    val arabicShawarma = listOf(
        openOrder("builtin-dubai-allo-beirut-city-walk", "Allo Beirut", "ألو بيروت", "City Walk", "سيتي ووك", "Lebanese street food & shawarma", "مأكولات لبنانية وشاورما", null, "Al Safa Street, Al Wasl, Dubai", allDay, 4.6, 10_968, "https://wanderlog.com/place/details/1228214/allo-beirut-city-walk"),
        openOrder("builtin-dubai-allo-beirut-hessa", "Allo Beirut", "ألو بيروت", "Hessa Street", "شارع حصة", "Lebanese street food & shawarma", "مأكولات لبنانية وشاورما", null, "Hessa Street, Al Barsha Third, Dubai", allDay, 4.6, null, "https://wanderlog.com/place/details/1228211/allo-beirut-hessa-street"),
        openOrder("builtin-dubai-operation-falafel-media-city", "Operation Falafel", "أوبريشن فلافل", "Dubai Media City", "مدينة دبي للإعلام", "Arabic street food", "مأكولات عربية شعبية", null, "CNBC Building C7, Dubai Media City, Dubai", allDay, 4.6, 2_145, "https://wanderlog.com/place/details/1968127"),
        openOrder("builtin-dubai-operation-falafel-downtown", "Operation Falafel", "أوبريشن فلافل", "Downtown Dubai", "وسط مدينة دبي", "Arabic street food", "مأكولات عربية شعبية", null, "Sheikh Mohammed bin Rashid Boulevard, Downtown Dubai", allDay, 4.6, 5_025, "https://wanderlog.com/fr/place/details/839758/operation-falafel-boulevard-downtown"),
        openOrder("builtin-dubai-operation-falafel-festival-city", "Operation Falafel", "أوبريشن فلافل", "Dubai Festival City", "دبي فستيفال سيتي", "Arabic street food", "مأكولات عربية شعبية", null, "Rebat Street, Dubai Festival City, Dubai", allDay, 4.5, 644, "https://wanderlog.com/place/details/8981298"),
        openOrder("builtin-dubai-al-mallah-dhiyafah", "Al Mallah", "الملاح", "Al Satwa", "السطوة", "Lebanese shawarma & grills", "شاورما ومشاوي لبنانية", "+971529987193", "Al Dhiyafa Road, near Satwa Roundabout, Dubai", allDay, 4.0, 5_089, "https://wanderlog.com/place/details/480986/al-mallah-dhiyafah"),
        openOrder("builtin-dubai-shiraz-nights", "Shiraz Nights", "ليالي شيراز", "Deira", "ديرة", "Arabic shawarma", "شاورما عربية", null, "Deira, Dubai", lunchDinner, 4.1, 1_689, "https://restaurantguru.com/Shiraz-Nights-Dubai"),
        openOrder("builtin-dubai-laffah-barsha", "Laffah Restaurant", "مطعم لفاح", "Al Barsha 1", "البرشاء 1", "Syrian shawarma & broasted", "شاورما وبروستد سوري", "+97142225383", "Al Barsha 1, Dubai", lunchDinner, 4.1, 5_962, "https://wanderlog.com/place/details/865095/laffah-al-barsha-branch"),
        openOrder("builtin-dubai-rawabi-al-sham", "Rawabi Al Sham", "روابي الشام", "Al Barsha 1", "البرشاء 1", "Syrian shawarma & grills", "شاورما ومشاوي سورية", "+97143401115", "Trio Building, Al Barsha 1, Dubai", allDay, 4.1, 2_941, "https://restaurantguru.com/Rawabi-Al-Sham-Dubai-2"),
        openOrder("builtin-dubai-al-beiruti-szr", "Al Beiruti", "البيروتي", "Umm Al Sheif", "أم الشيف", "Lebanese & shawarma", "لبناني وشاورما", "+97143200043", "Exit 41, Sheikh Zayed Road, Umm Al Sheif, Dubai", allDay, 4.6, 10_006, "https://wanderlog.com/place/details/2466872"),
    )

    val other = listOf(
        openOrder("builtin-dubai-ravi-satwa", "Ravi Restaurant", "مطعم رافي", "Al Satwa", "السطوة", "Pakistani", "باكستاني", "+97143315353", "8 9th Street, Al Satwa, Dubai", allDay, 4.0, 6_942, "https://yalah.ae/restaurants/ravi-restaurant-satwa-al-satwa"),
        openOrder("builtin-dubai-din-tai-fung-mall", "Din Tai Fung", "دين تاي فونغ", "Dubai Mall", "دبي مول", "Chinese", "صيني", "+97143200477", "Lower Ground Floor, The Dubai Mall, Downtown Dubai", lunchDinner, 4.7, 4_742, "https://wanderlog.com/place/details/7720850/din-tai-fung-the-dubai-mall"),
        openOrder("builtin-dubai-bosporus-mall", "Bosporus", "بوسفور", "Dubai Mall", "دبي مول", "Turkish", "تركي", "+97143808090", "Waterfront Entrance, The Dubai Mall, Downtown Dubai", allDay, 4.9, 16_907, "https://restaurantguru.com/Bosporus-Restaurant-Dubai"),
        openOrder("builtin-dubai-calicut-paragon", "Calicut Paragon", "كاليكوت باراغون", "Al Karama", "الكرامة", "Indian Kerala", "هندي كيرلا", "+97143358700", "Mattar Al Tayer Building, 20B Street, Al Karama, Dubai", allDay, 4.4, 7_071, "https://restaurantguru.com/Calicut-Paragon-Dubai-7"),
        openOrder("builtin-dubai-pitfire-pizza-jlt", "Pitfire Pizza", "بيتفاير بيتزا", "Jumeirah Lakes Towers", "أبراج بحيرات جميرا", "Italian pizza", "بيتزا إيطالية", "+97145530465", "Lake Terrace Tower, Cluster D, Jumeirah Lakes Towers, Dubai", lunchDinner, 4.5, 2_033, "https://restaurantguru.com/Pitfire-Pizza-Dubai-2"),
        openOrder("builtin-dubai-vietnamese-foodies-downtown", "Vietnamese Foodies", "فيتناميز فوديز", "Downtown Dubai", "وسط مدينة دبي", "Vietnamese", "فيتنامي", null, "Tower 1, Burj Vista Residence, Downtown Dubai", lunchDinner, 4.6, 1_176, "https://restaurantguru.com/Vietnamese-Foodies-Dubai-3"),
        openOrder("builtin-dubai-mythos-jlt", "Mythos Kouzina & Grill", "ميثوس كوزينا آند جريل", "Jumeirah Lakes Towers", "أبراج بحيرات جميرا", "Greek", "يوناني", "+97143998166", "Cluster P, Jumeirah Lakes Towers, Dubai", lunchDinner, 4.6, 2_476, "https://wanderlog.com/place/details/443462"),
        openOrder("builtin-dubai-reif-dar-wasl", "REIF Japanese Kushiyaki", "ريف جابانيز كوشياكي", "Al Wasl", "الوصل", "Japanese", "ياباني", "+97142555142", "Dar Wasl Mall, Al Wasl Road, Dubai", lunchDinner, 4.7, 1_733, "https://wanderlog.com/place/details/444002/reif-japanese-kushiyaki-dar-wasl"),
        openOrder("builtin-dubai-bu-qtair", "Bu Qtair", "بو قطير", "Umm Suqeim 2", "أم سقيم 2", "Seafood", "مأكولات بحرية", "+971557052130", "Fishing Harbour 2, Old 32B Street, Umm Suqeim 2, Dubai", lunchDinner, 4.2, 11_384, "https://wanderlog.com/place/details/503367"),
        openOrder("builtin-dubai-al-ustad", "Al Ustad Special Kebab", "الأستاذ للكباب الخاص", "Bur Dubai", "بر دبي", "Persian kebab", "كباب فارسي", "+97143971933", "Al Mussallah Road, near Al Fahidi Metro Station, Bur Dubai", lunchDinner, 4.4, 13_348, "https://wanderlog.com/place/details/735526/al-ustad-special-kebab"),
    )

    val all = egyptian + arabicShawarma + other
}
