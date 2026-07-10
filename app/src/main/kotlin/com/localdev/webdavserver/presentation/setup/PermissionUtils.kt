package com.localdev.webdavserver.presentation.setup

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings

/**
 * Shared runtime-permission helpers used by the setup wizard and the settings
 * screen. Centralizes the "all files access", notification, and background
 * (battery optimization) checks/requests.
 */
object PermissionUtils {

    /** True if the app can manage all files (or pre-R where it isn't required). */
    fun hasAllFilesAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Environment.isExternalStorageManager()
        else true

    /** True if POST_NOTIFICATIONS is granted (or pre-Tiramisu where implicit). */
    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * True when the app is exempt from battery optimizations (helps keep the
     * WebDAV foreground service alive in the background).
     */
    fun isBackgroundRunAllowed(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Opens the system dialog / screen to allow unrestricted background work
     * (ignore battery optimizations) for this app.
     */
    fun requestBackgroundRunIntent(context: Context): Intent {
        return Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /** Fallback: general battery optimization settings list. */
    fun batteryOptimizationSettingsIntent(): Intent =
        Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    /**
     * Builds the intent that opens the system "Allow access to manage all files"
     * screen for THIS app. Prefer launching this via an ActivityResultLauncher
     * so the result/return is observed reliably.
     *
     * On devices/ROMs where the app-specific screen is missing, the caller
     * should fall back to [appSettingsIntent].
     */
    fun manageAllFilesIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
        }
    }

    /** Global "all files access" list (fallback when the per-app screen fails). */
    fun manageAllFilesListIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }

    /** This app's system details page (for revoking/managing manually). */
    fun appSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }

    /**
     * Direct (non-launcher) request used as a last resort. Adds NEW_TASK because
     * it is called from a plain Context. Kept for compatibility.
     */
    fun requestAllFilesAccess(context: Context) {
        val intent = manageAllFilesIntent(context)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } ?: return
        runCatching { context.startActivity(intent) }.onFailure {
            val fallback = manageAllFilesListIntent()?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fallback != null) runCatching { context.startActivity(fallback) }
        }
    }

    fun openAppSettings(context: Context) {
        val intent = appSettingsIntent(context).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun requestAllFilesAccessOrSettings(context: Context, currentlyGranted: Boolean) {
        if (currentlyGranted) openAppSettings(context) else requestAllFilesAccess(context)
    }
}
