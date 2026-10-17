package com.karim.foodrun.server

import java.util.UUID

/** Explicitly invoked against an authorized Turso database; never part of the automated suite. */
object TursoLiveProbe {
    @JvmStatic fun main(args: Array<String>) {
        require(args.contentEquals(arrayOf("--synthetic-namespace-only")))
        val namespace = "verify-${UUID.randomUUID()}"
        val sql = TursoSql(requireNotNull(System.getenv("FOODRUN_TURSO_URL")), requireNotNull(System.getenv("FOODRUN_TURSO_AUTH_TOKEN")))
        try {
            TursoStore(sql, namespace, true).use { old ->
                check(old.load() == null)
                val row = StoredRow("account_records", listOf("synthetic-probe", "confirmed"))
                old.commit(mapOf(row.id to row))
                TursoStore(sql, namespace).use { current ->
                    check(current.load() == listOf(row))
                    check(runCatching { old.commit(mapOf(row.id to row.copy(cells = listOf("synthetic-probe", "stale")))) }.exceptionOrNull() is StorageUnavailable)
                    val updated = row.copy(cells = listOf("synthetic-probe", "updated"))
                    current.commit(mapOf(updated.id to updated))
                    check(current.load() == listOf(updated))
                    current.commit(mapOf(updated.id to null))
                    check(current.load() == emptyList<StoredRow>())
                }
            }
            println("Turso live probe passed: create, atomic save, restore, stale-writer rejection, update and deletion.")
        } finally {
            sql.execute("DELETE FROM foodrun_rows WHERE namespace=?", namespace)
            sql.execute("DELETE FROM foodrun_hubs WHERE namespace=?", namespace)
        }
    }
}
