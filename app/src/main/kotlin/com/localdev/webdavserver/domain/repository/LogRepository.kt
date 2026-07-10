package com.localdev.webdavserver.domain.repository

import com.localdev.webdavserver.data.local.model.LogEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/**
 * In-memory ring buffer of WebDAV request logs, exposed reactively.
 * Capped at [MAX_ENTRIES] to bound memory.
 */
class LogRepository {

    private val idGen = AtomicLong(0)
    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    private val _unreadErrors = MutableStateFlow(0)
    val unreadErrors: StateFlow<Int> = _unreadErrors.asStateFlow()

    @Synchronized
    fun log(
        method: String,
        path: String,
        username: String,
        statusCode: Int,
        statusMessage: String,
        ip: String = "-"
    ) {
        val entry = LogEntry(
            id = idGen.incrementAndGet(),
            timestampMillis = System.currentTimeMillis(),
            method = method,
            path = path,
            username = username,
            statusCode = statusCode,
            statusMessage = statusMessage,
            ip = ip
        )
        val updated = (_entries.value + entry).takeLast(MAX_ENTRIES)
        _entries.value = updated
        if (entry.isError) _unreadErrors.value += 1
    }

    fun clear() {
        _entries.value = emptyList()
        _unreadErrors.value = 0
    }

    fun markErrorsRead() {
        _unreadErrors.value = 0
    }

    companion object {
        const val MAX_ENTRIES = 1000
    }
}
