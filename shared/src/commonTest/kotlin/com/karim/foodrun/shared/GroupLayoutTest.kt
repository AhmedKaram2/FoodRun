package com.karim.foodrun.shared

import com.karim.foodrun.shared.orders.*
import kotlin.test.*

class GroupLayoutTest {
    @Test fun everyActionRemainsReachableWhenRoomControlsMove() {
        val actions = GroupAction.entries.mapIndexed { index, action ->
            GroupButton(action.name, action, value = "value:$index", primary = action == GroupAction.READY || action == GroupAction.PREPARE_SPIN)
        }
        for (page in GroupPage.entries) {
            val state = GroupState(page = page, buttons = actions)
            val rendered = listOfNotNull(state.primaryAction) + state.inlineButtons + state.utilityButtons
            assertEquals(actions.toSet(), rendered.toSet(), page.name)
            assertEquals(actions.size, rendered.size, "An action was duplicated on $page")
        }
    }

    @Test fun fieldsRemainAvailableWithOriginalValuesAndOrder() {
        val fields = GroupFieldKey.entries.map { GroupField(it, it.name, "draft:${it.name}") }
        for (page in GroupPage.entries) {
            val state = GroupState(page = page, fields = fields)
            assertEquals(fields.toSet(), (state.mainFields + state.extraFields).toSet())
            assertEquals(fields.filter { it !in state.extraFields }, state.mainFields)
            assertEquals(fields.size, state.mainFields.size + state.extraFields.size)
        }
    }

    @Test fun menuAndOrderPrecedeCrewWithoutLosingUnknownCards() {
        val cards = listOf("member:host", "invite:friend", "cart:meal", "new-card-type", "menu:burger", "estimate", "receipt:me", "transfer:1")
            .map { GroupCard(it, it) }
        val rendered = GroupState(page = GroupPage.ROOM, cards = cards).sections.flatMap { it.cards }
        assertEquals(cards.toSet(), rendered.toSet())
        assertEquals(cards.size, rendered.size)
        assertTrue(rendered.indexOfFirst { it.id == "menu:burger" } < rendered.indexOfFirst { it.id == "member:host" })
        assertTrue(rendered.indexOfFirst { it.id == "cart:meal" } < rendered.indexOfFirst { it.id == "member:host" })
    }

    @Test fun historicalRecipientsStayWithTheirOriginalOrder() {
        val cards = listOf("order:1", "receipt:past:1:me", "past-account:1", "order:2", "receipt:past:2:me", "past-account:2")
            .map { GroupCard(it, it) }
        val rendered = GroupState(page = GroupPage.HISTORY, cards = cards).sections.flatMap { it.cards }
        assertEquals(cards, rendered)
    }

    @Test fun libraryCountsOnlySavedRestaurants() {
        val placeholder = GroupCard("empty", "Add your first restaurant")
        val preview = GroupCard("preview", "Imported menu")
        val saved = GroupCard("restaurant:1", "Lunch spot")
        val empty = GroupState(page = GroupPage.LIBRARY, cards = listOf(placeholder)).sections
        assertTrue(empty.none { it.title == "Saved restaurants" })
        val populated = GroupState(page = GroupPage.LIBRARY, cards = listOf(preview, saved)).sections
        assertEquals(listOf(saved), populated.single { it.title == "Saved restaurants" }.cards)
        assertEquals(listOf(preview, saved), populated.flatMap { it.cards })
    }
}
