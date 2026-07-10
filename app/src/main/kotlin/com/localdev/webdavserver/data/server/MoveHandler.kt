package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles MOVE — copies the resource to the destination then deletes the
 * source. Implemented as copy+delete because SAF lacks a portable atomic move
 * across arbitrary subtrees.
 */
class MoveHandler(private val saf: SafResolver) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, srcPath: String): HttpStatusCode {
        val destPath = DestinationParser.parse(call.request.headers["Destination"])
        if (destPath == null) {
            call.respondText("400 Bad Request — missing Destination",
                status = HttpStatusCode.BadRequest)
            return HttpStatusCode.BadRequest
        }
        val overwrite = (call.request.headers["Overwrite"] ?: "T").equals("T", true)

        val source = saf.resolve(root, srcPath)
        if (source == null || !source.exists()) {
            call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }

        val destInfo = saf.resolveParent(root, destPath)
        if (destInfo == null) {
            call.respondText("409 Conflict — destination parent missing",
                status = HttpStatusCode.Conflict)
            return HttpStatusCode.Conflict
        }
        val (destParent, destName) = destInfo

        val existing = destParent.findFile(destName)
        if (existing != null && !overwrite) {
            call.respondText("412 Precondition Failed",
                status = HttpStatusCode.PreconditionFailed)
            return HttpStatusCode.PreconditionFailed
        }

        val status = withContext(Dispatchers.IO) {
            if (existing != null) deleteRecursive(existing)
            if (copyInto(source, destParent, destName)) {
                deleteRecursive(source)
                if (existing != null) HttpStatusCode.NoContent else HttpStatusCode.Created
            } else HttpStatusCode.InternalServerError
        }
        call.respondText(if (status.value >= 400) status.description else "", status = status)
        return status
    }

    private fun copyInto(source: DocumentFile, destParent: DocumentFile, newName: String): Boolean {
        return if (source.isDirectory) {
            val newDir = destParent.createDirectory(newName) ?: return false
            source.listFiles().all { child ->
                copyInto(child, newDir, child.name ?: return@all false)
            }
        } else {
            val newFile = saf.createFile(destParent, newName) ?: return false
            saf.openInput(source)?.use { input ->
                saf.openOutput(newFile)?.use { output ->
                    input.copyTo(output)
                    true
                } ?: false
            } ?: false
        }
    }

    private fun deleteRecursive(doc: DocumentFile): Boolean {
        if (doc.isDirectory) doc.listFiles().forEach { deleteRecursive(it) }
        return doc.delete()
    }
}
