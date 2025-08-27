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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.backend.MihonMangaEntry

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
    overrideFileName: String,
    overrideOutputDirectoryUri: Uri?,
    compressOutputPdf: Boolean,
    autoNameWithChapters: Boolean,
    directoryPickerLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    mihonDirectoryUri: Uri?,
    onSelectMihonDirectory: () -> Unit
) {
    val focusManager: FocusManager = LocalFocusManager.current
    val canMerge = viewModel.areSelectedFilesFromSameParent()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 8.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== MIHON DIRECTORY SELECTION =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            Column(Modifier.padding(8.dp)) {
                Text("Select Mihon Directory", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
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
        }

        Spacer(Modifier.height(16.dp))

        // ===== MANGA OPTIONS =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            Column(Modifier.padding(8.dp)) {
                Text("Manga Selection", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (mihonDirectoryUri != null) {
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
        }

        Spacer(Modifier.height(16.dp))

        // ===== SELECTED FILES =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            Column(Modifier.padding(8.dp)) {
                Text("Selected File(s)", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    val selectedSummary =
                        if (selectedFilesUri.size > 1 && selectedFileName.isNotBlank())
                            "$selectedFileName  (+${selectedFilesUri.size - 1} more)"
                        else selectedFileName.ifBlank { "None" }

                    Text(
                        text = selectedSummary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { if (selectedFilesUri.isNotEmpty()) viewModel.convertToPDF(selectedFilesUri) },
                    enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Convert to PDF") }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { if (selectedFilesUri.isNotEmpty()) viewModel.convertToEPUB(selectedFilesUri) },
                    enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Convert to EPUB") }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ===== CONFIGURATIONS (auto-applied) =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            var expanded by rememberSaveable { mutableStateOf(true) } // collapsible section

            Column(Modifier.padding(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Configurations",
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

                        // File name override — auto apply as the user types.
                        // IMPORTANT: if empty => send empty string to VM to mean "no override" (fallback to default name).
                        var editingName by remember(overrideFileName) { mutableStateOf(overrideFileName) }
                        ConfigTextItem(
                            title = "Override Output Filename",
                            infoText = "Set a custom output name (no extension). Leave blank to auto-name.",
                            text = editingName,
                            enabled = !isCurrentlyConverting && selectedFilesUri.isNotEmpty(),
                            supportingText = "Current: ${overrideFileName.ifBlank { "— (default)" }}",
                            onTextChange = { newText ->
                                editingName = newText
                                // Trim and push to VM immediately; blank => default behavior
                                viewModel.updateOverrideFileNameFromUserInput(newText.trim())
                            }
                        )

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
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.Start
            ) {
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