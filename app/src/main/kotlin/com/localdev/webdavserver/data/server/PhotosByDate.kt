package com.localdev.webdavserver.data.server

import androidx.documentfile.provider.DocumentFile
import com.localdev.webdavserver.data.local.model.FileCategory
import com.localdev.webdavserver.data.local.model.FileNode
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Read-only aliases over media in a shared Camera folder. */
class PhotosByDate(private val saf: SafResolver) {
    data class Source(val camera: DocumentFile, val path: String)
    data class Resource(val node: FileNode, val children: List<FileNode>, val originalPath: String? = null)

    private data class Snapshot(
        val cameraUri: String,
        val modified: Long,
        val expiresAt: Long,
        val folders: Map<List<String>, List<FileNode>>
    )

    private var snapshot: Snapshot? = null

    fun source(root: DocumentFile): Source? {
        val uri = root.uri
        if (uri.scheme != "file") return null
        val rootFile = File(uri.path ?: return null)
        if (Files.exists(rootFile.resolve(NAME).toPath(), LinkOption.NOFOLLOW_LINKS)) return null
        val path = if (rootFile.name == "Camera") "" else "/Camera"
        val camera = saf.resolve(root, path)?.takeIf { it.isDirectory } ?: return null
        return Source(camera, path)
    }

    fun rootNode() = folder(NAME)

    fun matches(segments: List<String>?) = segments?.firstOrNull() == NAME

    @Synchronized
    fun preload(root: DocumentFile, isActive: () -> Boolean) {
        if (!isActive()) return
        source(root)?.let { index(it) }
    }

    fun resolve(source: Source, path: List<String>): Resource? {
        if (path.isEmpty()) return Resource(rootNode(), index(source).getValue(emptyList()))
        if (path.size > 4 || path.take(3).any { !DATE_COMPONENT.matches(it) }) return null
        val folders = index(source)
        if (path.size <= 3) return folders[path]?.let { Resource(folder(path.last()), it) }
        val node = folders[path.dropLast(1)]?.firstOrNull { it.name == path.last() } ?: return null
        val originalPath = source.path + "/" + android.net.Uri.encode(node.name)
        return Resource(node, emptyList(), originalPath)
    }

    fun timeline(source: Source): List<PhotoDay> =
        index(source).entries.asSequence()
            .filter { it.key.size == 3 }
            .sortedByDescending { it.key.joinToString("-") }
            .map { PhotoDay(it.key, it.value) }
            .toList()

    data class PhotoDay(val path: List<String>, val files: List<FileNode>)

    @Synchronized
    fun clear() {
        snapshot = null
    }

    @Synchronized
    private fun index(source: Source): Map<List<String>, List<FileNode>> {
        val key = source.camera.uri.toString()
        val modified = source.camera.lastModified()
        val now = System.nanoTime()
        snapshot?.let {
            if (it.cameraUri == key && it.modified == modified && now < it.expiresAt) return it.folders
        }

        val folders = mutableMapOf<List<String>, List<FileNode>>()
        val byDay = saf.listChildrenForPropfind(source.camera, includeHidden = false)
            .filter { it.category == FileCategory.IMAGE || it.category == FileCategory.VIDEO }
            .groupBy { dateOf(it).toString().split('-') }
        folders.putAll(byDay)

        var paths = byDay.keys.toList()
        repeat(3) {
            val parents = paths.groupBy { it.dropLast(1) }
            for ((parent, children) in parents) {
                folders[parent] = children.map { folder(it.last()) }.sortedByDescending { it.name }
            }
            paths = parents.keys.toList()
        }
        folders.putIfAbsent(emptyList(), emptyList())
        snapshot = Snapshot(key, modified, System.nanoTime() + CACHE_TTL_NANOS, folders)
        return folders
    }

    private fun dateOf(node: FileNode): LocalDate {
        val date = DATE_IN_NAME.find(node.name)?.value
        if (date != null) {
            runCatching { LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()?.let { return it }
        }
        return Instant.ofEpochMilli(node.lastModified).atZone(ZoneId.systemDefault()).toLocalDate()
    }

    private fun folder(name: String) = FileNode(VIRTUAL_URI, name, true, 0, 0, "inode/directory")

    companion object {
        const val NAME = "Photos by Date"
        const val VIRTUAL_URI = "virtual:photos-by-date"
        private const val CACHE_TTL_NANOS = 60_000_000_000L
        private val DATE_IN_NAME = Regex("(?<![0-9])20[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])")
        private val DATE_COMPONENT = Regex("[0-9]{2,4}")
    }
}
