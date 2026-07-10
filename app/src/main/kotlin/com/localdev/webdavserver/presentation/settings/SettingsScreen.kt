package com.localdev.webdavserver.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.presentation.setup.FolderPickerSheet
import com.localdev.webdavserver.presentation.setup.PermissionUtils
import com.localdev.webdavserver.presentation.theme.SectionHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val config by viewModel.config.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var portDialog by remember { mutableStateOf(false) }
    var licenseDialog by remember { mutableStateOf(false) }
    var folderPickerOpen by remember { mutableStateOf(false) }
    // After granting all-files access, continue into the folder picker.
    var pendingFolderPick by remember { mutableStateOf(false) }

    // Permission state (re-checked on resume, since grants happen off-screen).
    var filesGranted by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess()) }
    var notifGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var backgroundGranted by remember {
        mutableStateOf(PermissionUtils.isBackgroundRunAllowed(context))
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    // Reliable launcher for the "all files access" system screen.
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        filesGranted = PermissionUtils.hasAllFilesAccess()
        if (filesGranted && pendingFolderPick) {
            pendingFolderPick = false
            folderPickerOpen = true
        } else if (!filesGranted) {
            pendingFolderPick = false
        }
    }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { backgroundGranted = PermissionUtils.isBackgroundRunAllowed(context) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                filesGranted = PermissionUtils.hasAllFilesAccess()
                notifGranted = PermissionUtils.hasNotificationPermission(context)
                backgroundGranted = PermissionUtils.isBackgroundRunAllowed(context)
                if (filesGranted && pendingFolderPick) {
                    pendingFolderPick = false
                    folderPickerOpen = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun requestFilesAccessThenPick() {
        if (PermissionUtils.hasAllFilesAccess()) {
            folderPickerOpen = true
            return
        }
        pendingFolderPick = true
        val intent = PermissionUtils.manageAllFilesIntent(context)
        if (intent != null) {
            runCatching { filesLauncher.launch(intent) }.onFailure {
                val list = PermissionUtils.manageAllFilesListIntent()
                if (list != null) {
                    runCatching { filesLauncher.launch(list) }
                        .onFailure {
                            pendingFolderPick = false
                            filesLauncher.launch(PermissionUtils.appSettingsIntent(context))
                        }
                } else {
                    pendingFolderPick = false
                    filesLauncher.launch(PermissionUtils.appSettingsIntent(context))
                }
            }
        } else {
            // Pre-R: all-files access not required.
            filesGranted = true
            pendingFolderPick = false
            folderPickerOpen = true
        }
    }

    // Scrollable so new rows (e.g. background permission) stay reachable.
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
        )

        // NETWORK
        SectionHeader("Network")
        Group {
            DisclosureRow("Port", config.port.toString(), showDivider = true) { portDialog = true }
            DisclosureRow(
                "Root Folder",
                viewModel.rootFolderLabel(config.rootFolderUri),
                showDivider = false
            ) { requestFilesAccessThenPick() }
        }

        // STARTUP
        SectionHeader("Startup")
        Group {
            ToggleRow("Auto-start on App Open", config.autoStartOnOpen, showDivider = false) {
                viewModel.setAutoOpen(it)
            }
        }

        // FILES
        SectionHeader("Files")
        Group {
            ToggleRow("Show Hidden Files", config.showHiddenFiles, showDivider = false) {
                viewModel.setShowHidden(it)
            }
        }

        // NOTIFICATION
        SectionHeader("Notification")
        Group {
            ToggleRow(
                "Hide Address in Notification",
                config.hideAddressInNotification,
                showDivider = false
            ) {
                viewModel.setHideAddressInNotification(it)
            }
        }

        // PERMISSIONS
        SectionHeader("Permissions")
        Group {
            PermissionToggleRow(
                title = "Files access",
                granted = filesGranted,
                showDivider = true,
                onToggle = { _ ->
                    if (!filesGranted) {
                        val intent = PermissionUtils.manageAllFilesIntent(context)
                        if (intent != null) {
                            runCatching { filesLauncher.launch(intent) }.onFailure {
                                filesLauncher.launch(PermissionUtils.appSettingsIntent(context))
                            }
                        } else {
                            filesGranted = true
                        }
                    } else {
                        filesLauncher.launch(PermissionUtils.appSettingsIntent(context))
                    }
                }
            )
            PermissionToggleRow(
                title = "Notifications",
                granted = notifGranted,
                showDivider = true,
                onToggle = { _ ->
                    if (!notifGranted) {
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            PermissionUtils.openAppSettings(context)
                        }
                    } else {
                        PermissionUtils.openAppSettings(context)
                    }
                }
            )
            PermissionToggleRow(
                title = "Run in background",
                granted = backgroundGranted,
                showDivider = false,
                onToggle = { _ ->
                    if (!backgroundGranted) {
                        runCatching {
                            backgroundLauncher.launch(
                                PermissionUtils.requestBackgroundRunIntent(context)
                            )
                        }.onFailure {
                            runCatching {
                                backgroundLauncher.launch(
                                    PermissionUtils.batteryOptimizationSettingsIntent()
                                )
                            }
                        }
                    } else {
                        PermissionUtils.openAppSettings(context)
                    }
                }
            )
        }

        // ABOUT
        SectionHeader("About")
        Group {
            DisclosureRow("Version", viewModel.versionName, showDivider = true, onClick = null)
            DisclosureRow("Licenses", "", showDivider = false) { licenseDialog = true }
        }

        // Fixed bottom inset — do not use Modifier.weight inside verticalScroll
        // (infinite height constraints crash / ignore weight).
        Spacer(Modifier.height(32.dp))
    }

    if (folderPickerOpen) {
        FolderPickerSheet(
            onDismiss = { folderPickerOpen = false },
            onFolderChosen = { path ->
                viewModel.onFolderPicked(path)
                folderPickerOpen = false
            }
        )
    }

    if (portDialog) {
        PortDialog(
            current = config.port.toString(),
            onDismiss = { portDialog = false },
            onConfirm = { viewModel.setPort(it); portDialog = false }
        )
    }
    if (licenseDialog) {
        AnimatedInfoDialog(
            onDismiss = { licenseDialog = false },
            title = "Licenses",
            body = "WebDAV Server — Apache License 2.0.\n\n" +
                "Open-source components:\n" +
                "• Ktor (Apache 2.0)\n" +
                "• Jetpack Compose / AndroidX (Apache 2.0)\n" +
                "• Kotlin Coroutines (Apache 2.0)\n" +
                "• ZXing (Apache 2.0)"
        )
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) { content() }
}

@Composable
private fun DisclosureRow(
    title: String,
    value: String,
    showDivider: Boolean,
    onClick: (() -> Unit)?
) {
    Column {
        val base = Modifier.fillMaxWidth().height(48.dp)
        val mod = if (onClick != null) base.clickable(onClick = onClick) else base
        Row(
            mod.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    value,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (onClick != null) {
                    Spacer(Modifier.height(0.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (showDivider) {
            Box(
                Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun PermissionToggleRow(
    title: String,
    granted: Boolean,
    showDivider: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (granted) "Granted" else "Tap to allow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = granted,
                onCheckedChange = { wantOn -> onToggle(wantOn) }
            )
        }
        if (showDivider) {
            Box(
                Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    title: String,
    checked: Boolean,
    showDivider: Boolean,
    onChange: (Boolean) -> Unit
) {
    Column {
        Row(
            Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Switch(checked = checked, onCheckedChange = onChange)
        }
        if (showDivider) {
            Box(
                Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun AnimatedInfoDialog(
    onDismiss: () -> Unit,
    title: String,
    body: String
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.85f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = 0.6f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "scale"
    )
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(220),
        label = "alpha"
    )

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .graphicsLayer { this.scaleX = scale; this.scaleY = scale; this.alpha = alpha }
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surface)
                .border(BorderStroke(1.5.dp, Color.White), RoundedCornerShape(20.dp))
                .padding(22.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp, androidx.compose.ui.graphics.Color.White
                ),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("OK", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun PortDialog(current: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    val port = text.toIntOrNull()
    val valid = port != null && port in 1024..65535
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = valid) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Server Port") },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(5) },
                    singleLine = true,
                    isError = text.isNotEmpty() && !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (text.isNotEmpty() && !valid) {
                    Text(
                        "Port must be between 1024 and 65535",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    )
}
