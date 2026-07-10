package com.localdev.webdavserver.domain.usecase

import com.localdev.webdavserver.data.server.FileChangeBus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Filesystem operations for the in-app file manager. Uses direct java.io.File
 * access (the app holds MANAGE_EXTERNAL_STORAGE), so it works on any path,
 * including file:// URIs produced by the browser.
 */
class FileOperationsUseCase {

    /** Result of an operation. */
    sealed interface OpResult {
        data object Success : OpResult
        data class Error(val message: String) : OpResult
    }

    private fun toFile(uriOrPath: String): File {
        val path = when {
            uriOrPath.startsWith("file://") -> android.net.Uri.parse(uriOrPath).path ?: uriOrPath
            else -> uriOrPath
        }
        return File(path)
    }

    private fun ok(result: OpResult): OpResult {
        if (result is OpResult.Success) FileChangeBus.notifyChanged()
        return result
    }

    suspend fun delete(uriOrPath: String): OpResult = withContext(Dispatchers.IO) {
        ok(
            runCatching {
                val f = toFile(uriOrPath)
                if (f.isDirectory) f.deleteRecursively() else f.delete()
            }.fold(
                onSuccess = { if (it) OpResult.Success else OpResult.Error("Could not delete") },
                onFailure = { OpResult.Error(it.message ?: "Delete failed") }
            )
        )
    }

    suspend fun rename(uriOrPath: String, newName: String): OpResult = withContext(Dispatchers.IO) {
        ok(
            runCatching {
                val f = toFile(uriOrPath)
                val target = File(f.parentFile, newName.trim())
                if (target.exists()) return@runCatching false
                f.renameTo(target)
            }.fold(
                onSuccess = { if (it) OpResult.Success else OpResult.Error("Could not rename") },
                onFailure = { OpResult.Error(it.message ?: "Rename failed") }
            )
        )
    }

    /** Copies (or moves when [move] is true) a file/dir into [destDirUriOrPath]. */
    suspend fun copyOrMove(
        srcUriOrPath: String,
        destDirUriOrPath: String,
        move: Boolean
    ): OpResult = withContext(Dispatchers.IO) {
        runCatching {
            val src = toFile(srcUriOrPath)
            val destDir = toFile(destDirUriOrPath)
            if (!destDir.isDirectory) return@runCatching false
            var target = File(destDir, src.name)
            // Avoid overwriting: append " (copy)" / counter.
            if (target.exists() && !move) {
                val base = src.nameWithoutExtension
                val ext = if (src.extension.isEmpty()) "" else ".${src.extension}"
                var i = 1
                while (target.exists()) {
                    target = File(destDir, "$base ($i)$ext"); i++
                }
            }
            // Prevent moving a directory into itself (or a subfolder of itself).
            val srcPath = src.canonicalFile.absolutePath
            val destPath = destDir.canonicalFile.absolutePath
            if (src.isDirectory &&
                (destPath == srcPath || destPath.startsWith(srcPath + File.separator))
            ) {
                return@runCatching false
            }
            val ok = if (src.isDirectory) {
                src.copyRecursively(target, overwrite = false)
            } else {
                src.copyTo(target, overwrite = false); true
            }
            if (ok && move) {
                if (src.isDirectory) src.deleteRecursively() else src.delete()
            }
            ok
        }.fold(
            onSuccess = { if (it) OpResult.Success else OpResult.Error("Operation failed") },
            onFailure = { OpResult.Error(it.message ?: "Operation failed") }
        ).let { ok(it) }
    }

    suspend fun createTextFile(dirUriOrPath: String, name: String, content: String = ""):
        OpResult = withContext(Dispatchers.IO) {
        ok(
            runCatching {
                val dir = toFile(dirUriOrPath)
                // Keep the name exactly as typed (do not append another .txt).
                val fileName = name.trim().ifEmpty { return@runCatching false }
                val f = File(dir, fileName)
                if (f.exists()) return@runCatching false
                f.writeText(content)
                true
            }.fold(
                onSuccess = { if (it) OpResult.Success else OpResult.Error("File already exists") },
                onFailure = { OpResult.Error(it.message ?: "Could not create file") }
            )
        )
    }

    suspend fun createFolder(dirUriOrPath: String, name: String): OpResult =
        withContext(Dispatchers.IO) {
            ok(
                runCatching {
                    val dir = toFile(dirUriOrPath)
                    val f = File(dir, name.trim())
                    if (f.exists()) false else f.mkdirs()
                }.fold(
                    onSuccess = { if (it) OpResult.Success else OpResult.Error("Could not create folder") },
                    onFailure = { OpResult.Error(it.message ?: "Create failed") }
                )
            )
        }

    /** Read file as UTF-8 text for the in-app read-only viewer (txt/md). */
    suspend fun readText(uriOrPath: String, maxBytes: Long = 2_000_000): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val f = toFile(uriOrPath)
                if (!f.isFile) return@runCatching null
                if (f.length() > maxBytes) {
                    f.inputStream().use { input ->
                        val buf = ByteArray(maxBytes.toInt())
                        val n = input.read(buf)
                        if (n <= 0) "" else String(buf, 0, n, Charsets.UTF_8)
                    }
                } else {
                    f.readText(Charsets.UTF_8)
                }
            }.getOrNull()
        }

    /** Absolute filesystem path for a node uri/path (for media players/viewers). */
    fun absolutePath(uriOrPath: String): String = toFile(uriOrPath).absolutePath
}
