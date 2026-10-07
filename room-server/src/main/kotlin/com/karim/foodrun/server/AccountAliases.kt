package com.karim.foodrun.server

/** Explicit repairs can join two verified login identities without deleting either Firebase login. */
internal object AccountAliases {
    fun resolve(db: RoomDatabase, userId: String): String {
        var current = userId
        val visited = mutableSetOf<String>()
        repeat(16) {
            check(visited.add(current)) { "Account identity contains a cycle." }
            val next = db.record("account-alias:$current") ?: return current
            check(next.isNotBlank() && next != current) { "Account identity is invalid." }
            current = next
        }
        error("Account identity exceeds the supported limit.")
    }
}
