package com.karim.foodrun.server

import com.karim.foodrun.orders.FoodProfile
import com.karim.foodrun.orders.orderJson
import java.nio.file.Files
import kotlin.test.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*

class AdminServiceTest {
    private class AdminIdentityProvider : IdentityProvider {
        var identity = CloudIdentity("admin", "valid-token", email = AdminService.ADMIN_EMAIL, emailVerified = true)
        override fun exchange(idToken: String): CloudIdentity { require(idToken == "valid-token"); return identity }
        override fun signIn(email: String, password: String, register: Boolean) = error("Not used")
        override fun refresh(refreshToken: String) = error("Not used")
        override fun profile(identity: CloudIdentity): FoodProfile? = null
        override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
        override fun resetPassword(email: String) = Unit
        override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
    }
    @Test fun everyAdminHttpRouteRejectsOtherAccountsAndLegacyLoginIsRemoved() {
        val directory = Files.createTempDirectory("foodrun-admin-routes").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider()
            val rooms = RoomService(db)
            val admin = AdminService(db, rooms, identityProvider = provider)
            testApplication {
                application { hubRoutes(rooms, admin) }
                assertEquals(HttpStatusCode.OK, client.get("/admin/dashboard") { bearerAuth("valid-token") }.status)
                provider.identity = provider.identity.copy(email = "other@example.com")
                assertEquals(HttpStatusCode.Unauthorized, client.get("/admin/dashboard") { bearerAuth("valid-token") }.status)
                for (path in listOf("settings", "restaurant", "user", "room", "block-request", "cleanup/preview", "cleanup/delete")) {
                    val response = client.post("/admin/$path") { bearerAuth("valid-token"); contentType(ContentType.Application.Json); setBody("{}") }
                    assertFalse(response.status.isSuccess(), path)
                }
                assertEquals(HttpStatusCode.NotFound, client.post("/admin/login") { setBody("{}") }.status)
                assertTrue(admin.settings().registrationsEnabled)
            }
        }
        directory.deleteRecursively()
    }

    @Test fun onlyVerifiedAllowedEmailCanAccessAndDisabledOrChangedIdentityIsDenied() {
        val directory = Files.createTempDirectory("foodrun-admin-auth").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider()
            val admin = AdminService(db, RoomService(db), identityProvider = provider)
            admin.authorize("Bearer valid-token")
            for (header in listOf(null, "valid-token", "Bearer invalid", "Bearer old-password-session")) assertFails { admin.authorize(header) }
            provider.identity = provider.identity.copy(email = "other@example.com")
            assertFails { admin.authorize("Bearer valid-token") }
            provider.identity = provider.identity.copy(email = AdminService.ADMIN_EMAIL, emailVerified = false)
            assertFails { admin.authorize("Bearer valid-token") }
            provider.identity = provider.identity.copy(email = AdminService.ADMIN_EMAIL.uppercase(), emailVerified = true)
            admin.authorize("Bearer valid-token")
            db.putRecord("admin:disabled:admin", "true")
            admin.authorize("Bearer valid-token") // A room-access block does not disable account administration.
            db.putRecord("admin:restriction:admin", orderJson.encodeToString(AccountRestriction(removed = true)))
            assertFails { admin.authorize("Bearer valid-token") }
        }
        directory.deleteRecursively()
    }

    @Test fun deletedRestaurantIdsPersistAndExplicitSaveRestoresTheRestaurant() {
        val directory = Files.createTempDirectory("foodrun-catalog-deletion").toFile()
        RoomDatabase(directory).use { db ->
            val admin = AdminService(db, RoomService(db))
            val restaurant = admin.catalog().first()
            admin.mutateRestaurant(AdminRestaurantMutation(action = "delete", restaurantId = restaurant.id))
            val restoredAdmin = AdminService(db, RoomService(db))
            assertTrue(restaurant.id in restoredAdmin.catalogPayload().deletedRestaurantIds)
            assertFalse(restoredAdmin.catalog().any { it.id == restaurant.id })
            restoredAdmin.mutateRestaurant(AdminRestaurantMutation(restaurant = restaurant))
            assertFalse(restaurant.id in restoredAdmin.catalogPayload().deletedRestaurantIds)
            assertTrue(restoredAdmin.catalog().any { it.id == restaurant.id })
        }
        directory.deleteRecursively()
    }

    @Test fun adminIdentityControlsSettingsUsersAndRestaurantCatalog() {
        val directory = Files.createTempDirectory("foodrun-admin-test").toFile()
        RoomDatabase(directory).use { db ->
            val rooms = RoomService(db)
            val admin = AdminService(db, rooms, identityProvider = AdminIdentityProvider())
            db.putRecord("profile:user-1", orderJson.encodeToString(FoodProfile("user-1", "Ahmed", "+971501234567")))

            admin.authorize("Bearer valid-token")
            assertFails { admin.authorize("Bearer invalid") }

            admin.saveSettings(AdminSettings(registrationsEnabled = false, roomCreationEnabled = true, maintenanceMessage = "Updating menus"))
            admin.mutateUser(AdminUserMutation("user-1", disabled = true))
            val dashboard = admin.dashboard()
            assertFalse(dashboard.settings.registrationsEnabled)
            assertEquals("Updating menus", dashboard.settings.maintenanceMessage)
            assertTrue(dashboard.users.single().disabled)
            assertEquals(43, dashboard.restaurants.size)
            assertTrue(dashboard.restaurants.all { it.currency == "AED" })
        }
        directory.deleteRecursively()
    }
}
