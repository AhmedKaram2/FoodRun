package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.nio.file.Files
import java.security.SecureRandom
import java.util.UUID
import kotlin.test.*

internal class RoomFixture : AutoCloseable {
    val directory = Files.createTempDirectory("foodrun-test-").toFile()
    var now = 100_000L
    var db = RoomDatabase(directory)
    private val random = object : SecureRandom() { override fun nextInt(bound: Int): Int = if (bound == 900000) super.nextInt(bound) else 0 }
    var service = RoomService(db, { now }, random)
    val restaurant = Restaurant("restaurant", "Test Kitchen", contact = RestaurantContact("+971501234567"), menu = Menu(categories = listOf(MenuCategory("main", "Main")), items = listOf(MenuItem("meal", "main", "Meal", basePriceMinor = 100))))
    val account = ReceivingAccount("account", "Owner", "Test Bank", "12345678")
    val owner = execute(RoomCommand(commandId = id(), kind = CommandKind.CREATE, name = "Owner", text = "Daily lunch", restaurant = restaurant))
    fun id(): String = UUID.randomUUID().toString()
    fun execute(c: RoomCommand): RoomReply = service.execute(c).also { assertTrue(it.ok, "${c.kind}: ${it.error}") }
    fun state(actor: RoomReply = owner): RoomReply = service.snapshot(requireNotNull(owner.room).id, actor.token)
    fun command(actor: RoomReply, kind: CommandKind): RoomCommand {
        val room = requireNotNull(state(actor).room)
        return RoomCommand(commandId = id(), kind = kind, roomId = room.id, token = actor.token,
            expectedRevision = room.revision, expectedOrderNumber = room.orderNumber)
    }
    fun send(actor: RoomReply, kind: CommandKind, modify: (RoomCommand) -> RoomCommand = { it }): RoomReply = execute(modify(command(actor, kind)))
    fun join(name: String = "Member", guest: Boolean = false): RoomReply = execute(RoomCommand(commandId = id(), kind = CommandKind.JOIN, name = name, code = requireNotNull(owner.room).code, guest = guest))
    fun approve(actor: RoomReply) = send(owner, CommandKind.APPROVE) { it.copy(memberId = actor.memberId) }
    fun start(member: RoomReply): RoomReply {
        send(owner, CommandKind.READY) { it.copy(flag = true, eligible = true) }
        send(member, CommandKind.READY) { it.copy(flag = true) }
        val preparation = requireNotNull(send(owner, CommandKind.PREPARE_SPIN).room).preparationId
        send(owner, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        val spinning = send(member, CommandKind.ACK_SPIN) { it.copy(text = preparation) }
        now = requireNotNull(spinning.room?.spin).endAt
        service.tick()
        send(owner, CommandKind.ACCEPT_DUTY)
        return spinning
    }
    fun cart(actor: RoomReply, quantity: Int) {
        val revision = state(actor).room!!.carts.singleOrNull { it.memberId == actor.memberId }?.revision ?: 0
        val cart = MemberCart(actor.memberId, lines = if (quantity == 0) emptyList() else listOf(CartLine(id(), "meal", quantity, notes = "Private preparation note for ${actor.memberId}")))
        send(actor, CommandKind.CART) { it.copy(expectedRevision = revision, cart = cart) }
        send(actor, CommandKind.SUBMIT_CART) { it.copy(expectedRevision = revision + 1) }
    }
    fun placed(ownerQuantity: Int = 45, memberQuantity: Int = 30): RoomReply {
        val member = join(); approve(member); start(member)
        send(owner, CommandKind.SHARE_ACCOUNT) { it.copy(account = account) }
        cart(owner, ownerQuantity); cart(member, memberQuantity)
        send(owner, CommandKind.REVIEW)
        listOf(owner, member).forEach { actor -> send(actor, CommandKind.CONFIRM_QUOTE) { it.copy(expectedRevision = state(actor).room!!.quoteRevision) } }
        send(owner, CommandKind.PLACE) { it.copy(text = "Restaurant confirmed; 30 minutes") }
        return member
    }
    fun pay() = send(owner, CommandKind.PAY_RESTAURANT) { it.copy(amount = state().receipts.sumOf { r -> r.total }) }
    fun restart() { db.close(); db = RoomDatabase(directory); service = RoomService(db, { now }, random) }
    override fun close() { db.close(); directory.deleteRecursively() }
}
