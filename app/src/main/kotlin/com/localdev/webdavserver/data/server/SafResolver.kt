package com.localdev.webdavserver.data.server

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.localdev.webdavserver.data.local.model.FileNode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Resolves WebDAV / file-browser paths to [DocumentFile] handles.
 *
 * With MANAGE_EXTERNAL_STORAGE granted, the root is a plain filesystem path
 * (e.g. "/storage/emulated/0/Hi") wrapped via [DocumentFile.fromFile], which
 * gives full read/write/list/create/delete on real directories — avoiding the
 * SAF OpenDocumentTree restrictions that block selecting many folders.
 *
 * Legacy SAF tree URIs (content://) are still supported as a fallback.
 */
class SafResolver(private val context: Context) {

    fun rootDocument(rootUri: String): DocumentFile? = documentForUri(rootUri)

    /**
     * Resolves a stored root reference to a browsable [DocumentFile].
     * - A plain path or file:// URI -> DocumentFile.fromFile (preferred).
     * - A content:// tree URI       -> DocumentFile.fromTreeUri (legacy).
     */
    fun documentForUri(uriString: String): DocumentFile? {
        if (uriString.startsWith("content://")) {
            val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
            return runCatching { DocumentFile.fromTreeUri(context, uri) }.getOrNull()
                ?: DocumentFile.fromSingleUri(context, uri)
        }
        val path = if (uriString.startsWith("file://")) Uri.parse(uriString).path else uriString
        val file = path?.let { File(it) } ?: return null
        return DocumentFile.fromFile(file)
    }

    fun hasPersistedPermission(rootUri: String): Boolean {
        // File-based roots rely on all-files access.
        if (!rootUri.startsWith("content://")) {
            return Environment.isExternalStorageManager() || File(filePathOf(rootUri)).canRead()
        }
        val target = runCatching { Uri.parse(rootUri) }.getOrNull() ?: return false
        return context.contentResolver.persistedUriPermissions.any {
            it.uri == target && it.isReadPermission
        }
    }

    private fun filePathOf(ref: String): String =
        if (ref.startsWith("file://")) (Uri.parse(ref).path ?: ref) else ref

    /**
     * Navigates from [root] to the document identified by a WebDAV [path] like
     * "/docs/sub/file.txt". Returns null when any segment is missing.
     */
    fun resolve(root: DocumentFile, path: String): DocumentFile? {
        val segments = splitPath(path) ?: return null
        var current: DocumentFile = root
        for (seg in segments) {
            val child = current.findFile(seg) ?: return null
            current = child
        }
        return current
    }

    /**
     * Resolves the *parent* directory of a path and returns it together with the
     * final segment name (the leaf). Useful for PUT/MKCOL where the leaf may not
     * yet exist.
     */
    fun resolveParent(root: DocumentFile, path: String): Pair<DocumentFile, String>? {
        val segments = splitPath(path) ?: return null
        if (segments.isEmpty()) return null
        var current: DocumentFile = root
        for (i in 0 until segments.size - 1) {
            val child = current.findFile(segments[i]) ?: return null
            if (!child.isDirectory) return null
            current = child
        }
        return current to segments.last()
    }

    fun listChildren(dir: DocumentFile, includeHidden: Boolean): List<FileNode> {
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()
            .asSequence()
            .filter { it.name != null }
            .filter { includeHidden || !(it.name!!.startsWith(".")) }
            .map { it.toNode() }
            .sortedWith(compareByDescending<FileNode> { it.isDirectory }.thenBy { it.name.lowercase() })
            .toList()
    }

    fun DocumentFile.toNode(): FileNode {
        val n = name ?: "?"
        val children = if (isDirectory) runCatching { listFiles().size }.getOrDefault(0) else 0
        return FileNode(
            uri = uri.toString(),
            name = n,
            isDirectory = isDirectory,
            sizeBytes = if (isDirectory) 0L else length(),
            lastModified = lastModified(),
            mimeType = type ?: guessMime(n),
            childCount = children
        )
    }

    fun openInput(doc: DocumentFile): InputStream? {
        val f = fileOf(doc)
        if (f != null) return runCatching { FileInputStream(f) }.getOrNull()
        return runCatching { context.contentResolver.openInputStream(doc.uri) }.getOrNull()
    }

    fun openOutput(doc: DocumentFile, append: Boolean = false): OutputStream? {
        val f = fileOf(doc)
        if (f != null) return runCatching { FileOutputStream(f, append) }.getOrNull()
        return runCatching {
            context.contentResolver.openOutputStream(doc.uri, if (append) "wa" else "wt")
        }.getOrNull()
    }

    /** Returns the backing java.io.File for a file:// DocumentFile, else null. */
    private fun fileOf(doc: DocumentFile): File? {
        val uri = doc.uri
        return if (uri.scheme == "file") uri.path?.let { File(it) } else null
    }

    /**
     * Creates [name] under [parent]. Avoids Android's DocumentFile.createFile
     * double-extension bug (`1.txt` + MIME → `1.txt.txt`) by using direct
     * [File] creation on file:// roots, and stripping a matching extension on
     * SAF trees (the provider re-appends one from the MIME type).
     */
    fun createFile(parent: DocumentFile, name: String): DocumentFile? {
        if (name.isBlank()) return null
        parent.findFile(name)?.let { return it }
        val mime = guessMime(name)

        // file:// (all-files / DocumentFile.fromFile): create exactly [name].
        val parentFile = fileOf(parent)
        if (parentFile != null) {
            val target = File(parentFile, name)
            if (target.exists()) return DocumentFile.fromFile(target)
            return runCatching {
                if (target.createNewFile()) DocumentFile.fromFile(target) else null
            }.getOrNull()
        }

        // SAF tree: createFile(mime, displayName) appends an extension from mime.
        val displayName = displayNameWithoutMimeExtension(name, mime)
        return parent.createFile(mime, displayName)
    }

    /**
     * If [name] already ends with the extension implied by [mime], strip it so
     * DocumentsContract does not produce `name.ext.ext`.
     */
    private fun displayNameWithoutMimeExtension(name: String, mime: String): String {
        val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime) ?: return name
        val suffix = ".$ext"
        return if (name.endsWith(suffix, ignoreCase = true)) {
            name.dropLast(suffix.length).ifEmpty { name }
        } else {
            name
        }
    }

    fun createDirectory(parent: DocumentFile, name: String): DocumentFile? {
        parent.findFile(name)?.let { return if (it.isDirectory) it else null }
        return parent.createDirectory(name)
    }

    companion object {
        /**
         * Splits a WebDAV path into decoded segments. Returns null when the path
         * contains traversal or other illegal segments (`..`, `.`, empty after
         * decode, embedded separators).
         */
        fun splitPath(path: String): List<String>? {
            val segments = path.trim('/')
                .split('/')
                .filter { it.isNotEmpty() }
                .map { Uri.decode(it) }
            if (segments.any { seg ->
                    seg.isEmpty() ||
                        seg == "." ||
                        seg == ".." ||
                        seg.contains('/') ||
                        seg.contains('\\') ||
                        seg.contains('\u0000')
                }
            ) {
                return null
            }
            return segments
        }

        fun guessMime(name: String): String {
            val ext = name.substringAfterLast('.', "").lowercase()
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                ?: "application/octet-stream"
        }
    }
}
