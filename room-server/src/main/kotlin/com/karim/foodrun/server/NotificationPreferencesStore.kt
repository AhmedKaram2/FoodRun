package com.karim.foodrun.server

import com.karim.foodrun.orders.*

internal object NotificationPreferencesStore {
    fun read(db: RoomDatabase, uid: String) = db.record("notification-preferences:${AccountAliases.resolve(db,uid)}")?.let { orderJson.decodeFromString<NotificationPreferences>(it) } ?: NotificationPreferences()
    fun emailUser(db: RoomDatabase, address: String): String? = db.record("account-email:${RoomService.hash(address.lowercase())}")
        ?: db.records("wallet-search-email:").firstOrNull { it.second.equals(address,true) }?.first?.removePrefix("wallet-search-email:")
    fun emailAllowed(db: RoomDatabase, uid: String, address: String) = (uid.takeIf { it.isNotEmpty() } ?: emailUser(db,address))?.let { read(db,it).emailEnabled } != false
}
