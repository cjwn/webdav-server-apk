package com.localdev.webdavserver.data.local.model

/**
 * Role of a WebDAV user.
 */
enum class Role {
    ADMIN,
    USER
}

/**
 * A WebDAV account. Passwords are never stored in plain text — only a
 * SHA-256(salt + password) hash plus the random salt are persisted.
 */
data class UserEntity(
    val id: String,
    val username: String,
    val passwordHash: String,    // SHA-256 of (salt + password), hex encoded
    val salt: String,            // random salt, hex encoded
    val role: Role,
    val readOnly: Boolean,
    val rootFolderUri: String?   // overrides global root if set
) {
    val initials: String
        get() {
            val parts = username.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
            return when {
                parts.isEmpty() -> "?"
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
            }
        }
}
