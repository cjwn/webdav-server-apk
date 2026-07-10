package com.localdev.webdavserver.presentation.setup

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.R
import com.localdev.webdavserver.di.AppModule

private enum class SetupStep { ACCOUNT, PERMISSIONS }

@Composable
fun SetupWizardScreen(
    onSetupComplete: () -> Unit,
    viewModel: SetupViewModel = viewModel()
) {
    // If the admin account was already created (user left mid-setup), resume on
    // the permissions step instead of asking them to sign up again.
    var step by remember {
        mutableStateOf(
            if (AppModule.userRepository.snapshot().isNotEmpty()) SetupStep.PERMISSIONS
            else SetupStep.ACCOUNT
        )
    }

    when (step) {
        SetupStep.ACCOUNT -> AccountStep(
            viewModel = viewModel,
            onAccountCreated = { step = SetupStep.PERMISSIONS }
        )
        SetupStep.PERMISSIONS -> PermissionsStep(
            viewModel = viewModel,
            onContinue = { viewModel.finishSetup(onSetupComplete) }
        )
    }
}

@Composable
private fun AccountStep(
    viewModel: SetupViewModel,
    onAccountCreated: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val haptics = LocalHapticFeedback.current
    var showPassword by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(60.dp))
        Text(
            text = stringResource(R.string.setup_title),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.setup_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::onUsername,
            label = { Text(stringResource(R.string.username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = { Text(stringResource(R.string.password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(
                            if (showPassword) R.drawable.ic_visibility_off
                            else R.drawable.ic_visibility
                        ),
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.confirmPassword,
            onValueChange = viewModel::onConfirm,
            label = { Text(stringResource(R.string.confirm_password)) },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { showPassword = !showPassword }) {
                    Icon(
                        painterResource(
                            if (showPassword) R.drawable.ic_visibility_off
                            else R.drawable.ic_visibility
                        ),
                        contentDescription = if (showPassword) "Hide password" else "Show password"
                    )
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = state.confirmPassword.isNotEmpty() && state.confirmPassword != state.password,
            modifier = Modifier.fillMaxWidth()
        )

        if (state.error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = state.error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                viewModel.createAdmin(onAccountCreated)
            },
            enabled = state.canSubmit,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            if (state.isSubmitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(stringResource(R.string.create_account), fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun PermissionsStep(
    viewModel: SetupViewModel,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val rootUri by viewModel.rootUri.collectAsState()

    // Tracks whether all-files access is granted; refreshed on resume because the
    // grant happens in a separate system settings screen.
    var storageGranted by remember { mutableStateOf(PermissionUtils.hasAllFilesAccess()) }
    var notifGranted by remember { mutableStateOf(PermissionUtils.hasNotificationPermission(context)) }
    var backgroundGranted by remember {
        mutableStateOf(PermissionUtils.isBackgroundRunAllowed(context))
    }

    // Launch the "all files access" screen via a result launcher (reliable) and
    // re-check on return.
    val filesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { storageGranted = PermissionUtils.hasAllFilesAccess() }

    // POST_NOTIFICATIONS runtime permission (API 33+).
    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> notifGranted = granted }

    val backgroundLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { backgroundGranted = PermissionUtils.isBackgroundRunAllowed(context) }

    // In-app folder picker (direct File access; no SAF restrictions).
    var folderPickerOpen by remember { mutableStateOf(false) }

    // Re-check the storage grant whenever we come back to the app.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                storageGranted = PermissionUtils.hasAllFilesAccess()
                notifGranted = PermissionUtils.hasNotificationPermission(context)
                backgroundGranted = PermissionUtils.isBackgroundRunAllowed(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun launchFilesAccess() {
        haptics.performHapticFeedback(
            androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
        )
        val intent = PermissionUtils.manageAllFilesIntent(context)
        if (intent != null) {
            runCatching { filesLauncher.launch(intent) }.onFailure {
                val list = PermissionUtils.manageAllFilesListIntent()
                if (list != null) runCatching { filesLauncher.launch(list) }
                    .onFailure { filesLauncher.launch(PermissionUtils.appSettingsIntent(context)) }
                else filesLauncher.launch(PermissionUtils.appSettingsIntent(context))
            }
        } else {
            // Pre-R: all-files access not required; treat as granted.
            storageGranted = true
        }
    }

    val rootChosen = rootUri != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.height(60.dp))
        Text(
            text = "Permissions",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Allow access so the server can read and share your files, then " +
                "choose the folder to share.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        // 1) All-files access.
        PermissionRow(
            title = "Files access",
            subtitle = if (storageGranted) "Granted" else "Required — allow managing all files",
            granted = storageGranted,
            buttonLabel = "Allow",
            onRequest = { launchFilesAccess() }
        )

        Spacer(Modifier.height(16.dp))

        // 2) Notification permission (only meaningful on API 33+).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionRow(
                title = "Notifications",
                subtitle = if (notifGranted) "Granted" else "Show server running status",
                granted = notifGranted,
                buttonLabel = "Allow",
                onRequest = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            )
            Spacer(Modifier.height(16.dp))
        }

        // 3) Background run (battery optimization exemption).
        PermissionRow(
            title = "Run in background",
            subtitle = if (backgroundGranted) "Granted — unrestricted battery use"
            else "Allow so the WebDAV server stays alive",
            granted = backgroundGranted,
            buttonLabel = "Allow",
            onRequest = {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                runCatching {
                    backgroundLauncher.launch(PermissionUtils.requestBackgroundRunIntent(context))
                }.onFailure {
                    runCatching {
                        backgroundLauncher.launch(PermissionUtils.batteryOptimizationSettingsIntent())
                    }
                }
            }
        )
        Spacer(Modifier.height(16.dp))

        // 4) Root folder — shown only after files access is granted.
        if (storageGranted) {
            PermissionRow(
                title = "Root folder",
                subtitle = if (rootChosen) viewModel.rootFolderLabel(rootUri)
                else "Choose the folder to share",
                granted = rootChosen,
                buttonLabel = if (rootChosen) "Change" else "Choose",
                onRequest = { folderPickerOpen = true }
            )
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onContinue,
            enabled = storageGranted && rootChosen,
            modifier = Modifier.fillMaxWidth().height(50.dp)
        ) {
            Text("Continue", fontWeight = FontWeight.SemiBold)
        }
        TextButton(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(40.dp))
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
}

@Composable
private fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    buttonLabel: String,
    onRequest: () -> Unit
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (granted) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("✓", style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary)
                // "Change" affordance (e.g. re-pick the root folder).
                if (buttonLabel.equals("Change", ignoreCase = true)) {
                    TextButton(onClick = onRequest) { Text(buttonLabel) }
                }
            }
        } else {
            Button(onClick = onRequest) { Text(buttonLabel) }
        }
    }
}
