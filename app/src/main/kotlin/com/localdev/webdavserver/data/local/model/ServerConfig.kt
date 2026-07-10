package com.localdev.webdavserver.data.local.model

/**
 * Appearance / theme mode.
 *
 * DARK uses a true-black (AMOLED-friendly) palette.
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * Persisted server + app configuration.
 */
data class ServerConfig(
    val port: Int = 8080,
    val rootFolderUri: String? = null,
    val autoStartOnBoot: Boolean = false,
    val autoStartOnOpen: Boolean = false,
    val showHiddenFiles: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val setupComplete: Boolean = false,
    val hideAddress: Boolean = false,
    val hideQr: Boolean = false,
    val hideAddressInNotification: Boolean = false,
    /** True after the one-time "enable notifications" prompt was shown. */
    val notificationPromptShown: Boolean = false
) {
    val isPortValid: Boolean get() = port in 1024..65535
}
