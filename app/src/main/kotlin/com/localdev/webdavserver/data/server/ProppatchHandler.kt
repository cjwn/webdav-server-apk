package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText

/**
 * Handles PROPPATCH. SAF does not allow setting arbitrary dead properties, so
 * we acknowledge requested property changes with a 207 Multi-Status that reports
 * 200 OK for each property (a common, client-compatible behaviour). Live
 * properties remain read-only.
 */
class ProppatchHandler(private val saf: SafResolver) {

    suspend fun handle(call: ApplicationCall, root: DocumentFile, path: String): HttpStatusCode {
        val doc = saf.resolve(root, path)
        if (doc == null || !doc.exists()) {
            call.respondText("404 Not Found", status = HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }

        val body = runCatching { call.receiveText() }.getOrDefault("")
        val propNames = PROP_REGEX.findAll(body)
            .map { it.groupValues[1] }
            .filter { it.isNotBlank() }
            .toList()

        val propstats = if (propNames.isEmpty()) {
            "      <D:status>HTTP/1.1 200 OK</D:status>\n"
        } else {
            buildString {
                append("      <D:prop>\n")
                propNames.forEach { append("        <$it/>\n") }
                append("      </D:prop>\n")
                append("      <D:status>HTTP/1.1 200 OK</D:status>\n")
            }
        }

        val xml = """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>${WebDavXml.escape(call.request.path())}</D:href>
    <D:propstat>
$propstats    </D:propstat>
  </D:response>
</D:multistatus>"""

        call.respondText(xml, WebDavConstants.XML_UTF8, HttpStatusCode.MultiStatus)
        return HttpStatusCode.MultiStatus
    }

    private companion object {
        val PROP_REGEX = Regex("<(?:\\w+:)?prop>.*?</(?:\\w+:)?prop>", RegexOption.DOT_MATCHES_ALL)
            .let { Regex("<(?:[A-Za-z]+:)?([A-Za-z][\\w-]*)\\s*/?>") }
    }
}
