package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.json.*
import java.io.File
import java.nio.file.Files

/** Offline rehearsal against an exported snapshot. Never connects to Firestore or prints credentials. */
internal object AccountMergePreview {
    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2) { "Supply a snapshot file and a merge plan file." }
        val snapshot = orderJson.parseToJsonElement(File(args[0]).readText()).jsonObject
        val rows = orderJson.decodeFromJsonElement<List<StoredRow>>(snapshot.getValue("rows"))
        val plan = orderJson.decodeFromString<AccountMergePlan>(File(args[1]).readText())
        val storage = object : DurableStore {
            var current = rows.associateBy { it.id }.toMutableMap()
            override fun load() = current.values.toList()
            override fun commit(changes: Map<String, StoredRow?>) { changes.forEach { (id, row) -> if (row == null) current.remove(id) else current[id] = row } }
        }
        val directory = Files.createTempDirectory("foodrun-merge-preview-").toFile()
        try {
            RoomDatabase(directory, storage).use { db ->
                val rooms = db.allRooms()
                val wallets = db.records("wallet:").toMap()
                val result = AccountMergeRepair.apply(db, plan, System.currentTimeMillis())
                rooms.forEach { previous ->
                    val next = requireNotNull(db.room(previous.id))
                    check(previous.carts == next.carts && previous.transfers == next.transfers && previous.ownerId == next.ownerId && previous.payerId == next.payerId)
                    check(Billing.receipts(previous).map { it.total to it.balance } == Billing.receipts(next).map { it.total to it.balance })
                }
                check(wallets.keys == db.records("wallet:").map { it.first }.toSet())
                check(db.record("profile:${plan.sourceUserId}") == null)
                println(orderJson.encodeToString(result))
                println("PASS: order bills, carts, transfers, roles and wallet transaction IDs are preserved.")
                println("Unified wallet balances: " + WalletService.snapshot(db, plan.targetUserId).balances.filter { it.customerId == plan.targetUserId }.joinToString { Money.format(it.available, it.currency) })
            }
        } finally { directory.deleteRecursively() }
    }
}
