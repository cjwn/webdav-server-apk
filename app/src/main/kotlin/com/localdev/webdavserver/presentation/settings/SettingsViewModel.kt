package com.localdev.webdavserver.presentation.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.BuildConfig
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.di.AppModule
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    val config: StateFlow<ServerConfig> = AppModule.settingsRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServerConfig())

    val versionName: String get() = BuildConfig.VERSION_NAME

    fun setPort(value: String) {
        val port = value.toIntOrNull() ?: return
        viewModelScope.launch { AppModule.settingsRepository.setPort(port) }
    }


    fun setAutoOpen(value: Boolean) =
        viewModelScope.launch { AppModule.settingsRepository.setAutoOpen(value) }

    fun setShowHidden(value: Boolean) =
        viewModelScope.launch { AppModule.settingsRepository.setShowHidden(value) }

    fun setHideAddressInNotification(value: Boolean) =
        viewModelScope.launch { AppModule.settingsRepository.setHideAddressInNotification(value) }

    /**
     * Persists the picked tree URI and takes a persistable read/write grant.
     */
    /** Persists the picked folder as an absolute filesystem path. */
    fun onFolderPicked(path: String) {
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
}
