package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import kotlinx.serialization.Serializable
import java.time.*
import java.time.temporal.TemporalAdjusters

@Serializable private data class Expense(val id: String, val at: Long, val amount: Long, val currency: String, val meal: String)

/** Private monthly ledger: confirmed food payments survive room removal, without counting top-ups or cash handoffs. */
internal class SpendingService(private val db: RoomDatabase, private val clock: () -> Long) {
    private fun owner(room: Room, memberId: String): String? = db.record("member-user:${room.id}:$memberId")?.let { AccountAliases.resolve(db,it) }
    private fun month(at: Long): String = YearMonth.from(Instant.ofEpochMilli(at).atZone(ZoneOffset.UTC)).toString()
    private fun entries(key: String): List<Expense> = db.record(key)?.let { orderJson.decodeFromString(it) } ?: emptyList()
    private fun add(uid: String, entry: Expense) {
        if(entry.at <= 0 || entry.amount == 0L) return
        val key="spending:$uid:${month(entry.at)}"
        val old=entries(key)
        if(old.none { it.id == entry.id }) db.putRecord(key,orderJson.encodeToString(old+entry))
    }
    internal fun record(room: Room, onlyUser: String? = null) = db.transaction {
        val meal=meal(room.name)
        room.transfers.filter { it.status == TransferStatus.CONFIRMED && it.memberId != room.payerId }.forEach { transfer ->
            owner(room,transfer.memberId)?.takeIf { onlyUser == null || it == onlyUser }?.let { uid ->
                add(uid,Expense("${room.id}:${room.orderNumber}:${transfer.id}",transfer.createdAt,
                    if(transfer.refund) -transfer.amount else transfer.amount,room.restaurant.currency,meal))
            }
        }
        if(room.restaurantPaid) room.payerId?.let { payer -> owner(room,payer)?.takeIf { onlyUser == null || it == onlyUser }?.let { uid ->
            val own=Billing.receipts(room).firstOrNull { it.memberId == payer }?.total ?: 0L
            val marker="spending-own:${room.id}:${room.orderNumber}:$uid"
            val previous=db.record(marker)?.toLong() ?: 0L
            val payment=room.audit.lastOrNull { it.action == "PAY_RESTAURANT" || room.paymentRoom != null && it.action == "ADJUST_BILL" }
            // Payment-only rooms already represent a bill paid by their creator.
            val at=payment?.at ?: room.orderCreatedAt.takeIf { it > 0 } ?: room.createdAt
            add(uid,Expense("${room.id}:${room.orderNumber}:own:${payment?.id ?: "initial"}:${room.billRevision}:$own",at,own-previous,room.restaurant.currency,meal))
            if(own != previous || db.record(marker) == null) db.putRecord(marker,own.toString())
        } }
    }
    private fun backfill(uid: String) {
        val key="spending-initialized:$uid"
        if(db.record(key) != null) return
        // Only this account's rooms, ten historical orders at a time, to bound memory on small hosts.
        db.allRooms().filter { room -> room.members.any { owner(room,it.id) == uid } }.forEach { room ->
            var offset=0
            while(true) {
                val page=db.history(room.id,offset,10)
                if(page.isEmpty()) break
                page.sortedBy { it.orderNumber }.forEach { record(it,uid) }
                offset+=page.size
            }
            record(room,uid)
        }
        db.putRecord(key,"done")
    }
    fun summary(uid: String, requestedZone: String = ""): SpendingSummary = db.transaction {
        val zoneKey="spending-zone:$uid"
        val requested=requestedZone.takeIf { it.length <= 100 && it.isNotBlank() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
        val zone=requested ?: db.record(zoneKey)?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneOffset.UTC
        if(requested != null && db.record(zoneKey) != zone.id) db.putRecord(zoneKey,zone.id)
        backfill(uid)
        val now=clock(); val today=Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val week=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val month=today.withDayOfMonth(1)
        val first=minOf(week,month).atStartOfDay(zone).toInstant().toEpochMilli()
        val next=maxOf(week.plusDays(7),month.plusMonths(1)).atStartOfDay(zone).toInstant().toEpochMilli()
        val months=generateSequence(YearMonth.parse(month(first))) { it.plusMonths(1) }.takeWhile { it <= YearMonth.parse(month(next-1)) }.toList()
        val aliases=listOf(uid)+db.records("account-alias:").map { it.first.removePrefix("account-alias:") }.filter { AccountAliases.resolve(db,it) == uid }
        val expenses=aliases.flatMap { id -> months.flatMap { entries("spending:$id:$it") } }.distinctBy { it.id }.filter { it.at <= now }
        val currencies=expenses.map { it.currency }.distinct().sorted().ifEmpty { listOf("AED") }
        val periods=listOf("week" to week,"month" to month).flatMap { (kind,start) ->
            val end=if(kind == "week") start.plusDays(7) else start.plusMonths(1)
            val from=start.atStartOfDay(zone).toInstant().toEpochMilli(); val until=end.atStartOfDay(zone).toInstant().toEpochMilli()
            currencies.map { currency ->
                val rows=expenses.filter { it.currency == currency && it.at >= from && it.at < until }
                val paid=rows.filter { it.amount > 0 }.sumOf { it.amount }; val refunds=-rows.filter { it.amount < 0 }.sumOf { it.amount }
                SpendingPeriod(kind,start.toString(),end.minusDays(1).toString(),currency,paid,refunds,paid-refunds,
                    listOf("breakfast","lunch","dinner","other").map { meal -> MealSpending(meal,rows.filter { it.meal == meal }.sumOf { it.amount }) })
            }
        }
        SpendingSummary(now,zone.id,periods)
    }
    companion object {
        internal fun meal(name: String): String {
            val text=name.lowercase().replace(Regex("[ًٌٍَُِّْـ]"),"")
            val matches=listOf("breakfast" to Regex("breakfast|فطور|فطار|إفطار|افطار"), "lunch" to Regex("lunch|غداء|غدا"),"dinner" to Regex("dinner|supper|عشاء|عشا"))
                .filter { it.second.containsMatchIn(text) }.map { it.first }
            return matches.singleOrNull() ?: "other"
        }
    }
}
