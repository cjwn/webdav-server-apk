package com.localdev.webdavserver.domain.usecase

import com.localdev.webdavserver.domain.repository.UserRepository

/**
 * Deletes a user unless it is the last remaining admin.
 */
class DeleteUserUseCase(private val repository: UserRepository) {
    sealed interface Result {
        data object Success : Result
        data class Blocked(val reason: String) : Result
    }

    suspend operator fun invoke(id: String): Result {
        if (repository.isLastAdmin(id)) {
            return Result.Blocked("At least 1 administrator is required")
        }
        repository.deleteUser(id)
        return Result.Success
    }
}
