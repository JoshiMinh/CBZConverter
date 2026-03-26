package com.joshiminh.cbzconverter.ui

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.joshiminh.cbzconverter.core.MainViewModel

@Composable
fun MihonScreen(activity: ComponentActivity, viewModel: MainViewModel) {
    val isConverting by viewModel.isCurrentlyConverting.collectAsState()
    val taskStatus by viewModel.currentTaskStatus.collectAsState()
    val subTaskStatus by viewModel.currentSubTaskStatus.collectAsState()
    val fileName by viewModel.selectedFileName.collectAsState()
    val fileUri by viewModel.selectedFileUri.collectAsState()
    val canMerge by viewModel.canMergeSelection.collectAsState()
    val maxPages by viewModel.maxNumberOfPages.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val overrideMerge by viewModel.overrideMergeFiles.collectAsState()
    val outUri by viewModel.overrideOutputDirectoryUri.collectAsState()
    val hasOut by viewModel.hasWritableOutputDirectory.collectAsState()
    val compress by viewModel.compressOutputPdf.collectAsState()
    val autoName by viewModel.autoNameWithChapters.collectAsState()
    val mihonDirUri by viewModel.mihonDirectoryUri.collectAsState()
    val mihonManga by viewModel.mihonMangaEntries.collectAsState()
    val isLoading by viewModel.isLoadingMihonManga.collectAsState()
    val progress by viewModel.mihonLoadProgress.collectAsState()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.updateSelectedFileUrisFromUserInput(it) }
    val dirPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { viewModel.updateOverrideOutputPathFromUserInput(it) } }
    val mihonPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let { viewModel.updateMihonDirectoryUri(it) } }

    Scaffold { inner ->
        Column(Modifier.padding(inner).fillMaxSize()) {
            MihonMode(
                viewModel, activity, isConverting, fileName, fileUri, canMerge, mihonManga,
                taskStatus, subTaskStatus, maxPages, batchSize, overrideMerge, outUri, hasOut,
                compress, autoName, filePicker, dirPicker, mihonDirUri, isLoading, progress
            ) { viewModel.checkPermissionAndSelectDirectoryAction(activity, mihonPicker) }
        }
    }
}