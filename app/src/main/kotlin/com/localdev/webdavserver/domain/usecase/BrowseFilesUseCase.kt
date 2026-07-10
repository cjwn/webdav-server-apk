package com.localdev.webdavserver.domain.usecase

import android.content.Context
import com.localdev.webdavserver.data.local.model.FileNode
import com.localdev.webdavserver.data.server.SafResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists files under a SAF directory URI for the in-app file browser.
 */
class BrowseFilesUseCase(context: Context) {
    private val saf = SafResolver(context)

    suspend operator fun invoke(dirUri: String, showHidden: Boolean): List<FileNode> =
        withContext(Dispatchers.IO) {
            val doc = saf.documentForUri(dirUri) ?: return@withContext emptyList()
            saf.listChildren(doc, showHidden)
        }

    fun hasPermission(rootUri: String): Boolean = saf.hasPersistedPermission(rootUri)

    /** Lists subdirectories of a filesystem path for the in-app folder picker. */
    suspend fun listDirectories(path: String): List<java.io.File> = withContext(Dispatchers.IO) {
        val dir = java.io.File(path)
        if (!dir.isDirectory) return@withContext emptyList()
        (dir.listFiles()?.toList() ?: emptyList())
            .filter { it.isDirectory }
            .sortedBy { it.name.lowercase() }
    }
}
