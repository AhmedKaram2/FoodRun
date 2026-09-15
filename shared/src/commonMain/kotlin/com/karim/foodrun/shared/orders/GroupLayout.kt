package com.karim.foodrun.shared.orders

/** Presentation grouping shared by the native renderers. All domain actions remain unchanged. */
data class GroupSection(val title: String, val cards: List<GroupCard>)

internal object GroupLayout {
    val roomUtilities = setOf(
        GroupAction.SHARE_ROOM, GroupAction.OPEN_RECEIPTS, GroupAction.OPEN_HISTORY,
        GroupAction.SAVE_ROOM_RESTAURANT, GroupAction.SET_FEES, GroupAction.REOPEN,
        GroupAction.CANCEL, GroupAction.ADJUST_BILL,
    )
    val roomExtraFields = setOf(
        GroupFieldKey.REASON, GroupFieldKey.DELIVERY_FEE, GroupFieldKey.SERVICE_FEE,
        GroupFieldKey.DISCOUNT, GroupFieldKey.PROPORTIONAL,
    )

    fun sections(page: GroupPage, cards: List<GroupCard>): List<GroupSection> {
        if (page == GroupPage.LIBRARY) {
            val (restaurants, information) = cards.partition { it.id.startsWith("restaurant:") }
            return listOf(GroupSection("", information), GroupSection("Saved restaurants", restaurants))
                .filter { it.cards.isNotEmpty() }
        }
        // Historical receipts and their recipient must stay next to their original order.
        if (page != GroupPage.ROOM) return listOf(GroupSection(when (page) {
            GroupPage.RESTAURANT -> "On the menu"
            GroupPage.ACCOUNT -> "Receiving accounts"
            GroupPage.ITEM -> "Make it yours"
            else -> ""
        }, cards)).filter { it.cards.isNotEmpty() }
        fun category(card: GroupCard): String = when {
            card.id.startsWith("menu:") -> "Choose your food"
            card.id.startsWith("cart:") || card.id == "estimate" -> "Your order"
            card.id.startsWith("receipt:") || card.id == "account" -> "Totals & recipient"
            card.id.startsWith("transfer:") -> "Payment activity"
            card.id.startsWith("member:") || card.id.startsWith("invite:") -> "At the table"
            card.id.startsWith("quote:") -> "Quote confirmations"
            else -> "Order updates"
        }
        val grouped = cards.groupBy(::category)
        return listOf("Order updates", "Choose your food", "Your order", "Totals & recipient", "Quote confirmations", "Payment activity", "At the table")
            .mapNotNull { title -> grouped[title]?.let { GroupSection(title, it) } }
    }
}
