package com.karim.foodrun.server

import com.karim.foodrun.orders.*
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
    @Test fun nativeSessionRequiresTheSameVerifiedAdminIdentityForEveryAction() {
        val directory = Files.createTempDirectory("foodrun-native-admin").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider()
            val rooms = RoomService(db, identityProvider = provider)
            val admin = AdminService(db, rooms, identityProvider = provider)
            val login = rooms.execute(RoomCommand(commandId = "native-admin-login", kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token")))
            assertTrue(login.ok, login.error)
            val session = login.identityToken
            assertNotNull(admin.nativeRequest(NativeAdminRequest(session)).dashboard)
            provider.identity = provider.identity.copy(email = "another@example.test")
            assertFails { admin.nativeRequest(NativeAdminRequest(session, "settings", orderJson.encodeToString(AdminSettings(registrationsEnabled = false)))) }
            assertTrue(admin.settings().registrationsEnabled)
            provider.identity = provider.identity.copy(email = AdminService.ADMIN_EMAIL, emailVerified = false)
            assertFails { admin.nativeRequest(NativeAdminRequest(session)) }
            provider.identity = provider.identity.copy(emailVerified = true)
            assertTrue(admin.nativeRequest(NativeAdminRequest(session, "access")).ok)
            assertFails { admin.nativeRequest(NativeAdminRequest("invalid-session")) }
        }
        directory.deleteRecursively()
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

    @Test fun signedInDashboardUserCanPublishANewPricedRestaurantButCannotReplaceAnotherContribution() {
        val directory = Files.createTempDirectory("foodrun-catalog-contribution").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider().apply { identity = identity.copy(userId = "contributor-1", email = "owner@example.test") }
            val rooms = RoomService(db, identityProvider = provider)
            val admin = AdminService(db, rooms, identityProvider = provider)
            val firstLogin = rooms.execute(RoomCommand(commandId = "catalog-login-one", kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token")))
            val restaurant = admin.catalog().first().copy(id = "community-kitchen", name = "Community Kitchen", branchName = "Downtown")
            val result = admin.contributeRestaurant(CatalogRestaurantMutation(firstLogin.identityToken, restaurant = restaurant))
            assertEquals(restaurant, result.restaurants.single { it.id == restaurant.id })
            assertEquals("contributor-1", db.record("${AdminService.CONTRIBUTOR_PREFIX}${restaurant.id}"))

            provider.identity = provider.identity.copy(userId = "contributor-2", email = "other@example.test")
            val secondLogin = rooms.execute(RoomCommand(commandId = "catalog-login-two", kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token")))
            assertFailsWith<IllegalArgumentException> {
                admin.contributeRestaurant(CatalogRestaurantMutation(secondLogin.identityToken, restaurant = restaurant.copy(nameAr = "مطبخ المجتمع")))
            }
            assertFailsWith<IllegalArgumentException> {
                admin.contributeRestaurant(CatalogRestaurantMutation(secondLogin.identityToken,
                    restaurant = restaurant.copy(id = "empty-menu", name = "Empty Menu", menu = Menu(), openOrdering = true)))
            }
        }
        directory.deleteRecursively()
    }

    @Test fun membersAppendItemsDurablyWithoutOverwritingEachOtherOrAnExistingBill() {
        val directory = Files.createTempDirectory("shared-menu-additions").toFile()
        try {
            RoomDatabase(directory).use { db ->
                val provider = AdminIdentityProvider().apply { identity = identity.copy(userId = "order-owner", email = "owner@example.test", name = "Owner") }
                val rooms = RoomService(db, identityProvider = provider)
                fun command(kind: CommandKind, token: String, restaurant: Restaurant? = null) = RoomCommand(commandId = java.util.UUID.randomUUID().toString(), kind = kind, identityToken = token, restaurant = restaurant)
                fun login() = rooms.execute(command(CommandKind.IDENTITY, "").copy(identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token"))).also { assertTrue(it.ok, it.error) }.identityToken
                val owner = login()
                val original = AdminService(db, rooms, identityProvider = provider).catalog().first()
                val order = rooms.execute(command(CommandKind.CREATE, owner, original).copy(name = "Owner", text = "Breakfast"))
                assertTrue(order.ok, order.error)
                provider.identity = provider.identity.copy(userId = "baraa", email = "baraa@example.test", name = "Baraa")
                val baraa = login()
                val category = original.menu.categories.first()
                val first = MenuItem("new-breakfast-item", category.id, "New sandwich", basePriceMinor = 750)
                val second = first.copy(id = "another-breakfast-item", name = "Another sandwich", basePriceMinor = 450)
                fun contribution(item: MenuItem) = original.copy(menu = Menu(items = listOf(item), categories = listOf(category)))
                val add = command(CommandKind.ADD_MENU_ITEMS, baraa, contribution(first))
                assertTrue(rooms.execute(add).ok)
                assertTrue(rooms.execute(command(CommandKind.ADD_MENU_ITEMS, owner, contribution(second))).ok)
                assertTrue(rooms.execute(add).ok) // lost acknowledgement must not duplicate the item
                val saved = rooms.execute(command(CommandKind.HOME, owner)).home!!.restaurants.single { it.id == original.id }
                assertEquals(1, saved.menu.items.count { it.id == first.id })
                assertTrue(saved.menu.items.contains(second))
                assertEquals(original, db.room(order.room!!.id)!!.restaurant, "Existing order prices stay unchanged")
                val bad = rooms.execute(command(CommandKind.ADD_MENU_ITEMS, baraa, contribution(original.menu.items.first().copy(basePriceMinor = 1))))
                assertFalse(bad.ok, "Appending cannot overwrite an existing price")
                val newer = rooms.execute(command(CommandKind.CREATE, owner, original).copy(name = "Owner", text = "Tomorrow"))
                assertTrue(newer.room!!.restaurant.menu.items.contains(first))
                val completed = db.room(order.room!!.id)!!.copy(phase = RoomPhase.ARCHIVED)
                db.save(completed)
                val next = rooms.execute(RoomCommand(commandId = java.util.UUID.randomUUID().toString(), kind = CommandKind.NEXT_ORDER,
                    roomId = completed.id, token = order.token, expectedOrderNumber = completed.orderNumber, expectedRevision = completed.revision))
                assertTrue(next.ok, next.error)
                assertTrue(next.room!!.restaurant.menu.items.contains(first))
                assertTrue(next.room!!.restaurant.menu.items.contains(second))
            }
            RoomDatabase(directory).use { db ->
                val restaurants = orderJson.decodeFromString<List<Restaurant>>(db.record(AdminService.RESTAURANTS)!!)
                assertTrue(restaurants.any { r -> r.menu.items.any { it.id == "new-breakfast-item" } })
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun roomOwnerCanPublishFromTheRoomWithTheirLinkedSession() {
        val directory = Files.createTempDirectory("foodrun-owner-catalog").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider().apply { identity = identity.copy(userId = "room-owner", email = "owner@example.test") }
            val rooms = RoomService(db, identityProvider = provider)
            val admin = AdminService(db, rooms, identityProvider = provider)
            val login = rooms.execute(RoomCommand(commandId = "owner-catalog-login", kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token")))
            val selected = admin.catalog().first()
            val created = rooms.execute(RoomCommand(commandId = "owner-create-room", kind = CommandKind.CREATE,
                identityToken = login.identityToken, name = "Owner", text = "Lunch", restaurant = selected, restaurants = listOf(selected)))
            assertTrue(created.ok, created.error)
            val restaurant = selected.copy(id = "owner-new-restaurant", name = "Owner New Restaurant")
            val result = admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, created.room!!.id, created.token, restaurant))
            assertTrue(result.restaurants.any { it.id == restaurant.id })
            val edited = selected.copy(nameAr = "مطعم الغرفة")
            assertEquals(edited, admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, created.room!!.id, created.token, edited))
                .restaurants.single { it.id == selected.id })
            assertFailsWith<IllegalArgumentException> {
                val unrelated = admin.catalog().first { it.id != selected.id && it.id != restaurant.id }
                admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, created.room!!.id, created.token, unrelated.copy(nameAr = "غير مسموح")))
            }
            assertFailsWith<IllegalArgumentException> {
                admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, created.room!!.id, "wrong-room-token", restaurant.copy(id = "rejected")))
            }
        }
        directory.deleteRecursively()
    }

    @Test fun verifiedAdministratorCanEditThroughTheNormalCatalogWithoutTakingContributorOwnership() {
        val directory = Files.createTempDirectory("foodrun-admin-catalog-edit").toFile()
        RoomDatabase(directory).use { db ->
            val provider = AdminIdentityProvider()
            val rooms = RoomService(db, identityProvider = provider)
            val admin = AdminService(db, rooms, identityProvider = provider)
            val login = rooms.execute(RoomCommand(commandId = "admin-catalog-login", kind = CommandKind.IDENTITY,
                identity = IdentityRequest(IdentityAction.FIREBASE_SIGN_IN, firebaseToken = "valid-token")))
            val selected = admin.catalog().first()
            val contributionKey = "${AdminService.CONTRIBUTOR_PREFIX}${selected.id}"
            db.putRecord(contributionKey, "original-contributor")
            val edited = selected.copy(nameAr = "تعديل المدير")
            assertEquals(edited, admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, restaurant = edited))
                .restaurants.single { it.id == selected.id })
            assertEquals("original-contributor", db.record(contributionKey))
            provider.identity = provider.identity.copy(emailVerified = false)
            assertFailsWith<IllegalArgumentException> { admin.contributeRestaurant(CatalogRestaurantMutation(login.identityToken, restaurant = edited)) }
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
