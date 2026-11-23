package edu.androidsec.notes.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import edu.androidsec.notes.R
import edu.androidsec.notes.appContainer
import edu.androidsec.notes.domain.NoteSort
import edu.androidsec.notes.ui.list.NoteRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenNote: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val container = LocalContext.current.appContainer
    val viewModel: SearchViewModel = viewModel { SearchViewModel(container.notesRepository, container.pendingDeletions) }
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.search_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.note_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Три одинаковые по ширине ячейки, подпись в каждой в две строки.
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteSort.entries.forEach { option ->
                    FilterChip(
                        selected = sort == option,
                        onClick = { viewModel.onSortChange(option) },
                        label = {
                            Text(
                                text = stringResource(option.labelRes()).replaceFirst(' ', '\n'),
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val list = results
            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))

                    list.isEmpty() -> Text(
                        text = stringResource(R.string.search_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                    )

                    else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(list, key = { it.id }) { note ->
                            NoteRow(note = note, onClick = { onOpenNote(note.id) })
                        }
                    }
                }
            }
        }
    }
}

private fun NoteSort.labelRes(): Int = when (this) {
    NoteSort.TITLE -> R.string.sort_title
    NoteSort.NEWEST -> R.string.sort_newest
    NoteSort.OLDEST -> R.string.sort_oldest
}
