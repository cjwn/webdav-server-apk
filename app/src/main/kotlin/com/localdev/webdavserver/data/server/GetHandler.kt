package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respondOutputStream
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles GET and HEAD for **WebDAV clients** (Basic auth already applied).
 * Browser navigations are handled earlier by [BrowserAccess].
 */
class GetHandler(
    private val saf: SafResolver,
    private val onBytesOut: (Long) -> Unit = {}
) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, path: String): HttpStatusCode {
        val doc = saf.resolve(root, path)
        if (doc == null || !doc.exists()) {
            call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }
        if (doc.isDirectory) {
            call.respondText(
                "WebDAV collection — use a WebDAV client (PROPFIND) to list contents.",
                status = HttpStatusCode.OK
            )
            return HttpStatusCode.OK
        }

        val length = doc.length()
        val mime = doc.type ?: SafResolver.guessMime(doc.name ?: "")
        call.response.header("Accept-Ranges", "bytes")
        call.response.header("Last-Modified", HttpDates.format(doc.lastModified()))

        val isHead = call.request.httpMethod.value.equals("HEAD", true)
        if (isHead) {
            call.response.header("Content-Length", length.toString())
            call.respondText("", ContentType.parse(mime), HttpStatusCode.OK)
            return HttpStatusCode.OK
        }

        val rangeHeader = call.request.headers["Range"]
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            val range = parseRange(rangeHeader, length)
            if (range != null) {
                val (start, end) = range
                call.response.header("Content-Range", "bytes $start-$end/$length")
                call.response.header("Content-Length", (end - start + 1).toString())
                call.respondOutputStream(ContentType.parse(mime), HttpStatusCode.PartialContent) {
                    withContext(Dispatchers.IO) {
                        saf.openInput(doc)?.use { input ->
                            input.skip(start)
                            val buffer = ByteArray(64 * 1024)
                            var remaining = end - start + 1
                            while (remaining > 0) {
                                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                                val read = input.read(buffer, 0, toRead)
                                if (read <= 0) break
                                write(buffer, 0, read)
                                onBytesOut(read.toLong())
                                remaining -= read
                            }
                        }
                    }
                }
                return HttpStatusCode.PartialContent
            }
        }

        call.respondOutputStream(ContentType.parse(mime), HttpStatusCode.OK) {
            withContext(Dispatchers.IO) {
                saf.openInput(doc)?.use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        write(buffer, 0, read)
                        onBytesOut(read.toLong())
                    }
                }
            }
        }
        return HttpStatusCode.OK
    }

    private fun parseRange(header: String, length: Long): Pair<Long, Long>? {
        val spec = header.removePrefix("bytes=").split(",").firstOrNull()?.trim() ?: return null
        val parts = spec.split("-")
        return runCatching {
            val start = parts[0].toLongOrNull()
            val end = parts.getOrNull(1)?.toLongOrNull()
            when {
                start != null && end != null -> start to end.coerceAtMost(length - 1)
                start != null -> start to (length - 1)
                end != null -> (length - end) to (length - 1)
                else -> null
            }?.takeIf { it.first in 0 until length && it.second >= it.first }
        }.getOrNull()
    }
}
