package edu.androidsec.notes.data

import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteSort
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDeletionsTest {

    private class FakeRepository : NotesRepository {
        val deleted = ConcurrentLinkedQueue<Long>()
        override fun observeAll(): Flow<List<Note>> = emptyFlow()
        override fun search(query: String, sort: NoteSort): Flow<List<Note>> = emptyFlow()
        override suspend fun get(id: Long): Note? = null
        override suspend fun save(note: Note): Long = note.id
        override suspend fun delete(id: Long) {
            deleted.add(id)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val repository = FakeRepository()
    private val afterDelete = ConcurrentLinkedQueue<Long>()
    private val pending = PendingDeletions(repository, scope, undoMillis = 150) { afterDelete.add(it) }

    @After
    fun tearDown() = scope.cancel()

    private fun waitUntil(timeoutMillis: Long = 3_000, condition: () -> Boolean) {
        val end = System.nanoTime() + timeoutMillis * 1_000_000
        while (!condition() && System.nanoTime() < end) Thread.sleep(10)
    }

    @Test
    fun noteIsHiddenImmediatelyButNotDeletedYet() {
        pending.schedule(1)
        assertTrue(1L in pending.hidden.value)
        assertTrue("В базе ничего не должно меняться до конца ожидания", repository.deleted.isEmpty())
    }

    @Test
    fun noteIsDeletedAfterTheDelay() {
        pending.schedule(1)
        waitUntil { repository.deleted.isNotEmpty() }
        assertEquals(listOf(1L), repository.deleted.toList())
        waitUntil { afterDelete.isNotEmpty() }
        assertEquals(listOf(1L), afterDelete.toList())
    }

    @Test
    fun undoBeforeTheDelayKeepsTheNote() {
        pending.schedule(1)
        assertTrue(pending.undo(1))
        assertFalse(1L in pending.hidden.value)
        Thread.sleep(400)
        assertTrue("После отмены заметка не должна удаляться", repository.deleted.isEmpty())
        assertTrue(afterDelete.isEmpty())
    }

    @Test
    fun undoAfterDeletionStartedReturnsFalse() {
        pending.schedule(1)
        waitUntil { repository.deleted.isNotEmpty() }
        assertFalse(pending.undo(1))
    }

    @Test
    fun undoOfUnknownNoteReturnsFalse() = assertFalse(pending.undo(42))

    @Test
    fun schedulingTwiceDeletesOnce() {
        pending.schedule(1)
        pending.schedule(1)
        waitUntil { repository.deleted.isNotEmpty() }
        Thread.sleep(300)
        assertEquals(listOf(1L), repository.deleted.toList())
    }

    @Test
    fun notesAreIndependent() {
        pending.schedule(1)
        pending.schedule(2)
        assertTrue(pending.undo(1))
        waitUntil { repository.deleted.isNotEmpty() }
        Thread.sleep(300)
        assertEquals(listOf(2L), repository.deleted.toList())
    }

    @Test
    fun requestKnowsHowMuchTimeIsLeft() {
        val slow = PendingDeletions(repository, scope, undoMillis = 10_000)
        slow.schedule(7)
        val remaining = runBlocking { slow.requests.first().remainingMillis() }
        assertTrue(remaining in 1..10_000)
        slow.undo(7)
    }
}
