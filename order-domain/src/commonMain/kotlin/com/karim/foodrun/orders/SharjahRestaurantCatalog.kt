package com.karim.foodrun.orders

/** Sharjah listings checked against public restaurant and Google-rating sources. Entries without official prices use open ordering. */
internal object SharjahRestaurantCatalog {
    private fun openOrder(
        id: String, name: String, nameAr: String, area: String, areaAr: String,
        cuisine: String, cuisineAr: String, phone: String, address: String,
        meals: List<MealType>, rating: Double, ratingCount: Int, source: String,
    ) = RestaurantExport(exportId = id, restaurant = Restaurant(
        id = id, name = name, nameAr = nameAr, currency = "AED",
        contact = RestaurantContact(phoneE164 = phone, address = address),
        pricing = RestaurantPricing(TaxTreatment.UNSPECIFIED),
        notes = "Restaurant details verified from public sources. Confirm current items and prices when ordering. Source: $source",
        openOrdering = true, emirate = "Sharjah", emirateAr = "الشارقة", area = area, areaAr = areaAr,
        cuisine = cuisine, cuisineAr = cuisineAr, mealTypes = meals,
        googleRating = rating, googleRatingCount = ratingCount, googleRatingVerifiedOn = "2026-09-20",
    ))

    private fun menuRestaurant(
        id: String, name: String, nameAr: String, area: String, areaAr: String,
        cuisine: String, cuisineAr: String, phone: String, address: String,
        meals: List<MealType>, rating: Double, ratingCount: Int, categories: List<MenuCategory>, items: List<MenuItem>, source: String,
    ) = RestaurantExport(exportId = id, restaurant = Restaurant(
        id = id, name = name, nameAr = nameAr, currency = "AED",
        contact = RestaurantContact(phoneE164 = phone, address = address),
        pricing = RestaurantPricing(TaxTreatment.UNSPECIFIED),
        notes = "Selected prices verified from the restaurant's public menu; confirm availability and the final bill. Source: $source",
        menu = Menu(categories = categories, items = items), openOrdering = true,
        emirate = "Sharjah", emirateAr = "الشارقة", area = area, areaAr = areaAr,
        cuisine = cuisine, cuisineAr = cuisineAr, mealTypes = meals,
        googleRating = rating, googleRatingCount = ratingCount, googleRatingVerifiedOn = "2026-09-20",
    ))

    private fun items(category: MenuCategory, vararg values: Triple<String, String, Long>) = values.mapIndexed { index, value ->
        MenuItem("${category.id}-item-$index", category.id, value.first, basePriceMinor = value.third, nameAr = value.second)
    }

    private val teaBreakfast = MenuCategory("builtin-arabian-tea-house-breakfast", "Emirati breakfast", 0, "الفطور الإماراتي")
    private val teaMains = MenuCategory("builtin-arabian-tea-house-mains", "Emirati mains", 1, "الأطباق الإماراتية")
    private val teaDrinks = MenuCategory("builtin-arabian-tea-house-drinks", "Drinks", 2, "المشروبات")
    val arabianTeaHouse = menuRestaurant(
        "builtin-arabian-tea-house", "Arabian Tea House", "أرابيان تي هاوس", "Al Mareija", "المريجة",
        "Emirati", "إماراتي", "+97165612686", "Souq Al Shanasiyah, Corniche Street, Heart of Sharjah",
        listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.7, 6_452, listOf(teaBreakfast, teaMains, teaDrinks),
        items(teaBreakfast,
            Triple("Children's Emirati breakfast tray", "صينية ريوق للأطفال", 5_500),
            Triple("Children's chebab bread tray", "صينية خبز الجباب للأطفال", 5_500),
            Triple("Special Emirati breakfast tray", "صينية ريوق إماراتي خاص", 8_700),
            Triple("Tahta Lahm", "تحته لحم", 7_000),
        ) + items(teaMains,
            Triple("Deyay Yumma", "دياي يمه", 6_000),
            Triple("Shrimp Jareesh", "جريش روبيان", 5_900),
            Triple("Fried sheri fish", "سمك شيري مقلي", 7_000),
            Triple("Fish or shrimp biryani", "برياني سمك أو روبيان", 7_000),
        ) + items(teaDrinks,
            Triple("Arabic coffee dallah", "دلة قهوة عربية", 3_000),
            Triple("Karak tea", "شاي كرك", 3_000),
            Triple("Fresh juices", "عصائر طازجة", 3_300),
            Triple("Laban ayran", "لبن عيران", 1_800),
        ),
        "https://arabianteahouse.com/ar/الشارقة/",
    )

    private val waheedOriental = MenuCategory("builtin-waheed-oriental", "Oriental", 0, "مقبلات شرقية")
    private val waheedMains = MenuCategory("builtin-waheed-mains", "Grills and mains", 1, "المشاوي والأطباق الرئيسية")
    val waheed = menuRestaurant(
        "builtin-waheed", "Waheed Restaurant & Cafe", "مطعم وكافيه وحيد", "Al Qasimia", "القاسمية",
        "Persian & Arabic", "فارسي وعربي", "+971562266106", "89WW+2X9, Bu Danig, Al Qasimia, Sharjah",
        listOf(MealType.LUNCH, MealType.DINNER), 4.7, 30, listOf(waheedOriental, waheedMains),
        items(waheedOriental,
            Triple("Falafel · 10 pieces", "فلافل · ١٠ حبات", 625),
            Triple("Hummus", "حمص", 1_250),
            Triple("Mutabbal", "متبل", 1_250),
            Triple("Fattoush", "فتوش", 1_250),
            Triple("Rocca salad", "سلطة جرجير", 1_500),
        ) + items(waheedMains,
            Triple("Chicken kabab koobideh sandwich", "ساندويتش كباب كوبيده دجاج", 1_250),
            Triple("Shish tawook sandwich", "ساندويتش شيش طاووق", 1_250),
            Triple("Chicken escalope", "اسكالوب دجاج", 1_500),
            Triple("Chicken fettuccine Alfredo", "فيتوتشيني ألفريدو بالدجاج", 2_500),
            Triple("Shish tawook meal", "وجبة شيش طاووق", 3_125),
            Triple("Mansaf peas rice with full chicken", "منسف أرز بالبازلاء مع دجاجة كاملة", 7_500),
            Triple("Mixed chicken grill · 1 kg", "مشاوي دجاج مشكلة · ١ كجم", 15_000),
        ),
        "https://www.waheedrestaurant.com/",
    )

    private val shawermanShawarma = MenuCategory("builtin-shawerman-shawarma", "Shawarma", 0, "الشاورما")
    val shawerman = menuRestaurant(
        "builtin-shawerman", "Shawerman Restaurant", "مطعم شاورمان", "Al Majaz 3", "المجاز 3",
        "Arabic shawarma", "شاورما عربية", "+97165757666", "Canal Star Tower, Al Qasba, Al Majaz 3, Sharjah",
        listOf(MealType.LUNCH, MealType.DINNER), 4.8, 34_528, listOf(shawermanShawarma),
        items(shawermanShawarma,
            Triple("Chicken shawarma small", "ساندويش شاورما دجاج صغير", 700),
            Triple("Chicken shawarma large", "ساندويش شاورما دجاج كبير", 1_200),
            Triple("Arabic chicken shawarma", "شاورما عربي دجاج", 1_900),
            Triple("Arabic chicken shawarma extra", "شاورما دجاج عربي إكسترا", 2_500),
            Triple("Meat shawarma small", "ساندويش شاورما لحم صغير", 1_000),
            Triple("Meat shawarma large", "ساندويش شاورما لحم كبير", 1_700),
            Triple("Arabic meat shawarma", "شاورما لحم عربي", 2_700),
            Triple("Arabic meat shawarma extra", "شاورما لحم عربي إكسترا", 3_400),
        ),
        "https://shawerman.ae/menu-item/shawarma/",
    )

    private val laffahShawarma = MenuCategory("builtin-laffah-shawarma", "Chicken shawarma", 0, "شاورما الدجاج")
    val laffah = menuRestaurant(
        "builtin-laffah-al-qasba", "Laffah Restaurant", "مطعم لفاح", "Al Majaz 3", "المجاز 3",
        "Syrian shawarma & broasted", "شاورما وبروستد سوري", "+97165569877", "Entifadah Road, Al Qasba, Al Majaz 3, Sharjah",
        listOf(MealType.LUNCH, MealType.DINNER), 4.2, 9_865, listOf(laffahShawarma),
        items(laffahShawarma,
            Triple("Chicken shawarma", "شاورما دجاج", 800),
            Triple("Chicken shawarma · Lebanese bread", "شاورما دجاج · خبز لبناني", 800),
            Triple("Double chicken shawarma", "شاورما دجاج دبل", 1_500),
            Triple("Chicken shawarma · samoon", "شاورما دجاج · صمون", 1_200),
            Triple("Arabic chicken shawarma meal", "وجبة شاورما عربي دجاج", 2_000),
            Triple("Double Arabic chicken shawarma meal", "وجبة شاورما عربي دجاج دبل", 2_700),
            Triple("Chicken shawarma plate", "صحن شاورما دجاج", 3_700),
            Triple("Chicken shawarma · half kilogram", "شاورما دجاج · نصف كيلو", 5_500),
            Triple("Arabic chicken shawarma with sliced potatoes", "وجبة شاورما دجاج عربي مع بطاطا شرحات", 2_700),
        ),
        "https://protal.laffahrestaurants.com/meals",
    )

    val all = listOf(
        arabianTeaHouse,
        waheed,
        shawerman,
        laffah,
        openOrder("builtin-al-farooj-al-shami", "Al Farooj Al Shami Restaurant", "مطعم الفروج الشامي", "Muwaileh Commercial", "تجارية مويلح", "Syrian grills & shawarma", "مشاوي وشاورما سورية", "+97165659922", "8F45+P5X, Muwaileh Commercial, Sharjah", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.4, 2_467, "https://www.alfaroojalshamirestaurant.ae/branches/"),
        openOrder("builtin-aroos-damascus", "Aroos Damascus", "عروس دمشق", "Al Qasimia", "القاسمية", "Syrian", "سوري", "+97165739900", "King Abdul Aziz Street, Al Nad, Al Qasimia, Sharjah", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.2, 8_600, "https://aroosdamascus.ae/locations/"),
        openOrder("builtin-mahrosah", "Mahrosah Restaurant & Sweets", "مطعم وحلويات مهروسة", "Al Khan", "الخان", "Aleppine", "حلبي", "+97165560990", "Al Khan Street, behind Sharjah Aquarium, Sharjah", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.6, 9_796, "https://mahrosah.ae/"),
        openOrder("builtin-al-rabiah-al-khadra", "Falafil Al Rabiah Al Khadra", "فلافل الرابية الخضراء", "Al Soor", "السور", "Arabic street food", "مأكولات عربية شعبية", "+97165774044", "992P+6X3, Al Soor, Sharjah", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.3, 2_925, "https://restaurantguru.com/Falafil-Al-Rabiah-Al-Khadhra-Cafeteria-Sharjah"),
        openOrder("builtin-falafel-frayha", "Falafel Frayha", "فلافل فريحة", "Al Majaz", "المجاز", "Lebanese & Arabic", "لبناني وعربي", "+97165314666", "Jamal Abdul Naser Street, Al Majaz 2, Sharjah", listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER), 4.4, 1_495, "https://falafelfrayha.com/"),
    )
}
