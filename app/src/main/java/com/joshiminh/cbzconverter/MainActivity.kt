package com.joshiminh.cbzconverter

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.ContextHelper
import com.joshiminh.cbzconverter.backend.MainViewModel
import com.joshiminh.cbzconverter.ui.CbzConverterTheme
import com.joshiminh.cbzconverter.ui.MihonMode
import com.joshiminh.cbzconverter.ui.NormalMode

private const val PREFS_NAME = "cbz_prefs"
private const val KEY_LAST_SCREEN = "last_screen"

private enum class Screen { HOME, MIHON, NORMAL }

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val contextHelper = ContextHelper(this)
        val viewModel = MainViewModel(contextHelper)

        // Restore last screen (HOME if not saved)
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val initialScreen: Screen = runCatching {
            Screen.valueOf(prefs.getString(KEY_LAST_SCREEN, Screen.HOME.name) ?: Screen.HOME.name)
        }.getOrDefault(Screen.HOME)

        setContent {
            CbzConverterTheme {
                MainApp(
                    activity = this@MainActivity,
                    viewModel = viewModel,
                    initialScreen = initialScreen,
                    onPersistScreen = { screen ->
                        prefs.edit().putString(KEY_LAST_SCREEN, screen.name).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(
    activity: ComponentActivity,
    viewModel: MainViewModel,
    initialScreen: Screen,
    onPersistScreen: (Screen) -> Unit
) {
    var currentScreen by rememberSaveable { mutableStateOf(initialScreen) }

    // Persist whenever screen changes
    LaunchedEffect(currentScreen) { onPersistScreen(currentScreen) }

    when (currentScreen) {
        Screen.HOME -> {
            Scaffold(
                topBar = { TopAppBar(title = { Text("CBZ Converter") }) }
            ) { inner ->
                HomeScreen(
                    modifier = Modifier
                        .padding(inner)
                        .fillMaxSize()
                        .padding(24.dp),
                    onOpenMihon = { currentScreen = Screen.MIHON },
                    onOpenNormal = { currentScreen = Screen.NORMAL }
                )
            }
        }

        Screen.MIHON -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Mihon Mode") },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = Screen.HOME }) {
                                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { inner ->
                Column(
                    modifier = Modifier
                        .padding(inner)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    MihonMode()
                }
            }
        }

        Screen.NORMAL -> {
            // Collect states for Normal page (now includes config states)
            val isCurrentlyConverting by viewModel.isCurrentlyConverting.collectAsState()
            val currentTaskStatus by viewModel.currentTaskStatus.collectAsState()
            val currentSubTaskStatus by viewModel.currentSubTaskStatus.collectAsState()

            val selectedFileName by viewModel.selectedFileName.collectAsState()
            val selectedFilesUri by viewModel.selectedFileUri.collectAsState()

            val maxNumberOfPages by viewModel.maxNumberOfPages.collectAsState()
            val batchSize by viewModel.batchSize.collectAsState()
            val overrideSortOrderToUseOffset by viewModel.overrideSortOrderToUseOffset.collectAsState()
            val overrideMergeFiles by viewModel.overrideMergeFiles.collectAsState()
            val overrideFileName by viewModel.overrideFileName.collectAsState()
            val overrideOutputDirectoryUri by viewModel.overrideOutputDirectoryUri.collectAsState()

            // Pickers for files and directory (the latter is now required)
            val filePickerLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
                    if (uris.isNotEmpty()) viewModel.updateUpdateSelectedFileUriFromUserInput(uris)
                }

            val directoryPickerLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
                    uri?.let { viewModel.updateOverrideOutputPathFromUserInput(it) }
                }

            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Normal Mode") },
                        navigationIcon = {
                            IconButton(onClick = { currentScreen = Screen.HOME }) {
                                Icon(imageVector = Icons.Filled.ArrowBack, contentDescription = "Back")
                            }
                        }
                    )
                }
            ) { inner ->
                Column(
                    modifier = Modifier
                        .padding(inner)
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    NormalMode(
                        selectedFileName = selectedFileName,
                        viewModel = viewModel,
                        activity = activity,
                        filePickerLauncher = filePickerLauncher,
                        isCurrentlyConverting = isCurrentlyConverting,
                        selectedFilesUri = selectedFilesUri,
                        currentTaskStatus = currentTaskStatus,
                        currentSubTaskStatus = currentSubTaskStatus,
                        // NEW: pass all config-related UI args
                        maxNumberOfPages = maxNumberOfPages,
                        batchSize = batchSize,
                        overrideSortOrderToUseOffset = overrideSortOrderToUseOffset,
                        overrideMergeFiles = overrideMergeFiles,
                        overrideFileName = overrideFileName,
                        overrideOutputDirectoryUri = overrideOutputDirectoryUri,
                        directoryPickerLauncher = directoryPickerLauncher
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenMihon: () -> Unit,
    onOpenNormal: () -> Unit
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = onOpenMihon) { Text("Mihon Mode") }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onOpenNormal) { Text("Normal Mode") }
    }
}