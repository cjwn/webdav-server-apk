package com.localdev.webdavserver.presentation.users

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.R
import com.localdev.webdavserver.data.local.model.Role
import com.localdev.webdavserver.data.local.model.UserEntity
import com.localdev.webdavserver.presentation.theme.AvatarPalette
import com.localdev.webdavserver.presentation.theme.IosBlue
import com.localdev.webdavserver.presentation.theme.IosGray
import com.localdev.webdavserver.presentation.theme.IosGreen
import kotlin.math.abs

@Composable
fun UsersScreen(viewModel: UsersViewModel = viewModel()) {
    val users by viewModel.users.collectAsState()
    val running by viewModel.serverRunning.collectAsState()
    val ui by viewModel.ui.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var addOpen by remember { mutableStateOf(false) }
    var editUser by remember { mutableStateOf<UserEntity?>(null) }
    var deleteUser by remember { mutableStateOf<UserEntity?>(null) }
    var deleteOfferFolder by remember { mutableStateOf(false) }
    var deleteFolderChecked by remember { mutableStateOf(false) }
    // Admins may need a disk check for leftover homes before the dialog opens.
    var pendingDeleteResolve by remember { mutableStateOf<UserEntity?>(null) }
    val sheetOpen = addOpen || editUser != null

    // Error snackbars only when no sheet is open (sheet would cover them).
    LaunchedEffect(ui.message, sheetOpen) {
        val msg = ui.message
        if (msg != null && !sheetOpen) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        floatingActionButton = {
            if (!sheetOpen) {
                FloatingActionButton(
                    onClick = { addOpen = true },
                    containerColor = MaterialTheme.colorScheme.primary
                ) { Icon(Icons.Filled.Add, contentDescription = "Add user", tint = Color.White) }
            }
        },
        snackbarHost = {
            // Keep snackbars above the bottom nav / FAB area.
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 8.dp)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad).padding(horizontal = 16.dp)) {
            Text(
                "Users",
                style = MaterialTheme.typography.headlineLarge,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp)
            )

            LazyColumn(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                val adminCount = users.count { it.role == Role.ADMIN }
                items(users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        online = running,
                        showDivider = user != users.last(),
                        onClick = { editUser = user },
                        onDelete = {
                            deleteFolderChecked = false
                            if (user.role == Role.USER) {
                                // Users always get the folder checkbox — open dialog immediately.
                                deleteOfferFolder = true
                                deleteUser = user
                            } else {
                                // Admins: only offer folder delete if a leftover home exists.
                                pendingDeleteResolve = user
                            }
                        },
                        // Hide Delete on the sole admin; show when 2+ admins exist.
                        // Regular users always show Delete.
                        canDelete = user.role != Role.ADMIN || adminCount > 1
                    )
                }
            }
        }
    }

    // Resolve leftover home for admins before showing the delete dialog (no checkbox flash).
    LaunchedEffect(pendingDeleteResolve) {
        val target = pendingDeleteResolve ?: return@LaunchedEffect
        deleteOfferFolder = viewModel.shouldOfferFolderDelete(target)
        deleteFolderChecked = false
        deleteUser = target
        pendingDeleteResolve = null
    }

    deleteUser?.let { target ->
        DeleteUserDialog(
            user = target,
            offerFolderDelete = deleteOfferFolder,
            folderChecked = deleteFolderChecked,
            onFolderCheckedChange = { deleteFolderChecked = it },
            onDismiss = {
                deleteUser = null
                deleteOfferFolder = false
                deleteFolderChecked = false
            },
            onConfirm = {
                viewModel.deleteUser(target, deleteFolder = deleteFolderChecked && deleteOfferFolder)
                deleteUser = null
                deleteOfferFolder = false
                deleteFolderChecked = false
            }
        )
    }

    if (addOpen) {
        UserSheet(
            title = "Add User",
            existing = null,
            // Errors while the sheet is open are shown inside it (above keyboard).
            externalMessage = ui.message,
            onExternalMessageShown = { viewModel.clearMessage() },
            onDismiss = { addOpen = false },
            onSave = { username, password, role, readOnly ->
                viewModel.addUser(username, password ?: "", role, readOnly, null) { ok ->
                    if (ok) addOpen = false
                }
            }
        )
    }
    editUser?.let { u ->
        UserSheet(
            title = "Edit User",
            existing = u,
            externalMessage = ui.message,
            onExternalMessageShown = { viewModel.clearMessage() },
            onDismiss = { editUser = null },
            onSave = { username, password, role, readOnly ->
                viewModel.updateUser(u, username, password, role, readOnly) {
                    editUser = null
                }
            }
        )
    }
}

@Composable
private fun UserRow(
    user: UserEntity,
    online: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    canDelete: Boolean
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(user.username, user.initials)
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    user.username,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RoleBadge(user.role)
                    if (online) {
                        Spacer(Modifier.size(8.dp))
                        Box(Modifier.size(8.dp).clip(CircleShape).background(IosGreen))
                        Spacer(Modifier.size(4.dp))
                        Text(
                            "online",
                            style = MaterialTheme.typography.labelSmall,
                            color = IosGreen
                        )
                    }
                    if (user.readOnly) {
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "read-only",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (canDelete) {
                Text(
                    "Delete",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.clickable(onClick = onDelete)
                )
            }
        }
        if (showDivider) {
            Box(
                Modifier.padding(start = 58.dp).fillMaxWidth().height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )
        }
    }
}

@Composable
private fun DeleteUserDialog(
    user: UserEntity,
    offerFolderDelete: Boolean,
    folderChecked: Boolean,
    onFolderCheckedChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete user?") },
        text = {
            Column {
                Text("Delete account “${user.username}”?")
                if (offerFolderDelete) {
                    Spacer(Modifier.height(14.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onFolderCheckedChange(!folderChecked) }
                    ) {
                        Checkbox(
                            checked = folderChecked,
                            onCheckedChange = onFolderCheckedChange
                        )
                        Text(
                            // Users: simple label. Promoted admins: explicit “select checkbox…” copy.
                            text = if (user.role == Role.ADMIN) {
                                "Select checkbox to delete folder of the user ${user.username} too"
                            } else {
                                "Also delete folder of user ${user.username}"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 4.dp, top = 12.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun Avatar(username: String, initials: String) {
    val color = AvatarPalette[abs(username.hashCode()) % AvatarPalette.size]
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(initials, fontWeight = FontWeight.Bold, color = Color(0xFF222222))
    }
}

@Composable
private fun RoleBadge(role: Role) {
    val (bg, label) = when (role) {
        Role.ADMIN -> IosBlue to "Admin"
        Role.USER -> IosGray to "User"
    }
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(bg.copy(alpha = 0.18f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = bg,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserSheet(
    title: String,
    existing: UserEntity?,
    externalMessage: String?,
    onExternalMessageShown: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (username: String, password: String?, role: Role, readOnly: Boolean) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var username by remember { mutableStateOf(existing?.username ?: "") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var role by remember { mutableStateOf(existing?.role ?: Role.USER) }
    var readOnly by remember {
        mutableStateOf(if (existing?.role == Role.USER) existing.readOnly else false)
    }
    // Inline banner so errors stay visible above the keyboard / sheet content.
    var banner by remember { mutableStateOf<String?>(null) }
    // Confirm before promoting to Admin (home becomes global root).
    var showAdminPromoteDialog by remember { mutableStateOf(false) }

    LaunchedEffect(externalMessage) {
        if (externalMessage != null) {
            banner = externalMessage
            onExternalMessageShown()
        }
    }

    fun selectAdminRole() {
        role = Role.ADMIN
        readOnly = false
        banner = null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))

            if (banner != null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        banner!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = username,
                onValueChange = {
                    username = it
                    banner = null
                },
                label = { Text("Username") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    banner = null
                },
                label = { Text(if (existing == null) "Password" else "New password (optional)") },
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
            Text("Role", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = role == Role.USER,
                    onClick = {
                        role = Role.USER
                        banner = null
                    },
                    label = { Text("User") }
                )
                FilterChip(
                    selected = role == Role.ADMIN,
                    onClick = {
                        if (role == Role.ADMIN) return@FilterChip
                        // Warn before switching to Admin (personal home → global root).
                        showAdminPromoteDialog = true
                    },
                    label = { Text("Admin") }
                )
            }
            if (role == Role.USER) {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = readOnly, onCheckedChange = { readOnly = it })
                    Text("Read-only access")
                }
            }
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = {
                    val pw = password.ifEmpty { null }
                    val effectiveReadOnly = if (role == Role.ADMIN) false else readOnly
                    if (username.isBlank()) {
                        banner = "Username cannot be empty"
                        return@Button
                    }
                    if (existing == null && password.length < 4) {
                        banner = "Password must be at least 4 characters"
                        return@Button
                    }
                    onSave(
                        username.trim(),
                        if (existing == null) (pw ?: "") else pw,
                        role,
                        effectiveReadOnly
                    )
                },
                enabled = username.isNotBlank() && (existing != null || password.length >= 4),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) { Text("Save", fontWeight = FontWeight.SemiBold) }
        }
    }

    if (showAdminPromoteDialog) {
        val displayName = username.trim().ifEmpty { existing?.username ?: "this user" }
        AlertDialog(
            onDismissRequest = { showAdminPromoteDialog = false },
            title = { Text("Promote to Admin?") },
            text = {
                Text(
                    "Now $displayName’s home will be the root folder over WebDAV " +
                        "(not a personal user folder). You may need to reconfigure " +
                        "WebDAV clients that used the old home. Continue anyway?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showAdminPromoteDialog = false
                        selectAdminRole()
                    }
                ) { Text("Continue anyway") }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPromoteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
