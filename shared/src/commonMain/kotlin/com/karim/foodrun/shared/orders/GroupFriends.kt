package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupFriends(private val c: GroupController) {
    private var id = ""
    private var members = emptyList<FriendContact>()
    private var revision = 0L
    private var viewing: FriendGroupMembership? = null
    private var searchVersion = 0
    private var results = emptyList<FriendContact>()
    private var searching = false
    fun search() {
        val version = ++searchVersion
        val query = c.text(GroupFieldKey.FRIEND_EMAIL).trim()
        results = emptyList(); searching = query.length >= 2
        if(!searching) return
        val identity = c.library.identityToken; val hub = c.library.identityHub ?: return
        c.platform.schedule(300, object : GroupScheduledCallback {
            override fun run() {
                if(version != searchVersion || c.page != GroupPage.FRIENDS || c.library.identityToken != identity) return
                val command = RoomCommand(commandId = c.platform.uuid(),kind = CommandKind.FRIEND_SEARCH,text = query,identityToken = identity,friendsDetails = true)
                c.platform.request(hub,orderJson.encodeToString(command),object : GroupReplyCallback {
                    override fun complete(body: String, error: String) {
                        if(version != searchVersion || c.page != GroupPage.FRIENDS || c.library.identityToken != identity || c.library.identityHub != hub) return
                        searching = false
                        try { require(error.isEmpty()) { error }; val reply = orderJson.decodeFromString<RoomReply>(body); require(reply.ok) { reply.error }; results = reply.friendContacts.orEmpty() }
                        catch(e: Exception) { c.error = e.message ?: "Could not search people." }
                        c.publish()
                    }
                })
            }
        })
    }
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun button(en: String, ar: String, value: String) = GroupButton(tr(en, ar), GroupAction.FRIENDS_ACTION, value)
    fun open(group: FriendGroup? = null) {
        ++searchVersion; results = emptyList(); searching = false
        viewing = null; revision = group?.revision ?: 0
        id = group?.id ?: c.platform.uuid(); members = group?.members.orEmpty()
        c.draft[GroupFieldKey.FRIEND_GROUP_NAME] = group?.name.orEmpty()
        c.draft[GroupFieldKey.FRIEND_GROUP_FAVOURITE] = (group?.favourite ?: true).toString()
        c.draft[GroupFieldKey.FRIEND_EMAIL] = ""; c.page = GroupPage.FRIENDS
    }
    fun accept(reply: RoomReply, command: RoomCommand) {
        reply.friendContact?.takeIf { command.kind == CommandKind.FRIEND_LOOKUP && c.page == GroupPage.FRIENDS &&
            command.text == c.text(GroupFieldKey.FRIEND_EMAIL).trim().lowercase() }?.let {
            members = members.filterNot { old -> old.memberKey() == it.memberKey() } + it
            c.draft[GroupFieldKey.FRIEND_EMAIL] = ""
            ++searchVersion; results = emptyList(); searching = false
        }
        if(command.kind in listOf(CommandKind.SAVE_FRIEND_GROUP, CommandKind.DELETE_FRIEND_GROUP, CommandKind.LEAVE_FRIEND_GROUP)) open()
    }
    fun dispatch(value: String) {
        val action = value.substringBefore('|'); val target = value.substringAfter('|', "")
        when(action) {
            "open", "new" -> open()
            "edit" -> open(c.library.home?.friendGroups?.single { it.id == target })
            "view" -> { viewing = c.library.home?.joinedFriendGroups?.single { "${it.ownerId}|${it.group.id}" == target }; c.page = GroupPage.FRIENDS }
            "leave" -> {
                val group = c.library.home?.joinedFriendGroups?.single { "${it.ownerId}|${it.group.id}" == target } ?: error("This group is no longer available.")
                c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.LEAVE_FRIEND_GROUP, friendGroupOwnerId = group.ownerId, friendGroupId = group.group.id), returnPage = GroupPage.FRIENDS)
            }
            "remove" -> members = members.filterNot { it.memberKey() == target || it.email == target }
            "pick" -> {
                require(members.size < 30) { "Add up to 30 friends." }
                val person = results.single { it.memberKey() == target }
                members = members.filterNot { it.memberKey() == target } + person
                c.draft[GroupFieldKey.FRIEND_EMAIL] = ""; ++searchVersion; results = emptyList(); searching = false
            }
            "add" -> {
                require(members.size < 30) { "Add up to 30 friends." }
                c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.FRIEND_LOOKUP,
                    text = c.text(GroupFieldKey.FRIEND_EMAIL).trim().lowercase()), returnPage = GroupPage.FRIENDS)
            }
            "save" -> c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.SAVE_FRIEND_GROUP,
                friendGroup = FriendGroup(id, c.text(GroupFieldKey.FRIEND_GROUP_NAME).trim(), c.flag(GroupFieldKey.FRIEND_GROUP_FAVOURITE), members, revision)), returnPage = GroupPage.FRIENDS)
            "delete" -> c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.DELETE_FRIEND_GROUP, friendGroupId = target), returnPage = GroupPage.FRIENDS)
        }
    }
    fun content(): GroupFlowContent {
        viewing?.let { selected ->
            val latest = c.library.home?.joinedFriendGroups?.singleOrNull { it.ownerId == selected.ownerId && it.group.id == selected.group.id }
            if(latest == null) { open(); return content() }
            return GroupFlowContent(cards = listOf(GroupCard("friend-view", latest.group.name, tr("Created by ${latest.ownerName}", "أنشأها ${latest.ownerName}"))) + latest.group.members.map {
                GroupCard("friend-view-member:${it.memberKey()}", it.name.ifBlank { it.email }, it.email.ifBlank { tr("App notifications available", "إشعارات التطبيق متاحة") })
            }, buttons = listOf(button("Leave group", "مغادرة المجموعة", "leave|${latest.ownerId}|${latest.group.id}"), button("Back to groups", "العودة للمجموعات", "open")))
        }
        val fields = listOf(
            GroupField(GroupFieldKey.FRIEND_GROUP_NAME, tr("Group name", "اسم المجموعة"), c.text(GroupFieldKey.FRIEND_GROUP_NAME)),
            GroupField(GroupFieldKey.FRIEND_GROUP_FAVOURITE, tr("Favourite group", "مجموعة مفضلة"), c.text(GroupFieldKey.FRIEND_GROUP_FAVOURITE), toggle = true),
            GroupField(GroupFieldKey.FRIEND_EMAIL, tr("Find people by name or email", "ابحث عن الأشخاص بالاسم أو البريد"), c.text(GroupFieldKey.FRIEND_EMAIL)))
        val cards = c.library.home?.friendGroups.orEmpty().map { GroupCard("friend-group:${it.id}", "${it.name}${if(it.favourite) " ★" else ""}",
            "${it.members.size} ${tr("friends", "أصدقاء")}", buttons = listOf(button("Edit", "تعديل", "edit|${it.id}"), button("Delete", "حذف", "delete|${it.id}"))) } +
            c.library.home?.joinedFriendGroups.orEmpty().map { GroupCard("friend-joined:${it.ownerId}:${it.group.id}", it.group.name, tr("Created by ${it.ownerName}", "أنشأها ${it.ownerName}"),
                buttons = listOf(button("View group", "عرض المجموعة", "view|${it.ownerId}|${it.group.id}"), button("Leave group", "مغادرة المجموعة", "leave|${it.ownerId}|${it.group.id}"))) } +
            (if(searching) listOf(GroupCard("friend-searching",tr("Searching…", "جارٍ البحث…"))) else results.filterNot { person -> members.any { it.memberKey() == person.memberKey() } }.map { person ->
                GroupCard("friend-result:${person.memberKey()}",person.name.ifBlank { person.email },person.email,buttons = listOf(button("Add person", "إضافة الشخص", "pick|${person.memberKey()}"))) }) +
            members.map { GroupCard("friend-email:${it.memberKey()}", it.name.ifBlank { it.email }, it.email.ifBlank { tr("App notifications available", "إشعارات التطبيق متاحة") } + "\n" +
                if(it.userId.isEmpty()) tr("Sign-up invitation will be emailed when saved", "تُرسل دعوة التسجيل بالبريد عند الحفظ") else tr("Registered user", "مستخدم مسجل"),
                buttons = listOf(button("Remove", "إزالة", "remove|${it.memberKey()}"))) }
        val buttons = listOf(button("New group", "مجموعة جديدة", "new"), button("Add by email", "إضافة بالبريد", "add"),
            GroupButton(tr("Save group", "حفظ المجموعة"), GroupAction.FRIENDS_ACTION, "save", primary = true, enabled = c.text(GroupFieldKey.FRIEND_GROUP_NAME).isNotBlank()))
        return GroupFlowContent(fields, cards, buttons)
    }
}
