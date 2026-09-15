package com.karim.foodrun.server

import com.karim.foodrun.orders.*

class RoomReducer(private val id: () -> String, private val randomIndex: (Int) -> Int) {
    fun apply(r: Room, actorId: String, c: RoomCommand, now: Long): Room {
        require(c.expectedOrderNumber > 0) { "Update Food Run before changing this order, then refresh the room and try again." }
        require(c.expectedOrderNumber == r.orderNumber) { "This request belongs to an earlier order. Refresh the room and review today's order before trying again." }
        val actor = RoomRules.member(r, actorId)
        require(actor.approved) { "Wait for organizer approval." }
        fun owner() { require(actorId == r.ownerId) { "Only the organizer can do this." } }
        fun payer() { require(actorId == r.payerId) { "Only the selected payer can do this." } }
        fun orderer() { require(!actor.guest && actor.participating) { "View-only guests cannot order or pay." } }
        fun phase(vararg phases: RoomPhase) { require(r.phase in phases) { "This action is unavailable at this order stage. Refresh the room." } }
        fun fresh() { require(c.expectedRevision == r.revision) { "The room changed. Review the latest details and retry." } }
        fun reason() { require(c.text.isNotBlank() && c.text.length <= 500) { "Enter a reason (up to 500 characters)." } }
        val result = when (c.kind) {
            CommandKind.NEXT_ORDER -> {
                owner(); phase(RoomPhase.ARCHIVED, RoomPhase.CANCELLED); fresh()
                val restaurant = c.restaurant ?: r.restaurant
                MenuValidation.validate(restaurant)
                Room(r.id, r.code, r.ownerId, r.name, restaurant,
                    expectedNames = c.expectedNames, deliveryMode = c.flag, destination = c.destination,
                    deadline = c.deadline, fees = c.fees ?: FeePolicy(restaurant.pricing.defaultDeliveryFeeMinor, restaurant.pricing.defaultServiceFeeMinor),
                    members = r.members.filterNot { it.removed }.map { it.copy(ready = false, eligible = it.id == r.ownerId && !it.guest, participating = it.id == r.ownerId, latePayerApproved = false) },
                    revision = r.revision, orderNumber = r.orderNumber + 1, createdAt = r.createdAt, updatedAt = now,
                ).also(RoomRules::validateRoom)
            }
            CommandKind.APPROVE_LATE_JOIN -> {
                payer(); phase(RoomPhase.COLLECTING); fresh()
                val target = RoomRules.member(r, c.memberId)
                require(!target.approved && !target.guest) { "This member does not need late-order approval." }
                r.copy(members = r.members.map { if (it.id == target.id) it.copy(latePayerApproved = true) else it })
            }
            CommandKind.APPROVE -> {
                owner(); phase(RoomPhase.LOBBY, RoomPhase.COLLECTING); fresh()
                val target = RoomRules.member(r, c.memberId)
                require(!target.approved)
                require(r.activeMembers.count { it.guest == target.guest } < if (target.guest) 10 else 30) { "Room capacity reached." }
                require(r.phase == RoomPhase.LOBBY || target.guest || actorId == r.payerId || target.latePayerApproved) { "The payer must approve this late join before the organizer admits them." }
                r.copy(members = r.members.map { if (it.id == target.id) it.copy(approved = true) else it }, quoteRevision = r.quoteRevision + 1)
            }
            CommandKind.REMOVE -> {
                owner(); phase(RoomPhase.LOBBY); fresh(); reason()
                require(c.memberId != r.ownerId) { "Hand over ownership before leaving." }
                if (c.memberId.isEmpty()) r.copy(expectedNames = r.expectedNames.filterNot { it == c.name })
                else { val target = RoomRules.member(r, c.memberId); r.copy(members = r.members.map { if (it.id == target.id) it.copy(removed = true) else it }, expectedNames = r.expectedNames.filterNot { it.equals(target.name, true) }) }
            }
            CommandKind.PARTICIPATE -> {
                require(!actor.guest) { "View-only guests cannot order." }; phase(RoomPhase.LOBBY)
                r.copy(members = r.members.map { if (it.id == actorId) it.copy(participating = c.flag, ready = false, eligible = c.flag && (!it.participating || it.eligible)) else it })
            }
            CommandKind.READY -> {
                require(!actor.guest) { "View-only guests cannot order." }; phase(RoomPhase.LOBBY)
                r.copy(members = r.members.map { if (it.id == actorId) it.copy(ready = c.flag, eligible = c.eligible, participating = true, lastSeen = now) else it })
            }
            CommandKind.PREPARE_SPIN -> {
                owner(); phase(RoomPhase.LOBBY); fresh(); RoomRules.spinReady(r, now)
                r.copy(phase = RoomPhase.PREPARING_SPIN, preparationId = id(), preparedIds = emptyList())
            }
            CommandKind.ABORT_PREPARE -> { owner(); phase(RoomPhase.PREPARING_SPIN); fresh(); reason(); r.copy(phase = RoomPhase.LOBBY, preparationId = "", preparedIds = emptyList()) }
            CommandKind.ACK_SPIN -> {
                orderer(); phase(RoomPhase.PREPARING_SPIN)
                require(c.text == r.preparationId) { "Spin preparation has changed." }
                val prepared = (r.preparedIds + actorId).distinct()
                if (r.orderingMembers.all { it.id in prepared }) {
                    require(r.orderingMembers.all { now - it.lastSeen in 0 until 15_000 }) { "A required member disconnected before the spin. Wait for reconnection or restart preparation." }
                    val candidates = r.orderingMembers.filter { it.eligible }.map { it.id }
                    require(candidates.isNotEmpty())
                    val spin = SpinRound(id(), candidates, candidates[randomIndex(candidates.size)], now + 3000)
                    r.copy(phase = RoomPhase.SPINNING, spin = spin, preparedIds = prepared)
                } else r.copy(preparedIds = prepared)
            }
            CommandKind.ACCEPT_DUTY -> {
                phase(RoomPhase.ACCEPTING); require(r.spin?.winnerId == actorId) { "The selected person must accept." }
                r.copy(phase = RoomPhase.COLLECTING, payerId = actorId)
            }
            CommandKind.DECLINE_DUTY -> {
                phase(RoomPhase.ACCEPTING); require(r.spin?.winnerId == actorId); reason()
                r.copy(phase = RoomPhase.LOBBY, pastSpins = r.pastSpins + listOfNotNull(r.spin), spin = null, members = r.members.map { if (it.id == actorId) it.copy(eligible = false, ready = false) else it })
            }
            CommandKind.SHARE_ACCOUNT -> {
                payer(); phase(RoomPhase.COLLECTING, RoomPhase.REVIEW); fresh()
                require(r.transfers.isEmpty()) { "An account with recorded transfers cannot be replaced." }
                val account = requireNotNull(c.account); account.validate(); require(account.currency == r.restaurant.currency) { "Account currency must match the room." }
                r.copy(account = account.copy(version = (r.account?.version ?: 0) + 1), quoteRevision = r.quoteRevision + 1)
            }
            CommandKind.CART -> {
                orderer(); phase(RoomPhase.COLLECTING)
                require(r.deadline == 0L || now <= r.deadline) { "The ordering deadline has passed. Ask the organizer to reopen." }
                val old = r.carts.singleOrNull { it.memberId == actorId } ?: MemberCart(actorId)
                val draft = requireNotNull(c.cart)
                require(draft.memberId == actorId && c.expectedRevision == old.revision) { "Your cart changed. Review the latest cart." }
                val cart = draft.copy(revision = old.revision + 1, submitted = false, confirmedQuote = -1)
                Billing.lines(r.restaurant, cart)
                r.copy(carts = r.carts.filterNot { it.memberId == actorId } + cart, quoteRevision = r.quoteRevision + 1).also { Billing.receipts(it) }
            }
            CommandKind.SUBMIT_CART -> {
                orderer(); phase(RoomPhase.COLLECTING)
                require(r.deadline == 0L || now <= r.deadline) { "The ordering deadline has passed. Ask the organizer to reopen." }
                val cart = r.carts.singleOrNull { it.memberId == actorId } ?: MemberCart(actorId)
                require(c.expectedRevision == cart.revision) { "Your cart changed. Review it before submitting." }
                r.copy(carts = r.carts.filterNot { it.memberId == actorId } + cart.copy(submitted = true))
            }
            CommandKind.REVIEW -> {
                owner(); phase(RoomPhase.COLLECTING); fresh()
                require(r.orderingMembers.all { m -> r.carts.any { it.memberId == m.id && it.submitted } }) { "Wait for every member to submit a cart or choose no food." }
                require(r.account != null) { "The payer must share an account first." }
                require(Billing.receipts(r).any { it.lines.isNotEmpty() }) { "No food was ordered." }
                require(r.fees.discount <= Billing.receipts(r).sumOf { it.food }) { "Discount cannot exceed food total." }
                r.copy(phase = RoomPhase.REVIEW)
            }
            CommandKind.CONFIRM_QUOTE -> {
                orderer(); phase(RoomPhase.REVIEW); require(c.expectedRevision == r.quoteRevision) { "Your total changed. Review it again." }
                r.copy(carts = r.carts.map { if (it.memberId == actorId) it.copy(confirmedQuote = r.quoteRevision) else it })
            }
            CommandKind.REOPEN -> {
                require(actorId == r.ownerId || actorId == r.payerId); phase(RoomPhase.REVIEW, RoomPhase.COLLECTING); fresh(); reason()
                r.copy(phase = RoomPhase.COLLECTING, quoteRevision = r.quoteRevision + 1, deadline = 0)
            }
            CommandKind.UPDATE_RESTAURANT -> {
                require(actorId == r.ownerId || actorId == r.payerId) { "Only the organizer or payer can update the room menu." }
                phase(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW); fresh(); reason()
                val restaurant = requireNotNull(c.restaurant) { "Choose the updated restaurant menu." }
                require(restaurant.id == r.restaurant.id && restaurant.currency == r.restaurant.currency) { "Keep the same restaurant and currency for this order. Start a new order to change them." }
                MenuValidation.validate(restaurant)
                r.carts.forEach { cart ->
                    try { Billing.lines(restaurant, cart) }
                    catch (invalid: IllegalArgumentException) { throw IllegalArgumentException("Some cart selections are no longer valid. Reopen ordering and ask members to remove those items or extras before updating the menu.") }
                    catch (invalid: IllegalStateException) { throw IllegalArgumentException("A cart still contains a removed menu item. Reopen ordering and ask that member to remove it before updating the menu.") }
                }
                r.copy(restaurant = restaurant, phase = if (r.phase == RoomPhase.LOBBY) RoomPhase.LOBBY else RoomPhase.COLLECTING,
                    members = r.members.map { it.copy(ready = false) },
                    carts = r.carts.map { it.copy(revision = it.revision + 1, submitted = false, confirmedQuote = -1) },
                    quoteRevision = r.quoteRevision + 1, deadline = 0,
                ).also { Billing.receipts(it) }
            }
            CommandKind.SET_FEES -> {
                require(actorId == r.ownerId || actorId == r.payerId); phase(RoomPhase.COLLECTING, RoomPhase.REVIEW); fresh(); reason()
                val fees = requireNotNull(c.fees); RoomRules.validateFees(fees)
                r.copy(fees = fees, phase = RoomPhase.COLLECTING, quoteRevision = r.quoteRevision + 1).also { Billing.receipts(it) }
            }
            CommandKind.PLACE -> {
                payer(); phase(RoomPhase.REVIEW); fresh(); RoomRules.requireConfirmed(r)
                require(c.text.isNotBlank() && c.text.length <= 500) { "Enter the restaurant confirmation/reference and ETA." }
                r.copy(phase = RoomPhase.PLACED, restaurantReference = c.text)
            }
            CommandKind.PAY_RESTAURANT -> {
                payer(); phase(RoomPhase.PLACED, RoomPhase.FULFILLED); fresh()
                require(c.amount == Billing.receipts(r).sumOf { it.total }) { "Paid amount differs from the bill. Record an approved bill adjustment first." }
                require(r.billRevision == 1L || r.orderingMembers.all { it.id in r.adjustmentApprovals }) { "Wait for bill adjustment approval." }
                r.copy(restaurantPaid = true)
            }
            CommandKind.FULFILL -> { payer(); phase(RoomPhase.PLACED); fresh(); r.copy(phase = RoomPhase.FULFILLED) }
            CommandKind.DECLARE_TRANSFER, CommandKind.DECLARE_REFUND -> {
                phase(RoomPhase.PLACED, RoomPhase.FULFILLED); orderer(); require(r.restaurantPaid) { "Wait for the payer to confirm restaurant payment." }
                val refund = c.kind == CommandKind.DECLARE_REFUND
                val target = if (refund) { payer(); c.memberId } else actorId
                require(target != r.payerId) { "The payer's own contribution needs no transfer." }
                require(r.transfers.none { it.memberId == target && it.status == TransferStatus.DECLARED }) { "Resolve the existing pending transfer first." }
                val receipt = Billing.receipts(r).singleOrNull { it.memberId == target } ?: error("No receipt exists for this member.")
                require(c.amount > 0 && c.amount <= if (refund) -receipt.balance else receipt.balance) { "Amount exceeds the remaining balance." }
                require(c.text.isNotBlank() && c.text.length <= 160) { "Enter a transfer reference or cash note." }
                val transfer = Transfer(id(), target, c.amount, c.text, requireNotNull(r.account), refund, createdAt = now)
                r.copy(transfers = r.transfers + transfer)
            }
            CommandKind.CONFIRM_TRANSFER, CommandKind.REJECT_TRANSFER, CommandKind.CONFIRM_REFUND -> {
                phase(RoomPhase.PLACED, RoomPhase.FULFILLED)
                val transfer = r.transfers.singleOrNull { it.id == c.transferId } ?: error("Transfer not found.")
                require(transfer.status == TransferStatus.DECLARED) { "This transfer was already resolved." }
                if (transfer.refund) require(actorId == transfer.memberId) { "The refund recipient must confirm." } else payer()
                if (c.kind == CommandKind.CONFIRM_REFUND) require(transfer.refund)
                val status = if (c.kind == CommandKind.REJECT_TRANSFER) { reason(); TransferStatus.REJECTED } else TransferStatus.CONFIRMED
                r.copy(transfers = r.transfers.map { if (it.id == transfer.id) it.copy(status = status) else it })
            }
            CommandKind.ADJUST_BILL -> {
                payer(); phase(RoomPhase.PLACED, RoomPhase.FULFILLED); fresh(); reason()
                require(c.amount in -MenuValidation.MAX_MONEY..MenuValidation.MAX_MONEY) { "Invalid bill adjustment." }
                r.copy(adjustment = c.amount, adjustmentApprovals = listOf(actorId), billRevision = r.billRevision + 1, restaurantPaid = false).also { Billing.receipts(it) }
            }
            CommandKind.APPROVE_ADJUSTMENT -> {
                orderer(); phase(RoomPhase.PLACED, RoomPhase.FULFILLED); require(c.expectedRevision == r.billRevision) { "The bill changed again." }
                r.copy(adjustmentApprovals = (r.adjustmentApprovals + actorId).distinct())
            }
            CommandKind.HANDOVER -> {
                owner(); phase(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW); fresh(); reason()
                val target = RoomRules.member(r, c.memberId); require(target.approved && !target.guest)
                r.copy(ownerId = target.id)
            }
            CommandKind.ARCHIVE -> {
                owner(); phase(RoomPhase.FULFILLED); fresh()
                require(r.restaurantPaid) { "Confirm the restaurant payment first." }
                require(Billing.receipts(r).all { it.balance == 0L } && r.transfers.none { it.status == TransferStatus.DECLARED }) { "Settle every reimbursement and refund before archiving. Resolve pending transfers first." }
                r.copy(phase = RoomPhase.ARCHIVED)
            }
            CommandKind.CANCEL -> { owner(); phase(RoomPhase.LOBBY, RoomPhase.COLLECTING, RoomPhase.REVIEW, RoomPhase.ACCEPTING); fresh(); reason(); r.copy(phase = RoomPhase.CANCELLED) }
            else -> error("Unsupported room action.")
        }
        require(r.audit.size < 10000 || c.kind in listOf(CommandKind.CANCEL, CommandKind.ARCHIVE, CommandKind.NEXT_ORDER)) { "This order has reached its action limit. Archive or cancel it before starting the next order in this room." }
        return result.copy(revision = r.revision + 1, updatedAt = now, audit = (if (c.kind == CommandKind.NEXT_ORDER) emptyList() else r.audit) + AuditEntry(c.commandId, actorId, c.kind.name, c.text.take(500).takeIf { c.kind !in listOf(CommandKind.ACK_SPIN, CommandKind.DECLARE_TRANSFER, CommandKind.DECLARE_REFUND) } ?: "", now))
    }
}
