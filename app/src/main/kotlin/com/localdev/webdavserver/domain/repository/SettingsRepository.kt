package com.localdev.webdavserver.domain.repository

import com.localdev.webdavserver.data.local.datastore.SettingsDataStore
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.data.local.model.ThemeMode
import kotlinx.coroutines.flow.Flow

/**
 * Reactive access to app + server settings.
 */
class SettingsRepository(private val dataStore: SettingsDataStore) {

    val config: Flow<ServerConfig> = dataStore.config

    suspend fun setPort(port: Int) = dataStore.setPort(port)
    suspend fun setRootUri(uri: String?) = dataStore.setRootUri(uri)
    suspend fun setAutoBoot(value: Boolean) = dataStore.setAutoBoot(value)
    suspend fun setAutoOpen(value: Boolean) = dataStore.setAutoOpen(value)
    suspend fun setShowHidden(value: Boolean) = dataStore.setShowHidden(value)
    suspend fun setTheme(mode: ThemeMode) = dataStore.setTheme(mode)
    suspend fun setDynamicColor(value: Boolean) = dataStore.setDynamicColor(value)
    suspend fun setSetupComplete(value: Boolean) = dataStore.setSetupComplete(value)
    suspend fun setHideAddress(value: Boolean) = dataStore.setHideAddress(value)
    suspend fun setHideQr(value: Boolean) = dataStore.setHideQr(value)
    suspend fun setHideAddressInNotification(value: Boolean) =
        dataStore.setHideAddressInNotification(value)

    suspend fun setNotificationPromptShown(value: Boolean) =
        dataStore.setNotificationPromptShown(value)
}
