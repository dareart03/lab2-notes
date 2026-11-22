package edu.androidsec.notes.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.data.db.NotesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Проверяет обновление базы с версии 1 на 2: файл базы создаётся «как в старом приложении»
 * (таблица без тегов и признака секретности), затем открывается текущей [NotesDatabase].
 * Room при открытии сверяет итоговую схему с ожидаемой, поэтому тест заодно ловит расхождения
 * между `ALTER TABLE` в миграции и аннотациями в `NoteEntity`.
 */
@RunWith(AndroidJUnit4::class)
class NotesMigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(dbName)
        createVersion1Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Схема версии 1 в том виде, в каком её создаёт Room для старой `NoteEntity`. */
    private fun createVersion1Database() {
        val path = context.getDatabasePath(dbName)
        path.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(path, null)
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `notes` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`title` TEXT NOT NULL, " +
                "`body` TEXT NOT NULL, " +
                "`photo_path` TEXT, " +
                "`updated_at` INTEGER NOT NULL)",
        )
        db.execSQL(
            "INSERT INTO notes (id, title, body, photo_path, updated_at) VALUES " +
                "(1, 'Старая заметка', 'Текст до обновления', '/data/photo.jpg', 111), " +
                "(2, 'Вторая', '', NULL, 222)",
        )
        db.version = 1
        db.close()
    }

    private fun openMigrated(): NotesDatabase =
        Room.databaseBuilder(context, NotesDatabase::class.java, dbName)
            .addMigrations(NotesDatabase.MIGRATION_1_2)
            .build()

    @Test
    fun oldNotesSurviveMigration() = runBlocking {
        val db = openMigrated()
        try {
            val first = db.noteDao().getById(1)
            assertNotNull("Старая заметка должна сохраниться", first)
            assertEquals("Старая заметка", first!!.title)
            assertEquals("Текст до обновления", first.body)
            assertEquals("/data/photo.jpg", first.photoPath)
            assertEquals(111L, first.updatedAt)

            val second = db.noteDao().getById(2)
            assertEquals("Вторая", second?.title)
            assertEquals(null, second?.photoPath)
        } finally {
            db.close()
        }
    }

    @Test
    fun oldNotesGetDefaultTagsAndAreNotSecret() = runBlocking {
        val db = openMigrated()
        try {
            val note = db.noteDao().getById(1)!!
            assertEquals("", note.tags)
            assertFalse(note.isSecret)
        } finally {
            db.close()
        }
    }
}
