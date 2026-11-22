package edu.androidsec.notes.ui.note

import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.R
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.InvalidNoteException
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteRules
import edu.androidsec.notes.photo.PhotoStore
import edu.androidsec.notes.reminder.ReminderScheduler
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Всё, что экран заметки рисует. `noteId == 0` — новая, ещё не сохранённая заметка. */
data class NoteUiState(
    val noteId: Long,
    val isLoading: Boolean = true,
    val photoPath: String? = null,
    /** Признак «секретная»: переключатель появится в пункте 6, а пока значение просто не теряется при сохранении. */
    val isSecret: Boolean = false,
    /** Заметки с таким id нет (например, deep link на удалённую заметку): редактировать нечего. */
    val notFound: Boolean = false,
) {
    val isNew: Boolean get() = noteId == 0L
}

/** Одноразовые события для экрана: показать сообщение или закрыть экран. */
sealed interface NoteEvent {
    data class Message(@param:StringRes val resId: Int) : NoteEvent
    data object Close : NoteEvent
}

class NoteViewModel(
    noteId: Long,
    private val repository: NotesRepository,
    private val photoStore: PhotoStore,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {

    /** Поля ввода: текст живёт в [TextFieldState], правила проверяет [NoteRules]. */
    val title = ValidatedField(NoteRules::checkTitle)
    val body = ValidatedField(NoteRules::checkBody)
    val tags = ValidatedField(NoteRules::checkTags)

    private val _uiState = MutableStateFlow(NoteUiState(noteId = noteId))
    val uiState: StateFlow<NoteUiState> = _uiState.asStateFlow()

    private val _events = Channel<NoteEvent>(Channel.BUFFERED)
    val events: Flow<NoteEvent> = _events.receiveAsFlow()

    init {
        load(noteId)
    }

    private fun load(noteId: Long) {
        if (noteId == 0L) {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        viewModelScope.launch {
            val note = repository.get(noteId)
            if (note == null) {
                // Заметки с таким id нет: экран показывает «Заметка не найдена» и не даёт
                // редактировать несуществующую запись (save() и scheduleReminder() это тоже проверяют).
                _uiState.update { it.copy(isLoading = false, notFound = true) }
                return@launch
            }
            title.set(note.title)
            body.set(note.body)
            tags.set(note.tags)
            _uiState.update {
                it.copy(
                    isLoading = false,
                    photoPath = note.photoPath,
                    isSecret = note.isSecret,
                )
            }
        }
    }

    /** Заметка из текущего содержимого полей. */
    fun currentNote(): Note = Note(
        id = _uiState.value.noteId,
        title = title.text,
        body = body.text,
        tags = tags.text,
        isSecret = _uiState.value.isSecret,
        photoPath = _uiState.value.photoPath,
    )

    fun save() {
        if (_uiState.value.notFound) return
        viewModelScope.launch {
            val id = try {
                repository.save(currentNote())
            } catch (e: InvalidNoteException) {
                // Экран такой ввод не пропускает; сюда попадаем, только если правила обошли.
                Log.w(TAG, "Repository rejected note: ${e.error}")
                _events.send(NoteEvent.Message(R.string.note_invalid))
                return@launch
            }
            _uiState.update { it.copy(noteId = id) }
            _events.send(NoteEvent.Message(R.string.note_saved))
        }
    }

    fun delete() {
        val state = _uiState.value
        viewModelScope.launch {
            if (!state.isNew && !state.notFound) {
                repository.delete(state.noteId)
                _events.send(NoteEvent.Message(R.string.note_deleted))
            }
            _events.send(NoteEvent.Close)
        }
    }

    /** Выбор фото вернул `content://`-URI: копируем файл к себе, право на чтение URI временное. */
    fun onPhotoPicked(uri: Uri) {
        viewModelScope.launch {
            runCatching { photoStore.copyToPrivateStorage(uri) }
                .onSuccess { path -> _uiState.update { it.copy(photoPath = path) } }
                .onFailure {
                    Log.w(TAG, "Cannot store photo", it)
                    _events.send(NoteEvent.Message(R.string.note_photo_failed))
                }
        }
    }

    fun removePhoto() = _uiState.update { it.copy(photoPath = null) }

    /** Вызывается экраном, когда разрешение на уведомления уже получено. */
    fun scheduleReminder() {
        val state = _uiState.value
        if (state.isNew || state.notFound) return
        reminderScheduler.schedule(state.noteId, title.text)
        _events.trySend(NoteEvent.Message(R.string.reminder_scheduled))
    }

    private companion object {
        const val TAG = "NoteViewModel"
    }
}
