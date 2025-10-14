package com.joshiminh.cbzconverter

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.R
import com.joshiminh.cbzconverter.backend.ContextHelper
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.backend.MihonMangaEntry
import com.joshiminh.cbzconverter.components.ConfigButtonItem
import com.joshiminh.cbzconverter.components.ConfigNumberItem
import com.joshiminh.cbzconverter.components.ConfigSwitchItem
import com.joshiminh.cbzconverter.components.ManualSelectionCard
import com.joshiminh.cbzconverter.components.MihonSelectionCard
import com.joshiminh.cbzconverter.components.SectionCard
import com.joshiminh.cbzconverter.components.SelectedFilesList
import com.joshiminh.cbzconverter.components.Spacer12Divider
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

@Composable
fun MihonScreen(
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

    Scaffold { inner ->
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
    maxNumberOfPages: Int,
    batchSize: Int,
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
    val focusManager = LocalFocusManager.current
    var selectionMode by rememberSaveable { mutableStateOf(SelectionMode.Mihon) }

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
        SectionCard(
            title = when (selectionMode) {
                SelectionMode.Mihon -> "Mihon Manga Selection"
                SelectionMode.Manual -> "Direct Selection"
            },
            iconResId = when (selectionMode) {
                SelectionMode.Mihon -> R.drawable.mihon
                SelectionMode.Manual -> R.drawable.cbz
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
                            onToggleSelection = { uri, isSelected ->
                                viewModel.setFileSelection(uri, isSelected)
                            },
                            onToggleGroup = { uris, isSelected ->
                                viewModel.setFilesSelection(uris, isSelected)
                            }
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
                        resolveInfo = viewModel::getSelectedFileInfo,
                        onMove = { from, to -> viewModel.moveSelectedFile(from, to) },
                        onRemove = { uri -> viewModel.setFileSelection(uri, false) }
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.elevatedCardElevation()
        ) {
            var expanded by rememberSaveable { mutableStateOf(true) }

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

                        ConfigSwitchItem(
                            title = "Merge All Files Into One",
                            infoText = "Combine all selected CBZ files into a single PDF. If no custom name is set, the first file's name is used.",
                            checked = overrideMergeFiles,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleMergeFilesOverride(it) }

                        if (!canMergeSelection && selectedFilesUri.size > 1) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Warning: selected files come from different manga. Double-check the order before merging.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Spacer12Divider()

                        ConfigSwitchItem(
                            title = "Compress Output PDF",
                            infoText = "Use compression to reduce PDF file size (slower processing).",
                            checked = compressOutputPdf,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleCompressOutputPdf(it) }

                        Spacer12Divider()

                        ConfigSwitchItem(
                            title = "Autonaming with Chapters",
                            infoText = "Automatically name outputs using manga title and detected chapter numbers.",
                            checked = autoNameWithChapters,
                            enabled = !isCurrentlyConverting
                        ) { viewModel.toggleAutoNameWithChapters(it) }

                        Spacer12Divider()

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

        SectionCard(title = "Status") {
            val taskColor = when {
                currentTaskStatus.contains("Completed", ignoreCase = true) ||
                        currentTaskStatus.contains("Created", ignoreCase = true) -> Color(0xFF4CAF50)
                currentTaskStatus.contains("Failed", ignoreCase = true) ||
                        currentTaskStatus.contains("Error", ignoreCase = true) -> Color(0xFFF44336)
                else -> Color.Unspecified
            }

            Text(
                text = "Progress: $currentTaskStatus",
                fontWeight = FontWeight.SemiBold,
                color = taskColor
            )

            Spacer(Modifier.height(8.dp))

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
        ) {
            Text("Export")
        }

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
                val resolvedUri = when {
                    overrideOutputDirectoryUri == null -> {
                        DocumentsContract.buildDocumentUri(
                            "com.android.externalstorage.documents",
                            "primary:Download"
                        )
                    }

                    DocumentsContract.isTreeUri(overrideOutputDirectoryUri) -> {
                        DocumentsContract.buildDocumentUriUsingTree(
                            overrideOutputDirectoryUri,
                            DocumentsContract.getTreeDocumentId(overrideOutputDirectoryUri)
                        )
                    }

                    else -> overrideOutputDirectoryUri
                }

                val explorerIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(resolvedUri, DocumentsContract.Document.MIME_TYPE_DIR)
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, resolvedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                val fallbackIntent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                    putExtra(DocumentsContract.EXTRA_INITIAL_URI, resolvedUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                runCatching { activity.startActivity(explorerIntent) }
                    .onFailure { activity.startActivity(fallbackIntent) }
            },
            enabled = !isCurrentlyConverting,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open File Explorer")
        }

        Spacer(Modifier.height(12.dp))
    }
}