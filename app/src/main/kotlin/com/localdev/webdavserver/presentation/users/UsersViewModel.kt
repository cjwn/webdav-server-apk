package com.localdev.webdavserver.presentation.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.data.server.FileChangeBus
import com.localdev.webdavserver.data.server.UserHomeFolders
import com.localdev.webdavserver.di.AppModule
import com.localdev.webdavserver.domain.usecase.AddUserUseCase
import com.localdev.webdavserver.domain.usecase.DeleteUserUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UsersUiState(
    val message: String? = null
)

class UsersViewModel : ViewModel() {

    val users: StateFlow<List<UserEntity>> = AppModule.userRepository.users
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            AppModule.userRepository.snapshot())

    private val _serverRunning =
        MutableStateFlow(AppModule.webDavEngine.state.value.isRunning)
    val serverRunning: StateFlow<Boolean> = _serverRunning.asStateFlow()

    private val _ui = MutableStateFlow(UsersUiState())
    val ui: StateFlow<UsersUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            AppModule.webDavEngine.state.collect { _serverRunning.value = it.isRunning }
        }
    }

    fun addUser(
        username: String,
        password: String,
        role: Role,
        readOnly: Boolean,
        rootFolderUri: String?,
        onDone: (Boolean) -> Unit
    ) {
        viewModelScope.launch {
            when (val r = AppModule.addUserUseCase(username, password, role, readOnly, rootFolderUri)) {
                is AddUserUseCase.Result.Success -> { _ui.value = UsersUiState(); onDone(true) }
                is AddUserUseCase.Result.Error -> {
                    _ui.value = UsersUiState(message = r.message); onDone(false)
                }
            }
        }
    }

    fun updateUser(
        existing: UserEntity,
        username: String,
        newPassword: String?,
        role: Role,
        readOnly: Boolean,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            // Block demoting the only remaining admin.
            if (existing.role == Role.ADMIN && role != Role.ADMIN &&
                AppModule.userRepository.isLastAdmin(existing.id)
            ) {
                _ui.value = UsersUiState(message = "At least 1 administrator is required")
                return@launch
            }

            val name = username.trim()
            val home = when (role) {
                Role.ADMIN -> null
                Role.USER -> {
                    val global = AppModule.settingsRepository.config.first().rootFolderUri
                    // Username rename → new personal folder. Otherwise keep a valid home.
                    if (!existing.username.equals(name, ignoreCase = true) ||
                        existing.rootFolderUri == null ||
                        !UserHomeFolders.isUnderGlobalRoot(global, existing.rootFolderUri)
                    ) {
                        UserHomeFolders.ensureUserHome(global, name)
                    } else {
                        existing.rootFolderUri
                    }
                }
            }

            val updated = AppModule.userRepository.updateUser(
                existing, name, newPassword, role, readOnly, home
            )
            if (updated == null) {
                _ui.value = UsersUiState(message = "At least 1 administrator is required")
            } else {
                onDone()
            }
        }
    }

    /**
     * Whether the delete dialog should offer a "delete personal folder" checkbox.
     * - Always for [Role.USER].
     * - For [Role.ADMIN] only when a leftover `{root}/{username}` folder still
     *   exists (account was a user before being promoted).
     * - Never for a pure admin with no personal folder (and admins never own a
     *   home path in account data).
     */
    suspend fun shouldOfferFolderDelete(user: UserEntity): Boolean {
        return when (user.role) {
            Role.USER -> true
            Role.ADMIN -> {
                val global = AppModule.settingsRepository.config.first().rootFolderUri
                UserHomeFolders.existingHomePath(global, user.username) != null
            }
        }
    }

    fun deleteUser(user: UserEntity, deleteFolder: Boolean) {
        viewModelScope.launch {
            when (val r = AppModule.deleteUserUseCase(user.id)) {
                is DeleteUserUseCase.Result.Success -> {
                    if (deleteFolder) {
                        withContext(Dispatchers.IO) {
                            val global = AppModule.settingsRepository.config.first().rootFolderUri
                            // USER: stored personal home or {root}/{username}.
                            // ADMIN: only leftover {root}/{username} from a prior user role.
                            // Pure admins never had a personal folder — path stays null.
                            val path = when (user.role) {
                                Role.USER -> UserHomeFolders.homePathForUser(
                                    globalRoot = global,
                                    username = user.username,
                                    storedHome = user.rootFolderUri
                                )
                                Role.ADMIN -> UserHomeFolders.existingHomePath(
                                    global, user.username
                                )
                            }
                            if (path != null) {
                                UserHomeFolders.deleteHomeFolder(global, path)
                                FileChangeBus.notifyChanged()
                            }
                        }
                    }
                    // No toast/snackbar on success — list updates via users StateFlow.
                    _ui.value = UsersUiState()
                }
                is DeleteUserUseCase.Result.Blocked ->
                    _ui.value = UsersUiState(message = r.reason)
            }
        }
    }

    fun clearMessage() { _ui.value = _ui.value.copy(message = null) }

    fun isLastAdmin(id: String) = AppModule.userRepository.isLastAdmin(id)
}
