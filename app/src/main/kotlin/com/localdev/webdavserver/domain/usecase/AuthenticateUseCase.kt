package com.localdev.webdavserver.domain.usecase

import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.domain.repository.UserRepository

/**
 * Verifies credentials against the user store (constant-time).
 */
class AuthenticateUseCase(private val repository: UserRepository) {
    operator fun invoke(username: String, password: String): UserEntity? =
        repository.authenticate(username, password)
}
