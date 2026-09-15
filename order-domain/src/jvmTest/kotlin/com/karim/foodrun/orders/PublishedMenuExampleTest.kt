package com.karim.foodrun.orders

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

class PublishedMenuExampleTest {
    @Test fun publishedExampleImportsAndMatchesTheDocumentedPrice() {
        // Gradle runs this task with order-domain as its working directory.
        val export = MenuValidation.import(File("../Docs/restaurant-menu.example.json").readText())
        val cart = MemberCart(
            memberId = "example-reader",
            lines = listOf(CartLine("lunch", "burger", 1, "large", listOf("extra-cheese"))),
        )

        assertEquals("AED", export.restaurant.currency)
        assertEquals(4500L, Billing.lines(export.restaurant, cart).single().amount)
    }
}
