package com.karim.foodrun.server

import com.karim.foodrun.orders.*
import java.sql.DriverManager
import kotlin.test.*

class DatabaseReadPerformanceTest {
    @Test fun prefixQueriesUseTheKeyIndexAndPreserveLiteralMatchingAndOrder() = RoomFixture().use { f ->
        f.db.transaction {
            repeat(3000) { f.db.putRecord("unrelated:$it","Fixture $it") }
            for(key in listOf("wallet:two","wallet:one","walletish:other","wallet_%:literal","wallet_XY:other","م:واحد","م:اثنان","ن:آخر","😀:yes","😁:no")) f.db.putRecord(key,key)
        }
        assertEquals(listOf("wallet:two","wallet:one"),f.db.records("wallet:").map { it.first })
        assertEquals(listOf("wallet_%:literal"),f.db.records("wallet_%:").map { it.first })
        assertEquals(listOf("م:واحد","م:اثنان"),f.db.records("م:").map { it.first })
        assertEquals(listOf("😀:yes"),f.db.records("😀").map { it.first })
        assertEquals(3010,f.db.records("").count { it.first.startsWith("unrelated:") || it.second==it.first })
        DriverManager.getConnection("jdbc:sqlite:${f.directory.resolve("rooms.sqlite").absolutePath}").use { connection ->
            connection.prepareStatement("EXPLAIN QUERY PLAN SELECT key,body FROM account_records WHERE key>=? AND key<? ORDER BY rowid").use { statement ->
                statement.setString(1,"wallet:");statement.setString(2,"wallet;")
                val plan=statement.executeQuery().use { result -> buildList { while(result.next()) add(result.getString("detail")) } }
                assertTrue(plan.any { it.contains("SEARCH account_records USING INDEX") },"Prefix queries should search the primary key index: $plan")
            }
        }
    }
    @Test fun requestReadsReuseRoomsButWritesRollbackAndFollowingRequestsStayFresh() = RoomFixture().use { f ->
        val id=f.owner.room!!.id
        f.db.transaction {
            val first=f.db.allRooms().single { it.id==id }
            assertSame(first,f.db.room(id))
            val changed=first.copy(name="Updated lunch")
            f.db.save(changed)
            assertSame(changed,f.db.room(id))
            assertSame(changed,f.db.allRooms().single { it.id==id })
            assertNull(f.db.record("fixture:missing"));f.db.putRecord("fixture:missing","added")
            assertEquals("added",f.db.record("fixture:missing"));f.db.deleteRecord("fixture:missing")
            assertNull(f.db.record("fixture:missing"))
        }
        assertEquals("Updated lunch",f.db.room(id)!!.name)
        assertFails { f.db.transaction {
            f.db.save(f.db.room(id)!!.copy(name="Rolled back"));f.db.putRecord("fixture:rollback","changed")
            error("Fixture rollback")
        } }
        assertEquals("Updated lunch",f.db.room(id)!!.name);assertNull(f.db.record("fixture:rollback"))
        f.db.save(f.db.room(id)!!.copy(name="Next request"))
        f.db.transaction { assertEquals("Next request",f.db.room(id)!!.name) }
    }
}
