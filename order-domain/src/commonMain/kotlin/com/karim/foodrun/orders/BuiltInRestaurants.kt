package com.karim.foodrun.orders

/** Menus transcribed from the restaurant images supplied with Food Run. Prices are stored in fils. */
object BuiltInRestaurants {
    private data class SourceItem(val name: String, val prices: List<Pair<String, Int>>)
    private data class SourceCategory(val name: String, val items: List<SourceItem>)

    private fun item(name: String, price: Int) = SourceItem(name, listOf("" to price))
    private fun item(name: String, vararg prices: Pair<String, Int>) = SourceItem(name, prices.toList())
    private fun category(name: String, vararg items: SourceItem) = SourceCategory(name, items.toList())
    private fun restaurant(id: String, name: String, vararg categories: SourceCategory): RestaurantExport {
        val menuCategories = categories.mapIndexed { index, category -> MenuCategory("$id-category-$index", category.name, index, ArabicMenuNames.category(category.name)) }
        val menuItems = categories.flatMapIndexed { categoryIndex, category ->
            category.items.mapIndexed { itemIndex, source ->
                val itemId = "$id-item-$categoryIndex-$itemIndex"
                val variants = if (source.prices.size == 1 && source.prices.single().first.isEmpty()) emptyList() else
                    source.prices.mapIndexed { variantIndex, price -> MenuVariant("$itemId-variant-$variantIndex", price.first, price.second * 100L, ArabicMenuNames.variant(price.first)) }
                MenuItem(itemId, menuCategories[categoryIndex].id, source.name,
                    basePriceMinor = source.prices.first().second * 100L, variants = variants, nameAr = ArabicMenuNames.item(source.name))
            }
        }
        val phone = when (id) {
            "builtin-sultan" -> "+971566960295"
            "builtin-al-kalha" -> "+97165588444"
            "builtin-al-mahla" -> "+971502250569"
            else -> null
        }
        val nameAr = when(id) { "builtin-sultan" -> "مطعم سلطان"; "builtin-al-kalha" -> "مطعم الكلحة"; "builtin-al-mahla" -> "مطعم المحلة"; else -> "" }
        val cuisine = when(id) { "builtin-sultan", "builtin-al-mahla" -> "Egyptian"; "builtin-al-kalha" -> "Levantine"; else -> "Arabic" }
        val cuisineAr = when(id) { "builtin-sultan", "builtin-al-mahla" -> "مصري"; "builtin-al-kalha" -> "شامي"; else -> "عربي" }
        val restaurant = Restaurant(id, name, currency = "AED", contact = RestaurantContact(phoneE164 = phone), pricing = RestaurantPricing(TaxTreatment.INCLUDED),
            notes = "Menu transcribed from the restaurant's supplied menu image.", menu = Menu(menuCategories, items = menuItems), nameAr = nameAr,
            emirate = "Sharjah", emirateAr = "الشارقة", area = "Sharjah", areaAr = "الشارقة", cuisine = cuisine, cuisineAr = cuisineAr,
            mealTypes = listOf(MealType.BREAKFAST, MealType.LUNCH, MealType.DINNER))
        return RestaurantExport(exportId = id, restaurant = restaurant)
    }

    /** The photographed Sultan prices include the requested AED 1 increase. */
    val sultan = restaurant("builtin-sultan", "Sultan Restaurant",
        category("The Dish",
            item("Beans With Corn Oil Dish", "Small" to 6, "Medium" to 9, "Large" to 11),
            item("Beans With Oil (Spicy And Olive)", "Regular" to 9, "Large" to 11),
            item("Beans With Ghee Dish", 13), item("Beans With Sauce Dish", 13),
            item("Beans With Alexandrian Sauce Dish", 13), item("Beans With Eggs Dish", 13),
            item("Mixed Dish", 11), item("Sultan's Service", 26), item("Falafel Dish", 7),
            item("French Fried Potato Dish", 7), item("Mashed Potato Dish", 9), item("Mashed Egg Dish", 13),
            item("Mussaka Dish", 9), item("Fried Eggplant Dish With Flour", 7), item("Stuffed Falafel Dish", 9),
            item("Cheese And Tomato Dish", 11), item("Boiled Egg Dish", 11), item("Fried Egg Dish", 11),
            item("Fried Egg With Cheese Dish", 13), item("Eggs With Pastrami Dish", 13),
            item("Eggs With Sausage Dish", 13), item("Shakshouka Dish", 13),
            item("Alexandrian Liver Dish", 21), item("Alexandrian Sausage Dish", 21),
            item("Stuffed Falafel Ball", 2), item("2 Falafel Balls", 2), item("Alexandrian Falafel", 2),
            item("Falafel Stuffed With Cheese", 3),
        ),
        category("Sandwiches",
            item("Falafel", 4), item("Stuffed Falafel", 5), item("Roman Falafel", 7), item("Potato Falafel", 5),
            item("Eggplant Falafel", 5), item("Egg Falafel", 6), item("Alexandrian Falafel", 5), item("Mixed", 6),
            item("Saroukh", 7), item("Beans With Corn Oil", 4), item("Beans With Oil (Hot - Olive)", 5),
            item("Alexandrian Fava Bean", 5), item("Beans With Sausage", 8), item("Beans With Sauce", 6),
            item("Foul Ghee", 6), item("Beans With Egg", 6), item("Falafel Fava Bean", 6),
            item("Potato Fries", 5), item("Roman Potato", 6), item("Mashed Potato", 5),
            item("Roman Mashed Potato", 6), item("Mashed Potatoes With Egg", 6), item("Eggplant", 5),
            item("Mussaka", 5), item("Cheese And Tomato", 5), item("Boiled Egg", 6), item("Fried Egg", 6),
            item("Fried Egg And Cheese", 7), item("Shakshouka", 7), item("Egg With Pastrami", 7),
            item("Egg And Sausage", 7), item("Alexandrian Liver", 7), item("Alexandrian Sausage", 7),
            item("Loaf Hawawshi", 10), item("Sausage Hawawshi Loaf", 10),
            item("Mozzarella Hawawshi Loaf", 14), item("Alexandrian Hawawshi", 16),
        ),
        category("Drinks",
            item("Suleimani Tea", 3), item("Milk Tea", 6), item("Green Tea", 3),
            item("Turkish Coffee", "Small" to 4, "Large" to 6),
            item("Nescafe Black", "Small" to 3, "Large" to 6), item("Nescafe Milk", 6),
            item("Teapot", 8), item("Soft Drinks", 4),
        ),
    )

    private val breads = listOf("Samoon", "Shrak", "Tannour", "Kaak", "Lebanese", "Saj")
    private fun sandwich(name: String, vararg prices: Int?): SourceItem = SourceItem(name,
        prices.mapIndexedNotNull { index, price -> price?.let { breads[index] to it } })

    val alKalha = restaurant("builtin-al-kalha", "Al Kalha Restaurant",
        category("Sandwiches",
            sandwich("Chicken liver and hummus", 12, 12, 12, 12, 9, 9),
            sandwich("Chicken liver with egg & onion", 13, 13, 13, 13, 10, 10),
            sandwich("Lamb liver with hummus", 15, 15, 15, 15, 12, 12),
            sandwich("Kidney sandwich with hummus", 15, null, null, 15, null, null),
            sandwich("Plain fried eggs", 9, 9, 9, 9, 6, 6),
            sandwich("Eggs with meat", 15, 15, 15, 15, 10, 10),
            sandwich("Mferakeh egg and potato", 10, 10, 10, 10, 7, 7),
            sandwich("Egg with nabulsi cheese", 13, 13, 13, 13, 9, 9),
            sandwich("Shakshoka egg and tomato", 10, null, null, 10, null, 7),
            sandwich("Ajjieh (egg & parsley)", 9, 9, 9, 9, 7, 7),
            sandwich("Fried cheese", 13, 13, 13, 13, 9, 9),
            sandwich("Falafel with hummus and salad", 9, 9, 9, 9, 6, 6),
            sandwich("Falafel, hummus, vegetables, fried & salad", 10, 10, 10, 10, 6, 7),
            sandwich("Mixed vegetables fried", 10, 10, 10, 10, 7, 7),
            sandwich("Foul sandwich", 8, null, null, 8, null, 5),
            sandwich("Foul, falafel and fried vegetables", 10, null, null, 10, null, 7),
            sandwich("Super stuffed falafel", 10, 10, 10, 10, 7, 7),
            sandwich("Super stuffed falafel with cheese", 10, 10, 10, 10, 7, 7),
            sandwich("Al Kalha falafel super and qalaya", 10, 10, 10, 10, 8, 8),
            sandwich("French fries sandwich", 9, 9, 9, 9, 7, 7),
            sandwich("Spicy potato sandwich", 10, 10, 10, 10, 7, 7),
            sandwich("Mutabal (eggplant)", 10, null, null, 10, null, 7),
            sandwich("Hummus and meat", 12, 12, 12, 12, 9, 9),
        ),
        category("Manakish",
            item("Pieda Mankosha Normal", 25), item("Pieda Mankosha Mix", 30), item("Pizza Margherita", 29),
            item("Pizza with meat", 30), item("Vegetable pizza", 25), item("Musakhan onion and sumac Mankosha", 12),
            item("Lahm with green dough / pomegranate molasses", 12), item("Meat and cheese mankosha", 13),
            item("Akkawi cheese", 12), item("Kashkawan cheese", 12), item("Mortadella cheese", 12),
            item("Spinach", 12), item("Spinach cheese", 13), item("Potato", 12), item("Potato with cheese", 13),
            item("Egg Mankosha", 10), item("Cheese and Egg Mankosha", 12), item("Mix cheese", 13),
            item("Zaatar", 10), item("Cheese with zaatar", 12), item("Muhammara", 10),
            item("Muhammara with Kashkawan", 12), item("Labneh", 12), item("Labneh with Vegetable", 13),
            item("Labneh with Zaatar", 12), item("Labneh with Falafel", 12), item("Kraft with Honey", 12),
        ),
    )

    val alMahla = restaurant("builtin-al-mahla", "Al Mahla Restaurant",
        category("Sandwiches",
            item("Foul with eggs", 6), item("Foul with sujiq", 7), item("Foul with liver", 7),
            item("Foul with olive oil / Hot oil", 4), item("Foul with ghee", 5), item("Foul muslih", 6),
            item("Foul with corn oil", 3), item("Foul with pastrami", 6), item("Foul with Sauce or Garlic", 5),
            item("Eggs with pastrami", 6), item("Eggs with sujiq", 7), item("Eggs with cheese", 6),
            item("Eggs with romy cheese", 6), item("Cheese with tomatoes", 4), item("Mashed potatoes", 4),
            item("Mashed potatoes with eggs", 6), item("French fries", 4), item("Potato with romy cheese", 5),
            item("Shakshuka", 6), item("Msaqaa", 4), item("Eggplant", 4), item("Alexandrian liver", 6),
            item("Kofta", 7), item("Sujiq", 6), item("Falafel", 3), item("Falafel with romy cheese", 6),
            item("Egg falafel", 6), item("Mixed sandwiches", 4), item("Mahlah rocket sandwich", 6),
        ),
    )

    val baitAlWaleema: RestaurantExport = BaitAlWaleemaRestaurant.export.let { export -> export.copy(restaurant = export.restaurant.copy(
        emirate = "Sharjah", emirateAr = "الشارقة", area = "Al Majaz", areaAr = "المجاز", cuisine = "Egyptian", cuisineAr = "مصري",
        mealTypes = listOf(MealType.LUNCH, MealType.DINNER),
    )) }

    val all: List<RestaurantExport> = listOf(sultan, alKalha, alMahla, baitAlWaleema) + SharjahRestaurantCatalog.all + DubaiRestaurantCatalog.all
}
