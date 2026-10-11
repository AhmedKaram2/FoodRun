package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.*
import kotlin.test.*

class RestaurantOrderSummaryTest {
    @Test fun ownerSummaryContainsTheWholeFoodBillWithoutMemberPaymentOrAccountData() = RoomFixture().use { f ->
        val selected=f.join("Selected")
        val other=f.join("Other")
        f.send(f.owner,CommandKind.PARTICIPATE) { it.copy(flag=false) }
        f.send(f.owner,CommandKind.SELECT_PAYER) { it.copy(memberId=selected.memberId) }
        f.send(selected,CommandKind.SHARE_ACCOUNT) { it.copy(account=f.account) }
        f.cart(selected,2);f.cart(other,3)
        val owner=f.state()
        assertTrue(owner.receipts.isEmpty())
        assertNull(owner.room!!.account)
        val summary=owner.restaurantOrder!!
        assertEquals(500,summary.total)
        assertEquals(5,summary.lines.sumOf { it.quantity })
        assertNull(f.state(other).restaurantOrder)
        val oldClient=orderJson.parseToJsonElement(orderJson.encodeToString(owner.forClient(true))).jsonObject
        assertFalse("restaurantOrder" in oldClient)
        val current=orderJson.parseToJsonElement(orderJson.encodeToString(owner.forClient(true,restaurantOrderDetails=true))).jsonObject
        assertEquals(setOf("lines","total","currency"),current.getValue("restaurantOrder").jsonObject.keys)
        assertFalse(orderJson.encodeToString(summary).contains(f.account.identifier))
        val command=f.command(f.owner,CommandKind.PLACE).copy(restaurantOrderDetails=true)
        f.execute(command)
        val revision=f.state().room!!.revision
        f.execute(command.copy(restaurantOrderDetails=false))
        assertEquals(revision,f.state().room!!.revision)
    }
}
