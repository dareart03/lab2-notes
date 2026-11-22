package edu.androidsec.notes.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.NoteEntity
import edu.androidsec.notes.data.db.NotesDatabase
import edu.androidsec.notes.domain.NoteSort
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Тесты поиска и сортировки на настоящей (in-memory) базе Room. */
@RunWith(AndroidJUnit4::class)
class NoteSearchTest {

    private lateinit var db: NotesDatabase
    private lateinit var dao: NoteDao
    private lateinit var repository: RoomNotesRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        dao = db.noteDao()
        repository = RoomNotesRepository(dao)
        runBlocking {
            dao.upsert(note("Банан", "жёлтый фрукт", at = 100))
            dao.upsert(note("apple", "красный фрукт", at = 300))
            dao.upsert(note("Скидка 100% на всё", "акция", at = 200))
            dao.upsert(note("snake_case", "имя переменной", at = 400))
            dao.upsert(note("Путь C:\\temp", "папка", at = 500))
            dao.upsert(note("Заметка", "в тексте есть needle внутри", at = 600))
        }
    }

    @After
    fun tearDown() = db.close()

    private fun note(title: String, body: String, at: Long) =
        NoteEntity(title = title, body = body, photoPath = null, updatedAt = at)

    private fun search(query: String, sort: NoteSort = NoteSort.NEWEST): List<String> = runBlocking {
        repository.search(query, sort).first().map { it.title }
    }

    // --- что находится ---

    @Test
    fun emptyQueryReturnsAllNotes() = assertEquals(6, search("").size)

    @Test
    fun searchesByTitle() = assertEquals(listOf("Банан"), search("Банан"))

    @Test
    fun searchesByBody() = assertEquals(listOf("Заметка"), search("needle"))

    @Test
    fun matchesPartOfWord() = assertEquals(listOf("snake_case"), search("nake"))

    @Test
    fun latinSearchIgnoresCase() = assertEquals(listOf("apple"), search("APPLE"))

    @Test
    fun nothingFoundGivesEmptyList() = assertEquals(emptyList<String>(), search("такого-нет"))

    // --- спецсимволы LIKE ищутся буквально ---

    @Test
    fun percentIsLiteral() = assertEquals(listOf("Скидка 100% на всё"), search("%"))

    @Test
    fun underscoreIsLiteral() = assertEquals(listOf("snake_case"), search("_"))

    @Test
    fun backslashIsLiteral() = assertEquals(listOf("Путь C:\\temp"), search("\\"))

    @Test
    fun percentInTheMiddleIsLiteral() = assertEquals(listOf("Скидка 100% на всё"), search("0% н"))

    // --- ввод, похожий на SQL-инъекцию, остаётся обычным текстом ---

    @Test
    fun injectionLikeInputIsJustText() {
        assertEquals(emptyList<String>(), search("' OR 1=1 --"))
        assertEquals(emptyList<String>(), search("x'; DROP TABLE notes; --"))
        // таблица жива, заметки на месте
        assertEquals(6, search("").size)
    }

    // --- сортировка ---

    @Test
    fun sortNewestFirst() = assertEquals(
        listOf("Заметка", "Путь C:\\temp", "snake_case", "apple", "Скидка 100% на всё", "Банан"),
        search("", NoteSort.NEWEST),
    )

    @Test
    fun sortOldestFirst() = assertEquals(
        listOf("Банан", "Скидка 100% на всё", "apple", "snake_case", "Путь C:\\temp", "Заметка"),
        search("", NoteSort.OLDEST),
    )

    @Test
    fun sortByTitleKeepsLatinCaseInsensitive() {
        val titles = search("", NoteSort.TITLE)
        // apple (латиница) идёт раньше snake_case; кириллица в SQLite NOCASE сравнивается по кодам.
        assertEquals(true, titles.indexOf("apple") < titles.indexOf("snake_case"))
        assertEquals(6, titles.size)
    }

    @Test
    fun sortAppliesToFilteredResults() {
        // «фрукт» есть в тексте у Банана (t=100) и apple (t=300)
        assertEquals(listOf("apple", "Банан"), search("фрукт", NoteSort.NEWEST))
        assertEquals(listOf("Банан", "apple"), search("фрукт", NoteSort.OLDEST))
    }
}
