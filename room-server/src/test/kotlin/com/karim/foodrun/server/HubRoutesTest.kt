package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.*

class HubRoutesTest {
    @Test fun completedRoomSendsFinalSnapshotAndClosesForCapableClients() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val socketClient = createClient { install(WebSockets) }
            socketClient.webSocket("/events") {
                send(Frame.Text(orderJson.encodeToString(f.command(f.owner, CommandKind.SNAPSHOT).copy(liveRoomDetails = true))))
                val initial = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText())
                val room = initial.room!!
                f.db.save(room.copy(phase = RoomPhase.CANCELLED, revision = room.revision + 1))
                f.service.adminChanged(listOf(room.id))
                val final = withTimeout(5000) { orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText()) }
                assertEquals(RoomPhase.CANCELLED, final.room!!.phase)
                val end = withTimeout(5000) { incoming.receiveCatching() }
                assertTrue(end.isClosed || end.getOrNull() is Frame.Close)
            }
        }
    }

    @Test fun roomLifecycleMetadataIsOmittedForStrictLegacyClients() {
        val reply = RoomReply(home = HomePayload(FoodProfile(), rooms = listOf(AccountRoom("r", "Room", "m", "token", RoomPhase.ARCHIVED, 3))))
        assertFalse(orderJson.encodeToString(reply.forClient(true, true)).contains("ARCHIVED"))
        assertTrue(orderJson.encodeToString(reply.forClient(true, true, true)).contains("ARCHIVED"))
        assertEquals(0, reply.forClient(true, true).home!!.rooms.single().orderNumber)
    }

    @Test fun unrelatedRoomEditsDoNotResendAnUnchangedHomeCatalog() {
        val provider = object : IdentityProvider {
            override fun signIn(email: String, password: String, register: Boolean) = CloudIdentity(email, "fixture")
            override fun exchange(idToken: String) = error("Unused")
            override fun refresh(refreshToken: String) = error("Unused")
            override fun profile(identity: CloudIdentity) = FoodProfile(identity.userId, "Owner", "+971501234567")
            override fun saveProfile(identity: CloudIdentity, profile: FoodProfile) = Unit
            override fun resetPassword(email: String) = Unit
            override fun saveHubRecord(identity: CloudIdentity, hubId: String, key: String, value: String) = Unit
        }
        RoomFixture(provider).use { f ->
            val signedIn = f.execute(RoomCommand(commandId = f.id(), kind = CommandKind.IDENTITY, identity = IdentityRequest(IdentityAction.SIGN_IN, email = "fixture-owner@example.test", password = "fixture-password")))
            testApplication {
                application { hubRoutes(f.service) }
                val socketClient = createClient { install(WebSockets) }
                socketClient.webSocket("/events") {
                    send(Frame.Text(orderJson.encodeToString(RoomCommand(commandId = f.id(), kind = CommandKind.HOME, identityToken = signedIn.identityToken, liveRoomDetails = true))))
                    val initial = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText())
                    assertEquals(RoomPhase.LOBBY, initial.home!!.rooms.single().phase)
                    f.send(f.owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
                    assertNull(withTimeoutOrNull(750) { incoming.receive() })
                    val room = f.state().room!!
                    f.db.save(room.copy(phase = RoomPhase.CANCELLED, revision = room.revision + 1)); f.service.adminChanged(listOf(room.id))
                    val changed = withTimeout(5000) { orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText()) }
                    assertEquals(RoomPhase.CANCELLED, changed.home!!.rooms.single().phase)
                    close()
                }
            }
        }
    }
    @Test fun selectionFieldsAreOnlySentToClientsThatUnderstandThem() = RoomFixture().use { f ->
        val member = f.join(); f.approve(member); f.start(member)
        val saved = f.state().room!!
        testApplication {
            application { hubRoutes(f.service) }
            val socketClient = createClient { install(WebSockets) }
            for (supported in listOf(false, true)) {
                val command = f.command(f.owner, CommandKind.SNAPSHOT).copy(selectionDetails = supported)
                val body = client.post("/command") { setBody(orderJson.encodeToString(command)) }.bodyAsText()
                assertEquals(supported, body.contains("\"lastChosenMemberId\""))
                assertEquals(supported, body.contains("\"weights\""))
                val response = orderJson.decodeFromString<RoomReply>(body)
                assertEquals(saved.spin!!.winnerId, response.room!!.spin!!.winnerId)
                socketClient.webSocket("/events") {
                    send(Frame.Text(orderJson.encodeToString(command)))
                    val snapshot = (incoming.receive() as Frame.Text).readText()
                    assertEquals(supported, snapshot.contains("\"lastChosenName\""))
                    assertEquals(supported, snapshot.contains("\"weights\""))
                    close()
                }
            }
            assertEquals(saved.lastChosenMemberId, f.state().room!!.lastChosenMemberId)
            val change = f.command(f.owner, CommandKind.SHARE_ACCOUNT).copy(account = f.account)
            val first = client.post("/command") { setBody(orderJson.encodeToString(change)) }.bodyAsText()
            val retry = client.post("/command") { setBody(orderJson.encodeToString(change.copy(selectionDetails = true, liveRoomDetails = true))) }.bodyAsText()
            val old = orderJson.decodeFromString<RoomReply>(first)
            val current = orderJson.decodeFromString<RoomReply>(retry)
            assertTrue(old.ok && current.ok)
            assertEquals(old.room!!.revision, current.room!!.revision)
        }
    }

    @Test fun httpAndWebSocketClientsCommunicateThroughTheSameRoom() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val socketClient = createClient { install(WebSockets) }
            val member = f.join(); f.approve(member)
            socketClient.webSocket("/events") {
                send(Frame.Text(orderJson.encodeToString(f.command(member, CommandKind.SNAPSHOT))))
                val initial = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText())
                assertTrue(initial.ok)
                val change = client.post("/command") {
                    contentType(ContentType.Application.Json)
                    setBody(orderJson.encodeToString(f.command(f.owner, CommandKind.READY).copy(flag = true, eligible = true)))
                }
                assertTrue(orderJson.decodeFromString<RoomReply>(change.bodyAsText()).ok)
                val update = withTimeout(5000) {
                    var reply: RoomReply
                    do { reply = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText()) }
                    while (reply.room!!.revision <= initial.room!!.revision)
                    reply
                }
                assertTrue(update.room!!.members.single { it.id == f.owner.memberId }.ready)
                close()
            }
        }
    }

    @Test fun unchangedRoomsDoNotResendFullSnapshotsAfterTheFormerRecoveryInterval() = RoomFixture().use { f ->
        testApplication {
            val socketClock = AtomicLong(0)
            application { hubRoutes(f.service, socketClock = socketClock::get) }
            val socketClient = createClient { install(WebSockets) }
            socketClient.webSocket("/events") {
                send(Frame.Text(orderJson.encodeToString(f.command(f.owner, CommandKind.SNAPSHOT))))
                val initial = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText())
                assertTrue(initial.ok)

                // Ktor ping frames keep the connection healthy. An idle room must not repeatedly
                // resend its potentially multi-megabyte menu, history, and receipt-photo payload.
                socketClock.set(31_000)
                assertNull(withTimeoutOrNull(750) { incoming.receive() })

                val change = client.post("/command") {
                    contentType(ContentType.Application.Json)
                    setBody(orderJson.encodeToString(f.command(f.owner, CommandKind.READY).copy(flag = true, eligible = true)))
                }
                assertTrue(orderJson.decodeFromString<RoomReply>(change.bodyAsText()).ok)
                val update = withTimeout(5000) {
                    var reply: RoomReply
                    do { reply = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText()) }
                    while (reply.room!!.revision <= initial.room!!.revision)
                    reply
                }
                assertTrue(update.room!!.members.single { it.id == f.owner.memberId }.ready)
                close()
            }
        }
    }
    @Test fun malformedAmbiguousAndDeepJsonReturnValidationWithoutKillingTheHub() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val bodies = listOf("{broken", "{" + "\"kind\":\"READY\",\"kind\":\"CANCEL\"}", "[".repeat(25) + "0" + "]".repeat(25))
            for (body in bodies) {
                val result = client.post("/command") { setBody(body) }
                val reply = orderJson.decodeFromString<RoomReply>(result.bodyAsText())
                assertFalse(reply.ok); assertEquals("VALIDATION", reply.code)
            }
            assertEquals(HttpStatusCode.OK, client.get("/health").status)
        }
    }
    @Test fun oversizedRequestsAndUnknownProtocolFailWithoutChangingRoom() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val before = f.state().room!!.revision
            val huge = client.post("/command") { setBody(" ".repeat(MenuValidation.MAX_BYTES + 2)) }
            assertFalse(orderJson.decodeFromString<RoomReply>(huge.bodyAsText()).ok)
            val invalid = client.post("/command") { setBody(orderJson.encodeToString(f.command(f.owner, CommandKind.READY).copy(protocolVersion = 99))) }
            assertFalse(orderJson.decodeFromString<RoomReply>(invalid.bodyAsText()).ok)
            assertEquals(before, f.state().room!!.revision)
        }
    }
    @Test fun socketAuthenticationDoesNotRevealARegisteredRoomsPrivateState() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val socketClient = createClient { install(WebSockets) }
            socketClient.webSocket("/events") {
                send(Frame.Text(orderJson.encodeToString(f.command(f.owner, CommandKind.SNAPSHOT).copy(token = "x".repeat(43)))))
                val reply = orderJson.decodeFromString<RoomReply>((incoming.receive() as Frame.Text).readText())
                assertFalse(reply.ok); assertNull(reply.room); assertTrue(reply.receipts.isEmpty())
                close()
            }
        }
    }
    @Test fun postRetriesReturnOneCommittedResponse() = RoomFixture().use { f ->
        testApplication {
            application { hubRoutes(f.service) }
            val body = orderJson.encodeToString(f.command(f.owner, CommandKind.READY).copy(flag = true, eligible = true))
            val first = client.post("/command") { setBody(body) }.bodyAsText()
            val retry = client.post("/command") { setBody(body) }.bodyAsText()
            assertEquals(first, retry)
            assertTrue(orderJson.decodeFromString<RoomReply>(first).ok)
        }
    }
}
