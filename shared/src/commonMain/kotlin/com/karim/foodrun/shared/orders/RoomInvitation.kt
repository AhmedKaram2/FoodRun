package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal fun roomInvitation(room: Room, link: String, language: String): String {
    val arabic = language == "ar"
    val restaurant = if (room.restaurantPollOpen) {
        (if (arabic) "تصويت المطاعم: " else "Restaurant poll: ") +
            room.restaurantOptions.joinToString(" · ") { it.localizedName(language) }
    } else (if (arabic) "المطعم: " else "Restaurant: ") + room.restaurant.localizedName(language)
    return listOf("Intrvioo · ${room.name}", restaurant,
        (if (arabic) "طلب رقم " else "Order #") + room.orderNumber,
        (if (arabic) "كود الغرفة: " else "Room code: ") + room.code,
        if (arabic) "انضم بالرابط أو الكود. بعد الاختيار، يجب أن يوافق منشئ الغرفة أو الشخص المختار على المنضمين الجدد." else "Join using the link or code. After selection, the room creator or selected person must approve new joiners.",
        link).joinToString("\n")
}
