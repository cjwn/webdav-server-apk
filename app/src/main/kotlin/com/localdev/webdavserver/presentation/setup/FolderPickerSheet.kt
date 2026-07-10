package com.localdev.webdavserver.presentation.setup

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Create
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.localdev.webdavserver.R
import java.io.File

/**
 * An in-app folder picker that browses the real filesystem using direct File
 * access (works because the app holds MANAGE_EXTERNAL_STORAGE). Unlike the SAF
 * OpenDocumentTree picker, this can select ANY directory — including storage
 * roots and folders you create — and never shows "Can't use this folder".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    onDismiss: () -> Unit,
    onFolderChosen: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val storageRoot = remember { Environment.getExternalStorageDirectory().absolutePath }

    var currentPath by remember { mutableStateOf(storageRoot) }
    var dirs by remember { mutableStateOf<List<File>>(emptyList()) }
    var createOpen by remember { mutableStateOf(false) }

    fun refresh() {
        val dir = File(currentPath)
        dirs = (dir.listFiles()?.toList() ?: emptyList())
            .filter { it.isDirectory && it.canRead() }
            .sortedBy { it.name.lowercase() }
    }

    LaunchedEffect(currentPath) { refresh() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            // Header: back + current path
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        // Go up one level, but never above the storage root.
                        if (currentPath != storageRoot) {
                            File(currentPath).parentFile?.let { currentPath = it.absolutePath }
                        }
                    },
                    enabled = currentPath != storageRoot
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                }
                Text(
                    text = "Choose folder",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = displayPath(currentPath, storageRoot),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))

            // Directory list
            if (dirs.isEmpty()) {
                Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    Text("No subfolders here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                    items(dirs, key = { it.absolutePath }) { d ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { currentPath = d.absolutePath }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painterResource(R.drawable.ic_folder),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(d.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { createOpen = true },
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Create, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("New folder")
                }
                OutlinedButton(
                    onClick = { onFolderChosen(currentPath) },
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Use this folder", fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (createOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { createOpen = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newDir = File(currentPath, name.trim())
                        if (name.isNotBlank() && (newDir.exists() || newDir.mkdirs())) {
                            currentPath = newDir.absolutePath
                        }
                        createOpen = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { createOpen = false }) { Text("Cancel") } },
            title = { Text("New folder") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Folder name") }
                )
            }
        )
    }
}

private fun displayPath(path: String, storageRoot: String): String {
    val rel = path.removePrefix(storageRoot).trim('/')
    return if (rel.isEmpty()) "Internal storage" else "Internal storage / $rel"
}
