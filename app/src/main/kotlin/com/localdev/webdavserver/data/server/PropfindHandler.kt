package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.respondText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * HTTP date formatting helpers (RFC 1123 + ISO for creationdate).
 */
object HttpDates {
    private val rfc1123 = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }
    private val iso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("GMT") }

    @Synchronized fun format(millis: Long): String = rfc1123.format(Date(millis))
    @Synchronized fun formatIso(millis: Long): String = iso.format(Date(millis))
}

/**
 * Handles PROPFIND — returns a 207 Multi-Status XML document describing the
 * properties of a resource and (optionally) its children.
 */
class PropfindHandler(private val saf: SafResolver) {

    suspend fun handle(
        call: ApplicationCall,
        root: DocumentFile,
        path: String,
        includeHidden: Boolean
    ): HttpStatusCode {
        val doc = saf.resolve(root, path)
        if (doc == null || !doc.exists()) {
            call.respondText(notFoundXml(call.request.path()),
                WebDavConstants.XML_UTF8, HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }

        val depth = call.request.headers["Depth"] ?: "1"
        val basePath = call.request.path()

        val xml = withContext(Dispatchers.IO) {
            buildString {
                append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
                append("<D:multistatus xmlns:D=\"DAV:\">\n")
                append(responseXml(basePath, doc))
                if (doc.isDirectory && depth != "0") {
                    saf.listChildren(doc, includeHidden).forEach { node ->
                        val childHref = joinHref(basePath, node.name, node.isDirectory)
                        append(nodeResponseXml(childHref, node.isDirectory, node.sizeBytes,
                            node.lastModified, node.mimeType))
                    }
                }
                append("</D:multistatus>\n")
            }
        }

        call.respondText(xml, WebDavConstants.XML_UTF8, HttpStatusCode.MultiStatus)
        return HttpStatusCode.MultiStatus
    }

    private fun responseXml(href: String, doc: DocumentFile): String {
        val isDir = doc.isDirectory
        val normalizedHref = if (isDir && !href.endsWith("/")) "$href/" else href
        return nodeResponseXml(
            normalizedHref, isDir,
            if (isDir) 0 else doc.length(),
            doc.lastModified(),
            doc.type ?: SafResolver.guessMime(doc.name ?: "")
        )
    }

    private fun nodeResponseXml(
        href: String,
        isDir: Boolean,
        size: Long,
        modified: Long,
        mime: String
    ): String {
        val resourceType = if (isDir) "<D:collection/>" else ""
        val contentTypeProp = if (isDir) "" else
            "        <D:getcontenttype>${WebDavXml.escape(mime)}</D:getcontenttype>\n"
        val contentLengthProp = if (isDir) "" else
            "        <D:getcontentlength>$size</D:getcontentlength>\n"
        return """  <D:response>
    <D:href>${WebDavXml.escape(href)}</D:href>
    <D:propstat>
      <D:prop>
        <D:displayname>${WebDavXml.escape(href.trimEnd('/').substringAfterLast('/'))}</D:displayname>
        <D:resourcetype>$resourceType</D:resourcetype>
$contentLengthProp$contentTypeProp        <D:getlastmodified>${HttpDates.format(modified)}</D:getlastmodified>
        <D:creationdate>${HttpDates.formatIso(modified)}</D:creationdate>
        <D:supportedlock>
          <D:lockentry>
            <D:lockscope><D:exclusive/></D:lockscope>
            <D:locktype><D:write/></D:locktype>
          </D:lockentry>
        </D:supportedlock>
      </D:prop>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
"""
    }

    private fun joinHref(base: String, name: String, isDir: Boolean): String {
        val b = base.trimEnd('/')
        val encoded = android.net.Uri.encode(name)
        return "$b/$encoded" + if (isDir) "/" else ""
    }

    private fun notFoundXml(href: String): String =
        """<?xml version="1.0" encoding="utf-8"?>
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>${WebDavXml.escape(href)}</D:href>
    <D:status>HTTP/1.1 404 Not Found</D:status>
  </D:response>
</D:multistatus>"""
}
