package edu.androidsec.notes.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import edu.androidsec.notes.data.NotesRepository
import edu.androidsec.notes.domain.Note
import edu.androidsec.notes.domain.NoteSort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

class SearchViewModel(
    private val repository: NotesRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _sort = MutableStateFlow(NoteSort.NEWEST)
    val sort: StateFlow<NoteSort> = _sort.asStateFlow()

    /** Результаты поиска; `null` — ещё загружаются. Пустой запрос показывает все заметки. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val results: StateFlow<List<Note>?> =
        combine(_query, _sort) { query, sort -> query to sort }
            .flatMapLatest { (query, sort) -> repository.search(query, sort) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initialValue = null)

    fun onQueryChange(value: String) {
        _query.value = value
    }

    fun onSortChange(value: NoteSort) {
        _sort.value = value
    }
}
