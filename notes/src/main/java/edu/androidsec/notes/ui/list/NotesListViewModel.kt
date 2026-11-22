package edu.androidsec.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.data.PendingDeletions
import edu.androidsec.notes.domain.Note
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class NotesListViewModel(
    repository: NotesRepository,
    private val pendingDeletions: PendingDeletions,
) : ViewModel() {

    /** Список заметок из Room без тех, что ждут удаления; `null` — список ещё загружается. */
    val notes: StateFlow<List<Note>?> = combine(repository.observeAll(), pendingDeletions.hidden) { list, hidden ->
        list.filterNot { it.id in hidden }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    /** Запросы на показ снекбара «Заметка удалена». */
    val deleteRequests = pendingDeletions.requests

    /** Не удаляет сразу: заметка скрывается, а из базы пропадёт через 5 секунд (см. [PendingDeletions]). */
    fun delete(id: Long) = pendingDeletions.schedule(id)

    fun undo(id: Long) = pendingDeletions.undo(id)
}
