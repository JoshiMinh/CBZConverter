package com.joshiminh.cbzconverter.components

import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.MihonMangaEntry

@Composable
fun ManualSelectionCard(
    selectedFileName: String,
    selectedFilesUri: List<Uri>,
    isCurrentlyConverting: Boolean,
    onSelectFiles: () -> Unit
) {
    val firstLine = selectedFileName.lineSequence().firstOrNull { it.isNotBlank() } ?: ""
    val summary = when {
        selectedFilesUri.isEmpty() -> "No files selected."
        firstLine.isNotBlank() && selectedFilesUri.size > 1 -> "$firstLine (+${selectedFilesUri.size - 1} more)"
        firstLine.isNotBlank() -> firstLine
        else -> "${selectedFilesUri.size} file(s) selected."
    }

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSelectFiles,
            enabled = !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Select CBZ File(s)")
        }
    }
}

@Composable
fun MihonSelectionCard(
    mihonDirectoryUri: Uri?,
    isCurrentlyConverting: Boolean,
    onSelectMihonDirectory: () -> Unit,
    isLoadingMihonManga: Boolean,
    mihonLoadProgress: Float,
    mihonManga: List<MihonMangaEntry>,
    selectedFilesUri: List<Uri>,
    onToggleSelection: (Uri) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "Directory",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = mihonDirectoryUri?.toString() ?: "None",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onSelectMihonDirectory,
            enabled = !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mihonDirectoryUri == null) "Select Directory" else "Change Directory")
        }

        if (mihonDirectoryUri != null) {
            Spacer(Modifier.height(12.dp))
            if (isLoadingMihonManga) {
                LinearProgressIndicator(
                    progress = mihonLoadProgress,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
            var searchQuery by rememberSaveable(mihonDirectoryUri) { mutableStateOf("") }
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Search") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            val filtered = remember(mihonManga, searchQuery) {
                if (searchQuery.isBlank()) {
                    mihonManga
                } else {
                    val regex = runCatching { Regex(searchQuery, RegexOption.IGNORE_CASE) }.getOrNull()
                    if (regex != null) {
                        mihonManga.filter { regex.containsMatchIn(it.name) }
                    } else {
                        val lower = searchQuery.lowercase()
                        mihonManga.filter { it.name.lowercase().contains(lower) }
                    }
                }
            }
            MangaToggleList(
                manga = filtered,
                selectedUris = selectedFilesUri,
                onToggle = onToggleSelection
            )
        } else {
            Spacer(Modifier.height(12.dp))
            Text("Select a Mihon directory to browse your CBZ files.")
        }
    }
}