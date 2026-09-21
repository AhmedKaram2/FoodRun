package com.karim.foodrun.orders

object RoomRules {
    fun member(room: Room, id: String): Member = room.members.singleOrNull { it.id == id && !it.removed } ?: error("Membership has been removed. Ask the organizer to join again.")
    fun validateRoom(room: Room) {
        MenuValidation.label(room.name); MenuValidation.validate(room.restaurant)
        require(room.restaurantOptions.size <= 12) { "A restaurant poll supports up to 12 choices." }
        require(room.restaurantOptions.map { it.id }.distinct().size == room.restaurantOptions.size) { "Restaurant poll choices must be unique." }
        room.restaurantOptions.forEach(MenuValidation::validate)
        require(room.restaurantOptions.isEmpty() || room.restaurantOptions.any { it.id == room.restaurant.id }) { "The active restaurant must be included in the poll." }
        require(room.restaurantOptions.all { it.currency == room.restaurant.currency }) { "Restaurant poll choices must use the same currency." }
        require(room.restaurantVotes.map { it.memberId }.distinct().size == room.restaurantVotes.size &&
            room.restaurantVotes.all { vote -> room.members.any { it.id == vote.memberId && !it.removed } && room.restaurantOptions.any { it.id == vote.restaurantId } }) {
            "Restaurant poll contains an invalid vote."
        }
        require(room.expectedNames.size <= 30 && room.expectedNames.map { it.trim().lowercase() }.distinct().size == room.expectedNames.size) { "Expected names must be unique (up to 30)." }
        room.expectedNames.forEach(MenuValidation::label)
        require(!room.deliveryMode || room.destination.isNotBlank()) { "Enter a delivery destination and contact." }
        require(room.destination.length <= 1000 && room.deadline >= 0) { "Invalid destination or deadline." }
        validateFees(room.fees)
    }
    fun validateFees(fees: FeePolicy) { listOf(fees.delivery, fees.service, fees.discount).forEach(MenuValidation::price) }
    fun spinReady(room: Room) {
        require(!room.restaurantPollOpen) { "Finish the restaurant poll before starting the spin." }
        require(room.orderingMembers.isNotEmpty()) { "At least one ordering member must join." }
        require(room.expectedNames.all { name -> room.orderingMembers.any { it.name.equals(name.trim(), true) } }) { "Some expected people have not joined. Remove absent invitations explicitly." }
        require(room.orderingMembers.any { it.eligible }) { "At least one member must consent to ordering and paying." }
    }
    fun requireReview(room: Room) {
        require(room.carts.all { cart -> cart.lines.all { it.description.isEmpty() || it.unitPrice != null } }) { "The selected person must price every custom item before totals can be confirmed." }
        require(room.orderingMembers.all { m -> room.carts.any { it.memberId == m.id && it.submitted } }) { "Wait for every member to submit a cart or choose no food." }
        require(room.account != null) { "The payer must share an account first." }
        val receipts = Billing.receipts(room)
        require(receipts.any { it.lines.isNotEmpty() }) { "No food was ordered. Add food or cancel this order." }
        require(room.fees.discount <= receipts.sumOf { it.food }) { "Discount cannot exceed food total." }
    }
    fun requireArchive(room: Room) {
        require(room.restaurantPaid) { "Confirm the restaurant payment first." }
        require(Billing.receipts(room).all { it.balance == 0L } && room.transfers.none { it.status == TransferStatus.DECLARED }) { "Settle every reimbursement and refund before archiving. Resolve pending transfers first." }
    }
    fun requirePlaceable(room: Room) {
        requireReview(room)
        require(room.account != null) { "The payer must share a receiving account." }
        require(room.restaurant.pricing.taxTreatment != TaxTreatment.UNSPECIFIED) { "Resolve the restaurant's tax treatment first." }
        val receipts = Billing.receipts(room)
        require(receipts.any { it.lines.isNotEmpty() }) { "Everyone chose no food. Cancel this order." }
        require(room.fees.discount <= receipts.sumOf { it.food }) { "Discount cannot exceed food total." }
        require(receipts.sumOf { it.food } >= room.restaurant.pricing.minimumOrderMinor) { "Restaurant minimum order has not been reached." }
        require(!room.restaurant.contact.phoneE164.isNullOrBlank() || !room.restaurant.contact.whatsappE164.isNullOrBlank()) { "Add a restaurant phone or WhatsApp number in the restaurant details before placing this order." }
    }
}
