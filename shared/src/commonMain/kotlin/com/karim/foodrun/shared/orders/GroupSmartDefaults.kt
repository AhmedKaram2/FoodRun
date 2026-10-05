package com.karim.foodrun.shared.orders

import com.karim.foodrun.shared.localFoodDateTime
import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable

internal fun mealRoomName(now: Long, offsetSeconds: Int, language: String): String {
    val date = localFoodDateTime(now.toDouble(), offsetSeconds)
    val arabic = language == "ar"
    val months = if (arabic) listOf("يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو", "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر")
        else listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val meal = if (date.hour < 13) { if (arabic) "فطور" else "Breakfast" } else { if (arabic) "غداء" else "Lunch" }
    return "$meal · ${date.day.toString().padStart(2, '0')} ${months[date.month - 1]} ${date.year}"
}

@Serializable internal data class RoomPreferences(val restaurantId: String = "", val destination: String = "")
internal class GroupSmartDefaults(private val c: GroupController) {
    private val suggestedNames = mutableMapOf<GroupFieldKey, String>()
    private fun key() = c.library.home?.profile?.userId?.takeIf { it.isNotEmpty() }?.let { "foodrun-room-preferences:$it" }
    private fun currentRoomName(): String = c.platform.now().let { mealRoomName(it, c.platform.localOffsetSeconds(it), c.library.language) }
    fun roomName(key: GroupFieldKey = GroupFieldKey.ROOM_NAME): String = currentRoomName().also { suggestedNames[key] = it }
    fun submittedName(key: GroupFieldKey): String = c.text(key).trim().let { if (it == suggestedNames[key]) currentRoomName() else it }
    fun seed() {
        c.draft[GroupFieldKey.ROOM_NAME] = roomName()
        val saved = key()?.let { runCatching { orderJson.decodeFromString<RoomPreferences>(c.platform.read(it)) }.getOrNull() }
        c.draft[GroupFieldKey.DESTINATION] = saved?.destination ?: "Mohre, Backside Parking, Security gate, Opposite Suni's Restaurant https://maps.app.goo.gl/cLba7hYb9Rtfqyjr5"
        c.selectedRestaurant = saved?.let { prefs -> c.library.restaurants.firstOrNull { it.restaurant.id == prefs.restaurantId } }
    }
    fun remember(room: Room) {
        val key = key() ?: return
        c.platform.write(key, orderJson.encodeToString(RoomPreferences(if(room.restaurantPollOpen) "" else room.restaurant.id, room.destination)))
    }
}
