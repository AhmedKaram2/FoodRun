package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.security.SecureRandom
import java.util.UUID

/** Live selections exist only in process memory. No rooms, records, payments or notifications are created. */
internal class QuickChoiceService(private val clock: () -> Long, private val changed: () -> Unit, private val random: SecureRandom = SecureRandom()) {
    private data class Live(val session: QuickChoiceSession, val viewers: Set<String>, val audience: String)
    private val sessions = linkedMapOf<String,Live>()
    fun prune() { if(sessions.entries.removeIf { it.value.session.expiresAt <= clock() }) changed() }
    fun view(userId: String): QuickChoiceSession? {
        prune()
        return sessions.values.lastOrNull { it.audience == "all" || userId in it.viewers }?.session
    }
    fun execute(userId: String, home: HomePayload, request: QuickChoiceRequest): QuickChoiceSession? {
        prune()
        if(request.action == "close") {
            val live = requireNotNull(sessions[request.id]) { "This quick choice has ended." }
            require(live.session.ownerId == userId) { "Only the creator can close this quick choice for everyone." }
            sessions.remove(request.id); changed(); return null
        }
        if(request.action == "retry") {
            val live = requireNotNull(sessions[request.id]) { "This quick choice has ended." }
            require(live.session.ownerId == userId) { "Only the creator can restart this quick choice." }
            require(clock() >= live.session.spin.startAt + live.session.spin.duration) { "Wait for the selection to finish." }
            return start(userId,live.session.ownerName,live.session.purpose,live.session.style,live.session.people,live.viewers,live.audience,request.id)
        }
        require(request.action == "start" && request.audience in listOf("participants","all")) { "Choose a valid quick choice action." }
        require(request.purpose.length <= 160 && request.style in listOf("wheel","names")) { "Enter a purpose and choose an animation." }
        require(request.people.size in 1..100 && request.people.map { it.id }.distinct().size == request.people.size) { "Include between 1 and 100 unique participants." }
        val groups = (home.friendGroups.map { FriendGroupMembership(it,home.profile.userId,home.profile.name) } + home.joinedFriendGroups)
        val group = if(request.groupId.isNotBlank()) requireNotNull(groups.firstOrNull { it.selectionKey() == request.groupId }) { "This group is no longer available." } else null
        val allowed = group?.let { membership ->
            (listOf(FriendContact("",membership.ownerId,membership.ownerName)) + membership.group.members).associateBy { it.memberKey() }
        }.orEmpty()
        val people = request.people.map { person ->
            require(person.id.length in 1..180 && person.name.trim().length in 1..80) { "Enter valid participant names." }
            if(person.id.startsWith("user:") || person.id.startsWith("email:")) {
                val contact = requireNotNull(allowed[person.id]) { "Choose registered participants from your group." }
                person.copy(name = contact.name.ifBlank { contact.email })
            } else { require(person.id.startsWith("name:")) { "Add a name or choose a group." }; person.copy(name=person.name.trim()) }
        }
        val viewers = people.mapNotNull { it.id.takeIf { id -> id.startsWith("user:") }?.removePrefix("user:") }.toSet() + userId
        return start(userId,home.profile.name,request.purpose.trim(),request.style,people,viewers,request.audience)
    }
    private fun start(ownerId: String, ownerName: String, purpose: String, style: String, people: List<QuickChoiceCandidate>, viewers: Set<String>, audience: String, previous: String = ""): QuickChoiceSession {
        require(sessions.values.none { it.session.id != previous && clock() < it.session.spin.startAt + it.session.spin.duration &&
            (audience == "all" || it.audience == "all" || it.viewers.any { viewer -> viewer in viewers }) }) { "A selection is already running for these participants. Try again after it finishes." }
        require(sessions.size < 200 || previous.isNotEmpty()) { "Please try again shortly." }
        val id = UUID.randomUUID().toString()
        val start = clock()+3000
        val spin = SpinRound(id,people.map { it.id },people[random.nextInt(people.size)].id,start,duration=5400,turns=7)
        val session = QuickChoiceSession(id,ownerId,ownerName,purpose,style,people,spin,start+5400+45000)
        if(previous.isNotEmpty()) sessions.remove(previous)
        sessions[id] = Live(session,viewers,audience); changed(); return session
    }
}
