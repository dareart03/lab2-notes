package edu.androidsec.notes.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.domain.FieldError
import edu.androidsec.notes.domain.InvalidNoteException
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Некорректная заметка не должна попасть в базу, даже если обойти экран и вызвать репозиторий напрямую. */
@RunWith(AndroidJUnit4::class)
class NotesRepositoryValidationTest {

    private lateinit var db: NotesDatabase
    private lateinit var repository: RoomNotesRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // База в памяти: без SeedCallback, начинаем с пустой таблицы.
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        repository = RoomNotesRepository(db.noteDao())
    }

    @After
    fun tearDown() = db.close()

    private fun allNotes(): List<Note> = runBlocking { repository.observeAll().first() }

    private fun assertRejected(expected: FieldError, note: Note) = runBlocking {
        try {
            repository.save(note)
            fail("Ожидали InvalidNoteException")
        } catch (e: InvalidNoteException) {
            assertEquals(expected, e.error)
        }
        assertTrue("Заметка не должна попасть в базу", allNotes().isEmpty())
    }

    @Test
    fun validNoteIsSaved() = runBlocking {
        val id = repository.save(Note(title = "Заголовок", body = "Текст"))
        val saved = repository.get(id)
        assertEquals("Заголовок", saved?.title)
        assertEquals(1, allNotes().size)
    }

    @Test
    fun titleAtLimitIsSaved() = runBlocking {
        repository.save(Note(title = "a".repeat(100), body = ""))
        assertEquals(1, allNotes().size)
    }

    @Test
    fun tooLongTitleIsRejected() = assertRejected(FieldError.TITLE_TOO_LONG, Note(title = "a".repeat(101)))

    @Test
    fun multilineTitleIsRejected() = assertRejected(FieldError.TITLE_MULTILINE, Note(title = "a\nb"))

    @Test
    fun tooLongBodyIsRejected() = assertRejected(FieldError.BODY_TOO_LONG, Note(body = "a".repeat(5001)))

    @Test
    fun tagWithBadCharsIsRejected() = assertRejected(FieldError.TAG_BAD_CHARS, Note(tags = "Kotlin"))

    @Test
    fun tooManyTagsAreRejected() = assertRejected(FieldError.TAGS_TOO_MANY, Note(tags = "a b c d e f"))

    @Test
    fun tooLongTagIsRejected() = assertRejected(FieldError.TAG_TOO_LONG, Note(tags = "a".repeat(21)))

    @Test
    fun tagsAreNormalizedAndSaved() = runBlocking {
        val id = repository.save(Note(title = "t", tags = "  kotlin   android-sec "))
        assertEquals("kotlin android-sec", repository.get(id)?.tags)
    }

    @Test
    fun secretFlagIsSaved() = runBlocking {
        val id = repository.save(Note(title = "t", isSecret = true))
        assertEquals(true, repository.get(id)?.isSecret)
    }

    @Test
    fun invalidEditDoesNotOverwriteSavedNote() = runBlocking {
        val id = repository.save(Note(title = "Исходный", body = "Текст"))
        try {
            repository.save(Note(id = id, title = "a".repeat(101), body = "Новый текст"))
            fail("Ожидали InvalidNoteException")
        } catch (_: InvalidNoteException) {
        }
        assertEquals("Исходный", repository.get(id)?.title)
        assertEquals("Текст", repository.get(id)?.body)
    }
}
