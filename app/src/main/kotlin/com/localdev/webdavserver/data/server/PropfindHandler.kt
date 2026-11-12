package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import android.util.Log
import com.localdev.webdavserver.data.local.model.FileNode
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.xml.namespace.QName
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.SAXException
import org.xml.sax.ext.DefaultHandler2

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
class PropfindHandler(
    private val saf: SafResolver,
    private val listingDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun handle(
        call: ApplicationCall,
        root: DocumentFile,
        path: String,
        includeHidden: Boolean,
        extraChild: FileNode? = null,
        excludedChild: String? = null
    ): HttpStatusCode {
        val doc = saf.resolve(root, path)
        if (doc == null || !doc.exists()) {
            call.respondText(notFoundXml(call.request.path()),
                WebDavConstants.XML_UTF8, HttpStatusCode.NotFound)
            return HttpStatusCode.NotFound
        }

        return respondListing(call, {
            val isDirectory = doc.isDirectory
            FileNode("", doc.name.orEmpty(), isDirectory, if (isDirectory) 0 else doc.length(),
                doc.lastModified(), if (isDirectory) "" else doc.type ?: SafResolver.guessMime(doc.name.orEmpty()))
        }, {
            val children = saf.listChildrenForPropfind(doc, includeHidden)
                .filterNot { excludedChild?.equals(it.name, ignoreCase = true) == true }
            if (extraChild == null) children else children + extraChild
        })
    }

    suspend fun handleVirtual(call: ApplicationCall, node: FileNode, children: List<FileNode>): HttpStatusCode =
        respondListing(call, { node }, { children }, readOnly = true)

    private suspend fun respondListing(
        call: ApplicationCall,
        resource: () -> FileNode,
        children: () -> List<FileNode>,
        readOnly: Boolean = false
    ): HttpStatusCode {
        // A bodyless HTTP/1.1 request can omit both framing headers.
        val body = if (call.request.headers["Content-Length"] != null ||
            call.request.headers["Transfer-Encoding"] != null
        ) {
            call.receiveChannel().readRemaining(65_537).readBytes()
        } else {
            ByteArray(0)
        }
        if (body.size > 65_536) {
            call.respondText("PROPFIND body too large", status = HttpStatusCode.PayloadTooLarge)
            return HttpStatusCode.PayloadTooLarge
        }
        val properties = try {
            requestedProperties(body)
        } catch (_: SAXException) {
            call.respondText("Invalid PROPFIND XML", status = HttpStatusCode.BadRequest)
            return HttpStatusCode.BadRequest
        }

        val depth = call.request.headers["Depth"] ?: "1"
        val basePath = call.request.path()

        val startedNs = System.nanoTime()
        var listingMs = 0L
        var listedEntries = 0
        val xml = try {
            withContext(listingDispatcher) {
                buildString {
                    append("<?xml version=\"1.0\" encoding=\"utf-8\"?><D:multistatus xmlns:D=\"DAV:\">")
                    val node = resource()
                    val href = if (node.isDirectory && !basePath.endsWith("/")) "$basePath/" else basePath
                    appendResponse(href, node, properties, readOnly)
                    if (node.isDirectory && depth != "0") {
                        val listStartNs = System.nanoTime()
                        val nodes = children()
                        listingMs = (System.nanoTime() - listStartNs) / 1_000_000
                        listedEntries = nodes.size
                        nodes.forEach { child ->
                            appendResponse(joinHref(basePath, child.name, child.isDirectory), child, properties,
                                readOnly || child.uri == PhotosByDate.VIRTUAL_URI)
                        }
                    }
                    append("</D:multistatus>")
                }
            }
        } catch (_: IOException) {
            call.respondText("Could not read directory", status = HttpStatusCode.InternalServerError)
            return HttpStatusCode.InternalServerError
        }

        val buildMs = (System.nanoTime() - startedNs) / 1_000_000
        // Response headers remain visible in curl even if vivo suppresses Logcat.
        // These metrics cover building the XML, not network transfer/Finder parsing.
        call.response.header("X-WebDAV-Entries", listedEntries.toString())
        call.response.header("X-WebDAV-Listing-Ms", listingMs.toString())
        call.response.header("X-WebDAV-Build-Ms", buildMs.toString())
        call.response.header("X-WebDAV-Xml-Chars", xml.length.toString())
        Log.i("WebDavPerf", "PROPFIND path=$basePath depth=$depth items=$listedEntries " +
            "listMs=$listingMs buildMs=$buildMs xmlChars=${xml.length}")
        call.respondText(xml, WebDavConstants.XML_UTF8, HttpStatusCode.MultiStatus)
        return HttpStatusCode.MultiStatus
    }

    private fun requestedProperties(body: ByteArray): Set<QName>? {
        if (body.all { it.toInt().toChar().isWhitespace() }) return null
        var depth = 0
        var explicitProperties = false
        val properties = linkedSetOf<QName>()
        val handler = object : DefaultHandler2() {
            override fun startDTD(name: String?, publicId: String?, systemId: String?) {
                throw SAXException("DOCTYPE is not allowed")
            }

            override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
                depth++
                when (depth) {
                    1 -> if (uri != "DAV:" || localName != "propfind") {
                        throw SAXException("Expected DAV:propfind")
                    }
                    2 -> explicitProperties = uri == "DAV:" && localName == "prop"
                    3 -> if (explicitProperties) properties.add(QName(uri, localName))
                }
            }

            override fun endElement(uri: String, localName: String, qName: String) {
                depth--
            }
        }
        val parser = SAXParserFactory.newInstance().apply { isNamespaceAware = true }.newSAXParser()
        parser.setProperty("http://xml.org/sax/properties/lexical-handler", handler)
        parser.parse(body.inputStream(), handler)
        return if (explicitProperties) properties else null
    }

    private fun StringBuilder.appendResponse(
        href: String,
        node: FileNode,
        requested: Set<QName>?,
        readOnly: Boolean
    ) {
        append("<D:response><D:href>").append(WebDavXml.escape(href)).append("</D:href>")
        val properties = requested ?: DEFAULT_PROPERTIES
        val found = properties.filter { it in DEFAULT_PROPERTIES && (!node.isDirectory || it !in FILE_PROPERTIES) && (!readOnly || it.localPart != "supportedlock") }
        if (found.isNotEmpty()) {
            append("<D:propstat><D:prop>")
            for (property in found) {
                when (property.localPart) {
                    "displayname" -> appendProperty("displayname", WebDavXml.escape(href.trimEnd('/').substringAfterLast('/')))
                    "resourcetype" -> appendProperty("resourcetype", if (node.isDirectory) "<D:collection/>" else "")
                    "getcontentlength" -> appendProperty("getcontentlength", node.sizeBytes.toString())
                    "getcontenttype" -> appendProperty("getcontenttype", WebDavXml.escape(node.mimeType))
                    "getlastmodified" -> appendProperty("getlastmodified", HttpDates.format(node.lastModified))
                    "creationdate" -> appendProperty("creationdate", HttpDates.formatIso(node.lastModified))
                    "supportedlock" -> appendProperty(
                        "supportedlock",
                        "<D:lockentry><D:lockscope><D:exclusive/></D:lockscope><D:locktype><D:write/></D:locktype></D:lockentry>"
                    )
                }
            }
            append("</D:prop><D:status>HTTP/1.1 200 OK</D:status></D:propstat>")
        }
        val missing = requested?.minus(found.toSet()).orEmpty()
        if (missing.isNotEmpty()) {
            append("<D:propstat><D:prop>")
            for (property in missing) {
                append('<').append(property.localPart).append(" xmlns=\"")
                append(WebDavXml.escape(property.namespaceURI)).append("\"/>")
            }
            append("</D:prop><D:status>HTTP/1.1 404 Not Found</D:status></D:propstat>")
        }
        append("</D:response>")
    }

    private fun StringBuilder.appendProperty(name: String, value: String) {
        append("<D:").append(name).append('>').append(value).append("</D:").append(name).append('>')
    }

    private companion object {
        val FILE_PROPERTIES = setOf(QName("DAV:", "getcontentlength"), QName("DAV:", "getcontenttype"))
        val DEFAULT_PROPERTIES = setOf(
            QName("DAV:", "displayname"), QName("DAV:", "resourcetype"),
            QName("DAV:", "getlastmodified"), QName("DAV:", "creationdate"), QName("DAV:", "supportedlock")
        ) + FILE_PROPERTIES
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
