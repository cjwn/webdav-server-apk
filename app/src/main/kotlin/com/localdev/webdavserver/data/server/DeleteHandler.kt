package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles DELETE — removes a file or collection (recursively).
 */
class DeleteHandler(private val saf: SafResolver) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, path: String): HttpStatusCode {
        val doc = saf.resolve(root, path)
        if (doc == null || !doc.exists()) {
            call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }
        val ok = withContext(Dispatchers.IO) { deleteRecursive(doc) }
        return if (ok) {
            call.respondText("", status = HttpStatusCode.NoContent)
            HttpStatusCode.NoContent
        } else {
            call.respondText("500 Could not delete", status = HttpStatusCode.InternalServerError)
            HttpStatusCode.InternalServerError
        }
    }

    private fun deleteRecursive(doc: DocumentFile): Boolean {
        if (doc.isDirectory) {
            doc.listFiles().forEach { child ->
                if (!deleteRecursive(child)) return false
            }
        }
        return doc.delete()
    }
}
