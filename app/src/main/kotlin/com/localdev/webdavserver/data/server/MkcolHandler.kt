package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles MKCOL — create a new collection (directory).
 */
class MkcolHandler(private val saf: SafResolver) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, path: String): HttpStatusCode {
        // MKCOL with a body is unsupported.
        val body = runCatching { call.receiveText() }.getOrDefault("")
        if (body.isNotBlank()) {
            call.respondText("415 Unsupported Media Type",
                status = HttpStatusCode.UnsupportedMediaType)
            return HttpStatusCode.UnsupportedMediaType
        }

        val parentInfo = saf.resolveParent(root, path)
        if (parentInfo == null) {
            call.respondText("409 Conflict — parent collection missing",
                status = HttpStatusCode.Conflict)
            return HttpStatusCode.Conflict
        }
        val (parent, name) = parentInfo

        if (parent.findFile(name) != null) {
            call.respondText("405 Method Not Allowed — already exists",
                status = HttpStatusCode.MethodNotAllowed)
            return HttpStatusCode.MethodNotAllowed
        }

        val created = withContext(Dispatchers.IO) { saf.createDirectory(parent, name) }
        return if (created != null) {
            call.respondText("", status = HttpStatusCode.Created)
            HttpStatusCode.Created
        } else {
            call.respondText("500 Could not create collection",
                status = HttpStatusCode.InternalServerError)
            HttpStatusCode.InternalServerError
        }
    }
}
