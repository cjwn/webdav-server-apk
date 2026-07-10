package com.localdev.webdavserver.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.data.server.ServerRuntimeState
import com.localdev.webdavserver.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    val serverState: StateFlow<ServerRuntimeState> =
        AppModule.webDavEngine.state.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppModule.webDavEngine.state.value
        )

    private val _rootConfigured = MutableStateFlow(false)
    val rootConfigured: StateFlow<Boolean> = _rootConfigured.asStateFlow()

    private val _permissionRevoked = MutableStateFlow(false)
    val permissionRevoked: StateFlow<Boolean> = _permissionRevoked.asStateFlow()

    private val _hideAddress = MutableStateFlow(false)
    val hideAddress: StateFlow<Boolean> = _hideAddress.asStateFlow()

    private val _hideQr = MutableStateFlow(false)
    val hideQr: StateFlow<Boolean> = _hideQr.asStateFlow()

    // Becomes true after the first settings emission, so the QR/address aren't
    // shown (and don't flash) before the persisted hide state is known.
    private val _configLoaded = MutableStateFlow(false)
    val configLoaded: StateFlow<Boolean> = _configLoaded.asStateFlow()

    private val _notificationPromptShown = MutableStateFlow(false)
    val notificationPromptShown: StateFlow<Boolean> = _notificationPromptShown.asStateFlow()

    init {
        viewModelScope.launch {
            AppModule.settingsRepository.config.collect { config ->
                val uri = config.rootFolderUri
                _rootConfigured.value = uri != null
                _permissionRevoked.value =
                    uri != null && !AppModule.browseFilesUseCase.hasPermission(uri)
                _hideAddress.value = config.hideAddress
                _hideQr.value = config.hideQr
                _notificationPromptShown.value = config.notificationPromptShown
                _configLoaded.value = true
            }
        }
    }

    fun toggleHideAddress() = viewModelScope.launch {
        AppModule.settingsRepository.setHideAddress(!_hideAddress.value)
    }

    fun toggleHideQr() = viewModelScope.launch {
        AppModule.settingsRepository.setHideQr(!_hideQr.value)
    }

    /** Marks the one-time notification enable prompt as shown permanently. */
    fun markNotificationPromptShown() = viewModelScope.launch {
        AppModule.settingsRepository.setNotificationPromptShown(true)
        _notificationPromptShown.value = true
    }

    fun startServer() = AppModule.startServerUseCase()
    fun stopServer() = AppModule.stopServerUseCase()
}
