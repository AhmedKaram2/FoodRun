package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class TaxAndEmptyOrderTest {
    @Test fun organizerCanReplaceTheDefaultAutomaticDeliveryWithAManualFee() = RoomFixture().use { f ->
        val member = f.join()
        val initial = f.db.room(f.owner.room!!.id)!!
        f.db.save(initial.copy(deliveryMode = true, destination = "Restaurant delivery",
            fees = FeePolicy(automaticDelivery = true)))
        f.start(member)

        val updated = f.send(f.owner, CommandKind.SET_FEES) {
            it.copy(fees = FeePolicy(delivery = 700, automaticDelivery = false), text = "Restaurant delivery quote")
        }.room!!

        assertFalse(updated.fees.automaticDelivery)
        assertEquals(700, updated.fees.delivery)
    }

    @Test fun confirmingTaxPreservesEverySubmissionIncludingNoFood() = RoomFixture().use { f ->
        val unknown = f.restaurant.copy(pricing = f.restaurant.pricing.copy(taxTreatment = TaxTreatment.UNSPECIFIED))
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = unknown, text = "Tax not yet confirmed") }
        val member = f.join(); f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 30); f.cart(member, 0)
        assertTrue(f.state().progress!!.reviewBlocker.contains("tax treatment"))
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = f.restaurant, text = "Prices already include tax") }
        assertTrue(f.state().room!!.carts.all { it.submitted })
        assertTrue(f.state().progress!!.canReview)
        val empty = f.state(member).receipts.single()
        assertEquals(0L, empty.total); assertEquals(0L, empty.tax); assertEquals(0L, empty.delivery)
        assertEquals(RoomPhase.PLACED, f.send(f.owner, CommandKind.PLACE) { it.copy(text = "50 minutes") }.room!!.phase)
    }

    @Test fun feesAndTaxSaveTogetherWithExactScreenshotTotalsAndZeroPayer() = RoomFixture().use { f ->
        val members = (1..5).map { f.join("Person $it") }
        val initial = f.db.room(f.owner.room!!.id)!!
        f.db.save(initial.copy(deliveryMode = true, destination = "Restaurant delivery", restaurant = initial.restaurant.copy(pricing = initial.restaurant.pricing.copy(taxTreatment = TaxTreatment.UNSPECIFIED))))
        f.start(members.first())
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 0)
        members.zip(listOf(20, 20, 20, 26, 33)).forEach { (member, quantity) -> f.cart(member, quantity) }
        val room = f.state().room!!
        f.send(f.owner, CommandKind.SET_FEES) { it.copy(fees = FeePolicy(service = 1000, discount = 2500, automaticDelivery = true),
            restaurant = room.restaurant.copy(pricing = room.restaurant.pricing.copy(taxTreatment = TaxTreatment.INCLUDED)), text = "Tax included, service fee and discount") }
        val receipts = f.state().receipts
        assertEquals(10900L, receipts.sumOf { it.total })
        assertEquals(listOf(1880L,1880L,1880L,2354L,2906L), members.map { member -> receipts.single { it.memberId == member.memberId }.total })
        val empty = receipts.single { it.memberId == f.owner.memberId }
        assertEquals(listOf(0L,0L,0L,0L,0L,0L), listOf(empty.food,empty.delivery,empty.service,empty.discount,empty.tax,empty.total))
        assertTrue(f.state().room!!.carts.all { it.submitted })
        assertTrue(f.state().progress!!.canReview)
        // A valid added tax, including zero, is an explicit confirmed choice.
        val current = f.state().room!!
        f.send(f.owner, CommandKind.SET_FEES) { it.copy(fees = current.fees, restaurant = current.restaurant.copy(pricing = current.restaurant.pricing.copy(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = 0)), text = "Confirmed zero additional tax") }
        assertEquals(10900L, f.state().receipts.sumOf { it.total })
        assertTrue(f.state().progress!!.canReview)
    }

    @Test fun pollUsesUpdatedTaxInsteadOfRestoringStaleRestaurant() = RoomFixture().use { f ->
        val room = f.db.room(f.owner.room!!.id)!!
        val unknown = room.restaurant.copy(pricing = room.restaurant.pricing.copy(taxTreatment = TaxTreatment.UNSPECIFIED))
        f.db.save(room.copy(restaurant = unknown, restaurantOptions = listOf(unknown, unknown.copy(id = "other")), restaurantPollOpen = true))
        f.send(f.owner, CommandKind.UPDATE_RESTAURANT) { it.copy(restaurant = f.restaurant, text = "Confirmed tax included") }
        val chosen = f.send(f.owner, CommandKind.FINALIZE_RESTAURANT) { it.copy(text = f.restaurant.id) }.room!!
        assertEquals(TaxTreatment.INCLUDED, chosen.restaurant.pricing.taxTreatment)
    }

    @Test fun adjustedBillNeedsNoRepeatApprovalAndEmptyMemberRemainsUncharged() = RoomFixture().use { f ->
        val member = f.join(); val empty = f.join("No food")
        f.start(member)
        f.send(f.owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = f.account) }
        f.cart(f.owner, 20); f.cart(member, 10); f.cart(empty, 0)
        f.send(f.owner, CommandKind.PLACE) { it.copy(text = "Confirmed") }
        f.send(f.owner, CommandKind.ADJUST_BILL) { it.copy(amount = -300, text = "Restaurant discount") }
        assertTrue(empty.memberId in f.state().room!!.adjustmentApprovals)
        assertTrue(member.memberId in f.state().room!!.adjustmentApprovals)
        f.pay()
        assertTrue(f.state().room!!.restaurantPaid)
        assertEquals(0L, f.state(empty).receipts.single().total)
    }

    @Test fun invalidTaxDoesNotPartiallySaveFeesOrBypassMenuUpdatePermissions() = RoomFixture().use { f ->
        val member = f.join(); f.start(member)
        val before = f.state().room!!
        val command = f.command(f.owner, CommandKind.SET_FEES).copy(fees = FeePolicy(service = 1000), text = "Update fees")
        val invalid = before.restaurant.copy(pricing = before.restaurant.pricing.copy(taxTreatment = TaxTreatment.ADDED, taxRateBasisPoints = null))
        assertFalse(f.service.execute(command.copy(restaurant = invalid)).ok)
        assertEquals(before, f.state().room)
        val differentMenu = before.restaurant.copy(menu = before.restaurant.menu.copy(items = before.restaurant.menu.items.map { it.copy(basePriceMinor = 1) }))
        assertFalse(f.service.execute(command.copy(commandId = f.id(), restaurant = differentMenu)).ok)
        assertEquals(before, f.state().room)
    }
}
