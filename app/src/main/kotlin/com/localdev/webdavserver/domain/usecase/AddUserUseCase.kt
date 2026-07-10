package com.localdev.webdavserver.domain.usecase

import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.data.server.UserHomeFolders
import com.localdev.webdavserver.domain.repository.SettingsRepository
import com.localdev.webdavserver.domain.repository.UserRepository
import kotlinx.coroutines.flow.first

/**
 * Validates input and creates a new user.
 *
 * Non-admin users get a personal folder under the global root
 * (`{root}/{username}`). Admins never receive a personal folder.
 */
class AddUserUseCase(
    private val repository: UserRepository,
    private val settingsRepository: SettingsRepository
) {
    sealed interface Result {
        data class Success(val user: UserEntity) : Result
        data class Error(val message: String) : Result
    }

    suspend operator fun invoke(
        username: String,
        password: String,
        role: Role,
        readOnly: Boolean,
        rootFolderUri: String?
    ): Result {
        val name = username.trim()
        if (name.isEmpty()) return Result.Error("Username cannot be empty")
        if (password.length < 4) return Result.Error("Password must be at least 4 characters")
        if (repository.snapshot().any { it.username.equals(name, ignoreCase = true) }) {
            return Result.Error("Username already exists")
        }

        val effectiveReadOnly = if (role == Role.ADMIN) false else readOnly
        val home = when {
            role == Role.ADMIN -> null
            rootFolderUri != null -> rootFolderUri
            else -> {
                val global = settingsRepository.config.first().rootFolderUri
                UserHomeFolders.ensureUserHome(global, name)
            }
        }

        val user = repository.createUser(name, password, role, effectiveReadOnly, home)
        return Result.Success(user)
    }
}
