package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*

internal class GroupFriends(private val c: GroupController) {
    private var id = ""
    private var members = emptyList<FriendContact>()
    private fun tr(en: String, ar: String) = if(c.library.language == "ar") ar else en
    private fun button(en: String, ar: String, value: String) = GroupButton(tr(en, ar), GroupAction.FRIENDS_ACTION, value)
    fun open(group: FriendGroup? = null) {
        id = group?.id ?: c.platform.uuid(); members = group?.members.orEmpty()
        c.draft[GroupFieldKey.FRIEND_GROUP_NAME] = group?.name.orEmpty()
        c.draft[GroupFieldKey.FRIEND_GROUP_FAVOURITE] = (group?.favourite ?: true).toString()
        c.draft[GroupFieldKey.FRIEND_EMAIL] = ""; c.page = GroupPage.FRIENDS
    }
    fun accept(reply: RoomReply, command: RoomCommand) {
        reply.friendContact?.takeIf { command.kind == CommandKind.FRIEND_LOOKUP && c.page == GroupPage.FRIENDS &&
            command.text == c.text(GroupFieldKey.FRIEND_EMAIL).trim().lowercase() }?.let {
            members = members.filterNot { old -> old.email == it.email } + it
            c.draft[GroupFieldKey.FRIEND_EMAIL] = ""
        }
        if(command.kind == CommandKind.SAVE_FRIEND_GROUP || command.kind == CommandKind.DELETE_FRIEND_GROUP) open()
    }
    fun dispatch(value: String) {
        val action = value.substringBefore('|'); val target = value.substringAfter('|', "")
        when(action) {
            "open", "new" -> open()
            "edit" -> open(c.library.home?.friendGroups?.single { it.id == target })
            "remove" -> members = members.filterNot { it.email == target }
            "add" -> {
                require(members.size < 30) { "Add up to 30 friends." }
                c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.FRIEND_LOOKUP,
                    text = c.text(GroupFieldKey.FRIEND_EMAIL).trim().lowercase()), returnPage = GroupPage.FRIENDS)
            }
            "save" -> c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.SAVE_FRIEND_GROUP,
                friendGroup = FriendGroup(id, c.text(GroupFieldKey.FRIEND_GROUP_NAME).trim(), c.flag(GroupFieldKey.FRIEND_GROUP_FAVOURITE), members)), returnPage = GroupPage.FRIENDS)
            "delete" -> c.send(RoomCommand(commandId = c.platform.uuid(), kind = CommandKind.DELETE_FRIEND_GROUP, friendGroupId = target), returnPage = GroupPage.FRIENDS)
        }
    }
    fun content(): GroupFlowContent {
        val fields = listOf(
            GroupField(GroupFieldKey.FRIEND_GROUP_NAME, tr("Group name", "اسم المجموعة"), c.text(GroupFieldKey.FRIEND_GROUP_NAME)),
            GroupField(GroupFieldKey.FRIEND_GROUP_FAVOURITE, tr("Favourite group", "مجموعة مفضلة"), c.text(GroupFieldKey.FRIEND_GROUP_FAVOURITE), toggle = true),
            GroupField(GroupFieldKey.FRIEND_EMAIL, tr("Friend's email", "بريد الصديق"), c.text(GroupFieldKey.FRIEND_EMAIL)))
        val cards = c.library.home?.friendGroups.orEmpty().map { GroupCard("friend-group:${it.id}", "${it.name}${if(it.favourite) " ★" else ""}",
            "${it.members.size} ${tr("friends", "أصدقاء")}", buttons = listOf(button("Edit", "تعديل", "edit|${it.id}"), button("Delete", "حذف", "delete|${it.id}"))) } +
            members.map { GroupCard("friend-email:${it.email}", it.name.ifBlank { it.email }, it.email + "\n" +
                if(it.userId.isEmpty()) tr("Sign-up invitation will be emailed when saved", "تُرسل دعوة التسجيل بالبريد عند الحفظ") else tr("Registered user", "مستخدم مسجل"),
                buttons = listOf(button("Remove", "إزالة", "remove|${it.email}"))) }
        val buttons = listOf(button("New group", "مجموعة جديدة", "new"), button("Add by email", "إضافة بالبريد", "add"),
            GroupButton(tr("Save group", "حفظ المجموعة"), GroupAction.FRIENDS_ACTION, "save", primary = true, enabled = members.isNotEmpty() && c.text(GroupFieldKey.FRIEND_GROUP_NAME).isNotBlank()))
        return GroupFlowContent(fields, cards, buttons)
    }
}
