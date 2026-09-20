package com.karim.foodrun.server

import com.karim.foodrun.orders.FoodProfile
import com.karim.foodrun.orders.orderJson
import java.nio.file.Files
import kotlin.test.*

class AdminServiceTest {
    @Test fun adminSessionControlsSettingsUsersAndRestaurantCatalogWithoutExposingThePassword() {
        val directory = Files.createTempDirectory("foodrun-admin-test").toFile()
        RoomDatabase(directory).use { db ->
            val rooms = RoomService(db)
            val admin = AdminService(db, rooms, username = "Karam", password = "a-long-test-password")
            db.putRecord("profile:user-1", orderJson.encodeToString(FoodProfile("user-1", "Ahmed", "+971501234567")))

            assertFailsWith<IllegalArgumentException> { admin.login(AdminLogin("Karam", "wrong-password")) }
            val session = admin.login(AdminLogin("Karam", "a-long-test-password"))
            admin.authorize("Bearer ${session.token}")
            assertFails { admin.authorize("Bearer invalid") }

            admin.saveSettings(AdminSettings(registrationsEnabled = false, roomCreationEnabled = true, maintenanceMessage = "Updating menus"))
            admin.mutateUser(AdminUserMutation("user-1", disabled = true))
            val dashboard = admin.dashboard()
            assertFalse(dashboard.settings.registrationsEnabled)
            assertEquals("Updating menus", dashboard.settings.maintenanceMessage)
            assertTrue(dashboard.users.single().disabled)
            assertEquals(3, dashboard.restaurants.size)
            assertTrue(dashboard.restaurants.all { it.currency == "AED" })
        }
        directory.deleteRecursively()
    }
}
