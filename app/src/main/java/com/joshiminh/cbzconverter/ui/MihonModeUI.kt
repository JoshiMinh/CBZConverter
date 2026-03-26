package com.joshiminh.cbzconverter.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.R
import com.joshiminh.cbzconverter.core.MainViewModel
import com.joshiminh.cbzconverter.core.MihonMangaEntry
import kotlin.math.abs

private enum class SelectionMode { Mihon, Manual }

@Composable
fun MihonMode(
    viewModel: MainViewModel, activity: ComponentActivity, isCurrentlyConverting: Boolean,
    selectedFileName: String, selectedFilesUri: List<Uri>, canMergeSelection: Boolean,
    mihonManga: List<MihonMangaEntry>, currentTaskStatus: String, currentSubTaskStatus: String,
    maxPages: Int, batchSize: Int, overrideMerge: Boolean, overrideOutUri: Uri?,
    hasOut: Boolean, compress: Boolean, autoName: Boolean,
    fileLauncher: ManagedActivityResultLauncher<Array<String>, List<Uri>>,
    dirLauncher: ManagedActivityResultLauncher<Uri?, Uri?>,
    mihonDirUri: Uri?, isLoadingMihon: Boolean, mihonProgress: Float,
    onSelectMihonDir: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var selMode by rememberSaveable { mutableStateOf(SelectionMode.Mihon) }

    LaunchedEffect(mihonDirUri, isLoadingMihon, mihonManga) {
        if (mihonDirUri != null && mihonManga.isEmpty() && !isLoadingMihon) viewModel.refreshMihonManga()
    }

    Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
        SectionCard(
            title = if (selMode == SelectionMode.Mihon) "Mihon Selection" else "Direct Selection",
            iconResId = if (selMode == SelectionMode.Mihon) R.drawable.mihon else R.drawable.cbz,
            action = {
                Row {
                    IconButton(onClick = { selMode = if (selMode == SelectionMode.Mihon) SelectionMode.Manual else SelectionMode.Mihon }) { Icon(Icons.Filled.SwapHoriz, "Switch") }
                    IconButton(onClick = { viewModel.updateSelectedFileUrisFromUserInput(emptyList()) }) { Icon(Icons.Filled.Delete, "Clear") }
                }
            }
        ) {
            val threshold = with(LocalDensity.current) { 64.dp.toPx() }
            var drag by remember { mutableStateOf(0f) }
            Box(Modifier.fillMaxWidth().pointerInput(selMode) {
                detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, amt -> drag += amt }, onDragEnd = {
                    if (abs(drag) >= threshold) selMode = if (selMode == SelectionMode.Mihon) SelectionMode.Manual else SelectionMode.Mihon
                    drag = 0f
                })
            }) {
                Crossfade(selMode, label = "Mode") { mode ->
                    if (mode == SelectionMode.Mihon) MihonSelectionCard(mihonDirUri, isCurrentlyConverting, onSelectMihonDir, { viewModel.refreshMihonManga() }, isLoadingMihon, mihonProgress, mihonManga, selectedFilesUri, { u, s -> viewModel.setFileSelection(u, s) }, { us, s -> viewModel.setFilesSelection(us, s) })
                    else ManualSelectionCard(selectedFileName, selectedFilesUri, isCurrentlyConverting) { viewModel.checkPermissionAndSelectFileAction(activity, fileLauncher) }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionCard("Selected File(s)") {
            if (selectedFilesUri.isEmpty()) Card(Modifier.fillMaxWidth()) { Text("None", Modifier.padding(12.dp)) }
            else Card(Modifier.fillMaxWidth()) { SelectedFilesList(selectedFilesUri, viewModel::getSelectedFileInfo, { f, t -> viewModel.moveSelectedFile(f, t) }, { viewModel.setFileSelection(it, false) }) }
        }

        Spacer(Modifier.height(8.dp))
        ElevatedCard(Modifier.fillMaxWidth()) {
            var exp by rememberSaveable { mutableStateOf(true) }
            Column(Modifier.padding(12.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Configurations", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { exp = !exp }) { Icon(if (exp) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, "Exp") }
                }
                AnimatedVisibility(exp) {
                    Column {
                        ConfigNumberItem("Max Pages", "Images per PDF", maxPages.toString(), !isCurrentlyConverting) { viewModel.updateMaxNumberOfPagesSizeFromUserInput(it); focusManager.clearFocus() }
                        Spacer12Divider()
                        ConfigNumberItem("Batch Size", "Memory batch size", batchSize.toString(), !isCurrentlyConverting) { viewModel.updateBatchSizeFromUserInput(it); focusManager.clearFocus() }
                        ConfigSwitchItem("Merge All", "Combine selected CBZs", overrideMerge, !isCurrentlyConverting) { viewModel.toggleMergeFilesOverride(it) }
                        if (!canMergeSelection && selectedFilesUri.size > 1) Text("Warning: files from different manga.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer12Divider()
                        ConfigSwitchItem("Compress", "Reduce file size", compress, !isCurrentlyConverting) { viewModel.toggleCompressOutputPdf(it) }
                        Spacer12Divider()
                        ConfigSwitchItem("Autonaming", "Use manga title", autoName, !isCurrentlyConverting) { viewModel.toggleAutoNameWithChapters(it) }
                        Spacer12Divider()
                        ConfigButtonItem("Output Folder", "Where to save PDFs", overrideOutUri?.toString() ?: "Not set", "Select Folder", !isCurrentlyConverting) { viewModel.checkPermissionAndSelectDirectoryAction(activity, dirLauncher) }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        SectionCard("Status") {
            val color = when {
                currentTaskStatus.contains("Completed") -> Color(0xFF4CAF50)
                currentTaskStatus.contains("Failed") -> Color(0xFFF44336)
                else -> Color.Unspecified
            }
            Text("Progress: $currentTaskStatus", fontWeight = FontWeight.SemiBold, color = color)
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.height(130.dp)) { items(currentSubTaskStatus.lines()) { Text(it) } }
        }

        Spacer(Modifier.height(12.dp))
        Button(onClick = { if (selectedFilesUri.isNotEmpty()) viewModel.convertToPDF(selectedFilesUri, true) }, enabled = selectedFilesUri.isNotEmpty() && !isCurrentlyConverting && hasOut, modifier = Modifier.fillMaxWidth()) { Text("Export") }
    }
}