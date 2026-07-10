package com.localdev.webdavserver.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.data.local.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "webdav_settings")

/**
 * Persists [ServerConfig] using Jetpack DataStore (Preferences).
 */
class SettingsDataStore(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("port")
        val ROOT_URI = stringPreferencesKey("root_uri")
        val AUTO_BOOT = booleanPreferencesKey("auto_boot")
        val AUTO_OPEN = booleanPreferencesKey("auto_open")
        val SHOW_HIDDEN = booleanPreferencesKey("show_hidden")
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val HIDE_ADDRESS = booleanPreferencesKey("hide_address")
        val HIDE_QR = booleanPreferencesKey("hide_qr")
        val HIDE_ADDRESS_NOTIF = booleanPreferencesKey("hide_address_notif")
        val NOTIF_PROMPT_SHOWN = booleanPreferencesKey("notif_prompt_shown")
    }

    val config: Flow<ServerConfig> = context.settingsDataStore.data.map { p ->
        ServerConfig(
            port = p[Keys.PORT] ?: 8080,
            rootFolderUri = p[Keys.ROOT_URI],
            autoStartOnBoot = p[Keys.AUTO_BOOT] ?: false,
            autoStartOnOpen = p[Keys.AUTO_OPEN] ?: false,
            showHiddenFiles = p[Keys.SHOW_HIDDEN] ?: false,
            themeMode = (p[Keys.THEME])?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.DYNAMIC] ?: true,
            setupComplete = p[Keys.SETUP_DONE] ?: false,
            hideAddress = p[Keys.HIDE_ADDRESS] ?: false,
            hideQr = p[Keys.HIDE_QR] ?: false,
            hideAddressInNotification = p[Keys.HIDE_ADDRESS_NOTIF] ?: false,
            notificationPromptShown = p[Keys.NOTIF_PROMPT_SHOWN] ?: false
        )
    }

    suspend fun setPort(port: Int) = context.settingsDataStore.edit { it[Keys.PORT] = port }

    suspend fun setRootUri(uri: String?) = context.settingsDataStore.edit {
        if (uri == null) it.remove(Keys.ROOT_URI) else it[Keys.ROOT_URI] = uri
    }

    suspend fun setAutoBoot(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_BOOT] = value }

    suspend fun setAutoOpen(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.AUTO_OPEN] = value }

    suspend fun setShowHidden(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.SHOW_HIDDEN] = value }

    suspend fun setTheme(mode: ThemeMode) =
        context.settingsDataStore.edit { it[Keys.THEME] = mode.name }

    suspend fun setDynamicColor(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.DYNAMIC] = value }

    suspend fun setSetupComplete(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.SETUP_DONE] = value }

    suspend fun setHideAddress(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.HIDE_ADDRESS] = value }

    suspend fun setHideQr(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.HIDE_QR] = value }

    suspend fun setHideAddressInNotification(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.HIDE_ADDRESS_NOTIF] = value }

    suspend fun setNotificationPromptShown(value: Boolean) =
        context.settingsDataStore.edit { it[Keys.NOTIF_PROMPT_SHOWN] = value }
}
