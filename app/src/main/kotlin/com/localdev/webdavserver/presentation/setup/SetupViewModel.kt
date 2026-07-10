package com.localdev.webdavserver.presentation.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SetupUiState(
    val username: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val error: String? = null,
    val isSubmitting: Boolean = false
) {
    val canSubmit: Boolean
        get() = username.isNotBlank() &&
            password.length >= 4 &&
            password == confirmPassword &&
            !isSubmitting
}

class SetupViewModel : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state: StateFlow<SetupUiState> = _state.asStateFlow()

    private val _rootUri = MutableStateFlow<String?>(null)
    val rootUri: StateFlow<String?> = _rootUri.asStateFlow()

    init {
        // Restore any already-picked root when resuming the permissions step.
        viewModelScope.launch {
            AppModule.settingsRepository.config.collect { config ->
                if (_rootUri.value == null && config.rootFolderUri != null) {
                    _rootUri.value = config.rootFolderUri
                }
            }
        }
    }

    fun onUsername(v: String) = _state.update { it.copy(username = v, error = null) }
    fun onPassword(v: String) = _state.update { it.copy(password = v, error = null) }
    fun onConfirm(v: String) = _state.update { it.copy(confirmPassword = v, error = null) }

    fun createAdmin(onSuccess: () -> Unit) {
        val s = _state.value
        if (s.password != s.confirmPassword) {
            _state.update { it.copy(error = "Passwords do not match") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true) }
            val result = AppModule.addUserUseCase(
                username = s.username.trim(),
                password = s.password,
                role = Role.ADMIN,
                readOnly = false,
                rootFolderUri = null
            )
            when (result) {
                is com.localdev.webdavserver.domain.usecase.AddUserUseCase.Result.Success -> {
                    // NOTE: setup is NOT marked complete here — that happens only
                    // after the permissions step (finishSetup), otherwise the
                    // config flow would navigate away from the wizard early.
                    _state.update { it.copy(isSubmitting = false) }
                    onSuccess()
                }
                is com.localdev.webdavserver.domain.usecase.AddUserUseCase.Result.Error -> {
                    _state.update { it.copy(isSubmitting = false, error = result.message) }
                }
            }
        }
    }

    /** Persists the picked folder as an absolute filesystem path. */
    fun onFolderPicked(path: String) {
        _rootUri.value = path
        viewModelScope.launch { AppModule.settingsRepository.setRootUri(path) }
    }

    fun rootFolderLabel(ref: String?): String {
        if (ref == null) return "Not selected"
        if (ref.startsWith("content://")) {
            return Uri.decode(ref).substringAfterLast(':').ifEmpty { ref }
        }
        val root = android.os.Environment.getExternalStorageDirectory().absolutePath
        val rel = ref.removePrefix(root).trim('/')
        return if (rel.isEmpty()) "Internal storage" else "Internal storage / $rel"
    }

    /**
     * Marks first-time setup complete. Called only when the user finishes the
     * permissions step (Continue or Skip).
     */
    fun finishSetup(onDone: () -> Unit) {
        viewModelScope.launch {
            AppModule.settingsRepository.setSetupComplete(true)
            onDone()
        }
    }
}
