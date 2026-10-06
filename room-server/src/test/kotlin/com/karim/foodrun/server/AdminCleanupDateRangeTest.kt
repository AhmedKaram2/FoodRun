package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.time.Instant
import kotlin.test.*

class AdminCleanupDateRangeTest {
    private fun at(value: String) = Instant.parse(value).toEpochMilli()
    private fun moveClock(f: RoomFixture, timestamp: Long) {
        f.now = timestamp
        val room = f.db.room(f.owner.room!!.id)!!
        f.db.save(room.copy(createdAt = timestamp, updatedAt = timestamp))
    }
    private fun store(f: RoomFixture, updatedAt: Long, phase: RoomPhase = RoomPhase.CANCELLED): Room {
        val room = f.state().room!!.copy(id = f.id(), code = (100000 + f.db.allRooms().size).toString(), phase = phase, updatedAt = updatedAt)
        f.db.save(room)
        return room
    }
    private fun selection(from: String = "2026-10-01", to: String = "2026-10-02", zone: String = "Asia/Dubai") =
        AdminCleanupRequest(fromDate = from, toDate = to, timeZone = zone)

    @Test fun dateRangeIncludesBothFullCalendarDaysInTheSelectedTimeZone() = RoomFixture().use { f ->
        val before = store(f, at("2026-09-30T19:59:59.999Z"))
        val first = store(f, at("2026-09-30T20:00:00Z"))
        val last = store(f, at("2026-10-02T19:59:59.999Z"))
        val after = store(f, at("2026-10-02T20:00:00Z"))
        val admin = AdminService(f.db, f.service, { f.now })
        val request = selection()
        val preview = admin.cleanupPreview(request)
        assertEquals(setOf(first.id, last.id), preview.targets.map { it.id }.toSet())
        assertEquals(request.fromDate, preview.fromDate)
        assertEquals(request.toDate, preview.toDate)
        assertEquals(request.timeZone, preview.timeZone)
        assertEquals(2, admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin").removedCount)
        assertNotNull(f.db.room(before.id)); assertNotNull(f.db.room(after.id))
        assertNull(f.db.room(first.id)); assertNull(f.db.room(last.id))
    }

    @Test fun sameDayRangeUsesTheWholeDayAndFollowsDaylightSavingBoundaries() = RoomFixture().use { f ->
        val before = store(f, at("2026-03-08T04:59:59.999Z"))
        val first = store(f, at("2026-03-08T05:00:00Z"))
        val last = store(f, at("2026-03-09T03:59:59.999Z"))
        val after = store(f, at("2026-03-09T04:00:00Z"))
        val preview = AdminService(f.db, f.service, { f.now }).cleanupPreview(selection("2026-03-08", "2026-03-08", "America/New_York"))
        assertEquals(setOf(first.id, last.id), preview.targets.map { it.id }.toSet())
        assertFalse(preview.targets.any { it.id == before.id || it.id == after.id })
    }

    @Test fun invalidOrIncompleteDateRangesNeverFallBackToAgeCleanup() = RoomFixture().use { f ->
        store(f, f.now)
        val admin = AdminService(f.db, f.service, { f.now })
        val invalid = listOf(
            selection().copy(fromDate = ""), selection().copy(toDate = ""),
            selection("2026-10-03", "2026-10-02"), selection("2026-02-30", "2026-03-01"),
            selection("2026-1-01", "2026-10-01"), selection().copy(timeZone = "Not/AZone"),
        )
        invalid.forEach { assertFails { admin.cleanupPreview(it) } }
        assertEquals(2, f.db.allRooms().size)
    }

    @Test fun tokenBindsTheDatesAndTimeZoneEvenWhenTheRoomSelectionStaysTheSame() = RoomFixture().use { f ->
        val room = store(f, at("2026-10-02T12:00:00Z"))
        val admin = AdminService(f.db, f.service, { f.now })
        val request = selection("2026-10-02", "2026-10-02")
        val preview = admin.cleanupPreview(request)
        for (changed in listOf(request.copy(fromDate = "2026-10-01"), request.copy(toDate = "2026-10-03"), request.copy(timeZone = "UTC"))) {
            assertEquals(listOf(room.id), admin.cleanupPreview(changed).targets.map { it.id })
            assertFails { admin.cleanup(changed.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin") }
            assertNotNull(f.db.room(room.id))
        }
        assertFails { admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "delete"), "admin") }
    }

    @Test fun aChangedRoomOrNewMatchingRoomInvalidatesThePreviewWithoutPartialDeletion() = RoomFixture().use { f ->
        val room = store(f, at("2026-10-02T12:00:00Z"))
        val admin = AdminService(f.db, f.service, { f.now })
        val request = selection()
        val original = admin.cleanupPreview(request)
        f.db.save(room.copy(revision = room.revision + 1))
        assertFails { admin.cleanup(request.copy(previewToken = original.previewToken, confirmation = "DELETE"), "admin") }
        val refreshed = admin.cleanupPreview(request)
        val another = store(f, room.updatedAt)
        assertFails { admin.cleanup(request.copy(previewToken = refreshed.previewToken, confirmation = "DELETE"), "admin") }
        assertNotNull(f.db.room(room.id)); assertNotNull(f.db.room(another.id))
    }

    @Test fun dateRangeStillProtectsActiveAndUnsettledOrders() = RoomFixture().use { f ->
        val timestamp = at("2026-10-02T12:00:00Z")
        store(f, timestamp, RoomPhase.LOBBY)
        moveClock(f, timestamp)
        f.placed()
        val placed = f.state().room!!
        for (phase in listOf(RoomPhase.PLACED, RoomPhase.FULFILLED, RoomPhase.ARCHIVED)) {
            f.db.save(placed.copy(phase = phase))
            assertEquals(0, AdminService(f.db, f.service, { f.now }).cleanupPreview(selection()).count)
        }
        assertTrue(f.db.allRooms().isNotEmpty())
    }

    @Test fun deletingRoomsClearsTheirHistoryAndSessionsAndRecordsTheDateRange() = RoomFixture().use { f ->
        moveClock(f, at("2026-10-02T12:00:00Z"))
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished again") }
        val admin = AdminService(f.db, f.service, { f.now })
        val request = selection()
        val preview = admin.cleanupPreview(request)
        assertEquals(1, admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin").removedCount)
        assertNull(f.db.room(f.owner.room!!.id)); assertTrue(f.db.allHistory().isEmpty())
        assertNull(f.db.session(RoomService.hash(f.owner.token)))
        val audit = admin.dashboard().activity.single { it.action == "cleanup:closedRooms" }
        assertEquals("admin", audit.actorId)
        assertTrue(audit.target.contains("2026-10-01 through 2026-10-02 (Asia/Dubai)"))
    }

    @Test fun dateRangeCanClearOnlyHistoryAndKeepTheCurrentRoom() = RoomFixture().use { f ->
        moveClock(f, at("2026-10-02T12:00:00Z"))
        f.send(f.owner, CommandKind.CANCEL) { it.copy(text = "Finished") }
        f.send(f.owner, CommandKind.NEXT_ORDER)
        val admin = AdminService(f.db, f.service, { f.now })
        val request = selection().copy(scope = "history")
        val preview = admin.cleanupPreview(request)
        assertEquals(1, admin.cleanup(request.copy(previewToken = preview.previewToken, confirmation = "DELETE"), "admin").removedCount)
        assertNotNull(f.db.room(f.owner.room!!.id)); assertTrue(f.db.allHistory().isEmpty())
        assertEquals(setOf(1L), f.state().deletedHistoryNumbers)
    }
}
