package com.localdev.webdavserver.domain.usecase

import android.content.Context
import com.localdev.webdavserver.service.WebDavForegroundService

/**
 * Starts the WebDAV foreground service (which starts the Ktor engine).
 */
class StartServerUseCase(private val context: Context) {
    operator fun invoke() {
        WebDavForegroundService.start(context)
    }
}
