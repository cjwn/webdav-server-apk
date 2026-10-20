package com.localdev.webdavserver.presentation.files

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.R
import com.localdev.webdavserver.data.local.model.FileCategory
import com.localdev.webdavserver.data.local.model.FileNode
import com.localdev.webdavserver.presentation.theme.formatBytes

@Composable
fun FilesScreen(viewModel: FilesViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Currently open file (non-directory) for the viewer overlay.
    var openFile by remember { mutableStateOf<FileNode?>(null) }
    var newFileDialog by remember { mutableStateOf(false) }
    var newFolderDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FileNode?>(null) }
    var deleteTarget by remember { mutableStateOf<FileNode?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    // Full-screen viewer takes over when a file is open.
    val file = openFile
    if (file != null) {
        FileViewer(node = file, onClose = { openFile = null })
        return
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Files", style = MaterialTheme.typography.headlineLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::refresh, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reload",
                            tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    }
                    // New / paste menu — disabled until a root folder is selected.
                    var addMenu by remember { mutableStateOf(false) }
                    val canCreate = !state.noRoot && state.rootUri != null
                    IconButton(
                        onClick = { if (canCreate) addMenu = true },
                        enabled = canCreate,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = if (canCreate) "New" else "Select a root folder first",
                            tint = if (canCreate) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = addMenu && canCreate,
                        onDismissRequest = { addMenu = false }
                    ) {
                        DropdownMenuItem(text = { Text("New text file") }, onClick = {
                            addMenu = false; newFileDialog = true
                        })
                        DropdownMenuItem(text = { Text("New folder") }, onClick = {
                            addMenu = false; newFolderDialog = true
                        })
                        if (state.clipboard != null) {
                            DropdownMenuItem(
                                text = { Text("Paste") },
                                leadingIcon = {
                                    Icon(painterResource(R.drawable.ic_copy), null)
                                },
                                onClick = { addMenu = false; viewModel.paste() }
                            )
                        }
                    }
                }
            }

            TextField(
                value = state.query,
                onValueChange = viewModel::onQuery,
                placeholder = { Text("Search") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )

            if (state.breadcrumb.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    state.breadcrumb.forEachIndexed { i, name ->
                        if (i > 0) Text(" › ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = name,
                            color = if (i == state.breadcrumb.lastIndex) MaterialTheme.colorScheme.onSurface
                            else MaterialTheme.colorScheme.primary,
                            fontWeight = if (i == state.breadcrumb.lastIndex) FontWeight.SemiBold
                            else FontWeight.Normal,
                            modifier = Modifier.clickable { viewModel.navigateTo(i) }
                        )
                    }
                }
            }

            when {
                state.noRoot -> EmptyState("Select a root folder in Settings to browse files.")
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.filtered.isEmpty() -> EmptyState("No files here")
                else -> FileList(
                    nodes = state.filtered,
                    viewModel = viewModel,
                    onOpenFile = { openFile = it },
                    onRename = { renameTarget = it },
                    onDelete = { deleteTarget = it }
                )
            }
        }

        SnackbarHost(snackbar, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (newFileDialog) {
        NameDialog("New text file", "name.txt", onDismiss = { newFileDialog = false }) {
            viewModel.createTextFile(it); newFileDialog = false
        }
    }
    if (newFolderDialog) {
        NameDialog("New folder", "Folder name", onDismiss = { newFolderDialog = false }) {
            viewModel.createFolder(it); newFolderDialog = false
        }
    }
    renameTarget?.let { t ->
        NameDialog("Rename", t.name, initial = t.name, onDismiss = { renameTarget = null }) {
            viewModel.rename(t, it); renameTarget = null
        }
    }
    deleteTarget?.let { t ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(t); deleteTarget = null }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
            title = { Text("Delete ${t.name}?") },
            text = { Text("This cannot be undone.") }
        )
    }
}

@Composable
private fun FileList(
    nodes: List<FileNode>,
    viewModel: FilesViewModel,
    onOpenFile: (FileNode) -> Unit,
    onRename: (FileNode) -> Unit,
    onDelete: (FileNode) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    LazyColumn(
        Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        items(nodes, key = { it.uri }) { node ->
            FileRow(
                node = node,
                showDivider = node != nodes.last(),
                onOpen = { if (node.isDirectory) viewModel.open(node) else onOpenFile(node) },
                onCopyPath = { clipboard.setText(AnnotatedString(node.uri)) },
                onCut = { viewModel.cut(node) },
                onCopy = { viewModel.copy(node) },
                onRename = { onRename(node) },
                onDelete = { onDelete(node) }
            )
        }
    }
}

@Composable
private fun FileRow(
    node: FileNode,
    showDivider: Boolean,
    onOpen: () -> Unit,
    onCopyPath: () -> Unit,
    onCut: () -> Unit,
    onCopy: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClick(onClick = onOpen, onLongClick = {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                    )
                    menuOpen = true
                })
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(node.iconRes()),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text(node.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
            }
            Text(
                if (node.isDirectory) "${node.childCount} items" else formatBytes(node.sizeBytes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (!node.isDirectory) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { onOpen(); menuOpen = false })
                }
                DropdownMenuItem(text = { Text("Cut") }, onClick = { onCut(); menuOpen = false })
                DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(); menuOpen = false })
                DropdownMenuItem(text = { Text("Rename") }, onClick = { onRename(); menuOpen = false })
                DropdownMenuItem(text = { Text("Copy Path") }, onClick = { onCopyPath(); menuOpen = false })
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { onDelete(); menuOpen = false }
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
private fun NameDialog(
    title: String,
    placeholder: String,
    initial: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                placeholder = { Text(placeholder) }
            )
        }
    )
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_empty_files),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(12.dp))
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun FileNode.iconRes(): Int = when (category) {
    FileCategory.FOLDER -> R.drawable.ic_folder
    FileCategory.IMAGE -> R.drawable.ic_image
    FileCategory.AUDIO -> R.drawable.ic_audio
    FileCategory.VIDEO -> R.drawable.ic_video
    FileCategory.ARCHIVE -> R.drawable.ic_archive
    FileCategory.FILE -> R.drawable.ic_file
}

private fun Modifier.combinedClick(
    onClick: () -> Unit,
    onLongClick: () -> Unit
): Modifier = this.combinedClickable(
    onClick = onClick,
    onLongClick = onLongClick
)
