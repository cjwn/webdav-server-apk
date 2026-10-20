package com.localdev.webdavserver.presentation.files

import android.net.Uri
import android.os.FileObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.data.local.model.FileNode
import com.localdev.webdavserver.data.server.FileChangeBus
import com.localdev.webdavserver.di.AppModule
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class SortMode { NAME, SIZE, DATE }
enum class ClipMode { COPY, CUT }

data class Clipboard(val node: FileNode, val mode: ClipMode)

data class FilesUiState(
    val rootUri: String? = null,
    val stack: List<Pair<String, String>> = emptyList(), // (uri, name) breadcrumb
    val nodes: List<FileNode> = emptyList(),
    val query: String = "",
    val sort: SortMode = SortMode.NAME,
    val isLoading: Boolean = false,
    val showHidden: Boolean = false,
    val noRoot: Boolean = false,
    val clipboard: Clipboard? = null,
    val message: String? = null
) {
    val filtered: List<FileNode>
        get() {
            val visible = nodes.filter { showHidden || !it.name.startsWith(".") }
            val base = if (query.isBlank()) visible
            else visible.filter { it.name.contains(query, ignoreCase = true) }
            val sorted = when (sort) {
                SortMode.NAME -> base.sortedBy { it.name.lowercase() }
                SortMode.SIZE -> base.sortedByDescending { it.sizeBytes }
                SortMode.DATE -> base.sortedByDescending { it.lastModified }
            }
            return sorted.sortedByDescending { it.isDirectory }
        }

    val breadcrumb: List<String>
        get() = stack.map { it.second }
}

class FilesViewModel internal constructor(
    configs: Flow<ServerConfig>,
    private val browse: suspend (String, Boolean) -> List<FileNode>,
    private val watchFilesystem: Boolean
) : ViewModel() {
    constructor() : this(
        AppModule.settingsRepository.config,
        { uri, hidden -> AppModule.browseFilesUseCase(uri, hidden) },
        true
    )

    private val _state = MutableStateFlow(FilesUiState())
    val state: StateFlow<FilesUiState> = _state.asStateFlow()

    private var loadJob: Job? = null
    private var liveJob: Job? = null
    private var dirObserver: FileObserver? = null

    init {
        // Observe settings continuously so a root folder picked later (in Setup
        // or Settings) is reflected immediately without restarting the screen.
        viewModelScope.launch {
            configs.collect { config ->
                val newRoot = config.rootFolderUri
                val prevRoot = _state.value.rootUri
                _state.update { it.copy(showHidden = config.showHiddenFiles, noRoot = newRoot == null) }

                if (newRoot != prevRoot) {
                    if (newRoot == null) {
                        loadJob?.cancel()
                        stopDirObserver()
                        _state.update {
                            it.copy(
                                rootUri = null,
                                noRoot = true,
                                stack = emptyList(),
                                nodes = emptyList(),
                                isLoading = false
                            )
                        }
                    } else {
                        _state.update {
                            it.copy(
                                rootUri = newRoot,
                                noRoot = false,
                                stack = listOf(newRoot to "Root"),
                                query = ""
                            )
                        }
                        load(newRoot)
                        restartLiveWatch()
                    }
                }
            }
        }
        startLive()
    }

    /** Manual reload of the current directory. */
    fun refresh() {
        val top = _state.value.stack.lastOrNull()?.first ?: _state.value.rootUri ?: return
        load(top)
    }

    fun onQuery(q: String) = _state.update { it.copy(query = q) }

    fun cycleSort() = _state.update {
        it.copy(
            sort = when (it.sort) {
                SortMode.NAME -> SortMode.SIZE
                SortMode.SIZE -> SortMode.DATE
                SortMode.DATE -> SortMode.NAME
            }
        )
    }

    /** Returning to the screen preserves its snapshot and never scans disk. */
    fun onScreenResumed() = Unit

    fun open(node: FileNode) {
        if (!node.isDirectory) return
        _state.update { it.copy(stack = it.stack + (node.uri to node.name), query = "") }
        load(node.uri)
        restartLiveWatch()
    }

    fun navigateTo(index: Int) {
        if (index !in _state.value.stack.indices || index == _state.value.stack.lastIndex) return
        val newStack = _state.value.stack.take(index + 1)
        _state.update { it.copy(stack = newStack, query = "") }
        newStack.lastOrNull()?.let { load(it.first) }
        restartLiveWatch()
    }

    private fun load(uri: String) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val nodes = browse(uri, true)
            _state.update { it.copy(nodes = nodes, isLoading = false) }
        }
    }

    /**
     * Event-driven reload — no timer, spinner or log entries; only updates
     * UI when the directory contents actually changed.
     */
    private suspend fun silentReloadIfChanged() {
        val top = _state.value.stack.lastOrNull()?.first ?: _state.value.rootUri ?: return
        val nodes = browse(top, true)
        val prev = _state.value.nodes
        if (top == currentDir() && !_state.value.isLoading &&
            directorySignature(prev) != directorySignature(nodes)) {
            _state.update { it.copy(nodes = nodes) }
        }
    }

    private fun directorySignature(nodes: List<FileNode>): String =
        nodes.joinToString(separator = "\n") {
            "${it.uri}\t${it.name}\t${it.isDirectory}\t${it.sizeBytes}\t${it.lastModified}\t${it.childCount}"
        }

    @OptIn(FlowPreview::class)
    private fun startLive() {
        liveJob?.cancel()
        liveJob = viewModelScope.launch {
            // Refresh only on actual mutations; coalesce bursts from writes.
            FileChangeBus.events
                .debounce(200)
                .collectLatest { silentReloadIfChanged() }
            // Do not poll directories on a timer. A large Camera directory
            // is expensive to enumerate, even when nothing has changed.
        }
    }

    private fun stopLive() {
        liveJob?.cancel()
        liveJob = null
        stopDirObserver()
    }

    private fun restartLiveWatch() {
        stopDirObserver()
        if (!watchFilesystem) return
        val path = filesystemPath(currentDir()) ?: return
        val dir = File(path)
        if (!dir.isDirectory) return

        val mask = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.MODIFY or
            FileObserver.CLOSE_WRITE or
            FileObserver.ATTRIB or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

        dirObserver = object : FileObserver(dir, mask) {
            override fun onEvent(event: Int, path: String?) {
                // inotify IN_IGNORED (0x8000) is not exposed as FileObserver.IGNORE.
                // Skip empty events and observer-teardown noise.
                if (event == 0 || (event and 0x8000) != 0) return
                FileChangeBus.notifyChanged()
            }
        }.also { it.startWatching() }
    }

    private fun stopDirObserver() {
        runCatching { dirObserver?.stopWatching() }
        dirObserver = null
    }

    private fun filesystemPath(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        if (ref.startsWith("content://")) return null
        return if (ref.startsWith("file://")) Uri.parse(ref).path else ref
    }

    private fun currentDir(): String? =
        _state.value.stack.lastOrNull()?.first ?: _state.value.rootUri

    fun clearMessage() = _state.update { it.copy(message = null) }

    // ---- File operations ----
    fun copy(node: FileNode) =
        _state.update {
            it.copy(clipboard = Clipboard(node, ClipMode.COPY), message = "Copied ${node.name}")
        }

    fun cut(node: FileNode) =
        _state.update {
            it.copy(clipboard = Clipboard(node, ClipMode.CUT), message = "Cut ${node.name}")
        }

    fun paste() {
        val clip = _state.value.clipboard ?: return
        val dir = currentDir() ?: return
        viewModelScope.launch {
            val res = AppModule.fileOperationsUseCase.copyOrMove(
                srcUriOrPath = clip.node.uri,
                destDirUriOrPath = dir,
                move = clip.mode == ClipMode.CUT
            )
            when (res) {
                is com.localdev.webdavserver.domain.usecase.FileOperationsUseCase.OpResult.Success -> {
                    _state.update { it.copy(clipboard = null, message = "Pasted") }
                    load(dir)
                }
                is com.localdev.webdavserver.domain.usecase.FileOperationsUseCase.OpResult.Error ->
                    _state.update { it.copy(message = res.message) }
            }
        }
    }

    fun delete(node: FileNode) {
        viewModelScope.launch {
            val res = AppModule.fileOperationsUseCase.delete(node.uri)
            handle(res, "Deleted ${node.name}")
        }
    }

    fun rename(node: FileNode, newName: String) {
        viewModelScope.launch {
            val res = AppModule.fileOperationsUseCase.rename(node.uri, newName)
            handle(res, "Renamed")
        }
    }

    fun createTextFile(name: String) {
        val dir = currentDir() ?: return
        viewModelScope.launch {
            val res = AppModule.fileOperationsUseCase.createTextFile(dir, name)
            handle(res, "Created $name")
        }
    }

    fun createFolder(name: String) {
        val dir = currentDir() ?: return
        viewModelScope.launch {
            val res = AppModule.fileOperationsUseCase.createFolder(dir, name)
            handle(res, "Created $name")
        }
    }

    private fun handle(
        res: com.localdev.webdavserver.domain.usecase.FileOperationsUseCase.OpResult,
        successMsg: String
    ) {
        when (res) {
            is com.localdev.webdavserver.domain.usecase.FileOperationsUseCase.OpResult.Success -> {
                _state.update { it.copy(message = successMsg) }
                currentDir()?.let { load(it) }
            }
            is com.localdev.webdavserver.domain.usecase.FileOperationsUseCase.OpResult.Error ->
                _state.update { it.copy(message = res.message) }
        }
    }

    override fun onCleared() {
        loadJob?.cancel()
        stopLive()
        super.onCleared()
    }
}
