package com.joshiminh.cbzconverter.ui

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.core.MihonCbzFile
import com.joshiminh.cbzconverter.core.MihonMangaEntry
import com.joshiminh.cbzconverter.core.SelectedFileInfo

@Composable
fun ManualSelectionCard(selectedFileName: String, selectedFilesUri: List<Uri>, isCurrentlyConverting: Boolean, onSelectFiles: () -> Unit) {
    val firstLine = selectedFileName.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
    val summary = when {
        selectedFilesUri.isEmpty() -> "No files selected."
        firstLine.isNotBlank() && selectedFilesUri.size > 1 -> "$firstLine (+${selectedFilesUri.size - 1} more)"
        firstLine.isNotBlank() -> firstLine
        else -> "${selectedFilesUri.size} file(s) selected."
    }
    Column(Modifier.fillMaxWidth()) {
        Text(summary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSelectFiles, enabled = !isCurrentlyConverting, modifier = Modifier.fillMaxWidth()) { Text("Select CBZ File(s)") }
    }
}

@Composable
fun MihonSelectionCard(mihonDirectoryUri: Uri?, isCurrentlyConverting: Boolean, onSelectMihonDirectory: () -> Unit, onRefresh: () -> Unit, isLoadingMihonManga: Boolean, mihonLoadProgress: Float, mihonManga: List<MihonMangaEntry>, selectedFilesUri: List<Uri>, onToggleSelection: (Uri, Boolean) -> Unit, onToggleGroup: (List<Uri>, Boolean) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text("Directory", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(mihonDirectoryUri?.toString() ?: "None", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = onSelectMihonDirectory, enabled = !isCurrentlyConverting, modifier = Modifier.weight(1f)) { Text(if (mihonDirectoryUri == null) "Select Directory" else "Change Directory") }
            IconButton(onClick = onRefresh, enabled = !isCurrentlyConverting && !isLoadingMihonManga && mihonDirectoryUri != null) { Icon(Icons.Filled.Refresh, "Refresh") }
        }
        if (mihonDirectoryUri != null) {
            if (isLoadingMihonManga) LinearProgressIndicator(progress = mihonLoadProgress, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            var search by rememberSaveable(mihonDirectoryUri) { mutableStateOf("") }
            OutlinedTextField(value = search, onValueChange = { search = it }, label = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val filtered = remember(mihonManga, search) {
                if (search.isBlank()) mihonManga else mihonManga.filter { it.name.contains(search, ignoreCase = true) }
            }
            MangaToggleList(filtered, selectedFilesUri, onToggleSelection, onToggleGroup)
        } else {
            Text("Select a Mihon directory to browse your CBZ files.", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
fun MangaToggleList(manga: List<MihonMangaEntry>, selectedUris: List<Uri>, onToggleSingle: (Uri, Boolean) -> Unit, onToggleGroup: (List<Uri>, Boolean) -> Unit) {
    if (manga.isEmpty()) { Text("No CBZ files found"); return }
    val selectedSet = remember(selectedUris) { selectedUris.toSet() }
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        items(items = manga, key = { it.files.firstOrNull()?.uri?.toString() ?: it.name }) { entry ->
            var expanded by rememberSaveable(entry.name) { mutableStateOf(false) }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = entry.files.all { selectedSet.contains(it.uri) }, onCheckedChange = { onToggleGroup(entry.files.map { f -> f.uri }, it) })
                        Text(entry.name, Modifier.weight(1f).padding(end = 8.dp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                        Icon(if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Toggle", Modifier.clickable { expanded = !expanded })
                    }
                    AnimatedVisibility(expanded) {
                        Column(Modifier.padding(start = 12.dp)) {
                            entry.files.forEach { file ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selectedSet.contains(file.uri), onCheckedChange = { onToggleSingle(file.uri, it) })
                                    Text(file.name, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedFilesList(selectedFiles: List<Uri>, resolveInfo: (Uri) -> SelectedFileInfo, onMove: (Int, Int) -> Unit, onRemove: (Uri) -> Unit) {
    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
        itemsIndexed(items = selectedFiles, key = { _, uri -> uri.toString() }) { index, uri ->
            val info = resolveInfo(uri)
            Card(Modifier.fillMaxWidth().padding(4.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(info.displayName, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (info.parentName != null) Text(info.parentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { if (index > 0) onMove(index, index - 1) }, enabled = index > 0) { Icon(Icons.Default.ArrowUpward, "Up") }
                    IconButton(onClick = { if (index < selectedFiles.size - 1) onMove(index, index + 1) }, enabled = index < selectedFiles.size - 1) { Icon(Icons.Default.ArrowDownward, "Down") }
                    IconButton(onClick = { onRemove(uri) }) { Icon(Icons.Default.Close, "Remove", tint = Color.Red) }
                }
            }
        }
    }
}