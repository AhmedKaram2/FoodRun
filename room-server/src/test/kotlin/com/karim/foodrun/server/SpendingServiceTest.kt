package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.time.Instant
import kotlin.test.*

class SpendingServiceTest {
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private fun paidRoom(f: RoomFixture, name: String = "Daily lunch", number: Long = 1, currency: String = "AED"): Room {
        val member=Member("customer","Customer",approved=true)
        val room=f.state().room!!.copy(name=name,orderNumber=number,restaurant=f.restaurant.copy(currency=currency),payerId=f.owner.memberId,
            members=f.state().room!!.members+member,phase=RoomPhase.PLACED,carts=listOf(
                MemberCart(f.owner.memberId,lines=listOf(CartLine("own","meal",2))), MemberCart(member.id,lines=listOf(CartLine("their","meal",8)))),
            restaurantPaid=true,audit=listOf(AuditEntry("paid-$number",f.owner.memberId,"PAY_RESTAURANT","",f.now)))
        f.db.putRecord("member-user:${room.id}:${member.id}","customer")
        f.db.putRecord("member-user:${room.id}:${f.owner.memberId}","payer")
        return room
    }
    @Test fun onlyConfirmedFoodPaymentsCountWithRefundsAndCurrenciesKeptSeparate() = RoomFixture().use { f ->
        f.now=at("2026-10-14T10:00:00Z")
        val service=SpendingService(f.db,{f.now});val room=paidRoom(f)
        fun transfer(id: String, amount: Long, status: TransferStatus, refund: Boolean = false) = Transfer(id,"customer",amount,"",f.account,refund,status,f.now)
        val paid=room.copy(transfers=listOf(transfer("paid",800,TransferStatus.CONFIRMED),transfer("pending",700,TransferStatus.DECLARED),transfer("rejected",900,TransferStatus.REJECTED),transfer("refund",100,TransferStatus.CONFIRMED,true)))
        service.record(paid);service.record(paid)
        val topUp=WalletTopUp("credit","customer","Customer","holder","Holder",100000,"AED",f.account,"",status=WalletStatus.CONFIRMED,createdAt=f.now)
        f.db.putRecord("wallet:top-up:${topUp.id}",orderJson.encodeToString(topUp))
        service.record(paidRoom(f,number=2,currency="USD").copy(transfers=listOf(transfer("dollars",300,TransferStatus.CONFIRMED))))
        val summary=service.summary("customer","Asia/Dubai")
        val week=summary.periods.single { it.period=="week" && it.currency=="AED" }
        assertEquals(800,week.paid);assertEquals(100,week.refunds);assertEquals(700,week.total)
        assertEquals(700,week.meals.single { it.meal=="lunch" }.total)
        assertEquals(300,summary.periods.single { it.period=="week" && it.currency=="USD" }.total)
        assertEquals(200,service.summary("payer").periods.single { it.period=="week" && it.currency=="AED" }.total)
        assertEquals(0,service.summary("unrelated").periods.first().total)
    }
    @Test fun localMondayBoundaryAndCalendarMonthUseTheUsersTimeZone() = RoomFixture().use { f ->
        val service=SpendingService(f.db,{f.now})
        f.now=at("2026-09-30T20:30:00Z");val room=paidRoom(f,name="Breakfast")
        service.record(room.copy(transfers=listOf(Transfer("paid","customer",500,"",f.account,status=TransferStatus.CONFIRMED,createdAt=f.now))))
        f.now=at("2026-10-04T20:10:00Z")
        val dubai=service.summary("customer","Asia/Dubai")
        assertEquals("2026-10-05",dubai.periods.first { it.period=="week" }.startDate)
        assertEquals(0,dubai.periods.first { it.period=="week" }.total)
        assertEquals(500,dubai.periods.first { it.period=="month" }.total)
        val utc=service.summary("customer","UTC")
        assertEquals("2026-09-28",utc.periods.first { it.period=="week" }.startDate)
        assertEquals(500,utc.periods.first { it.period=="week" }.total)
        assertEquals(0,utc.periods.first { it.period=="month" }.total)
    }
    @Test fun backfillReadsBeyondSixOrdersAndHistorySurvivesRoomRemovalAndRestart() = RoomFixture().use { f ->
        f.now=at("2026-10-14T10:00:00Z")
        repeat(13) { i -> val room=paidRoom(f,number=i+1L);f.db.archive(room.copy(transfers=listOf(Transfer("p-$i","customer",100,"",f.account,status=TransferStatus.CONFIRMED,createdAt=f.now))));if(i==12) f.db.save(room) }
        val first=SpendingService(f.db,{f.now}).summary("customer")
        assertEquals(1300,first.periods.first().total)
        // The payer has never opened statistics. Admin cleanup still preserves their past own shares.
        f.service.preserveSpending(f.owner.room!!.id)
        f.db.deleteRoom(f.owner.room!!.id);f.restart()
        assertEquals(first,SpendingService(f.db,{f.now}).summary("customer"))
        assertEquals(2600,SpendingService(f.db,{f.now}).summary("payer").periods.first().total)
    }
    @Test fun payerBillAdjustmentsCountOnlyOwnShareDeltaAfterRestaurantPayment() = RoomFixture().use { f ->
        f.now=at("2026-10-14T10:00:00Z")
        val service=SpendingService(f.db,{f.now});val room=paidRoom(f)
        service.record(room);service.record(room.copy(restaurantPaid=false,adjustment=100))
        assertEquals(200,service.summary("payer").periods.first().total)
        val changed=room.copy(adjustment=100,audit=room.audit+AuditEntry("paid-again",f.owner.memberId,"PAY_RESTAURANT","",f.now))
        service.record(changed);service.record(changed)
        assertEquals(220,service.summary("payer").periods.first().total)
    }
    @Test fun arabicMealNamesAndAmbiguousRoomsUseClearCategories() {
        assertEquals("breakfast",SpendingService.meal("فطور المكتب"));assertEquals("lunch",SpendingService.meal("غداء الفريق"))
        assertEquals("dinner",SpendingService.meal("عشاء"));assertEquals("other",SpendingService.meal("فطور وغداء"));assertEquals("other",SpendingService.meal("Team order"))
    }
    @Test fun olderStrictClientsDoNotReceiveSpendingFields() {
        val reply=RoomReply(home=HomePayload(FoodProfile(name="Test"),spending=SpendingSummary(1,"UTC",emptyList())))
        assertFalse(orderJson.encodeToString(reply.forClient(false)).contains("spending"))
        assertTrue(orderJson.encodeToString(reply.forClient(false,spendingDetails=true)).contains("spending"))
    }
}
