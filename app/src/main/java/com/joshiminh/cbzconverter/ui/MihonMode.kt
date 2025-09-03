/*TODO:
- [x] Add PDF Compression
 */
@file:Suppress("SameParameterValue")

package com.joshiminh.cbzconverter.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.backend.MihonMangaEntry
import androidx.documentfile.provider.DocumentFile

@Composable
fun MihonMode(
    selectedFileName: String,
    viewModel: MainViewModel,
    activity: ComponentActivity,
    isCurrentlyConverting: Boolean,
    selectedFilesUri: List<Uri>,
    mihonManga: List<MihonMangaEntry>,
    currentTaskStatus: String,
    currentSubTaskStatus: String,

    // config states (provided from VM)
    maxNumberOfPages: Int,
    batchSize: Int,
    overrideSortOrderToUseOffset: Boolean,
    overrideMergeFiles: Boolean,
    overrideOutputDirectoryUri: Uri?,
    compressOutputPdf: Boolean,
    autoNameWithChapters: Boolean,
    directoryPickerLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    mihonDirectoryUri: Uri?,
    isLoadingMihonManga: Boolean,
    mihonLoadProgress: Float,
    onSelectMihonDirectory: () -> Unit
) {
    val focusManager: FocusManager = LocalFocusManager.current
    val context = LocalContext.current
    val canMerge = viewModel.areSelectedFilesFromSameParent()

    LaunchedEffect(selectedFilesUri) {
        if (!canMerge && overrideMergeFiles) {
            viewModel.toggleMergeFilesOverride(false)
        }
    }

    // Automatically refresh the Mihon manga list whenever a directory has been
    // previously selected (e.g. on app reopen). This avoids requiring the user to
    // reselect the directory each time the screen is opened.
    LaunchedEffect(mihonDirectoryUri) {
        if (mihonDirectoryUri != null) {
            viewModel.refreshMihonManga()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== MIHON DIRECTORY SELECTION =====
        SectionCard(title = "Select Mihon Directory") {
            Text(text = mihonDirectoryUri?.toString() ?: "None")
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onSelectMihonDirectory,
                enabled = !isCurrentlyConverting,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (mihonDirectoryUri == null) "Select Directory" else "Change Directory")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== MANGA OPTIONS =====
        SectionCard(title = "Manga Selection") {
            if (mihonDirectoryUri != null) {
                if (isLoadingMihonManga) {
                    LinearProgressIndicator(
                        progress = mihonLoadProgress,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                }
                var searchQuery by rememberSaveable { mutableStateOf("") }
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
                    onToggle = { viewModel.toggleFileSelection(it) }
                )
            } else {
                Text("Select a Mihon directory above")
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== SELECTED FILES =====
        SectionCard(title = "Selected File(s)") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors()
            ) {
                val parentName = selectedFilesUri.firstOrNull()?.let { uri ->
                    DocumentFile.fromSingleUri(context, uri)?.parentFile?.name
                }
                val selectedSummary = when {
                    selectedFilesUri.isEmpty() -> "None"
                    selectedFilesUri.size > 1 && !parentName.isNullOrBlank() ->
                        "$parentName  (+${selectedFilesUri.size - 1} more)"
                    !parentName.isNullOrBlank() -> parentName
                    selectedFileName.isNotBlank() -> selectedFileName
                    else -> "None"
                }

                Text(
                    text = selectedSummary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )
            }

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = {
                    if (selectedFilesUri.isNotEmpty()) {
                        viewModel.convertToPDF(selectedFilesUri, useParentDirectoryName = true)
                    }
                },
                enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Convert to PDF") }

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = {
                    if (selectedFilesUri.isNotEmpty()) {
                        viewModel.convertToEPUB(selectedFilesUri, useParentDirectoryName = true)
                    }
                },
                enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Convert to EPUB") }
        }

        Spacer(Modifier.height(16.dp))

        // ===== CONFIGURATIONS (auto-applied) =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            var expanded by rememberSaveable { mutableStateOf(true) } // collapsible section

            Column(Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Configurations",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column {
                        Spacer(Modifier.height(8.dp))

                        // Max pages per PDF — auto apply on valid number
                        ConfigNumberItem(
                            title = "Max Pages per PDF",
                            infoText = "How many images go into a single PDF. Lower = more output files.",
                            value = maxNumberOfPages.toString(),
                            enabled = !isCurrentlyConverting,
                            onValidNumber = { newValue ->
                                viewModel.updateMaxNumberOfPagesSizeFromUserInput(newValue)
                                focusManager.clearFocus()
                            }
                        )

                        Spacer12Divider()

                        // Batch size — auto apply on valid number
                        ConfigNumberItem(
                            title = "Memory Batch Size",
                            infoText = "Processing chunk size. Reduce if you see OutOfMemory errors; increase for speed on strong devices.",
                            value = batchSize.toString(),
                            enabled = !isCurrentlyConverting,
                            onValidNumber = { newValue ->
                                viewModel.updateBatchSizeFromUserInput(newValue)
                                focusManager.clearFocus()
                            }
                        )

                        Spacer12Divider()

                        // Sort order override — instant toggle
                        ConfigSwitchItem(
                            title = "Use Offset Sort Order",
                            infoText = "Override default alphabetical sort using numeric offsets embedded in filenames.",
                            checked = overrideSortOrderToUseOffset,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleOverrideSortOrderToUseOffset(it) }

                        Spacer12Divider()

                        // Merge files — instant toggle
                        ConfigSwitchItem(
                            title = "Merge All Files Into One",
                            infoText = "Combine all selected CBZ files into a single PDF. If no custom name is set, the first file's name is used.",
                            checked = overrideMergeFiles,
                            enabled = !isCurrentlyConverting && canMerge
                        ) { viewModel.toggleMergeFilesOverride(it) }

                        if (!canMerge && selectedFilesUri.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Cannot merge files from different manga.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer12Divider()

                        // PDF compression — instant toggle
                        ConfigSwitchItem(
                            title = "Compress Output PDF",
                            infoText = "Use compression to reduce PDF file size (slower processing).",
                            checked = compressOutputPdf,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleCompressOutputPdf(it) }

                        Spacer12Divider()

                        // Autonaming with chapters — instant toggle
                        ConfigSwitchItem(
                            title = "Autonaming with Chapters",
                            infoText = "Automatically name outputs using manga title and detected chapter numbers.",
                            checked = autoNameWithChapters,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleAutoNameWithChapters(it) }

                        Spacer12Divider()

                        // Output directory override
                        ConfigButtonItem(
                            title = "Output Directory",
                            infoText = "Pick where converted PDFs will be saved.",
                            primaryText = overrideOutputDirectoryUri?.toString() ?: "Not set",
                            buttonText = "Select Output Directory",
                            enabled = !isCurrentlyConverting
                        ) {
                            viewModel.checkPermissionAndSelectDirectoryAction(activity, directoryPickerLauncher)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== STATUS =====
        SectionCard(title = "Status") {
            // Decide color for task status
            val taskColor = when {
                currentTaskStatus.contains("Completed", ignoreCase = true) ||
                        currentTaskStatus.contains("Created", ignoreCase = true)  -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // light green
                currentTaskStatus.contains("Failed", ignoreCase = true) ||
                        currentTaskStatus.contains("Error", ignoreCase = true)   -> androidx.compose.ui.graphics.Color(0xFFF44336) // red
                else -> androidx.compose.ui.graphics.Color.Unspecified
            }

            // Progress + Current Task inline
            Text(
                text = "Progress: $currentTaskStatus",
                fontWeight = FontWeight.SemiBold,
                color = taskColor
            )

            Spacer(Modifier.height(8.dp))

            // Show sub-task lines below
            LazyColumn(Modifier.height(130.dp)) {
                items(currentSubTaskStatus.lines()) { line ->
                    Text(line)
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // ===== SEND TO KINDLE =====
        Button(
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.amazon.com/gp/sendtokindle")
                )
                activity.startActivity(intent)
            },
            enabled = !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send to Kindle")
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun MangaToggleList(
    manga: List<MihonMangaEntry>,
    selectedUris: List<Uri>,
    onToggle: (Uri) -> Unit
) {
    if (manga.isEmpty()) {
        Text("No CBZ files found")
        return
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        items(manga) { entry ->
            var expanded by rememberSaveable { mutableStateOf(false) }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = CardDefaults.shape
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.name,
                            modifier = Modifier.weight(1f),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand"
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(Modifier.padding(start = 16.dp)) {
                            entry.files.forEach { file ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedUris.contains(file.uri),
                                        onCheckedChange = { onToggle(file.uri) }
                                    )
                                    Text(
                                        file.name,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Small helpers ---------- */

@Composable private fun Spacer12Divider() {
    Spacer(Modifier.height(12.dp))
    Divider()
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun TitleWithInfo(
    title: String,
    infoText: String
) {
    var showInfo by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = { showInfo = true }) {
            Icon(Icons.Outlined.Info, contentDescription = "Info")
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            },
            title = { Text(title) },
            text = { Text(infoText) }
        )
    }
}

/* ---------- Reusable compact config rows (with info buttons) ---------- */

/**
 * Number input that auto-applies to the VM when the text is a valid Int > 0.
 * Keeps showing the user's raw input; highlights error when invalid.
 */
@Composable
private fun ConfigNumberItem(
    title: String,
    infoText: String,
    value: String,
    enabled: Boolean,
    onValidNumber: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    var error by remember { mutableStateOf<String?>(null) }

    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val trimmed = input.trim()
            val intVal = trimmed.toIntOrNull()
            error = when {
                trimmed.isEmpty() -> "Enter a number"
                intVal == null     -> "Enter a valid number"
                intVal <= 0        -> "Must be greater than 0"
                else               -> null
            }
            if (error == null) {
                onValidNumber(trimmed)
            }
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
    Spacer(Modifier.height(4.dp))
}

/**
 * Text input that auto-applies to the VM on every change.
 * Blank => means "no override" and VM should fall back to default filename.
 */
@Composable
private fun ConfigTextItem(
    title: String,
    infoText: String,
    text: String,
    enabled: Boolean,
    supportingText: String,
    onTextChange: (String) -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Type new name (leave blank for default)") },
        supportingText = { Text(supportingText) }
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun ConfigSwitchItem(
    title: String,
    infoText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun ConfigButtonItem(
    title: String,
    infoText: String,
    primaryText: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    Text(primaryText)
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onClick, enabled = enabled) { Text(buttonText) }
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}