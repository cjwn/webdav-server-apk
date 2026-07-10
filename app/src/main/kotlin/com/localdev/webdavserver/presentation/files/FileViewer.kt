package com.localdev.webdavserver.presentation.files

import android.graphics.BitmapFactory
import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdev.webdavserver.data.local.model.FileCategory
import com.localdev.webdavserver.data.local.model.FileNode
import com.localdev.webdavserver.di.AppModule
import com.localdev.webdavserver.presentation.theme.formatBytes

private const val FONT_MIN = 10
private const val FONT_MAX = 28
private const val FONT_DEFAULT = 14

private fun FileNode.localPath(): String =
    if (uri.startsWith("file://")) android.net.Uri.parse(uri).path ?: uri else uri

/** True for plain text / markdown files we can show in the read-only viewer. */
private fun FileNode.isTextOrMarkdown(): Boolean {
    val ext = name.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return ext == "txt" || ext == "md" || ext == "markdown" ||
        mimeType.equals("text/plain", ignoreCase = true) ||
        mimeType.equals("text/markdown", ignoreCase = true) ||
        mimeType.equals("text/x-markdown", ignoreCase = true)
}

/**
 * Full-screen viewer: image / audio / video, read-only txt/md, or “no preview”.
 */
@Composable
fun FileViewer(node: FileNode, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text(
                node.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        when {
            node.category == FileCategory.IMAGE -> ImageViewer(node)
            node.category == FileCategory.AUDIO -> MediaPlayerView(node, isVideo = false)
            node.category == FileCategory.VIDEO -> MediaPlayerView(node, isVideo = true)
            node.isTextOrMarkdown() -> TextFileViewer(node)
            else -> UnsupportedPreview(node)
        }
    }
}

@Composable
private fun ImageViewer(node: FileNode) {
    val bmp = remember(node.uri) {
        runCatching { BitmapFactory.decodeFile(node.localPath())?.asImageBitmap() }.getOrNull()
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = node.name,
                modifier = Modifier.fillMaxSize().padding(8.dp)
            )
        } else {
            Text(
                "Cannot preview this image",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MediaPlayerView(node: FileNode, isVideo: Boolean) {
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }
    var ready by remember { mutableStateOf(false) }

    DisposableEffect(node.uri) {
        val mp = MediaPlayer()
        runCatching {
            mp.setDataSource(node.localPath())
            mp.setOnPreparedListener { ready = true }
            mp.setOnCompletionListener { playing = false }
            mp.prepareAsync()
        }
        player = mp
        onDispose { runCatching { mp.release() } }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (isVideo) "🎬" else "🎵", fontSize = 72.sp)
            Spacer(Modifier.height(12.dp))
            Text(node.name, style = MaterialTheme.typography.titleMedium, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            Text(
                if (!ready) "Loading…" else if (playing) "Playing" else "Paused",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(72.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        val mp = player ?: return@IconButton
                        if (playing) {
                            mp.pause()
                            playing = false
                        } else {
                            mp.start()
                            playing = true
                        }
                    },
                    enabled = ready
                ) {
                    Icon(
                        // Pause is not in material-icons-core; Clear = stop/pause affordance.
                        if (playing) Icons.Filled.Clear else Icons.Filled.PlayArrow,
                        contentDescription = "Play/Pause",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            if (isVideo) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Audio-only playback in app — open in a video app for picture.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Read-only viewer for .txt / .md with word-wrap, font size, and optional line numbers.
 */
@Composable
private fun TextFileViewer(node: FileNode) {
    var content by remember { mutableStateOf<String?>(null) }
    var loadError by remember { mutableStateOf(false) }
    var wordWrap by remember { mutableStateOf(true) }
    var fontSp by remember { mutableIntStateOf(FONT_DEFAULT) }
    var showLineNo by remember { mutableStateOf(false) }

    LaunchedEffect(node.uri) {
        loadError = false
        content = null
        val text = AppModule.fileOperationsUseCase.readText(node.uri)
        if (text == null) loadError = true else content = text
    }

    Column(Modifier.fillMaxSize()) {
        // Toolbar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // Word wrap toggle
            IconButton(onClick = { wordWrap = !wordWrap }) {
                Icon(
                    Icons.Filled.List,
                    contentDescription = if (wordWrap) "Disable word wrap" else "Enable word wrap",
                    tint = if (wordWrap) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Font size up: A with up arrow
            IconButton(
                onClick = { fontSp = (fontSp + 1).coerceAtMost(FONT_MAX) },
                enabled = fontSp < FONT_MAX
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.KeyboardArrowUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        "A",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Font size down: A with down arrow
            IconButton(
                onClick = { fontSp = (fontSp - 1).coerceAtLeast(FONT_MIN) },
                enabled = fontSp > FONT_MIN
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "A",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Line numbers checkbox
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Checkbox(
                    checked = showLineNo,
                    onCheckedChange = { showLineNo = it }
                )
                Text(
                    "Line no",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        when {
            content == null && !loadError -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            loadError || content == null -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Could not open this file",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                TextViewerBody(
                    text = content!!,
                    wordWrap = wordWrap,
                    fontSp = fontSp,
                    showLineNo = showLineNo
                )
            }
        }
    }
}

@Composable
private fun TextViewerBody(
    text: String,
    wordWrap: Boolean,
    fontSp: Int,
    showLineNo: Boolean
) {
    val lines = remember(text) { text.split('\n') }
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val lineNoWidth = remember(lines.size, fontSp) {
        // Enough digits for the last line number + padding.
        val digits = lines.size.toString().length.coerceAtLeast(1)
        (digits * fontSp * 0.65f + 12f).dp
    }
    val lineHeight = (fontSp + 4).sp
    val textColor = Color(0xFFE5E5EA)
    val gutterColor = Color(0xFF6E6E73)

    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF101012))
            .padding(10.dp)
    ) {
        // One row per logical line so line numbers stay aligned when wrap is on.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(vScroll)
                .then(if (!wordWrap) Modifier.horizontalScroll(hScroll) else Modifier)
        ) {
            lines.forEachIndexed { index, line ->
                Row(Modifier.fillMaxWidth()) {
                    if (showLineNo) {
                        Text(
                            text = (index + 1).toString(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSp.sp,
                            color = gutterColor,
                            textAlign = TextAlign.End,
                            lineHeight = lineHeight,
                            modifier = Modifier
                                .width(lineNoWidth)
                                .padding(end = 8.dp)
                        )
                        Box(
                            Modifier
                                .width(1.dp)
                                .height((fontSp + 4).dp)
                                .background(Color(0xFF2C2C2E))
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSp.sp,
                        color = textColor,
                        softWrap = wordWrap,
                        maxLines = if (wordWrap) Int.MAX_VALUE else 1,
                        lineHeight = lineHeight,
                        modifier = if (wordWrap) Modifier.weight(1f) else Modifier
                    )
                }
            }
        }
    }
}

/** Non-media / non-text files: metadata only. */
@Composable
private fun UnsupportedPreview(node: FileNode) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text("📄", fontSize = 56.sp)
            Spacer(Modifier.height(12.dp))
            Text(
                node.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3
            )
            Spacer(Modifier.height(8.dp))
            Text(
                formatBytes(node.sizeBytes),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "No in-app preview for this file type.\nAccess it via WebDAV from another device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
        }
    }
}
