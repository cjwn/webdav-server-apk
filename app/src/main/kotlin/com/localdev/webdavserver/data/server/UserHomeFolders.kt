package com.localdev.webdavserver.data.server

import android.net.Uri
import java.io.File

/**
 * Creates and resolves per-user home folders under the global WebDAV root.
 *
 * Only [Role.USER] accounts get a personal folder (`{root}/{username}`).
 * Admins always see the global root and never receive a personal folder.
 */
object UserHomeFolders {

    /** Safe single path segment from a username. */
    fun sanitizeUsername(username: String): String {
        val cleaned = username.trim()
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('.', ' ')
        return when {
            cleaned.isEmpty() || cleaned == "." || cleaned == ".." -> "user"
            else -> cleaned
        }
    }

    /**
     * Ensures `{globalRoot}/{username}` exists and returns its absolute path,
     * or null when [globalRoot] is missing / not a plain filesystem path.
     */
    fun ensureUserHome(globalRoot: String?, username: String): String? {
        val rootPath = filesystemPath(globalRoot) ?: return null
        val rootDir = File(rootPath)
        if (!rootDir.isDirectory && !rootDir.mkdirs()) return null
        val home = File(rootDir, sanitizeUsername(username))
        if (!home.exists() && !home.mkdirs()) return null
        // Confirm the resolved path stays under the global root (no symlink escape).
        val rootCanon = rootDir.canonicalFile
        val homeCanon = home.canonicalFile
        if (!homeCanon.path.startsWith(rootCanon.path + File.separator) && homeCanon != rootCanon) {
            return null
        }
        return homeCanon.absolutePath
    }

    /**
     * True when [candidate] is the same as or a subdirectory of [globalRoot].
     * Used so a user's stored home cannot point outside the shared root.
     */
    fun isUnderGlobalRoot(globalRoot: String?, candidate: String?): Boolean {
        val rootPath = filesystemPath(globalRoot) ?: return false
        val homePath = filesystemPath(candidate) ?: return false
        return runCatching {
            val rootCanon = File(rootPath).canonicalFile
            val homeCanon = File(homePath).canonicalFile
            homeCanon == rootCanon ||
                homeCanon.path.startsWith(rootCanon.path + File.separator)
        }.getOrDefault(false)
    }

    /**
     * Path of an existing personal folder for [username] under [globalRoot], or null.
     * Used when an account was promoted to admin but their old home folder remains.
     */
    fun existingHomePath(globalRoot: String?, username: String): String? {
        val rootPath = filesystemPath(globalRoot) ?: return null
        val home = File(rootPath, sanitizeUsername(username))
        if (!home.exists()) return null
        return runCatching {
            val rootCanon = File(rootPath).canonicalFile
            val homeCanon = home.canonicalFile
            if (homeCanon == rootCanon) return@runCatching null
            if (!homeCanon.path.startsWith(rootCanon.path + File.separator)) return@runCatching null
            homeCanon.absolutePath
        }.getOrNull()
    }

    /**
     * Best path to delete for a user account: stored [UserEntity.rootFolderUri] when
     * valid, otherwise `{globalRoot}/{username}` if it exists on disk.
     */
    fun homePathForUser(
        globalRoot: String?,
        username: String,
        storedHome: String?
    ): String? {
        if (storedHome != null && isUnderGlobalRoot(globalRoot, storedHome)) {
            val path = filesystemPath(storedHome) ?: return existingHomePath(globalRoot, username)
            val f = File(path)
            if (f.exists()) {
                val rootPath = filesystemPath(globalRoot) ?: return null
                val rootCanon = File(rootPath).canonicalFile
                val homeCanon = f.canonicalFile
                if (homeCanon != rootCanon) return homeCanon.absolutePath
            }
        }
        return existingHomePath(globalRoot, username)
    }

    /**
     * Deletes a personal home folder. Refuses to delete the global root itself
     * or anything outside [globalRoot].
     */
    fun deleteHomeFolder(globalRoot: String?, homePath: String?): Boolean {
        if (homePath.isNullOrBlank()) return false
        if (!isUnderGlobalRoot(globalRoot, homePath)) return false
        val rootPath = filesystemPath(globalRoot) ?: return false
        val path = filesystemPath(homePath) ?: return false
        return runCatching {
            val rootCanon = File(rootPath).canonicalFile
            val homeCanon = File(path).canonicalFile
            if (homeCanon == rootCanon) return@runCatching false
            if (!homeCanon.path.startsWith(rootCanon.path + File.separator)) return@runCatching false
            if (!homeCanon.exists()) return@runCatching true
            homeCanon.deleteRecursively()
        }.getOrDefault(false)
    }

    private fun filesystemPath(ref: String?): String? {
        if (ref.isNullOrBlank()) return null
        if (ref.startsWith("content://")) return null
        return if (ref.startsWith("file://")) {
            Uri.parse(ref).path
        } else {
            ref
        }
    }
}
