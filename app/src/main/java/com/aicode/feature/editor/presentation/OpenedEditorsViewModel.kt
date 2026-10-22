package com.aicode.feature.editor.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aicode.feature.editor.data.OpenedEditorsStore
import com.aicode.feature.editor.data.displayName
import com.aicode.feature.editor.domain.OpenedEditorTab
import com.aicode.feature.editor.domain.OpenedEditorsState
import com.aicode.feature.workspace.data.repository.WorkspaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Owns the open-editor tab list for the current workspace.
 *
 * Tabs and cursor/scroll snapshots are written to `.aicode/config/opened_editors.json`.
 * Persist is debounced so cursor moves do not hammer disk.
 */
@HiltViewModel
class OpenedEditorsViewModel @Inject constructor(
    private val store: OpenedEditorsStore,
    private val workspaceRepository: WorkspaceRepository
) : ViewModel() {

    private val _state = MutableStateFlow(OpenedEditorsState())
    val state: StateFlow<OpenedEditorsState> = _state.asStateFlow()

    private val mutex = Mutex()
    private var persistJob: Job? = null
    private var loadedWorkspace: String? = null
    private var ready = CompletableDeferred<Unit>()

    /** Latest cursor/scroll snapshot; conflated so scroll bursts collapse into one mutex pass. */
    private data class PendingViewState(
        val path: String,
        val line: Int,
        val column: Int,
        val scrollX: Int,
        val scrollY: Int
    )

    private val pendingViewState = MutableStateFlow<PendingViewState?>(null)
    private var viewStateFlusher: Job? = null

    init {
        viewModelScope.launch {
            reload(workspaceRepository.currentPath())
            workspaceRepository.current.drop(1).collect { workspace ->
                val path = workspace?.path ?: workspaceRepository.currentPath()
                reload(path)
            }
        }
    }

    fun ensureOpen(path: String, initialLine: Int = 0) {
        if (path.isBlank()) return
        viewModelScope.launch {
            awaitReady()
            mutex.withLock {
                // Fold in any pending view state so switching right after a scroll burst
                // restores the newest position, not a pre-flush one.
                applyPendingViewStateLocked()
                val current = _state.value
                val existing = current.tabs.firstOrNull { it.path == path }
                val now = System.currentTimeMillis()
                val nextTabs = if (existing == null) {
                    current.tabs + OpenedEditorTab(
                        path = path,
                        name = fileNameOf(path),
                        cursorLine = initialLine.coerceAtLeast(1),
                        lastOpenedAt = now
                    )
                } else if (initialLine > 0) {
                    current.tabs.map { tab ->
                        if (tab.path == path) tab.copy(
                            cursorLine = initialLine,
                            lastOpenedAt = now
                        ) else tab
                    }
                } else {
                    current.tabs.map { tab ->
                        if (tab.path == path) tab.copy(lastOpenedAt = now) else tab
                    }
                }
                commitLocked(OpenedEditorsState(tabs = nextTabs, activePath = path), persistNow = true)
            }
        }
    }

    fun activate(path: String) {
        if (path.isBlank()) return
        viewModelScope.launch {
            awaitReady()
            mutex.withLock {
                applyPendingViewStateLocked()
                if (path == _state.value.activePath) return@withLock
                if (_state.value.tabs.none { it.path == path }) return@withLock
                commitLocked(_state.value.copy(activePath = path), persistNow = true)
            }
        }
    }

    /**
     * Close [path]. Returns the path that should become active afterwards, or null if none remain.
     */
    fun close(path: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch {
            awaitReady()
            mutex.withLock {
                applyPendingViewStateLocked()
                val current = _state.value
                val index = current.tabs.indexOfFirst { it.path == path }
                if (index < 0) {
                    onResult(current.activePath)
                    return@withLock
                }
                val remaining = current.tabs.filterNot { it.path == path }
                val nextActive = when {
                    remaining.isEmpty() -> null
                    current.activePath == path -> remaining[index.coerceAtMost(remaining.lastIndex)].path
                    else -> current.activePath
                }
                commitLocked(OpenedEditorsState(tabs = remaining, activePath = nextActive), persistNow = true)
                onResult(nextActive)
            }
        }
    }

    fun updateViewState(path: String, line: Int, column: Int, scrollX: Int, scrollY: Int) {
        if (path.isBlank()) return
        // Store-then-flush: the sink overwrites itself per event, and a single delayed flush
        // applies the newest snapshot once per interval instead of once per scroll/selection
        // event. Without this, fling scrolling floods the mutex and recomposes the whole
        // editor screen on every frame.
        pendingViewState.value = PendingViewState(path, line, column, scrollX, scrollY)
        if (viewStateFlusher?.isActive != true) {
            viewStateFlusher = viewModelScope.launch {
                awaitReady()
                delay(VIEW_STATE_FLUSH_MS)
                mutex.withLock { applyPendingViewStateLocked() }
            }
        }
    }

    fun tabFor(path: String): OpenedEditorTab? = _state.value.tabs.firstOrNull { it.path == path }

    /** Drain the conflated snapshot into the tab list; caller must hold [mutex]. */
    private fun applyPendingViewStateLocked() {
        while (true) {
            val pending = pendingViewState.value ?: return
            if (!pendingViewState.compareAndSet(pending, null)) continue
            val current = _state.value
            val index = current.tabs.indexOfFirst { it.path == pending.path }
            if (index < 0) return
            val tab = current.tabs[index]
            if (tab.cursorLine == pending.line &&
                tab.cursorColumn == pending.column &&
                tab.scrollX == pending.scrollX &&
                tab.scrollY == pending.scrollY
            ) {
                return
            }
            val updated = current.tabs.toMutableList()
            updated[index] = tab.copy(
                cursorLine = pending.line.coerceAtLeast(1),
                cursorColumn = pending.column.coerceAtLeast(1),
                scrollX = pending.scrollX.coerceAtLeast(0),
                scrollY = pending.scrollY.coerceAtLeast(0)
            )
            commitLocked(current.copy(tabs = updated), persistNow = false)
            return
        }
    }

    private fun commitLocked(state: OpenedEditorsState, persistNow: Boolean) {
        _state.value = state
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            if (!persistNow) delay(PERSIST_DEBOUNCE_MS)
            store.save(state, loadedWorkspace ?: workspaceRepository.currentPath())
        }
    }

    private suspend fun reload(workspacePath: String) {
        mutex.withLock {
            loadedWorkspace = workspacePath
            _state.value = store.load(workspacePath)
            if (!ready.isCompleted) ready.complete(Unit)
        }
    }

    private suspend fun awaitReady() {
        ready.await()
    }

    private fun fileNameOf(path: String): String =
        path.trimEnd('/').substringAfterLast('/').ifBlank { path }
}

fun OpenedEditorTab.title(dirty: Boolean): String {
    val label = displayName()
    return if (dirty) "*$label" else label
}

private const val PERSIST_DEBOUNCE_MS = 400L

/** Conflation window for cursor/scroll updates; one tab-list write per window at worst. */
private const val VIEW_STATE_FLUSH_MS = 100L
