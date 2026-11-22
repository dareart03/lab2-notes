package edu.androidsec.notes.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Запрос на показ снекбара «Заметка удалена»; [remainingMillis] — сколько ещё можно отменить. */
class DeletionRequest(val id: Long, private val deadlineNanos: Long) {
    fun remainingMillis(): Long = ((deadlineNanos - System.nanoTime()) / 1_000_000).coerceAtLeast(0)
}

/**
 * Отложенное удаление: заметка исчезает из интерфейса сразу, но из базы удаляется только через
 * [undoMillis]. До этого момента в базе, в напоминании и в фото ничего не меняется, поэтому:
 *  - «Отменить» просто возвращает заметку на экран, она вернулась целиком;
 *  - если приложение закрыли в эти секунды, процесс умирает вместе с таймером, а заметка
 *    остаётся в базе нетронутой, а не «наполовину удалённой».
 *
 * Реальное удаление выполняется в [scope] приложения, а не в ViewModel экрана: пользователь
 * может уйти с экрана, и удаление всё равно должно дойти до конца.
 */
class PendingDeletions(
    private val repository: NotesRepository,
    private val scope: CoroutineScope,
    private val undoMillis: Long = UNDO_MILLIS,
    /** Вызывается после удаления из базы (например, отменить напоминание). */
    private val onDeleted: (Long) -> Unit = {},
) {
    private val lock = Any()
    private val jobs = HashMap<Long, Job>()

    /**
     * Скрытые в интерфейсе id. После реального удаления id остаётся в наборе: Room не переиспользует
     * номера (AUTOINCREMENT), а так заметка не «мигнёт» на экране до обновления потока Room.
     */
    private val _hidden = MutableStateFlow<Set<Long>>(emptySet())
    val hidden: StateFlow<Set<Long>> = _hidden.asStateFlow()

    private val _requests = Channel<DeletionRequest>(Channel.BUFFERED)

    /** Для снекбара на экране списка. */
    val requests: Flow<DeletionRequest> = _requests.receiveAsFlow()

    fun schedule(id: Long) {
        val request: DeletionRequest
        synchronized(lock) {
            if (jobs.containsKey(id)) return
            _hidden.update { it + id }
            request = DeletionRequest(id, System.nanoTime() + undoMillis * 1_000_000)
            jobs[id] = scope.launch {
                delay(undoMillis)
                // «Захватываем» удаление: после этого undo() вернёт false, гонки нет.
                val claimed = synchronized(lock) { jobs.remove(id) != null }
                if (!claimed) return@launch
                repository.delete(id)
                onDeleted(id)
            }
        }
        _requests.trySend(request)
    }

    /** `true` — заметка возвращена целиком; `false` — удаление уже началось (или не планировалось). */
    fun undo(id: Long): Boolean {
        val job = synchronized(lock) { jobs.remove(id) } ?: return false
        job.cancel()
        _hidden.update { it - id }
        return true
    }

    companion object {
        const val UNDO_MILLIS = 5_000L
    }
}
