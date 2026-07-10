package com.localdev.webdavserver.domain.repository

import com.localdev.webdavserver.data.local.datastore.UserPreferencesDataStore
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.data.local.model.UserEntity
import kotlinx.coroutines.flow.StateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

/**
 * Manages users and credential verification.
 */
class UserRepository(private val store: UserPreferencesDataStore) {

    val users: StateFlow<List<UserEntity>> = store.users

    fun snapshot(): List<UserEntity> = store.snapshot()

    fun adminCount(): Int = store.snapshot().count { it.role == Role.ADMIN }

    fun isLastAdmin(id: String): Boolean {
        val target = store.snapshot().firstOrNull { it.id == id } ?: return false
        return target.role == Role.ADMIN && adminCount() <= 1
    }

    suspend fun createUser(
        username: String,
        password: String,
        role: Role,
        readOnly: Boolean,
        rootFolderUri: String?
    ): UserEntity {
        val salt = randomSaltHex()
        val hash = hash(salt, password)
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            username = username,
            passwordHash = hash,
            salt = salt,
            role = role,
            // Admins never use read-only.
            readOnly = if (role == Role.ADMIN) false else readOnly,
            rootFolderUri = if (role == Role.ADMIN) null else rootFolderUri
        )
        store.upsert(user)
        return user
    }

    /**
     * Updates an existing user. If [newPassword] is non-null, a new salt+hash
     * pair is generated.
     *
     * Returns null when the update would remove the last administrator
     * (demote or otherwise leave zero admins).
     */
    suspend fun updateUser(
        existing: UserEntity,
        username: String,
        newPassword: String?,
        role: Role,
        readOnly: Boolean,
        rootFolderUri: String?
    ): UserEntity? {
        // Cannot demote the only admin — at least one admin must remain.
        if (existing.role == Role.ADMIN && role != Role.ADMIN && isLastAdmin(existing.id)) {
            return null
        }
        val (salt, hash) = if (newPassword.isNullOrEmpty()) {
            existing.salt to existing.passwordHash
        } else {
            val s = randomSaltHex()
            s to hash(s, newPassword)
        }
        val updated = existing.copy(
            username = username,
            passwordHash = hash,
            salt = salt,
            role = role,
            readOnly = if (role == Role.ADMIN) false else readOnly,
            // Admins always use the global root (no personal folder).
            rootFolderUri = if (role == Role.ADMIN) null else rootFolderUri
        )
        store.upsert(updated)
        return updated
    }

    suspend fun deleteUser(id: String) {
        if (isLastAdmin(id)) return // blocked: cannot remove last admin
        store.delete(id)
    }

    /**
     * Verifies username/password using a constant-time comparison.
     * Returns the matching user or null.
     */
    fun authenticate(username: String, password: String): UserEntity? {
        val user = store.findByUsername(username) ?: return null
        val candidate = hash(user.salt, password)
        return if (constantTimeEquals(candidate, user.passwordHash)) user else null
    }

    companion object {
        fun randomSaltHex(): String {
            val bytes = ByteArray(16)
            SecureRandom().nextBytes(bytes)
            return bytes.toHex()
        }

        fun hash(salt: String, password: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(salt.toByteArray(Charsets.UTF_8))
            md.update(password.toByteArray(Charsets.UTF_8))
            return md.digest().toHex()
        }

        fun constantTimeEquals(a: String, b: String): Boolean {
            val ab = a.toByteArray(Charsets.UTF_8)
            val bb = b.toByteArray(Charsets.UTF_8)
            if (ab.size != bb.size) return false
            var result = 0
            for (i in ab.indices) result = result or (ab[i].toInt() xor bb[i].toInt())
            return result == 0
        }

        private fun ByteArray.toHex(): String =
            joinToString("") { "%02x".format(it) }
    }
}
