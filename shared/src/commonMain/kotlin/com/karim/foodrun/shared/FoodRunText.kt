package com.karim.foodrun.shared

/** Shared English copy catalog. Native views render these values without duplicating wording. */
object FoodRunText {
    const val appName = "Food Run"
    const val brand = "FOOD RUN"
    const val headlineFirst = "Who’s getting"
    const val headlineSecond = "the food?"
    const val tagline = "One spin. One hero. Zero debates."
    const val historyAccessibility = "Pickup history"
    const val wheelAccessibility = "Food pickup wheel"
    const val spinning = "Spinning"
    const val wheelCenter = "LET’S EAT"
    const val crewTitle = "The hungry crew"
    const val crewDescription = "Tap a friend to include them in this run.\nEveryone on the wheel gets an equal chance."
    const val crewAction = "Who’s in?"
    const val addPerson = "Add person"
    const val keepOneFriend = "Keep at least one hungry friend in."
    const val selectAll = "Select all"
    const val hapticTicks = "Haptic ticks"
    const val done = "Done"
    const val cancel = "Cancel"
    const val addPersonTitle = "Another hungry friend?"
    const val addPersonDescription = "They’ll join the wheel straight away."
    const val nameLabel = "Friend’s name"
    const val addPersonAction = "Add to the crew"
    const val historyTitle = "Food run legends"
    const val historyDescription = "A little appreciation for the pickup crew."
    const val historyEmptyTitle = "Your first hero awaits"
    const val historyEmptyDescription = "Give the wheel a spin. Every food run\nwill have its moment here."
    val historyFooter: String get() = "Your last ${FoodRunRules.historyLimit} picks, saved on this device."
    const val winnerEyebrow = "THE WHEEL HAS SPOKEN"
    const val winnerIntroduction = "Our food hero is…"
    const val winnerDescription = "Grab the keys. You’re on pickup duty.\nThe hungry crew is counting on you."
    const val winnerAction = "I’m on it!"
    const val shareWithCrew = "Share with the crew"
    const val participationLockedHint = "At least one person must stay included"
    const val participationToggleHint = "Double tap to change participation"
    const val saveFailed = "Couldn’t save your changes. Please try again."

    fun status(
        isSpinning: Boolean,
        winnerName: String?,
        activeCount: Int,
    ): String = when {
        isSpinning -> "Choosing our food hero…"
        winnerName != null -> "$winnerName is on pickup duty!"
        activeCount == 1 -> "1 friend. Equal chances."
        else -> "$activeCount friends. Equal chances."
    }

    fun spinButton(isSpinning: Boolean): String =
        if (isSpinning) "The suspense is delicious…" else "Spin for the food"

    fun mission(activeCount: Int): String =
        if (activeCount == 1) "A solo food mission. You’ve got this." else "Let fate handle the group chat."

    fun crewSummary(activeCount: Int): String =
        if (activeCount == 1) "1 friend in" else "$activeCount friends in"

    fun crewAccessibility(activeCount: Int): String =
        if (activeCount == 1) "$crewAction 1 person included" else "$crewAction $activeCount people included"

    fun winnerAnnouncement(name: String): String = "$name is picking up the food!"

    fun winnerTitle(name: String): String = "$name!"

    fun winnerShare(name: String): String =
        "The Food Run wheel has spoken! 🍟 $name is picking up the food. The hungry crew is counting on you!"

    fun personAddedAnnouncement(name: String): String = "$name added to the crew and included on the wheel."

    fun removePersonAccessibility(name: String): String = "Remove $name from the crew"

    fun participation(included: Boolean): String = if (included) "In" else "Sitting out"

    fun participationAccessibility(included: Boolean): String = if (included) "Included" else participation(false)

    fun wheelState(
        isSpinning: Boolean,
        winnerName: String?,
        activeCount: Int,
    ): String = when {
        isSpinning -> spinning
        winnerName != null -> "Selected $winnerName"
        activeCount == 1 -> "1 person, equal chances"
        else -> "$activeCount people, equal chances"
    }

    fun addPersonError(error: AddPersonError): String = when (error) {
        AddPersonError.EMPTY_NAME -> "Enter a name to add a friend."
        AddPersonError.DUPLICATE_NAME -> "That name is already in the crew."
        AddPersonError.NAME_TOO_LONG -> "Keep the name to ${FoodRunRules.maxNameLength} characters or fewer."
        AddPersonError.SPIN_IN_PROGRESS -> "Wait for the wheel to finish, then add your friend."
    }

    /** Calendar fields come from the device's timezone; presentation is shared across platforms. */
    fun formatHistoryDate(
        month: Int,
        day: Int,
        hour24: Int,
        minute: Int,
    ): String {
        val monthName = months.getOrElse(month - 1) { "" }
        val hour = (hour24 % 12).let { if (it == 0) 12 else it }
        val period = if (hour24 < 12) "AM" else "PM"
        return "$monthName $day, $hour:${minute.toString().padStart(2, '0')} $period"
    }

    private val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
}
