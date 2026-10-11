package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlin.test.*

class QuickChoiceServiceTest {
    private var time = 1000L
    private var events = 0
    private val service = QuickChoiceService({time},{events++})
    private val group = FriendGroup("group","Friends",members=listOf(FriendContact("a@example.test","a","Alice"),FriendContact("b@example.test","b","Bob")))
    private val home = HomePayload(FoodProfile("owner","Owner"),friendGroups=listOf(group))
    private fun request() = QuickChoiceRequest(purpose="Prize draw",groupId="owner:group",people=listOf(QuickChoiceCandidate("user:a","Changed name"),QuickChoiceCandidate("name:guest","Guest")))
    @Test fun connectedParticipantsSeeSameRoundWithoutJoiningAndExcludedPeopleAreNotBlocked() {
        val round=assertNotNull(service.execute("owner",home,request()))
        assertEquals(4000,round.spin.startAt)
        assertEquals("Alice",round.people.first().name)
        assertEquals(round,service.view("a"));assertEquals(round,service.view("owner"))
        assertNull(service.view("b"));assertNull(service.view("outsider"))
        assertTrue(round.spin.winnerId in listOf("user:a","name:guest"))
        assertEquals(1,events)
    }
    @Test fun onlyCreatorRetriesAndClosesAndEveryRetryHasNewCountdownAndResult() {
        val first=assertNotNull(service.execute("owner",home,request()))
        assertFailsWith<IllegalArgumentException>{service.execute("a",home,QuickChoiceRequest(action="retry",id=first.id))}
        assertFailsWith<IllegalArgumentException>{service.execute("owner",home,QuickChoiceRequest(action="retry",id=first.id))}
        time=first.spin.endAt
        val second=assertNotNull(service.execute("owner",home,QuickChoiceRequest(action="retry",id=first.id)))
        assertNotEquals(first.id,second.id);assertEquals(time+3000,second.spin.startAt)
        assertEquals(first.people,second.people);assertEquals(first.purpose,second.purpose)
        assertFailsWith<IllegalArgumentException>{service.execute("a",home,QuickChoiceRequest(action="close",id=second.id))}
        assertNull(service.execute("owner",home,QuickChoiceRequest(action="close",id=second.id)))
        assertNull(service.view("a"))
    }
    @Test fun expiredOrRestartedServerHasNoPersistedSelection() {
        val round=assertNotNull(service.execute("owner",home,request()))
        time=round.expiresAt
        assertNull(service.view("a"))
        assertNull(QuickChoiceService({time},{}).view("owner"))
    }
    @Test fun groupMembershipIsRequiredAndOverlappingRoundsCannotInterruptASelection() {
        assertFailsWith<IllegalArgumentException>{service.execute("owner",home,request().copy(people=listOf(QuickChoiceCandidate("user:outsider","Outsider"))))}
        assertFailsWith<IllegalArgumentException>{service.execute("owner",home,request().copy(people=emptyList()))}
        assertFailsWith<IllegalArgumentException>{service.execute("owner",home,request().copy(groupId="foreign:group"))}
        service.execute("owner",home,request())
        assertFailsWith<IllegalArgumentException>{service.execute("owner",home,request())}
    }
    @Test fun allOnlineAudienceIsExplicitAndIncludesUsersWithoutMembership() {
        val round=service.execute("owner",home,request().copy(audience="all"))
        assertEquals(round,service.view("outsider"))
    }
}
