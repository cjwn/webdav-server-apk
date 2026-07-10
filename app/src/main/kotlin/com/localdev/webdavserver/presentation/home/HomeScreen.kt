package com.localdev.webdavserver.presentation.home

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.R
import com.localdev.webdavserver.presentation.setup.PermissionUtils
import com.localdev.webdavserver.presentation.theme.IosGreen
import com.localdev.webdavserver.presentation.theme.RunningTintDark
import com.localdev.webdavserver.presentation.theme.RunningTintLight
import com.localdev.webdavserver.presentation.theme.formatBytes
import com.localdev.webdavserver.presentation.theme.formatUptime
import com.localdev.webdavserver.presentation.theme.generateQrBitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    onOpenSettings: () -> Unit = {},
    viewModel: HomeViewModel = viewModel()
) {
    val state by viewModel.serverState.collectAsState()
    val permissionRevoked by viewModel.permissionRevoked.collectAsState()
    val rootConfigured by viewModel.rootConfigured.collectAsState()
    val hideAddress by viewModel.hideAddress.collectAsState()
    val hideQr by viewModel.hideQr.collectAsState()
    val configLoaded by viewModel.configLoaded.collectAsState()
    val notifPromptShown by viewModel.notificationPromptShown.collectAsState()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showNotifPrompt by remember { mutableStateOf(false) }

    // Live uptime ticker.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.isRunning) {
        while (state.isRunning) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // Large title row with status dot.
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Server", style = MaterialTheme.typography.headlineLarge)
                StatusDot(running = state.isRunning, animated = true)
            }

            if (!configLoaded) {
                // Brief loading state — avoids flashing "no root"/"Start" on relaunch.
                Box(
                    Modifier.fillMaxWidth().padding(top = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                }
                return@Column
            }

            if (permissionRevoked) {
                WarningBanner("Folder permission was revoked. Re-pick the folder in Settings.")
                Spacer(Modifier.height(12.dp))
            }
            if (!rootConfigured) {
                WarningBanner("No root folder selected. Choose one in Settings to start the server.")
                Spacer(Modifier.height(12.dp))
            }

            StatusCard(
                running = state.isRunning,
                uptime = formatUptime(if (state.isRunning) now - state.startedAtMillis else 0),
                bytesIn = state.bytesIn,
                bytesOut = state.bytesOut
            )

            Spacer(Modifier.height(16.dp))

            AddressCard(
                address = state.addressLabel,
                hidden = hideAddress,
                onToggleHide = { viewModel.toggleHideAddress() },
                onCopy = {
                    clipboard.setText(AnnotatedString(state.addressLabel))
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    scope.launch { snackbarHostState.showSnackbar("Copied to clipboard") }
                }
            )

            Spacer(Modifier.height(16.dp))

            if (configLoaded && state.isRunning && state.ipAddress != null) {
                QrCard(
                    url = "http://${state.addressLabel}/",
                    hidden = hideQr,
                    onToggleHide = { viewModel.toggleHideQr() }
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(8.dp))
            ServerButton(
                running = state.isRunning,
                enabled = rootConfigured && !permissionRevoked,
                onClick = {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    if (state.isRunning) {
                        viewModel.stopServer()
                    } else {
                        // One-time prompt: enable notifications via system settings.
                        val needsNotifPrompt = !notifPromptShown &&
                            !PermissionUtils.hasNotificationPermission(context)
                        viewModel.startServer()
                        if (needsNotifPrompt) {
                            showNotifPrompt = true
                        }
                    }
                }
            )
            Spacer(Modifier.height(40.dp))
        }

        if (showNotifPrompt) {
            AlertDialog(
                onDismissRequest = {
                    showNotifPrompt = false
                    viewModel.markNotificationPromptShown()
                },
                title = { Text("Enable notifications") },
                text = {
                    Text(
                        "Allow notifications so you can see when the WebDAV server is running."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showNotifPrompt = false
                            viewModel.markNotificationPromptShown()
                            // In-app Settings tab (no system App Info / highlight animation).
                            onOpenSettings()
                        }
                    ) { Text("Open Settings") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showNotifPrompt = false
                            viewModel.markNotificationPromptShown()
                        }
                    ) { Text("Not now") }
                }
            )
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun StatusDot(running: Boolean, animated: Boolean) {
    val transition = rememberInfiniteTransition(label = "dot")
    val scale by transition.animateFloat(
        initialValue = if (running && animated) 0.85f else 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "scale"
    )
    Box(
        Modifier
            .size(14.dp)
            .scale(if (running) scale else 1f)
            .clip(CircleShape)
            .background(if (running) IosGreen else MaterialTheme.colorScheme.onSurfaceVariant)
    )
}

@Composable
private fun StatusCard(running: Boolean, uptime: String, bytesIn: Long, bytesOut: Long) {
    val dark = isSystemInDarkTheme()
    val bg = when {
        running && dark -> RunningTintDark
        running -> RunningTintLight
        else -> MaterialTheme.colorScheme.surface
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(running = running, animated = true)
            Spacer(Modifier.size(10.dp))
            Text(
                if (running) "RUNNING" else "STOPPED",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (running) IosGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("Uptime", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(uptime, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text("↑ ${formatBytes(bytesOut)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("↓ ${formatBytes(bytesIn)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AddressCard(
    address: String,
    hidden: Boolean,
    onToggleHide: () -> Unit,
    onCopy: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f)) {
            Text("Address", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if (hidden) "•••••••••••" else address,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        // Eye toggle (hide/show address) — state is persisted.
        androidx.compose.material3.IconButton(onClick = onToggleHide) {
            Icon(
                painterResource(
                    if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                ),
                contentDescription = if (hidden) "Show address" else "Hide address",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        androidx.compose.material3.IconButton(onClick = onCopy, enabled = !hidden) {
            Icon(
                painterResource(R.drawable.ic_copy),
                contentDescription = "Copy",
                tint = if (hidden) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun QrCard(
    url: String,
    hidden: Boolean,
    onToggleHide: () -> Unit
) {
    val bmp = remember(url) { generateQrBitmap(url) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Scan to connect", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Eye toggle (hide/show QR) — state is persisted.
            androidx.compose.material3.IconButton(onClick = onToggleHide) {
                Icon(
                    painterResource(
                        if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility
                    ),
                    contentDescription = if (hidden) "Show QR" else "Hide QR",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        if (hidden) {
            Box(
                Modifier.size(220.dp).clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Text("QR hidden", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = "QR code",
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun ServerButton(running: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (running) MaterialTheme.colorScheme.error else IosGreen
        )
    ) {
        Text(
            if (running) "■  Stop Server" else "▶  Start Server",
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}

@Composable
private fun WarningBanner(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("⚠️  $message", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
    }
}
