package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.client.plugins.websocket.*
import io.ktor.http.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.withTimeout
import kotlin.test.*

class HubRoutesTest {
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
