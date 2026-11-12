package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import java.io.EOFException
import java.io.IOException
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

        val range = call.request.headers["Range"]?.takeIf { it.startsWith("bytes=") }
            ?.let { parseRange(it, length) }
        val start = range?.first ?: 0L
        val count = range?.let { it.second - it.first + 1 } ?: length
        val responseStatus = if (range == null) HttpStatusCode.OK else HttpStatusCode.PartialContent
        if (range != null) {
            call.response.header("Content-Range", "bytes $start-${range.second}/$length")
        }

        call.respond(object : OutgoingContent.WriteChannelContent() {
            override val contentType = ContentType.parse(mime)
            override val contentLength = count
            override val status = responseStatus

            override suspend fun writeTo(channel: ByteWriteChannel) = withContext(Dispatchers.IO) {
                val input = saf.openInput(doc) ?: throw IOException("Could not open file")
                input.use {
                    var offset = start
                    while (offset > 0) {
                        val skipped = input.skip(offset)
                        if (skipped > 0) {
                            offset -= skipped
                        } else {
                            if (input.read() < 0) throw EOFException("File changed during read")
                            offset--
                        }
                    }

                    val buffer = ByteArray(64 * 1024)
                    var remaining = count
                    while (remaining > 0) {
                        val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (read < 0) throw EOFException("File changed during read")
                        channel.writeFully(buffer, 0, read)
                        onBytesOut(read.toLong())
                        remaining -= read
                    }
                }
            }
        })
        return responseStatus
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
