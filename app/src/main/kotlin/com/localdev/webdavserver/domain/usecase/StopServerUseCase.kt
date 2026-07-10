package com.localdev.webdavserver.domain.usecase

import android.content.Context
import com.localdev.webdavserver.service.WebDavForegroundService

/**
 * Stops the WebDAV foreground service and the embedded server.
 */
class StopServerUseCase(private val context: Context) {
    operator fun invoke() {
        WebDavForegroundService.stop(context)
    }
}
