package com.localdev.webdavserver.data.server

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Lightweight in-process bus for filesystem mutations (WebDAV writes, in-app
 * file ops, [android.os.FileObserver]). Used by the Files "Live" mode to
 * refresh the listing without polling WebDAV or writing log entries.
 */
object FileChangeBus {

    private val _events = MutableSharedFlow<Long>(
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )

    /** Emits a monotonic tick whenever something may have changed on disk. */
    val events: SharedFlow<Long> = _events.asSharedFlow()

    fun notifyChanged() {
        _events.tryEmit(System.currentTimeMillis())
    }
}
