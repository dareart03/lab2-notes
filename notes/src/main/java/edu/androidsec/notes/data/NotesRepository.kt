package edu.androidsec.notes.data

import edu.androidsec.notes.data.db.NoteDao
import edu.androidsec.notes.data.db.toDomain
import edu.androidsec.notes.data.db.toEntity
import edu.androidsec.notes.domain.InvalidNoteException
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteRules
import edu.androidsec.notes.domain.NoteSort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Порт доступа к заметкам. ViewModel-и зависят только от него. */
interface NotesRepository {
    fun observeAll(): Flow<List<Note>>

    /**
     * Заметки, в заголовке или тексте которых есть [query] (символы `%`, `_`, `\` ищутся буквально),
     * в порядке [sort]. Пустой запрос возвращает все заметки.
     */
    fun search(query: String, sort: NoteSort): Flow<List<Note>>

    suspend fun get(id: Long): Note?
    /**
     * Сохраняет заметку и возвращает её id (для новой заметки — только что выданный).
     * @throws InvalidNoteException если заметка нарушает [NoteRules]; в базу такая заметка не попадает.
     */
    suspend fun save(note: Note): Long
    suspend fun delete(id: Long)
}

class RoomNotesRepository(private val dao: NoteDao) : NotesRepository {

    override fun observeAll(): Flow<List<Note>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun search(query: String, sort: NoteSort): Flow<List<Note>> {
        val pattern = "%${query.escapeForLike()}%"
        val source = when (sort) {
            NoteSort.TITLE -> dao.searchByTitle(pattern)
            NoteSort.NEWEST -> dao.searchNewestFirst(pattern)
            NoteSort.OLDEST -> dao.searchOldestFirst(pattern)
        }
        return source.map { list -> list.map { it.toDomain() } }
    }

    override suspend fun get(id: Long): Note? = dao.getById(id)?.toDomain()

    override suspend fun save(note: Note): Long {
        // Вторая линия защиты: экран можно обойти (другой код, интент, баг), база — нельзя.
        NoteRules.validate(note.title, note.body, note.tags)
            ?.let { throw InvalidNoteException(it) }

        val stamped = note.copy(
            tags = NoteRules.normalizeTags(note.tags),
            updatedAt = System.currentTimeMillis(),
        )
        val rowId = dao.upsert(stamped.toEntity())
        return if (note.isNew) rowId else note.id
    }

    override suspend fun delete(id: Long) = dao.deleteById(id)
}
