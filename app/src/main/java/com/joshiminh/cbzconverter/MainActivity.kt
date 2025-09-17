@file:Suppress("SameParameterValue")

package com.joshiminh.cbzconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.documentfile.provider.DocumentFile
import com.joshiminh.cbzconverter.backend.ContextHelper
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.backend.MihonMangaEntry
import com.joshiminh.cbzconverter.ui.CbzConverterTheme
import kotlin.math.abs

class MainActivity : ComponentActivity() {

    private val contextHelper by lazy { ContextHelper(applicationContext) }
    private val viewModel: MainViewModel by viewModels {
        MainViewModel.Factory(contextHelper)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            CbzConverterTheme {
                MihonScreen(
                    activity = this@MainActivity,
                    viewModel = viewModel
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MihonScreen(
    activity: ComponentActivity,
    viewModel: MainViewModel
) {
    val isCurrentlyConverting by viewModel.isCurrentlyConverting.collectAsState()
    val currentTaskStatus by viewModel.currentTaskStatus.collectAsState()
    val currentSubTaskStatus by viewModel.currentSubTaskStatus.collectAsState()

    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFilesUri by viewModel.selectedFileUri.collectAsState()
    val canMergeSelection by viewModel.canMergeSelection.collectAsState()

    val maxNumberOfPages by viewModel.maxNumberOfPages.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val overrideSortOrderToUseOffset by viewModel.overrideSortOrderToUseOffset.collectAsState()
    val overrideMergeFiles by viewModel.overrideMergeFiles.collectAsState()
    val overrideOutputDirectoryUri by viewModel.overrideOutputDirectoryUri.collectAsState()
    val compressOutputPdf by viewModel.compressOutputPdf.collectAsState()
    val autoNameWithChapters by viewModel.autoNameWithChapters.collectAsState()
    val mihonDirectoryUri by viewModel.mihonDirectoryUri.collectAsState()
    val mihonMangaEntries by viewModel.mihonMangaEntries.collectAsState()
    val isLoadingMihonManga by viewModel.isLoadingMihonManga.collectAsState()
    val mihonLoadProgress by viewModel.mihonLoadProgress.collectAsState()

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            viewModel.updateSelectedFileUrisFromUserInput(uris)
        }

    val directoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateOverrideOutputPathFromUserInput(it) }
        }

    val mihonDirectoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateMihonDirectoryUri(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mihon Mode") }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .padding(inner)
                .fillMaxSize()
        ) {
            MihonMode(
                viewModel = viewModel,
                activity = activity,
                isCurrentlyConverting = isCurrentlyConverting,
                selectedFileName = selectedFileName,
                selectedFilesUri = selectedFilesUri,
                canMergeSelection = canMergeSelection,
                mihonManga = mihonMangaEntries,
                currentTaskStatus = currentTaskStatus,
                currentSubTaskStatus = currentSubTaskStatus,
                maxNumberOfPages = maxNumberOfPages,
                batchSize = batchSize,
                overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
                overrideMergeFiles = overrideMergeFiles,
                overrideOutputDirectoryUri = overrideOutputDirectoryUri,
                compressOutputPdf = compressOutputPdf,
                autoNameWithChapters = autoNameWithChapters,
                filePickerLauncher = filePickerLauncher,
                directoryPickerLauncher = directoryPickerLauncher,
                mihonDirectoryUri = mihonDirectoryUri,
                isLoadingMihonManga = isLoadingMihonManga,
                mihonLoadProgress = mihonLoadProgress,
                onSelectMihonDirectory = {
                    viewModel.checkPermissionAndSelectDirectoryAction(
                        activity,
                        mihonDirectoryPickerLauncher
                    )
                }
            )
        }
    }
}

private enum class SelectionMode { Mihon, Manual }

@Composable
private fun MihonMode(
    viewModel: MainViewModel,
    activity: ComponentActivity,
    isCurrentlyConverting: Boolean,
    selectedFileName: String,
    selectedFilesUri: List<Uri>,
    canMergeSelection: Boolean,
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
    filePickerLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
    directoryPickerLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    mihonDirectoryUri: Uri?,
    isLoadingMihonManga: Boolean,
    mihonLoadProgress: Float,
    onSelectMihonDirectory: () -> Unit
) {
    val focusManager: FocusManager = LocalFocusManager.current
    var selectionMode by rememberSaveable { mutableStateOf(SelectionMode.Mihon) }

    LaunchedEffect(canMergeSelection, overrideMergeFiles) {
        if (!canMergeSelection && overrideMergeFiles) {
            viewModel.toggleMergeFilesOverride(false)
        }
    }

    // Automatically refresh the Mihon manga list whenever a directory has been
    // previously selected (e.g. on app reopen). This avoids requiring the user to
    // reselect the directory each time the screen is opened.
    LaunchedEffect(mihonDirectoryUri, isLoadingMihonManga, mihonManga) {
        if (mihonDirectoryUri != null && mihonManga.isEmpty() && !isLoadingMihonManga) {
            viewModel.refreshMihonManga()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ===== LIBRARY & FILE SELECTION =====
        SectionCard(
            title = when (selectionMode) {
                SelectionMode.Mihon -> "Manga Selection"
                SelectionMode.Manual -> "Direct Selection"
            },
            action = {
                IconButton(onClick = { viewModel.updateSelectedFileUrisFromUserInput(emptyList()) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Clear selection")
                }
            }
        ) {
            val thresholdPx = with(LocalDensity.current) { 64.dp.toPx() }
            var accumulatedDrag by remember { mutableStateOf(0f) }
            val hintText = when (selectionMode) {
                SelectionMode.Mihon -> "Swipe to open direct file selection."
                SelectionMode.Manual -> "Swipe to browse Mihon downloads."
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(selectionMode) {
                        detectHorizontalDragGestures(
                            onDragStart = { accumulatedDrag = 0f },
                            onHorizontalDrag = { _, dragAmount -> accumulatedDrag += dragAmount },
                            onDragCancel = { accumulatedDrag = 0f },
                            onDragEnd = {
                                if (abs(accumulatedDrag) >= thresholdPx) {
                                    selectionMode = if (selectionMode == SelectionMode.Mihon) {
                                        SelectionMode.Manual
                                    } else {
                                        SelectionMode.Mihon
                                    }
                                }
                                accumulatedDrag = 0f
                            }
                        )
                    }
            ) {
                Crossfade(
                    targetState = selectionMode,
                    modifier = Modifier.fillMaxWidth(),
                    label = "SelectionModeSwitcher"
                ) { mode ->
                    when (mode) {
                        SelectionMode.Mihon -> MihonSelectionCard(
                            mihonDirectoryUri = mihonDirectoryUri,
                            isCurrentlyConverting = isCurrentlyConverting,
                            onSelectMihonDirectory = onSelectMihonDirectory,
                            isLoadingMihonManga = isLoadingMihonManga,
                            mihonLoadProgress = mihonLoadProgress,
                            mihonManga = mihonManga,
                            selectedFilesUri = selectedFilesUri,
                            onToggleSelection = { viewModel.toggleFileSelection(it) }
                        )

                        SelectionMode.Manual -> ManualSelectionCard(
                            selectedFileName = selectedFileName,
                            selectedFilesUri = selectedFilesUri,
                            isCurrentlyConverting = isCurrentlyConverting,
                            onSelectFiles = {
                                viewModel.checkPermissionAndSelectFileAction(activity, filePickerLauncher)
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = hintText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(8.dp))

        // ===== SELECTED FILES =====
        SectionCard(title = "Selected File(s)") {
            if (selectedFilesUri.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    Text(
                        text = "None",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            } else {
                Text(
                    text = "Export follows this order.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors()
                ) {
                    SelectedFilesList(
                        selectedFiles = selectedFilesUri,
                        onMove = { from, to -> viewModel.moveSelectedFile(from, to) },
                        onRemove = { uri -> viewModel.toggleFileSelection(uri) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ===== CONFIGURATIONS (auto-applied) =====
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            var expanded by rememberSaveable { mutableStateOf(true) } // collapsible section

            Column(Modifier.padding(12.dp)) {
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
                            enabled = !isCurrentlyConverting && canMergeSelection
                        ) { viewModel.toggleMergeFilesOverride(it) }

                        if (!canMergeSelection && selectedFilesUri.size > 1) {
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

        Spacer(Modifier.height(8.dp))

        // ===== STATUS =====
        SectionCard(title = "Status") {
            // Decide color for task status
            val taskColor = when {
                currentTaskStatus.contains("Completed", ignoreCase = true) ||
                        currentTaskStatus.contains("Created", ignoreCase = true) -> Color(0xFF4CAF50) // light green
                currentTaskStatus.contains("Failed", ignoreCase = true) ||
                        currentTaskStatus.contains("Error", ignoreCase = true) -> Color(0xFFF44336) // red
                else -> Color.Unspecified
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

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = {
                if (selectedFilesUri.isNotEmpty()) {
                    viewModel.convertToPDF(selectedFilesUri, useParentDirectoryName = true)
                }
            },
            enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Export") }

        Spacer(Modifier.height(8.dp))

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

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val outputUri = overrideOutputDirectoryUri
                    ?: Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload")
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(outputUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(intent)
            },
            enabled = !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open File Explorer")
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ManualSelectionCard(
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
private fun MihonSelectionCard(
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

@Composable
private fun SelectedFilesList(
    selectedFiles: List<Uri>,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        itemsIndexed(selectedFiles, key = { _, uri -> uri }) { index, uri ->
            Column {
                SelectedFileRow(
                    index = index,
                    uri = uri,
                    canMoveUp = index > 0,
                    canMoveDown = index < selectedFiles.lastIndex,
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                    onRemove = { onRemove(uri) }
                )
                if (index < selectedFiles.lastIndex) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SelectedFileRow(
    index: Int,
    uri: Uri,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val (displayName, parentName) = remember(uri, context) {
        val document = DocumentFile.fromSingleUri(context, uri)
        val name = document?.name ?: uri.lastPathSegment ?: "Unknown"
        val parent = document?.parentFile?.name
        name to parent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. $displayName",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            parentName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                imageVector = Icons.Filled.ArrowUpward,
                contentDescription = "Move up"
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                imageVector = Icons.Filled.ArrowDownward,
                contentDescription = "Move down"
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Remove from selection"
            )
        }
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

    val selectedUriSet = remember(selectedUris) { selectedUris.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        items(
            items = manga,
            key = { entry -> entry.files.firstOrNull()?.uri?.toString() ?: entry.name }
        ) { entry ->
            var expanded by rememberSaveable(entry.files.firstOrNull()?.uri?.toString() ?: entry.name) {
                mutableStateOf(false)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = CardDefaults.shape
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = entry.files.all { selectedUriSet.contains(it.uri) }
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                entry.files.forEach { file ->
                                    val contains = selectedUriSet.contains(file.uri)
                                    if (checked && !contains) onToggle(file.uri)
                                    else if (!checked && contains) onToggle(file.uri)
                                }
                            }
                        )
                        Text(
                            entry.name,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { expanded = !expanded },
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.clickable { expanded = !expanded }
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                    Column(Modifier.padding(start = 12.dp)) {
                            entry.files.forEach { file ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedUriSet.contains(file.uri),
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
                intVal == null -> "Enter a valid number"
                intVal <= 0 -> "Must be greater than 0"
                else -> null
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
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation()
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}
