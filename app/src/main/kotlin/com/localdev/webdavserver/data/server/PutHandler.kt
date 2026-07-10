package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respondText
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles PUT — create or overwrite a file with the request body.
 * [onBytesIn] is invoked with the number of body bytes received, driving the
 * live "upload" counter.
 */
class PutHandler(
    private val saf: SafResolver,
    private val onBytesIn: (Long) -> Unit = {}
) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, path: String): HttpStatusCode {
        val parentInfo = saf.resolveParent(root, path)
        if (parentInfo == null) {
            call.respondText("409 Conflict — parent collection missing",
                status = HttpStatusCode.Conflict)
            return HttpStatusCode.Conflict
        }
        val (parent, name) = parentInfo

        val existing = parent.findFile(name)
        if (existing != null && existing.isDirectory) {
            call.respondText("405 Method Not Allowed — target is a collection",
                status = HttpStatusCode.MethodNotAllowed)
            return HttpStatusCode.MethodNotAllowed
        }

        val target: DocumentFile = existing ?: saf.createFile(parent, name) ?: run {
            call.respondText("500 Could not create file",
                status = HttpStatusCode.InternalServerError)
            return HttpStatusCode.InternalServerError
        }

        val created = existing == null
        val channel = call.receiveChannel()
        withContext(Dispatchers.IO) {
            saf.openOutput(target, append = false)?.use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = channel.readAvailable(buffer, 0, buffer.size)
                    if (read == -1) break
                    if (read > 0) {
                        out.write(buffer, 0, read)
                        onBytesIn(read.toLong())
                    }
                }
                out.flush()
            }
        }

        val status = if (created) HttpStatusCode.Created else HttpStatusCode.NoContent
        call.respondText("", status = status)
        return status
    }
}
