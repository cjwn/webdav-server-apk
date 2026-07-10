package com.localdev.webdavserver.data.local.model

/**
 * A file or directory node exposed by the SAF browser and WebDAV engine.
 */
data class FileNode(
    val uri: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val lastModified: Long,
    val mimeType: String,
    val childCount: Int = 0
) {
    val category: FileCategory
        get() = when {
            isDirectory -> FileCategory.FOLDER
            mimeType.startsWith("image/") -> FileCategory.IMAGE
            mimeType.startsWith("audio/") -> FileCategory.AUDIO
            mimeType.startsWith("video/") -> FileCategory.VIDEO
            mimeType in ARCHIVE_TYPES ||
                name.substringAfterLast('.', "").lowercase() in ARCHIVE_EXTS -> FileCategory.ARCHIVE
            else -> FileCategory.FILE
        }

    companion object {
        private val ARCHIVE_TYPES = setOf(
            "application/zip", "application/x-tar", "application/gzip",
            "application/x-7z-compressed", "application/x-rar-compressed"
        )
        private val ARCHIVE_EXTS = setOf("zip", "tar", "gz", "7z", "rar", "bz2", "xz")
    }
}

enum class FileCategory {
    FOLDER, FILE, IMAGE, AUDIO, VIDEO, ARCHIVE
}
