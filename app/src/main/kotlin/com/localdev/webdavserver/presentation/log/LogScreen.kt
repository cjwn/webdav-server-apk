package com.localdev.webdavserver.presentation.log

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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdev.webdavserver.data.local.model.LogEntry
import com.localdev.webdavserver.presentation.theme.IosBlue
import com.localdev.webdavserver.presentation.theme.IosGreen
import com.localdev.webdavserver.presentation.theme.IosOrange
import com.localdev.webdavserver.presentation.theme.IosRed
import com.localdev.webdavserver.presentation.theme.formatTime

@Composable
fun LogScreen(viewModel: LogViewModel = viewModel()) {
    val entries by viewModel.entries.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val autoScroll by viewModel.autoScroll.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    var detailEntry by remember { mutableStateOf<LogEntry?>(null) }

    val filtered = viewModel.apply(entries)

    LaunchedEffect(Unit) { viewModel.markRead() }
    LaunchedEffect(filtered.size, autoScroll) {
        if (autoScroll && filtered.isNotEmpty()) {
            listState.animateScrollToItem(filtered.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Log", style = MaterialTheme.typography.headlineLarge)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Auto", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // Compact switch scaled down to roughly the height of the label.
                Switch(
                    checked = autoScroll,
                    onCheckedChange = { viewModel.toggleAutoScroll() },
                    modifier = Modifier
                        .scale(0.65f)
                        .size(width = 34.dp, height = 20.dp)
                )
                // Small but comfortably tappable delete button.
                IconButton(
                    onClick = viewModel::clear,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LogFilter.entries.forEach { f ->
                FilterChip(selected = filter == f, onClick = { viewModel.setFilter(f) },
                    label = { Text(f.name.lowercase().replaceFirstChar { it.uppercase() }) })
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No log entries yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                items(filtered, key = { it.id }) { entry ->
                    LogRow(
                        entry = entry,
                        showDivider = entry != filtered.last(),
                        onClick = { detailEntry = entry },
                        onLongPress = {
                            haptics.performHapticFeedback(
                                androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                            )
                            clipboard.setText(AnnotatedString(
                                "${formatTime(entry.timestampMillis)} ${entry.method} ${entry.path} " +
                                    "${entry.username} ${entry.ip} ${entry.statusCode} ${entry.statusMessage}"
                            ))
                        }
                    )
                }
            }
        }
    }

    detailEntry?.let { entry ->
        LogDetailDialog(entry = entry, onDismiss = { detailEntry = null }, onCopy = {
            clipboard.setText(AnnotatedString(
                "Time: ${formatTime(entry.timestampMillis)}\nMethod: ${entry.method}\n" +
                    "Path: ${entry.path}\nUser: ${entry.username}\nIP: ${entry.ip}\n" +
                    "Status: ${entry.statusCode} ${entry.statusMessage}"
            ))
        })
    }
}

@Composable
private fun LogDetailDialog(entry: LogEntry, onDismiss: () -> Unit, onCopy: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            androidx.compose.material3.OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp, MaterialTheme.colorScheme.primary
                )
            ) { Text("OK", fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onCopy) { Text("Copy") }
        },
        title = { Text("Request details") },
        text = {
            Column {
                DetailLine("Time", formatTime(entry.timestampMillis))
                DetailLine("Method", entry.method)
                DetailLine("Path", entry.path)
                DetailLine("User", entry.username)
                DetailLine("IP", entry.ip)
                DetailLine("Status", "${entry.statusCode} ${entry.statusMessage}")
            }
        }
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LogRow(
    entry: LogEntry,
    showDivider: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(formatTime(entry.timestampMillis), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.size(8.dp))
            MethodChip(entry.method)
            Spacer(Modifier.size(8.dp))
            Column(Modifier.weight(1f)) {
                Text(entry.path, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                Text(entry.username, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            StatusChip(entry.statusCode)
        }
        if (showDivider) {
            Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(1.dp)
                .background(MaterialTheme.colorScheme.outline))
        }
    }
}

@Composable
private fun MethodChip(method: String) {
    val color = when (method.uppercase()) {
        "GET", "HEAD" -> IosBlue
        "PUT" -> IosOrange
        "DELETE" -> IosRed
        "MKCOL" -> IosGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Chip(method.uppercase(), color)
}

@Composable
private fun StatusChip(code: Int) {
    val color = when (code / 100) {
        2 -> IosGreen
        3 -> IosBlue
        4 -> IosOrange
        else -> IosRed
    }
    Chip(code.toString(), color)
}

@Composable
private fun Chip(text: String, color: Color) {
    Box(
        Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.18f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = color,
            fontWeight = FontWeight.SemiBold)
    }
}


