package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal fun roomInvitation(room: Room, link: String, language: String): String {
    val arabic = language == "ar"
    val restaurant = if (room.restaurantPollOpen) {
        (if (arabic) "تصويت المطاعم: " else "Restaurant poll: ") +
            room.restaurantOptions.joinToString(" · ") { it.localizedName(language) }
    } else (if (arabic) "المطعم: " else "Restaurant: ") + room.restaurant.localizedName(language)
    return listOf("Food Run · ${room.name}", restaurant,
        (if (arabic) "طلب رقم " else "Order #") + room.orderNumber,
        (if (arabic) "كود الغرفة: " else "Room code: ") + room.code,
        if (arabic) "ادخل على طول بالرابط أو الكود، من غير موافقة." else "Join directly using the link or code. No approval needed.",
        link).joinToString("\n")
}
