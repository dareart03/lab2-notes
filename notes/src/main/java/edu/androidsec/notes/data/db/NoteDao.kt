package edu.androidsec.notes.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import edu.androidsec.notes.domain.NoteTitle
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    // Поиск: три готовых запроса, по одному на вариант NoteSort. Строка поиска приходит только
    // параметром :pattern (уже экранированным через escapeForLike), имя колонки в SQL не подставляется.
    // @RawQuery и конкатенация SQL не используются. Порядок по id делает сортировку стабильной.

    @Query(
        "SELECT * FROM notes " +
            "WHERE title LIKE :pattern ESCAPE '\\' OR body LIKE :pattern ESCAPE '\\' " +
            "ORDER BY title COLLATE NOCASE ASC, id ASC",
    )
    fun searchByTitle(pattern: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE title LIKE :pattern ESCAPE '\\' OR body LIKE :pattern ESCAPE '\\' " +
            "ORDER BY updated_at DESC, id DESC",
    )
    fun searchNewestFirst(pattern: String): Flow<List<NoteEntity>>

    @Query(
        "SELECT * FROM notes " +
            "WHERE title LIKE :pattern ESCAPE '\\' OR body LIKE :pattern ESCAPE '\\' " +
            "ORDER BY updated_at ASC, id ASC",
    )
    fun searchOldestFirst(pattern: String): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: NoteEntity): Long

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /**
     * Синхронный запрос заголовков: вызывается из `ContentProvider.query()`,
     * который работает в binder-потоке, а не в главном.
     */
    @Query("SELECT id, title FROM notes ORDER BY id")
    fun titlesBlocking(): List<NoteTitle>
}
