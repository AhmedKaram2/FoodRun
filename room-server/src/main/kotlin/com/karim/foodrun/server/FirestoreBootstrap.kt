package com.karim.foodrun.server

import com.google.auth.oauth2.ServiceAccountCredentials
import com.google.cloud.firestore.FirestoreOptions
import com.karim.foodrun.orders.*
import java.io.File
import kotlinx.serialization.encodeToString

/** One-time operator utility. Imports verified profiles/catalog only, never invents room balances. */
fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: FirestoreBootstrapKt <catalog-json> <settings-json>" }
    require(System.getenv("FOODRUN_STORAGE") == "firestore" && System.getenv("FOODRUN_FIRESTORE_BOOTSTRAP") == "true")
    require(System.getenv("FIRESTORE_EMULATOR_HOST").isNullOrBlank())
    val credentials = ServiceAccountCredentials.fromStream(requireNotNull(System.getenv("FOODRUN_FIRESTORE_CREDENTIALS")).byteInputStream())
    require(credentials.projectId == System.getenv("FOODRUN_FIREBASE_PROJECT_ID"))
    val namespace = requireNotNull(System.getenv("FOODRUN_FIRESTORE_NAMESPACE"))
    val catalog = orderJson.decodeFromString<RestaurantCatalogPayload>(File(args[0]).readText())
    val settings = orderJson.decodeFromString<AdminSettings>(File(args[1]).readText())
    val client = FirestoreOptions.newBuilder().setProjectId(credentials.projectId).setCredentials(credentials).build().service
    client.use { firestore ->
        require(!firestore.collection("foodrunServers").document(namespace).get().get().exists()) { "The target store already exists. Refusing to overwrite it." }
        val profiles = firestore.collection("users").select("foodRunProfile").get().get().documents.mapNotNull { document ->
            document.getString("foodRunProfile")?.let { body ->
                val profile = orderJson.decodeFromString<FoodProfile>(body)
                require(profile.userId == document.id) { "Profile identity does not match its source document." }
                StoredRow("account_records", listOf("profile:${document.id}", orderJson.encodeToString(profile)))
            }
        }
        val records = profiles + listOf(
            StoredRow("account_records", listOf("storage:format", "1")),
            StoredRow("account_records", listOf(AdminService.RESTAURANTS, orderJson.encodeToString(catalog.restaurants))),
            StoredRow("account_records", listOf(AdminService.DELETED_RESTAURANTS, orderJson.encodeToString(catalog.deletedRestaurantIds))),
            StoredRow("account_records", listOf(AdminService.SETTINGS, orderJson.encodeToString(settings))),
        )
        FirestoreStore(firestore, namespace, allowCreate = true).let { store ->
            require(store.load() == null) { "The target store was initialized by another process." }
            store.commit(records.associateBy { it.id })
        }
        println("Imported ${profiles.size} existing profiles and ${catalog.restaurants.size} restaurants. No room or wallet balances were synthesized.")
    }
}
