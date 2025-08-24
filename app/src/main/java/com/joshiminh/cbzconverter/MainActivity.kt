package com.joshiminh.cbzconverter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.ContextHelper
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.theme.CbzConverterTheme
import com.joshiminh.cbzconverter.ui.mihon.MihonCbzConverterPage
import com.joshiminh.cbzconverter.ui.normal.CbzConverterPage

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = getPreferences(MODE_PRIVATE)
        setContent {
            CbzConverterTheme {
                var selectedMode by rememberSaveable {
                    mutableStateOf(prefs.getString("selected_mode", null))
                }
                when (selectedMode) {
                    "normal" ->
                        NormalModeScreen(
                            onBack = {
                                selectedMode = null
                                prefs.edit().remove("selected_mode").apply()
                            },
                        )

                    "mihon" ->
                        MihonModeScreen(
                            onBack = {
                                selectedMode = null
                                prefs.edit().remove("selected_mode").apply()
                            },
                        )

                    else ->
                        ModeSelectionScreen(
                            onNormal = {
                                selectedMode = "normal"
                                prefs.edit().putString("selected_mode", "normal").apply()
                            },
                            onMihon = {
                                selectedMode = "mihon"
                                prefs.edit().putString("selected_mode", "mihon").apply()
                            },
                        )
                }
            }
        }
    }
}

@Composable
fun ModeSelectionScreen(onNormal: () -> Unit, onMihon: () -> Unit) {
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(onClick = onMihon) {
                Text("Mihon Mode")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onNormal) {
                Text("Normal Mode")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NormalModeScreen(onBack: () -> Unit) {
    val activity = LocalContext.current as ComponentActivity
    val viewModel = remember(activity) { MainViewModel(ContextHelper(activity)) }
    val isCurrentlyConverting by viewModel.isCurrentlyConverting.collectAsState()
    val currentTaskStatus by viewModel.currentTaskStatus.collectAsState()
    val currentSubTaskStatus by viewModel.currentSubTaskStatus.collectAsState()
    val maxNumberOfPages by viewModel.maxNumberOfPages.collectAsState()
    val batchSize by viewModel.batchSize.collectAsState()
    val overrideSortOrderToUseOffset by viewModel.overrideSortOrderToUseOffset.collectAsState()
    val overrideMergeFiles by viewModel.overrideMergeFiles.collectAsState()
    val selectedFileName by viewModel.selectedFileName.collectAsState()
    val selectedFilesUri by viewModel.selectedFileUri.collectAsState()
    val overrideFileName by viewModel.overrideFileName.collectAsState()
    val overrideOutputDirectoryUri by viewModel.overrideOutputDirectoryUri.collectAsState()

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
            viewModel.updateUpdateSelectedFileUriFromUserInput(uris)
        }
    val directoryPickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
            uri?.let { viewModel.updateOverrideOutputPathFromUserInput(it) }
        }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CBZ Converter") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            CbzConverterPage(
                selectedFileName,
                viewModel,
                filePickerLauncher,
                isCurrentlyConverting,
                selectedFilesUri,
                currentTaskStatus,
                currentSubTaskStatus,
                maxNumberOfPages,
                batchSize,
                overrideSortOrderToUseOffset,
                overrideMergeFiles,
                overrideFileName,
                overrideOutputDirectoryUri,
                directoryPickerLauncher
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MihonModeScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mihon Mode") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentAlignment = Alignment.Center
        ) {
            MihonCbzConverterPage()
        }
    }
}

